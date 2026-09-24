package com.saed.backend.pqrs.service;

import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.dto.TicketTrazabilidadDTO;

import java.util.List;

public interface TicketService {
    List<TicketResponseDTO> getAllTickets();
    List<TicketResponseDTO> getMyTickets();
    TicketResponseDTO getTicketById(Long id);
    Long createTicket(TicketRequestDTO request);
    void updateTicketStatus(Long id, String estado, String observacion);
    void responderTicket(Long id, String respuesta, String nuevoEstado);
    void asignarTicket(Long id, Long idResponsable, String observacion);
    void actualizarPrioridad(Long id, String prioridad);
    List<TicketTrazabilidadDTO> getTrazabilidad(Long id);
    List<PqrsSlaConfigDTO> getSlaConfigs();
    void upsertSlaConfig(PqrsSlaConfigDTO config);
}
