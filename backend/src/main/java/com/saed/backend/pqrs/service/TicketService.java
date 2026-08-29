package com.saed.backend.pqrs.service;

import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;

import java.util.List;

public interface TicketService {
    List<TicketResponseDTO> getAllTickets();
    List<TicketResponseDTO> getMyTickets();
    TicketResponseDTO getTicketById(Long id);
    Long createTicket(TicketRequestDTO request);
    void updateTicketStatus(Long id, String estado);
}
