package com.saed.backend.person.service;

import com.saed.backend.person.dto.*;

import java.util.List;

public interface DependentService {
    // Mascotas
    MascotaDTO createMascota(MascotaRequestDTO request);
    MascotaDTO getMascotaById(Long id);
    List<MascotaDTO> getMascotasByUnidad(Long unidadId);
    MascotaDTO updateMascota(Long id, MascotaRequestDTO request);
    void deleteMascota(Long id);

    // Vehiculos
    VehiculoDTO createVehiculo(VehiculoRequestDTO request);
    VehiculoDTO getVehiculoById(Long id);
    List<VehiculoDTO> getVehiculosByUnidad(Long unidadId);
    VehiculoDTO updateVehiculo(Long id, VehiculoRequestDTO request);
    void deleteVehiculo(Long id);

    // Tutores
    TutorDTO createTutor(TutorRequestDTO request);
    TutorDTO getTutorById(Long id);
    List<TutorDTO> getTutoresByMenor(Long menorId);
    TutorDTO updateTutor(Long id, TutorRequestDTO request);
    void deleteTutor(Long id);

    // Visitantes
    VisitanteDTO createVisitante(VisitanteRequestDTO request);
    VisitanteDTO getVisitanteById(Long id);
    VisitanteDTO getVisitanteByPersona(Long personaId);
    VisitanteDTO updateVisitante(Long id, VisitanteRequestDTO request);
    void deleteVisitante(Long id);
}
