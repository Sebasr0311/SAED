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
        return dependentRepository.createMascota(request);
    }

    @Override
    @Transactional(readOnly = true)
    public MascotaDTO getMascotaById(Long id) {
        return dependentRepository.getMascotaById(id)
                .orElseThrow(() -> new RuntimeException("Mascota no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MascotaDTO> getMascotasByUnidad(Long unidadId) {
        return dependentRepository.getMascotasByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public MascotaDTO updateMascota(Long id, MascotaRequestDTO request) {
        return dependentRepository.updateMascota(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteMascota(Long id) {
        dependentRepository.deleteMascota(id);
    }

    // --- Vehiculos ---
    @Override
    @Auditable(action = "CREATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO createVehiculo(VehiculoRequestDTO request) {
        return dependentRepository.createVehiculo(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VehiculoDTO getVehiculoById(Long id) {
        return dependentRepository.getVehiculoById(id)
                .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehiculoDTO> getVehiculosByUnidad(Long unidadId) {
        return dependentRepository.getVehiculosByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO updateVehiculo(Long id, VehiculoRequestDTO request) {
        return dependentRepository.updateVehiculo(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteVehiculo(Long id) {
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
