import os

base_dir = "backend/src/main/java/com/saed/backend/convivencia/controller"

controllers = {
    "MultasController.java": """package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.service.MultaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/multas")
@RequiredArgsConstructor
public class MultasController {
    private final MultaService service;

    @GetMapping("/todas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<List<MultaDTO>> getAllMultas() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<MultaDTO> getMultaById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}/pagar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<Void> pagarMulta(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.pagar(id, payload.getOrDefault("metodoPago", "EFECTIVO"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<Void> anularMulta(@PathVariable Long id) {
        service.anular(id);
        return ResponseEntity.ok().build();
    }
}
""",
    "QuejasController.java": """package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.service.QuejaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class QuejasController {
    private final QuejaService service;

    @GetMapping("/api/v1/quejas")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<List<QuejaDTO>> getMyQuejas() {
        return ResponseEntity.ok(service.findMyQuejas());
    }

    @PostMapping("/api/v1/quejas")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> createQueja(@Valid @RequestBody QuejaRequestDTO request) {
        service.createQueja(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/v1/quejas/todas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<List<QuejaDTO>> getAllQuejas() {
        return ResponseEntity.ok(service.findAll());
    }

    @PutMapping("/api/v1/quejas/{id}/responder")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> responderQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.responder(id, payload.get("respuesta"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/v1/quejas/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> actualizarEstadoQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.actualizarEstado(id, payload.get("estado"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/v1/quejas/{id}/prioridad")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> actualizarPrioridadQueja(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        service.actualizarPrioridad(id, payload.get("prioridad"));
        return ResponseEntity.ok().build();
    }
}
""",
    "BuzonController.java": """package com.saed.backend.convivencia.controller;

import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/buzon")
@RequiredArgsConstructor
public class BuzonController {
    private final NotificacionService service;

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
"""
}

for name, content in controllers.items():
    with open(os.path.join(base_dir, name), "w", encoding="utf-8") as f:
        f.write(content)

print("Phase 1J Controllers Created")
