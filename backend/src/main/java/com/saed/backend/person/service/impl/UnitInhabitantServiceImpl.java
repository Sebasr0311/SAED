package com.saed.backend.person.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.UnitInhabitantService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Inhabitants of a unit. Every operation is scoped and fails closed (F5-H07):
 * <ul>
 *   <li>unit-scoped callers act only on their own unit; RESIDENTE_CONVIVENCIA only reads it (F05-02);</li>
 *   <li>a titular manages only non-titular household members (FAMILIAR, CONVIVIENTE, OTRO);</li>
 *   <li>administrators and porters act only on units of their property/organization;</li>
 *   <li>SUPERADMIN and callers without context have no operational access (DEC-02).</li>
 * </ul>
 * Unlinking a person revokes only that unit's resident access; other assignments and accounts are kept (F05-01).
 */
@Service
public class UnitInhabitantServiceImpl implements UnitInhabitantService {

    private static final Set<String> UNIT_ROLES = Set.of("RESIDENTE", "PROPIETARIO", "RESIDENTE_CONVIVENCIA");
    private static final Set<String> TITULAR_TYPES = Set.of("PROPIETARIO", "ARRENDATARIO", "PRINCIPAL");
    private static final Set<String> HOUSEHOLD_TYPES = Set.of("FAMILIAR", "CONVIVIENTE", "OTRO");

    private final UnitInhabitantRepository unitInhabitantRepository;
    private final com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public UnitInhabitantServiceImpl(UnitInhabitantRepository unitInhabitantRepository,
                                    com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService,
                                    org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate) {
        this.unitInhabitantRepository = unitInhabitantRepository;
        this.convivienteQuotaService = convivienteQuotaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public UnitInhabitantServiceImpl(UnitInhabitantRepository unitInhabitantRepository,
                                    com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService) {
        this(unitInhabitantRepository, convivienteQuotaService, null);
    }

    private static boolean isUnitCaller(SaedContext ctx) {
        return "UNIDAD".equals(ctx.getRoleScope()) || UNIT_ROLES.contains(ctx.getRoleCode());
    }

    private Long resolveResidentUnitId(SaedContext ctx) {
        if (ctx.getUnitId() != null) {
            return ctx.getUnitId();
        }
        if (ctx.getUserId() == null || jdbcTemplate == null) {
            return null;
        }
        List<Long> uList = jdbcTemplate.queryForList(
                "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA "
                        + "WHERE u.ID_USUARIO = :uid AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1",
                java.util.Map.of("uid", ctx.getUserId()), Long.class);
        if (!uList.isEmpty()) {
            return uList.get(0);
        }
        List<Long> aList = jdbcTemplate.queryForList(
                "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ESTADO = 'ACTIVA' "
                        + "AND ID_UNIDAD IS NOT NULL AND ROWNUM = 1",
                java.util.Map.of("uid", ctx.getUserId()), Long.class);
        return aList.isEmpty() ? null : aList.get(0);
    }

    /**
     * Verifies that the caller may act on the unit and returns the context. {@code manage} marks household
     * management (add/unlink/status/delete) as opposed to reads.
     */
    private SaedContext authorizeUnit(Long unitId, boolean manage) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null
                || "SUPERADMIN".equals(ctx.getRoleCode()) || "GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Operación no permitida en este contexto");
        }
        if (isUnitCaller(ctx)) {
            if (manage && "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode())) {
                throw new AccessDeniedException("El residente de convivencia no puede gestionar a otros habitantes");
            }
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId == null || !callerUnitId.equals(unitId)) {
                throw new AccessDeniedException("No tiene permisos sobre los habitantes de otra unidad");
            }
            return ctx;
        }
        UnitInhabitantRepository.UnitScope scope = unitInhabitantRepository.findUnitScope(unitId)
                .orElseThrow(() -> new NoSuchElementException("Unidad no encontrada"));
        if ("ORGANIZACION".equals(ctx.getRoleScope())) {
            if (ctx.getOrganizationId() == null || !ctx.getOrganizationId().equals(scope.organizationId())) {
                throw new AccessDeniedException("La unidad no pertenece a su organización");
            }
            return ctx;
        }
        if (ctx.getPropertyId() == null || !ctx.getPropertyId().equals(scope.propertyId())) {
            throw new AccessDeniedException("La unidad no pertenece a su propiedad");
        }
        return ctx;
    }

    /** PERSONAS has no row-level policy (DEC-01): a foreign identity is reported exactly like a missing one. */
    private void requirePersonOfCallerOrganization(SaedContext ctx, Long personaId) {
        if (personaId == null || !unitInhabitantRepository.personBelongsToOrganization(personaId, ctx.getOrganizationId())) {
            throw new NoSuchElementException("Persona no encontrada");
        }
    }

    private static String normalizedType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }

    private void assertNotTitularForUnitCaller(SaedContext ctx, UnitResidentDTO resident) {
        if (isUnitCaller(ctx) && TITULAR_TYPES.contains(normalizedType(resident.tipoResidente()))) {
            throw new AccessDeniedException("No se puede modificar ni desvincular al titular principal de la unidad");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOwnerDTO> getOwnersByUnitId(Long unitId) {
        authorizeUnit(unitId, false);
        return unitInhabitantRepository.findOwnersByUnitId(unitId);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PROPIETARIO_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public Long addOwner(Long unitId, UnitOwnerRequestDTO request) {
        SaedContext ctx = authorizeUnit(unitId, true);
        if (isUnitCaller(ctx)) {
            throw new AccessDeniedException("Solo la administración registra propietarios");
        }
        requirePersonOfCallerOrganization(ctx, request.personaId());
        return unitInhabitantRepository.insertOwner(unitId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitResidentDTO> getResidentsByUnitId(Long unitId) {
        authorizeUnit(unitId, false);
        return unitInhabitantRepository.findResidentsByUnitId(unitId);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public Long addResident(Long unitId, UnitResidentRequestDTO request) {
        SaedContext ctx = authorizeUnit(unitId, true);
        String tipo = normalizedType(request.tipoResidente());
        if (isUnitCaller(ctx) && !HOUSEHOLD_TYPES.contains(tipo)) {
            throw new AccessDeniedException("Como residente solo puede registrar convivientes o familiares");
        }

        requirePersonOfCallerOrganization(ctx, request.personaId());

        // F05-02: the unit row is locked before counting, so parallel additions cannot exceed the quota.
        if ("CONVIVIENTE".equals(tipo)) {
            convivienteQuotaService.validateAndLockQuota(unitId);
        }

        if ("ARRENDATARIO".equals(tipo)) {
            Integer activeContracts = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = :unitId AND ID_ARRENDATARIO_PRINCIPAL = :personaId AND ESTADO = 'ACTIVO'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("unitId", unitId)
                    .addValue("personaId", request.personaId()),
                Integer.class
            );
            if (activeContracts == null || activeContracts == 0) {
                throw new IllegalArgumentException("Un habitante de tipo ARRENDATARIO requiere un contrato de arrendamiento activo asociado a la unidad");
            }
        }

        return unitInhabitantRepository.insertResident(unitId, request);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_STATUS", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.WARN)
    public void updateResidentStatus(Long unitId, Long residentId, String status) {
        SaedContext ctx = authorizeUnit(unitId, true);

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new NoSuchElementException("Habitante no encontrado en la unidad indicada"));
        assertNotTitularForUnitCaller(ctx, resident);

        if ("ACTIVO".equalsIgnoreCase(status)) {
            if ("ARRENDATARIO".equals(normalizedType(resident.tipoResidente()))) {
                Long personaId = resident.persona() != null ? resident.persona().id() : null;
                Integer activeContracts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = :unitId AND ID_ARRENDATARIO_PRINCIPAL = :personaId AND ESTADO = 'ACTIVO'",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("unitId", unitId)
                        .addValue("personaId", personaId),
                    Integer.class
                );
                if (activeContracts == null || activeContracts == 0) {
                    throw new IllegalArgumentException("No se puede activar un arrendatario sin un contrato de arrendamiento activo asociado a la unidad");
                }
            }
            convivienteQuotaService.validateAndLockQuotaForReactivation(unitId, residentId);
        }

        int updated = unitInhabitantRepository.updateResidentStatus(unitId, residentId, status);
        if (updated == 0) {
            throw new NoSuchElementException("No se pudo actualizar el estado del habitante");
        }
    }

    @Override
    @Transactional
    @Auditable(action = "UNLINK", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public void unlinkResident(Long unitId, Long residentId) {
        SaedContext ctx = authorizeUnit(unitId, true);

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new NoSuchElementException("Habitante no encontrado en la unidad indicada"));
        assertNotTitularForUnitCaller(ctx, resident);

        int unlinked = unitInhabitantRepository.unlinkResident(unitId, residentId);
        if (unlinked == 0) {
            throw new NoSuchElementException("No se pudo desvincular el habitante");
        }
        revokeUnitAccess(ctx, unitId, resident);
    }

    @Override
    @Transactional
    @Auditable(action = "DELETE_PERMANENT", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    public void deleteResidentPermanently(Long unitId, Long residentId) {
        SaedContext ctx = authorizeUnit(unitId, true);

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new NoSuchElementException("Habitante no encontrado en la unidad indicada"));
        assertNotTitularForUnitCaller(ctx, resident);

        // A titular's revocation is validated against the (inactive) residence row, so it runs before the row is
        // removed; everything is one transaction.
        if (isUnitCaller(ctx)) {
            unitInhabitantRepository.unlinkResident(unitId, residentId);
            revokeUnitAccess(ctx, unitId, resident);
        }
        int deleted = unitInhabitantRepository.deleteResidentPermanently(unitId, residentId);
        if (deleted == 0) {
            throw new NoSuchElementException("No se pudo eliminar el habitante");
        }
        if (!isUnitCaller(ctx)) {
            revokeUnitAccess(ctx, unitId, resident);
        }
    }

    /**
     * F05-01: revoke only this unit's resident access. Administrators do it in their own database context; a
     * titular uses the validated security procedure because row-level policies hide other users' assignments.
     * Any failure propagates and rolls the unlink back.
     */
    private void revokeUnitAccess(SaedContext ctx, Long unitId, UnitResidentDTO resident) {
        Long personaId = resident.persona() != null ? resident.persona().id() : null;
        if (personaId == null) {
            return;
        }
        if (isUnitCaller(ctx)) {
            unitInhabitantRepository.revokeHouseholdAccessAsTitular(unitId, personaId);
        } else {
            unitInhabitantRepository.revokeUnitResidentAccess(unitId, personaId);
        }
    }
}
