package com.saed.backend.obras.service;

import com.saed.backend.obras.dto.ObraDTO;
import java.util.List;

public interface ObraService {
    List<ObraDTO> getObrasAdmin();
    List<ObraDTO> getMisObras();
    ObraDTO getObraById(Long idObra);
    Long solicitarObra(ObraDTO request);
    void aprobarObra(Long idObra);
    void iniciarObra(Long idObra);
    void rechazarObra(Long idObra);
    /** Completes an active work; requires an accepting final inspection (F09-03). */
    void finalizarObra(Long idObra, com.saed.backend.obras.dto.InspeccionObraDTO inspeccion);

    /** Gate check: may this worker enter this work on this date? (F09-03) */
    com.saed.backend.obras.dto.IngresoObraDTO verificarIngresoTrabajador(Long idObra, Long idTrabajador, java.time.LocalDate fecha);
    void cambiarEstado(Long idObra, String nuevoEstado);
}
