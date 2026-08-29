package com.saed.backend.pqrs.controller;

import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pqrs")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/todos")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<List<TicketResponseDTO>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/mis-tickets")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<List<TicketResponseDTO>> getMyTickets() {
        return ResponseEntity.ok(ticketService.getMyTickets());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD', 'RESIDENTE')")
    public ResponseEntity<TicketResponseDTO> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RESIDENTE', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Long> createTicket(@RequestBody TicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam String estado) {
        ticketService.updateTicketStatus(id, estado);
        return ResponseEntity.ok().build();
    }
}
