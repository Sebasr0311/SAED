package com.saed.backend.sanciones.service;

import com.saed.backend.sanciones.dto.DescargoRequestDTO;
import com.saed.backend.sanciones.dto.ResolucionRequestDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;

import java.util.List;

public interface SancionService {
    List<SancionDTO> getAllSanciones();
    SancionDTO getSancionById(Long id);
    SancionDTO crearPliego(SancionCreateRequestDTO request);
    void radicarDescargos(Long idSancion, DescargoRequestDTO request);
    void emitirResolucion(Long idSancion, ResolucionRequestDTO request);
    List<SancionDTO> getMisSanciones();
}
