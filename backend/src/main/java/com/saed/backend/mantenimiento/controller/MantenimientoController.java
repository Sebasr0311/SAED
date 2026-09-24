package com.saed.backend.mantenimiento.controller;

import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoEstadoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoReprogramarDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;
import com.saed.backend.mantenimiento.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mantenimientos")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;

    public MantenimientoController(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    @GetMapping
    public ResponseEntity<List<MantenimientoDTO>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) Long idActivo,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(mantenimientoService.listar(estado, tipo, prioridad, idActivo, search));
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> obtenerKpis() {
        return ResponseEntity.ok(mantenimientoService.obtenerKpis());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MantenimientoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mantenimientoService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<MantenimientoDTO> crear(@Valid @RequestBody MantenimientoCreateDTO dto) {
        MantenimientoDTO creado = mantenimientoService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MantenimientoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody MantenimientoUpdateDTO dto) {
        return ResponseEntity.ok(mantenimientoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<MantenimientoDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody MantenimientoEstadoDTO dto) {
        return ResponseEntity.ok(mantenimientoService.cambiarEstado(id, dto));
    }

    @PutMapping("/{id}/reprogramar")
    public ResponseEntity<MantenimientoDTO> reprogramar(
            @PathVariable Long id,
            @Valid @RequestBody MantenimientoReprogramarDTO dto) {
        return ResponseEntity.ok(mantenimientoService.reprogramar(id, dto));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<MantenimientoDTO> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = body != null ? body.get("motivo") : null;
        return ResponseEntity.ok(mantenimientoService.cancelar(id, motivo));
    }
}
