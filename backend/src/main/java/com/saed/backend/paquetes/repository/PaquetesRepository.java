package com.saed.backend.paquetes.repository;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;

import java.util.List;
import java.util.Optional;

public interface PaquetesRepository {
    PaqueteDTO registrarPaquete(PaqueteRequestDTO request, Long idPropiedad, String codigoRetiro, Long idPorteroRegistra);
    List<PaqueteDTO> getPaquetesList();
    Optional<PaqueteDTO> getPaqueteById(Long idPaquete);
    PaqueteDTO actualizarPaquete(Long idPaquete, PaqueteRequestDTO request);
    void registrarEntrega(Long idPaquete, PaqueteEntregaDTO entregaDTO, Long idPorteroEntrega);
}
