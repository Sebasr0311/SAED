package com.saed.backend.sanciones.service;

import com.saed.backend.sanciones.dto.DescargoRequestDTO;
import com.saed.backend.sanciones.dto.ResolucionRequestDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;

import java.time.LocalDate;
import java.util.List;

public interface SancionService {
    List<SancionDTO> getAllSanciones();
    SancionDTO getSancionById(Long id);
    SancionDTO crearPliego(SancionCreateRequestDTO request);
    void radicarDescargos(Long idSancion, DescargoRequestDTO request);
    void emitirResolucion(Long idSancion, ResolucionRequestDTO request);
    void anularSancion(Long idSancion, String motivo);
    List<SancionDTO> getMisSanciones();

    /**
     * Whether the unit is under an APLICADA common-area suspension on the given date (audit F10-07).
     * Intended for the reservations module; evaluated under the caller's tenant context.
     */
    boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date);
}
