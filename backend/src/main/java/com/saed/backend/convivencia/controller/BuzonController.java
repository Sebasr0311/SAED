package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

@Tag(name = "Buzon", description = "API para la gestion de Buzon")
@RestController
@RequestMapping("/api/v1/buzon")
public class BuzonController {
    private final NotificacionService service;

    public BuzonController(NotificacionService service) {
        this.service = service;
    }


    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<NotificacionDTO>> getMyBuzon() {
        return ResponseEntity.ok(service.getMyNotificaciones());
    }

    @PutMapping("/{id}/leido")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> marcarLeido(@PathVariable Long id) {
        service.marcarLeido(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/vaciar")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> vaciarBuzon() {
        service.vaciarBuzon();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/vaciar-multi")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> vaciarMulti(@RequestBody Map<String, List<Long>> payload) {
        service.eliminarMensajes(payload.get("ids"));
        return ResponseEntity.ok().build();
    }
}

