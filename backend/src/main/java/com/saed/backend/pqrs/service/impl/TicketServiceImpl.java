package com.saed.backend.pqrs.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.repository.TicketRepository;
import com.saed.backend.pqrs.service.TicketService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    public TicketServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public List<TicketResponseDTO> getAllTickets() {
        return ticketRepository.findAll();
    }

    @Override
    public List<TicketResponseDTO> getMyTickets() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return ticketRepository.findByPersona(idUsuario);
    }

    @Override
    public TicketResponseDTO getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));
    }

    @Override
    public Long createTicket(TicketRequestDTO request) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId(); 

        if (idUsuario == null || idPropiedad == null) {
            throw new IllegalStateException("El usuario no tiene una propiedad asignada en su contexto.");
        }

        String numeroRadicado = "PQRS-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // MVP: Add 24 hours for SLA. This should ideally come from PQRS_SLA_CONFIG
        ZonedDateTime sla = ZonedDateTime.now(java.time.ZoneId.of("America/Bogota")).plusHours(24);

        return ticketRepository.create(request, idPropiedad, idUsuario, numeroRadicado, sla);
    }

    @Override
    public void updateTicketStatus(Long id, String estado) {
        // Validation that the ticket exists and RLS allows access
        getTicketById(id);
        ticketRepository.updateEstado(id, estado);
    }
}
