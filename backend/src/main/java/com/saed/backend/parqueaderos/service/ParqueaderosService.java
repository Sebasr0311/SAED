package com.saed.backend.parqueaderos.service;

import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;

import java.util.List;

public interface ParqueaderosService {
    List<ParqueaderoDTO> getParqueaderos();
    List<ParqueaderoDTO> getParqueaderos(String estado, String tipo);
    ParqueaderoDTO getParqueaderoById(Long id);
    ParqueaderoDTO registrarParqueadero(ParqueaderoRequestDTO request);
    ParqueaderoDTO actualizarParqueadero(Long id, ParqueaderoRequestDTO request);
    void eliminarParqueadero(Long id);

    List<AsignacionParqueaderoDTO> getAsignaciones();
    AsignacionParqueaderoDTO getAsignacionById(Long id);
    AsignacionParqueaderoDTO crearAsignacion(AsignacionParqueaderoRequestDTO request);
    void finalizarAsignacion(Long id);
}
