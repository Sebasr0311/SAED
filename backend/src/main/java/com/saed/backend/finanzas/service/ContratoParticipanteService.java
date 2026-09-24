package com.saed.backend.finanzas.service;

import com.saed.backend.finanzas.dto.CoarrendatarioCreateDTO;
import com.saed.backend.finanzas.dto.CoarrendatarioDTO;

import java.util.List;

public interface ContratoParticipanteService {
    List<CoarrendatarioDTO> listarCoarrendatarios(Long idContrato);
    CoarrendatarioDTO agregarCoarrendatario(CoarrendatarioCreateDTO request);
    void actualizarEstadoCoarrendatario(Long idContratoResidente, String estado);
    void eliminarCoarrendatario(Long idContratoResidente);
    void eliminarCoarrendatario(Long idContrato, Long idPersona);
}
