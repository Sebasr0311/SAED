package com.saed.backend.incidentes.repository;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import java.util.List;
import java.util.Optional;

public interface IncidenteRepository {
    List<IncidenteDTO> findAllByPropiedad(Long idPropiedad);
    List<IncidenteDTO> findAllByUnidad(Long idUnidad, Long idPropiedad);
    Optional<IncidenteDTO> findById(Long idIncidente, Long idPropiedad);
    Long createIncidente(IncidenteDTO incidente, Long idPropiedad, Long registradoPor);
    void updateEstado(Long idIncidente, Long idPropiedad, String estado, String conclusiones);
}
