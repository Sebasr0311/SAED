package com.saed.backend.person.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.UnitInhabitantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnitInhabitantServiceImpl implements UnitInhabitantService {
    
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

    private Long resolveResidentUnitId(com.saed.backend.context.SaedContext ctx) {
        if (ctx == null) return null;
        if (ctx.getUnitId() != null) return ctx.getUnitId();
        if (ctx.getUserId() != null && jdbcTemplate != null) {
            try {
                List<Long> uList = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :uid AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1",
                    java.util.Map.of("uid", ctx.getUserId()), Long.class
                );
                if (!uList.isEmpty()) return uList.get(0);
                List<Long> aList = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ESTADO = 'ACTIVA' AND ID_UNIDAD IS NOT NULL AND ROWNUM = 1",
                    java.util.Map.of("uid", ctx.getUserId()), Long.class
                );
                if (!aList.isEmpty()) return aList.get(0);
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOwnerDTO> getOwnersByUnitId(Long unitId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar habitantes de otra unidad");
            }
        }
        return unitInhabitantRepository.findOwnersByUnitId(unitId);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PROPIETARIO_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public Long addOwner(Long unitId, UnitOwnerRequestDTO request) {
        return unitInhabitantRepository.insertOwner(unitId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitResidentDTO> getResidentsByUnitId(Long unitId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar habitantes de otra unidad");
            }
        }
        return unitInhabitantRepository.findResidentsByUnitId(unitId);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public Long addResident(Long unitId, UnitResidentRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para registrar convivientes en otra unidad");
            }
            if ("PROPIETARIO".equalsIgnoreCase(request.tipoResidente()) || "ARRENDATARIO".equalsIgnoreCase(request.tipoResidente())) {
                throw new org.springframework.security.access.AccessDeniedException("Como residente solo puede registrar convivientes o familiares");
            }
        }

        // Si es tipo conviviente (no titular), validar cuota con bloqueo pesimista
        String tipo = request.tipoResidente() != null ? request.tipoResidente().trim().toUpperCase() : "";
        if ("CONVIVIENTE".equals(tipo) || "FAMILIAR".equals(tipo) || "OTRO".equals(tipo)) {
            convivienteQuotaService.validateAndLockQuota(unitId);
        }

        return unitInhabitantRepository.insertResident(unitId, request);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_STATUS", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.WARN)
    public void updateResidentStatus(Long unitId, Long residentId, String status) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar habitantes de otra unidad");
            }
        }

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Habitante no encontrado en la unidad indicada"));

        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            String tipo = resident.tipoResidente() != null ? resident.tipoResidente().toUpperCase() : "";
            if ("PROPIETARIO".equals(tipo) || "ARRENDATARIO".equals(tipo) || "TITULAR".equals(tipo) || "PRINCIPAL".equals(tipo)) {
                throw new org.springframework.security.access.AccessDeniedException("No se puede modificar ni desvincular al titular principal de la unidad");
            }
        }

        if ("ACTIVO".equalsIgnoreCase(status)) {
            convivienteQuotaService.validateAndLockQuotaForReactivation(unitId, residentId);
        }

        int updated = unitInhabitantRepository.updateResidentStatus(unitId, residentId, status);
        if (updated == 0) {
            throw new java.util.NoSuchElementException("No se pudo actualizar el estado del habitante");
        }
    }

    @Override
    @Transactional
    @Auditable(action = "UNLINK", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.HIGH)
    public void unlinkResident(Long unitId, Long residentId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para desvincular habitantes de otra unidad");
            }
        }

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Habitante no encontrado en la unidad indicada"));

        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            String tipo = resident.tipoResidente() != null ? resident.tipoResidente().toUpperCase() : "";
            if ("PROPIETARIO".equals(tipo) || "ARRENDATARIO".equals(tipo) || "TITULAR".equals(tipo) || "PRINCIPAL".equals(tipo)) {
                throw new org.springframework.security.access.AccessDeniedException("No se puede modificar ni desvincular al titular principal de la unidad");
            }
        }

        int unlinked = unitInhabitantRepository.unlinkResident(unitId, residentId);
        if (unlinked == 0) {
            throw new java.util.NoSuchElementException("No se pudo desvincular el habitante");
        }
    }

    @Override
    @Transactional
    @Auditable(action = "DELETE_PERMANENT", resource = "RESIDENTE_UNIDAD", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    public void deleteResidentPermanently(Long unitId, Long residentId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            Long callerUnitId = resolveResidentUnitId(ctx);
            if (callerUnitId != null && !callerUnitId.equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar habitantes de otra unidad");
            }
        }

        UnitResidentDTO resident = unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Habitante no encontrado en la unidad indicada"));

        if (ctx != null && ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope()))) {
            String tipo = resident.tipoResidente() != null ? resident.tipoResidente().toUpperCase() : "";
            if ("PROPIETARIO".equals(tipo) || "ARRENDATARIO".equals(tipo) || "TITULAR".equals(tipo) || "PRINCIPAL".equals(tipo)) {
                throw new org.springframework.security.access.AccessDeniedException("No se puede eliminar al titular principal de la unidad");
            }
        }

        int deleted = unitInhabitantRepository.deleteResidentPermanently(unitId, residentId);
        if (deleted == 0) {
            throw new java.util.NoSuchElementException("No se pudo eliminar el habitante");
        }
    }
}
