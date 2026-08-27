package com.saed.backend.parqueaderos.repository;

import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;

import java.util.List;
import java.util.Optional;

public interface ParqueaderosRepository {
    // Parqueaderos
    List<ParqueaderoDTO> getParqueaderos();
    Optional<ParqueaderoDTO> getParqueaderoById(Long id);
    ParqueaderoDTO registrarParqueadero(ParqueaderoRequestDTO request, Long idPropiedad);
    ParqueaderoDTO actualizarParqueadero(Long id, ParqueaderoRequestDTO request);
    void eliminarParqueadero(Long id);

    // Asignaciones
    List<AsignacionParqueaderoDTO> getAsignaciones();
    Optional<AsignacionParqueaderoDTO> getAsignacionById(Long id);
    AsignacionParqueaderoDTO crearAsignacion(AsignacionParqueaderoRequestDTO request);
    void finalizarAsignacion(Long id);
    void actualizarEstadoParqueadero(Long idParqueadero, String estado);
}
