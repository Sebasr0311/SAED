package com.saed.backend.person.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.person.dto.*;
import com.saed.backend.person.repository.DependentRepository;
import com.saed.backend.person.service.DependentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DependentServiceImpl implements DependentService {

    private final DependentRepository dependentRepository;

    public DependentServiceImpl(DependentRepository dependentRepository) {
        this.dependentRepository = dependentRepository;
    }

    // --- Mascotas ---
    @Override
    @Auditable(action = "CREATE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public MascotaDTO createMascota(MascotaRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para registrar mascotas en otra unidad");
            }
        }
        return dependentRepository.createMascota(request);
    }

    @Override
    @Transactional(readOnly = true)
    public MascotaDTO getMascotaById(Long id) {
        MascotaDTO mascota = dependentRepository.getMascotaById(id)
                .orElseThrow(() -> new RuntimeException("Mascota no encontrada"));
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(mascota.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar mascotas de otra unidad");
            }
        }
        return mascota;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MascotaDTO> getMascotasByUnidad(Long unidadId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unidadId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar mascotas de otra unidad");
            }
        }
        return dependentRepository.getMascotasByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public MascotaDTO updateMascota(Long id, MascotaRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            MascotaDTO existing = dependentRepository.getMascotaById(id)
                    .orElseThrow(() -> new RuntimeException("Mascota no encontrada"));
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(existing.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar mascotas de otra unidad");
            }
            if (request.unidadId() != null && ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No puede transferir una mascota a otra unidad");
            }
        }
        return dependentRepository.updateMascota(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteMascota(Long id) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            MascotaDTO existing = dependentRepository.getMascotaById(id)
                    .orElseThrow(() -> new RuntimeException("Mascota no encontrada"));
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(existing.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar mascotas de otra unidad");
            }
        }
        dependentRepository.deleteMascota(id);
    }

    // --- Vehiculos ---
    @Override
    @Auditable(action = "CREATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO createVehiculo(VehiculoRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para registrar vehículos en otra unidad");
            }
        }
        return dependentRepository.createVehiculo(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VehiculoDTO getVehiculoById(Long id) {
        VehiculoDTO vehiculo = dependentRepository.getVehiculoById(id)
                .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado"));
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(vehiculo.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar vehículos de otra unidad");
            }
        }
        return vehiculo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehiculoDTO> getVehiculosByUnidad(Long unidadId) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unidadId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar vehículos de otra unidad");
            }
        }
        return dependentRepository.getVehiculosByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO updateVehiculo(Long id, VehiculoRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            VehiculoDTO existing = dependentRepository.getVehiculoById(id)
                    .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado"));
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(existing.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar vehículos de otra unidad");
            }
            if (request.unidadId() != null && ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No puede transferir un vehículo a otra unidad");
            }
        }
        return dependentRepository.updateVehiculo(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteVehiculo(Long id) {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            VehiculoDTO existing = dependentRepository.getVehiculoById(id)
                    .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado"));
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(existing.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar vehículos de otra unidad");
            }
        }
        dependentRepository.deleteVehiculo(id);
    }

    // --- Tutores ---
    @Override
    @Auditable(action = "CREATE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TutorDTO createTutor(TutorRequestDTO request) {
        return dependentRepository.createTutor(request);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorDTO getTutorById(Long id) {
        return dependentRepository.getTutorById(id)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TutorDTO> getTutoresByMenor(Long menorId) {
        return dependentRepository.getTutoresByMenor(menorId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TutorDTO updateTutor(Long id, TutorRequestDTO request) {
        return dependentRepository.updateTutor(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteTutor(Long id) {
        dependentRepository.deleteTutor(id);
    }

    // --- Visitantes ---
    @Override
    @Auditable(action = "CREATE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VisitanteDTO createVisitante(VisitanteRequestDTO request) {
        return dependentRepository.createVisitante(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitanteDTO getVisitanteById(Long id) {
        return dependentRepository.getVisitanteById(id)
                .orElseThrow(() -> new RuntimeException("Visitante no encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public VisitanteDTO getVisitanteByPersona(Long personaId) {
        return dependentRepository.getVisitanteByPersona(personaId)
                .orElseThrow(() -> new RuntimeException("Visitante no encontrado"));
    }

    @Override
    @Auditable(action = "UPDATE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VisitanteDTO updateVisitante(Long id, VisitanteRequestDTO request) {
        return dependentRepository.updateVisitante(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteVisitante(Long id) {
        dependentRepository.deleteVisitante(id);
    }
}
