package com.saed.backend.paquetes.repository;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;

import java.util.List;
import java.util.Optional;

public interface PaquetesRepository {
    PaqueteDTO registrarPaquete(PaqueteRequestDTO request, Long idPropiedad, String codigoRetiro, Long idPorteroRegistra);
    List<PaqueteDTO> getPaquetesList();
    List<PaqueteDTO> getPaquetesByUnidad(Long idUnidad);
    Optional<PaqueteDTO> getPaqueteById(Long idPaquete);
    PaqueteDTO actualizarPaquete(Long idPaquete, PaqueteRequestDTO request);
    boolean registrarEntrega(Long idPaquete, PaqueteEntregaDTO entregaDTO, Long idPorteroEntrega);
    void marcarEntregadoDirecto(Long idPaquete, Long idPortero);
    void incrementarIntentosFallidos(Long idPaquete);
    /**
     * Atomically counts one PIN attempt in its own transaction, only while fewer than {@code maxIntentos}
     * attempts were made; returns false when the package is locked.
     */
    boolean reservarIntentoPin(Long idPaquete, int maxIntentos);
}
