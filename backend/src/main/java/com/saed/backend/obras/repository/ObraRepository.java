package com.saed.backend.obras.repository;

import com.saed.backend.obras.dto.ObraDTO;
import java.util.List;
import java.util.Optional;

public interface ObraRepository {
    List<ObraDTO> findAllByPropiedad(Long idPropiedad);
    List<ObraDTO> findAllByUnidad(Long idUnidad, Long idPropiedad);
    Optional<ObraDTO> findById(Long idObra, Long idPropiedad);
    Optional<ObraDTO> findByIdDirecto(Long idObra);
    Long createObra(ObraDTO obra, Long idPropiedad, Long solicitadoPor);
    void updateEstado(Long idObra, Long idPropiedad, String estado, Long aprobadoPor);

    /**
     * Records the final inspection and moves an active work (APROBADA, EN_EJECUCION, SUSPENDIDA) to
     * FINALIZADA in one conditional update. Returns the rows updated (0 when it is no longer active).
     */
    int finalizarConInspeccion(Long idObra, Long idPropiedad, String resultado, String observaciones, Long inspector);
}
