package com.saed.backend.pqrs.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public TicketServiceImpl(TicketRepository ticketRepository, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.ticketRepository = ticketRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        TicketResponseDTO ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            Long userId = ctx.getUserId();
            if (userId != null) {
                try {
                    Long myPersonaId = jdbcTemplate.queryForObject(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?", Long.class, userId);
                    if (ticket.getIdPersonaRadica() != null && !ticket.getIdPersonaRadica().equals(myPersonaId)) {
                        throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para ver este ticket");
                    }
                } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
            }
        }
        return ticket;
    }

    @Override
    @Auditable(action = "CREATE", resource = "PQRS", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
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
