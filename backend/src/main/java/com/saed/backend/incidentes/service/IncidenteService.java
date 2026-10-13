package com.saed.backend.incidentes.service;

import com.saed.backend.incidentes.dto.*;

import java.util.List;

public interface IncidenteService {
    List<IncidenteDTO> getAllIncidentes();
    List<IncidenteDTO> getMisIncidentes();
    IncidenteDTO getIncidenteById(Long idIncidente);
    Long reportarIncidente(IncidenteDTO request);

    // Ciclo de vida y máquina de estados
    void cambiarEstado(Long idIncidente, IncidenteEstadoRequestDTO dto);
    void iniciarInvestigacion(Long idIncidente);
    void actualizarInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto);
    void concluirInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto);
    void escalarIncidente(Long idIncidente, IncidenteEscalamientoRequestDTO dto);
    void cerrarIncidente(Long idIncidente, String conclusiones);

    /** Reopens a closed incident into investigation; admin only, reason required and persisted (F9-03). */
    void reabrirIncidente(Long idIncidente, String motivo);

    // Personas y vehículos involucrados
    List<IncidenteInvolucradoDTO> getInvolucrados(Long idIncidente);
    Long addInvolucrado(Long idIncidente, IncidenteInvolucradoDTO dto);
    void removeInvolucrado(Long idIncidente, Long idInvolucrado);
}
