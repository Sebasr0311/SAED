package com.saed.backend.obras.repository;

import com.saed.backend.obras.dto.ObraDTO;
import java.util.List;
import java.util.Optional;

public interface ObraRepository {
    List<ObraDTO> findAllByPropiedad(Long idPropiedad);
    List<ObraDTO> findAllByUnidad(Long idUnidad, Long idPropiedad);
    Optional<ObraDTO> findById(Long idObra, Long idPropiedad);
    Long createObra(ObraDTO obra, Long idPropiedad, Long solicitadoPor);
    void updateEstado(Long idObra, Long idPropiedad, String estado, Long aprobadoPor);
}
