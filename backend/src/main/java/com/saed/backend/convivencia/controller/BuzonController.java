package com.saed.backend.convivencia.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.service.NotificacionService;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.service.PaquetesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

@Tag(name = "Buzon", description = "API para la gestion de Buzon y Paquetes")
@RestController
@RequestMapping("/api/v1/buzon")
public class BuzonController {
    private final NotificacionService service;
    private final PaquetesService paquetesService;

    public BuzonController(NotificacionService service, PaquetesService paquetesService) {
        this.service = service;
        this.paquetesService = paquetesService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<?> getMyBuzon(@RequestParam(required = false) Long idApartamento) {
        if (idApartamento != null) {
            return ResponseEntity.ok(paquetesService.getPaquetesByUnidad(idApartamento));
        }
        return ResponseEntity.ok(service.getMyNotificaciones());
    }

    @PutMapping("/{id}/leido")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarLeido(@PathVariable Long id) {
        service.marcarLeido(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/vaciar")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> vaciarBuzon() {
        service.vaciarBuzon();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/vaciar-multi")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> vaciarMulti(@RequestBody Map<String, List<Long>> payload) {
        service.eliminarMensajes(payload.get("ids"));
        return ResponseEntity.ok().build();
    }

    // --- Endpoints de compatibilidad para Paquetería en Portería / Residente ---

    @PostMapping("/paquete")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<PaqueteDTO> registrarPaqueteBuzon(@RequestBody Map<String, Object> payload) {
        Long idApartamento = payload.get("idApartamento") != null
                ? ((Number) payload.get("idApartamento")).longValue()
                : 1L;
        String titulo = (String) payload.getOrDefault("titulo", "Paquete/Domicilio recibido");
        String foto = (String) payload.get("fotoCaptura");
        PaqueteRequestDTO req = new PaqueteRequestDTO(
                idApartamento,
                null,
                "Servicio de Envíos",
                null,
                titulo != null && !titulo.isBlank() ? titulo : "Paquete recibido en portería",
                "MEDIANO",
                foto,
                1L
        );
        return new ResponseEntity<>(paquetesService.registrarPaquete(req), HttpStatus.CREATED);
    }

    @GetMapping("/paquetes")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<PaqueteDTO>> getPaquetesPorteria() {
        return ResponseEntity.ok(paquetesService.getPaquetes());
    }

    @GetMapping("/paquetes-pendientes")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Object>> getPaquetesPendientes() {
        List<PaqueteDTO> pendientes = paquetesService.getPaquetes().stream()
                .filter(p -> !"ENTREGADO".equalsIgnoreCase(p.estado()))
                .toList();
        return ResponseEntity.ok(Map.of("count", pendientes.size(), "items", pendientes));
    }

    @PutMapping("/{id}/entregado")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public ResponseEntity<Void> marcarPaqueteEntregado(@PathVariable Long id) {
        paquetesService.marcarEntregadoDirecto(id);
        return ResponseEntity.ok().build();
    }
}
