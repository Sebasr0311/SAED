package com.saed.backend.incidentes.service;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import java.util.List;

public interface IncidenteService {
    List<IncidenteDTO> getAllIncidentes();
    List<IncidenteDTO> getMisIncidentes();
    IncidenteDTO getIncidenteById(Long idIncidente);
    Long reportarIncidente(IncidenteDTO request);
    void cerrarIncidente(Long idIncidente, String conclusiones);
}
