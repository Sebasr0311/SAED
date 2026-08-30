package com.saed.backend.sanciones.service;

import com.saed.backend.sanciones.dto.SancionDTO;
import java.util.List;
import java.util.Map;

public interface SancionService {
    List<SancionDTO> getAllSanciones();
    List<SancionDTO> getMisSanciones();
    SancionDTO getSancionById(Long idSancion);
    Long createSancion(SancionDTO request);
    void submitDescargos(Long idSancion, Map<String, String> payload);
    void emitirResolucion(Long idSancion, Map<String, String> payload);
}
