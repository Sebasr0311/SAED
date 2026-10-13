package com.saed.backend.person.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.person.dto.ResidentRegistrationRequest;
import com.saed.backend.person.dto.ResidentRegistrationResult;
import com.saed.backend.person.dto.TutorRequestDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * F05-01: registers a resident in ONE transaction, all or nothing: person, optional guardian (with its link),
 * optional lease, the link to the unit (owner and/or resident) and an optional access account. Every step reuses
 * the service that already owns its validation (persons, unit inhabitants, guardians, contracts, accounts), so
 * their scope, quota and duplicate rules apply unchanged. Any failure rolls the whole registration back.
 *
 * Scope comes from the server context: only ADMIN_PROPIEDAD / ADMIN_ORGANIZACION, only units of their own
 * property or organization; a unit outside it is reported exactly like a missing one.
 */
@Service
public class ResidentRegistrationService {

    private static final Set<String> RELATIONS = Set.of("PROPIETARIO_RESIDENTE", "PROPIETARIO_NO_RESIDENTE",
            "ARRENDATARIO", "CONVIVIENTE", "FAMILIAR", "OTRO");

    private final PersonaService personaService;
    private final UnitInhabitantService unitInhabitantService;
    private final UnitInhabitantRepository unitInhabitantRepository;
    private final DependentService dependentService;
    private final FinanzasService finanzasService;
    private final AccountProvisioner accountProvisioner;

    public ResidentRegistrationService(PersonaService personaService,
                                       UnitInhabitantService unitInhabitantService,
                                       UnitInhabitantRepository unitInhabitantRepository,
                                       DependentService dependentService,
                                       FinanzasService finanzasService,
                                       AccountProvisioner accountProvisioner) {
        this.personaService = personaService;
        this.unitInhabitantService = unitInhabitantService;
        this.unitInhabitantRepository = unitInhabitantRepository;
        this.dependentService = dependentService;
        this.finanzasService = finanzasService;
        this.accountProvisioner = accountProvisioner;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResidentRegistrationResult register(ResidentRegistrationRequest request) {
        SaedContext ctx = requireAdministrator();
        String relation = request.tipoRelacion() == null ? "" : request.tipoRelacion().trim().toUpperCase(Locale.ROOT);
        if (!RELATIONS.contains(relation)) {
            throw new IllegalArgumentException("Tipo de relación no válido");
        }
        boolean owner = relation.startsWith("PROPIETARIO");
        boolean lives = !"PROPIETARIO_NO_RESIDENTE".equals(relation);
        if ("ARRENDATARIO".equals(relation) && request.contract() == null) {
            throw new IllegalArgumentException("Un arrendatario requiere los datos del contrato de arrendamiento");
        }
        if (request.contract() != null && !"ARRENDATARIO".equals(relation)) {
            throw new IllegalArgumentException("Solo un arrendatario puede registrarse con un contrato de arrendamiento");
        }
        if (request.account() != null && (request.person().email() == null || request.person().email().isBlank())) {
            throw new IllegalArgumentException("Para crear la cuenta de acceso la persona debe tener un correo electrónico");
        }

        Long unitId = request.unitId();
        requireUnitInScope(ctx, unitId);
        Long guardianExistingId = null;
        if (request.guardian() != null) {
            guardianExistingId = request.guardian().existingPersonId();
            if (guardianExistingId == null && request.guardian().person() == null) {
                throw new IllegalArgumentException("Indique la persona tutora");
            }
            if (guardianExistingId != null
                    && !unitInhabitantRepository.personBelongsToOrganization(guardianExistingId, ctx.getOrganizationId())) {
                throw new NoSuchElementException("Persona no encontrada");
            }
        }

        // 1. Persons
        Long personId = personaService.createPersona(request.person());
        Long guardianId = guardianExistingId;
        if (request.guardian() != null && guardianId == null) {
            guardianId = personaService.createPersona(request.guardian().person());
        }

        // 2. Lease (the tenant must have an active lease before being linked as ARRENDATARIO)
        Long contractId = null;
        if (request.contract() != null) {
            ResidentRegistrationRequest.Contract c = request.contract();
            contractId = finanzasService.createContrato(new ContratoRequestDTO(unitId, personId,
                    c.fechaInicio() != null ? c.fechaInicio() : java.time.LocalDate.now(), c.fechaFin(),
                    c.tipoContrato() != null ? c.tipoContrato() : "INICIAL", c.canonMensual(), c.idPlantilla(),
                    guardianId, null, false));
        }

        // 3. Links to the unit
        Long ownerLinkId = null;
        if (owner) {
            ownerLinkId = unitInhabitantService.addOwner(unitId, new UnitOwnerRequestDTO(personId, new BigDecimal("100"), "S"));
        }
        Long residentLinkId = null;
        if (lives) {
            String residentType = switch (relation) {
                case "PROPIETARIO_RESIDENTE" -> "PROPIETARIO";
                default -> relation;
            };
            residentLinkId = unitInhabitantService.addResident(unitId, new UnitResidentRequestDTO(personId, residentType));
        }

        // 4. Guardian link (needs the dependent to be linked to the unit first)
        if (request.guardian() != null) {
            dependentService.createTutor(new TutorRequestDTO(personId, guardianId,
                    request.guardian().parentesco(), null, "ACTIVO"));
        }

        // 5. Access account
        Long userId = null;
        if (request.account() != null) {
            Map<String, Object> result = accountProvisioner.provision(accountPayload(request, relation, personId));
            Object id = result.get("idUsuario");
            userId = id instanceof Number n ? n.longValue() : null;
        }

        return new ResidentRegistrationResult(personId, request.guardian() != null ? guardianId : null, contractId,
                residentLinkId, ownerLinkId, userId);
    }

    private static Map<String, Object> accountPayload(ResidentRegistrationRequest request, String relation, Long personId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", request.account().username().trim().toLowerCase(Locale.ROOT));
        if (request.account().password() != null && !request.account().password().isBlank()) {
            payload.put("password", request.account().password().trim());
        }
        payload.put("enviarCorreoActivacion", !Boolean.FALSE.equals(request.account().enviarCorreoActivacion()));
        payload.put("rol", "PROPIETARIO_NO_RESIDENTE".equals(relation) ? "PROPIETARIO" : "RESIDENTE");
        payload.put("activo", true);
        payload.put("email", request.person().email().trim().toLowerCase(Locale.ROOT));
        payload.put("telefono", request.person().telefono() == null ? "" : request.person().telefono().trim());
        payload.put("primerNombre", request.person().primerNombre());
        payload.put("primerApellido", request.person().primerApellido());
        payload.put("numeroDocumento", request.person().numeroDocumento());
        payload.put("idPersona", personId);
        payload.put("idUnidad", request.unitId());
        return payload;
    }

    private static SaedContext requireAdministrator() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null || ctx.getOrganizationId() == null) {
            throw new AccessDeniedException("Se requiere un contexto de administración");
        }
        String role = ctx.getRoleCode();
        if (!"ADMIN_PROPIEDAD".equalsIgnoreCase(role) && !"ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo la administración registra residentes");
        }
        return ctx;
    }

    private void requireUnitInScope(SaedContext ctx, Long unitId) {
        UnitInhabitantRepository.UnitScope scope = unitInhabitantRepository.findUnitScope(unitId)
                .orElseThrow(() -> new NoSuchElementException("Unidad no encontrada"));
        boolean inScope = "ADMIN_ORGANIZACION".equalsIgnoreCase(ctx.getRoleCode())
                ? ctx.getOrganizationId().equals(scope.organizationId())
                : ctx.getPropertyId() != null && ctx.getPropertyId().equals(scope.propertyId());
        if (!inScope) {
            throw new NoSuchElementException("Unidad no encontrada");
        }
    }
}
