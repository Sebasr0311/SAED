package com.saed.backend.domicilios.service;

import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;

import java.util.List;

public interface DomicilioService {
    DomicilioDTO registrarDomicilio(DomicilioCreateDTO dto);
    List<DomicilioDTO> listarDomicilios(String estado);
    DomicilioDTO buscarPorId(Long idDomicilio);
    DomicilioDTO finalizarDomicilio(Long idDomicilio);
    DomicilioDTO cancelarDomicilio(Long idDomicilio);
}
