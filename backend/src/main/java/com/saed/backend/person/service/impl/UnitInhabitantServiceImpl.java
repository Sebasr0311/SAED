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

    public UnitInhabitantServiceImpl(UnitInhabitantRepository unitInhabitantRepository,
                                    com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService) {
        this.unitInhabitantRepository = unitInhabitantRepository;
        this.convivienteQuotaService = convivienteQuotaService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOwnerDTO> getOwnersByUnitId(Long unitId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unitId)) {
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
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unitId)) {
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
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unitId)) {
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
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar habitantes de otra unidad");
            }
        }

        unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Habitante no encontrado en la unidad indicada"));

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
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para desvincular habitantes de otra unidad");
            }
        }

        unitInhabitantRepository.findResidentByIdAndUnitId(unitId, residentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Habitante no encontrado en la unidad indicada"));

        int unlinked = unitInhabitantRepository.unlinkResident(unitId, residentId);
        if (unlinked == 0) {
            throw new java.util.NoSuchElementException("No se pudo desvincular el habitante");
        }
    }
}
