package com.saed.backend.person.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaBatchFilaErrorDTO;
import com.saed.backend.person.dto.PersonaBatchItemDTO;
import com.saed.backend.person.dto.PersonaBatchResultDTO;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.person.service.PersonaService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * DEC-01: PERSONAS has no row-level policy, so every read and write is scoped here and in the repository SQL.
 * <ul>
 *   <li>Administrators (property/organization scope) edit persons visible in their scope whose identity belongs to
 *   their organization; the identity of another tenant is never overwritten.</li>
 *   <li>Unit-scoped callers edit themselves; a titular (RESIDENTE/PROPIETARIO) also edits non-titular members of
 *   their household; RESIDENTE_CONVIVENCIA edits only themselves. Document identity is immutable for them.</li>
 *   <li>SUPERADMIN and callers without context have no operational access (DEC-02).</li>
 * </ul>
 * Out-of-scope persons are reported as not found.
 */
@Service
public class PersonaServiceImpl implements PersonaService {

    private static final String NOT_FOUND = "Persona no encontrada";
    private static final Set<String> UNIT_ROLES = Set.of("RESIDENTE", "PROPIETARIO", "RESIDENTE_CONVIVENCIA");
    private static final Set<String> TITULAR_ROLES = Set.of("RESIDENTE", "PROPIETARIO");
    private static final Set<String> HOUSEHOLD_TYPES = Set.of("FAMILIAR", "CONVIVIENTE", "OTRO");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN_PROPIEDAD", "ADMIN_ORGANIZACION");

    private final PersonaRepository personaRepository;

    public PersonaServiceImpl(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    private static boolean isUnitCaller(SaedContext ctx) {
        return "UNIDAD".equals(ctx.getRoleScope()) || UNIT_ROLES.contains(ctx.getRoleCode());
    }

    private static boolean isAdminCaller(SaedContext ctx) {
        return ADMIN_ROLES.contains(ctx.getRoleCode())
                && ("PROPIEDAD".equals(ctx.getRoleScope()) || "ORGANIZACION".equals(ctx.getRoleScope()));
    }

    private static SaedContext requireTenantContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null || "SUPERADMIN".equals(ctx.getRoleCode())
                || "GLOBAL".equals(ctx.getRoleScope()) || ctx.getOrganizationId() == null) {
            throw new AccessDeniedException("Operación no permitida sin contexto de organización");
        }
        return ctx;
    }

    private boolean isSelf(SaedContext ctx, Long personaId) {
        return personaRepository.findPersonaIdByUserId(ctx.getUserId())
                .map(personaId::equals)
                .orElse(false);
    }

    private boolean belongsToCallerOrganization(SaedContext ctx, Long personaId) {
        Optional<Long> org = personaRepository.findOrganizationId(personaId);
        // Legacy identities without a home organization are editable only when already visible in scope.
        return org.isEmpty() || ctx.getOrganizationId().equals(org.get());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonaDTO> getPersonas(int page, int size) {
        int offset = page * size;
        return personaRepository.findAll(size, offset);
    }

    @Override
    @Transactional
    public Long createPersona(PersonaRequestDTO request) {
        return personaRepository.insert(request);
    }

    @Override
    @Transactional
    public void updatePersona(Long id, PersonaRequestDTO request) {
        SaedContext ctx = requireTenantContext();
        PersonaDTO current = personaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));

        if (isAdminCaller(ctx)) {
            if (!isSelf(ctx, id) && !belongsToCallerOrganization(ctx, id)) {
                throw new AccessDeniedException(
                        "La identidad pertenece a otra organización y no puede modificarse desde este perímetro");
            }
            personaRepository.update(id, request);
            return;
        }

        if (!isUnitCaller(ctx)) {
            throw new AccessDeniedException("No tiene permisos para modificar personas");
        }
        if (!isSelf(ctx, id) && !isManagedHouseholdMember(ctx, id)) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        if (!Objects.equals(current.numeroDocumento(), request.numeroDocumento())
                || !Objects.equals(current.tipoDocumentoId(), request.tipoDocumentoId())) {
            throw new IllegalArgumentException("El documento de identidad solo puede ser corregido por la administración");
        }
        personaRepository.update(id, request);
    }

    /** F05-02: a titular manages the non-titular members of their own household. */
    private boolean isManagedHouseholdMember(SaedContext ctx, Long personaId) {
        if (!TITULAR_ROLES.contains(ctx.getRoleCode()) || ctx.getUnitId() == null) {
            return false;
        }
        Optional<String> type = personaRepository.findActiveResidentType(personaId, ctx.getUnitId());
        return type.map(t -> HOUSEHOLD_TYPES.contains(t.toUpperCase())).orElse(false)
                && belongsToCallerOrganization(ctx, personaId);
    }

    @Override
    @Transactional
    public void deletePersona(Long id) {
        SaedContext ctx = requireTenantContext();
        if (!isAdminCaller(ctx)) {
            throw new AccessDeniedException("No tiene permisos para desvincular personas");
        }
        personaRepository.findById(id).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        Long propertyId = "PROPIEDAD".equals(ctx.getRoleScope()) ? ctx.getPropertyId() : null;
        if ("PROPIEDAD".equals(ctx.getRoleScope()) && propertyId == null) {
            throw new AccessDeniedException("Sin propiedad activa");
        }
        personaRepository.unlinkFromScope(id, propertyId, ctx.getOrganizationId());
    }

    @Override
    @Transactional(readOnly = true)
    public PersonaDTO getPersonaById(Long id) {
        PersonaDTO persona = personaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && isUnitCaller(ctx) && !isSelf(ctx, id)
                && (ctx.getUnitId() == null || !personaRepository.isLinkedToUnit(id, ctx.getUnitId()))) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        return persona;
    }

    @Override
    @Transactional(readOnly = true)
    public PersonaDTO getPersonaByUserId(Long userId) {
        return personaRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
    }

    @Override
    @Transactional
    public PersonaBatchResultDTO importarBatch(List<PersonaBatchItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return new PersonaBatchResultDTO(0, 0, 0, List.of());
        }
        SaedContext ctx = requireTenantContext();
        Long propId = ctx.getPropertyId();

        int procesados = 0;
        int fallidos = 0;
        List<PersonaBatchFilaErrorDTO> errores = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            PersonaBatchItemDTO item = items.get(i);
            int fila = i + 1;

            if (item.numeroDocumento() == null || item.numeroDocumento().isBlank()) {
                fallidos++;
                errores.add(new PersonaBatchFilaErrorDTO(fila, "—", "El número de documento es obligatorio"));
                continue;
            }

            if (item.nombres() == null || item.nombres().isBlank() || item.apellidos() == null || item.apellidos().isBlank()) {
                fallidos++;
                errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), "Nombres y apellidos son obligatorios"));
                continue;
            }

            try {
                Long idTipoDoc = personaRepository.findTipoDocumentoIdByCodigo(item.tipoDocumento()).orElse(null);
                if (idTipoDoc == null) {
                    fallidos++;
                    errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), "Tipo de documento no reconocido"));
                    continue;
                }

                String[] nomParts = item.nombres().trim().split("\\s+", 2);
                String[] apeParts = item.apellidos().trim().split("\\s+", 2);
                PersonaRequestDTO req = new PersonaRequestDTO(
                        idTipoDoc,
                        item.numeroDocumento().trim(),
                        "NATURAL",
                        nomParts[0],
                        nomParts.length > 1 ? nomParts[1] : "",
                        apeParts[0],
                        apeParts.length > 1 ? apeParts[1] : "",
                        item.email() != null ? item.email().trim() : null,
                        item.telefono() != null ? item.telefono().trim() : null
                );

                Long personaId;
                Optional<PersonaDTO> exist = personaRepository.findByNumeroDocumento(item.numeroDocumento().trim());
                if (exist.isPresent()) {
                    personaId = exist.get().id();
                    if (belongsToCallerOrganization(ctx, personaId)) {
                        personaRepository.update(personaId, req);
                    } else {
                        // DEC-01: the global identity of another tenant is never overwritten by an import.
                        errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(),
                                "La persona ya está registrada por otra organización; sus datos no se modificaron"));
                    }
                } else {
                    personaId = personaRepository.insert(req);
                }

                if (item.apartamento() != null && !item.apartamento().isBlank() && propId != null) {
                    Optional<Long> unidadOpt = personaRepository.findUnidadIdByNumero(propId, item.apartamento().trim());
                    if (unidadOpt.isPresent()) {
                        personaRepository.asignarUnidad(personaId, unidadOpt.get(), item.tipoRelacion());
                    } else {
                        errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(),
                                "Persona guardada, pero la unidad/apartamento '" + item.apartamento().trim()
                                        + "' no existe en la propiedad activa"));
                    }
                }

                procesados++;
            } catch (DuplicateKeyException ex) {
                fallidos++;
                errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(),
                        "Documento o correo ya registrado"));
            } catch (DataAccessException ex) {
                // Never expose raw database messages (ORA codes, constraint names) to the client.
                fallidos++;
                errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(),
                        "No fue posible guardar la fila por una restricción de datos"));
            } catch (IllegalArgumentException ex) {
                fallidos++;
                errores.add(new PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), ex.getMessage()));
            }
        }

        return new PersonaBatchResultDTO(items.size(), procesados, fallidos, errores);
    }
}
