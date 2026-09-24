package com.saed.backend.incidentes.repository;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.dto.IncidenteInvolucradoDTO;

import java.util.List;
import java.util.Optional;

public interface IncidenteRepository {
    List<IncidenteDTO> findAllByPropiedad(Long idPropiedad);
    List<IncidenteDTO> findAllByUnidad(Long idUnidad, Long idPropiedad);
    Optional<IncidenteDTO> findById(Long idIncidente, Long idPropiedad);
    Long createIncidente(IncidenteDTO incidente, Long idPropiedad, Long registradoPor);
    void updateEstado(Long idIncidente, Long idPropiedad, String estado, String conclusiones);
    void iniciarInvestigacion(Long idIncidente, Long idPropiedad, Long investigadoPor);
    void actualizarInvestigacion(Long idIncidente, Long idPropiedad, String hallazgos);
    void concluirInvestigacion(Long idIncidente, Long idPropiedad, String hallazgos, String estadoDestino);
    void escalarIncidente(Long idIncidente, Long idPropiedad, Long escaladoPor, String motivo, String sancionSugerida);

    // Involucrados
    List<IncidenteInvolucradoDTO> findInvolucradosByIncidente(Long idIncidente);
    Long addInvolucrado(Long idIncidente, IncidenteInvolucradoDTO dto);
    void removeInvolucrado(Long idIncidente, Long idInvolucrado);
    boolean isPersonaInPropiedad(Long idPersona, Long idPropiedad);
}
