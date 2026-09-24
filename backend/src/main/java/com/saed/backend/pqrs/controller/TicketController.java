package com.saed.backend.pqrs.controller;

import com.saed.backend.platform.annotation.RequireModule;
import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.dto.TicketTrazabilidadDTO;
import com.saed.backend.pqrs.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PQRS Tickets", description = "Gestión operativa unificada de tickets PQRS de copropiedad")
@RestController
@RequestMapping("/api/v1/pqrs")
@RequireModule("PQRS")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/todos")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<List<TicketResponseDTO>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/mis-tickets")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<TicketResponseDTO>> getMyTickets() {
        return ResponseEntity.ok(ticketService.getMyTickets());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<TicketResponseDTO> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Long> createTicket(@Valid @RequestBody TicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String observacion,
            @RequestBody(required = false) Map<String, String> body) {

        String finalEstado = estado;
        String finalObservacion = observacion;

        if (body != null) {
            if (finalEstado == null && body.containsKey("estado")) {
                finalEstado = body.get("estado");
            }
            if (finalObservacion == null && body.containsKey("observacion")) {
                finalObservacion = body.get("observacion");
            }
        }

        if (finalEstado == null || finalEstado.isBlank()) {
            throw new IllegalArgumentException("Debe especificar el nuevo estado del ticket.");
        }

        ticketService.updateTicketStatus(id, finalEstado, finalObservacion);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/{id}/responder", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Void> responderTicket(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        String respuesta = payload != null ? payload.getOrDefault("respuesta", payload.get("comentario")) : null;
        String nuevoEstado = payload != null ? payload.getOrDefault("nuevoEstado", payload.get("estado")) : null;

        ticketService.responderTicket(id, respuesta, nuevoEstado);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/asignar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<Void> asignarTicket(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        Long idResponsable = null;
        if (payload != null && payload.containsKey("idResponsable")) {
            Object raw = payload.get("idResponsable");
            if (raw instanceof Number num) {
                idResponsable = num.longValue();
            } else if (raw != null) {
                idResponsable = Long.parseLong(raw.toString());
            }
        }
        String observacion = payload != null ? (String) payload.get("observacion") : null;

        ticketService.asignarTicket(id, idResponsable, observacion);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/prioridad")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<Void> actualizarPrioridad(
            @PathVariable Long id,
            @RequestParam(required = false) String prioridad,
            @RequestBody(required = false) Map<String, String> payload) {

        String finalPrioridad = prioridad;
        if (finalPrioridad == null && payload != null) {
            finalPrioridad = payload.get("prioridad");
        }
        ticketService.actualizarPrioridad(id, finalPrioridad);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/trazabilidad")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<TicketTrazabilidadDTO>> getTrazabilidad(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTrazabilidad(id));
    }

    @GetMapping("/sla-config")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<List<PqrsSlaConfigDTO>> getSlaConfigs() {
        return ResponseEntity.ok(ticketService.getSlaConfigs());
    }

    @PutMapping("/sla-config")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<Void> upsertSlaConfig(@RequestBody PqrsSlaConfigDTO config) {
        ticketService.upsertSlaConfig(config);
        return ResponseEntity.ok().build();
    }
}
