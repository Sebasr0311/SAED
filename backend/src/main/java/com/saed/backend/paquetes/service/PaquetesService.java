package com.saed.backend.paquetes.service;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;

import java.util.List;

public interface PaquetesService {
    PaqueteDTO registrarPaquete(PaqueteRequestDTO request);
    List<PaqueteDTO> getPaquetes();
    List<PaqueteDTO> getPaquetesByUnidad(Long idUnidad);
    PaqueteDTO getPaqueteById(Long id);
    PaqueteDTO actualizarPaquete(Long id, PaqueteRequestDTO request);
    PaqueteDTO registrarEntrega(Long id, PaqueteEntregaDTO request);
    void marcarEntregadoDirecto(Long id);
}
