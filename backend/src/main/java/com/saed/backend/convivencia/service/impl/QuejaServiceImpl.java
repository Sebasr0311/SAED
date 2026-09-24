package com.saed.backend.convivencia.service.impl;

import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.service.QuejaService;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.service.TicketService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @deprecated Legacy adapter for Quejas. All operations now delegate
 * directly to the canonical TicketService (/api/v1/pqrs) to preserve
 * unified state machine, dynamic SLA, and atomic traceability.
 */
@Deprecated(since = "F9-06", forRemoval = false)
@Service
public class QuejaServiceImpl implements QuejaService {

    private final TicketService ticketService;

    public QuejaServiceImpl(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    private QuejaDTO toQuejaDTO(TicketResponseDTO ticket) {
        if (ticket == null) return null;
        QuejaDTO dto = new QuejaDTO();
        dto.setIdQueja(ticket.getIdTicket());
        dto.setRadicado(ticket.getNumeroRadicado());
        dto.setTipo(ticket.getTipo());
        dto.setCategoria(ticket.getCategoria());
        dto.setPrioridad(ticket.getPrioridad());
        dto.setTitulo(ticket.getAsunto());
        dto.setDescripcion(ticket.getDescripcion());
        dto.setEstado(ticket.getEstado());
        dto.setRespuesta(ticket.getUltimaRespuesta() != null ? ticket.getUltimaRespuesta() : ticket.getObservacionCierre());
        dto.setAutor(ticket.getNombreRadicador());
        dto.setApartamento(ticket.getIdentificadorUnidad());
        if (ticket.getFechaRadicacion() != null) {
            dto.setFecha(ticket.getFechaRadicacion().toLocalDateTime());
        }
        return dto;
    }

    @Override
    public List<QuejaDTO> findAll() {
        return ticketService.getAllTickets().stream()
                .map(this::toQuejaDTO)
                .toList();
    }

    @Override
    public List<QuejaDTO> findMyQuejas() {
        return ticketService.getMyTickets().stream()
                .map(this::toQuejaDTO)
                .toList();
    }

    @Override
    public void createQueja(QuejaRequestDTO dto) {
        TicketRequestDTO tr = new TicketRequestDTO();
        tr.setTipo(dto.getTipo());
        tr.setCategoria(dto.getCategoria());
        tr.setPrioridad("MEDIA");
        tr.setAsunto(dto.getTitulo());
        tr.setDescripcion(dto.getDescripcion());
        ticketService.createTicket(tr);
    }

    @Override
    public void responder(Long id, String respuesta) {
        ticketService.responderTicket(id, respuesta, null);
    }

    @Override
    public void actualizarEstado(Long id, String estado) {
        ticketService.updateTicketStatus(id, estado, null);
    }

    @Override
    public void actualizarPrioridad(Long id, String prioridad) {
        ticketService.actualizarPrioridad(id, prioridad);
    }
}
