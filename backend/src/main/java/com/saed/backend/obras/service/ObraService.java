package com.saed.backend.obras.service;

import com.saed.backend.obras.dto.ObraDTO;
import java.util.List;

public interface ObraService {
    List<ObraDTO> getObrasAdmin();
    List<ObraDTO> getMisObras();
    ObraDTO getObraById(Long idObra);
    Long solicitarObra(ObraDTO request);
    void aprobarObra(Long idObra);
    void rechazarObra(Long idObra);
    void finalizarObra(Long idObra);
}
