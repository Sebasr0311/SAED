package com.saed.backend.domicilios.repository;

import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;

import java.util.List;
import java.util.Optional;

public interface DomicilioRepository {
    DomicilioDTO registrarDomicilio(DomicilioCreateDTO dto, Long idOrganizacion, Long idPropiedad, Long registradoPor);
    List<DomicilioDTO> listarDomicilios(Long idPropiedad, String estado, Long idUnidad);
    Optional<DomicilioDTO> buscarPorId(Long idDomicilio);
    DomicilioDTO finalizarDomicilio(Long idDomicilio, Long finalizadoPor);
    DomicilioDTO cancelarDomicilio(Long idDomicilio, Long canceladoPor);
}
