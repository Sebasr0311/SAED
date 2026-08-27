package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.ContratoDTO;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contratos")
public class ContratosController {
    private final FinanzasService finanzasService;
    public ContratosController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping
    public ResponseEntity<List<ContratoDTO>> getContratos() {
        return ResponseEntity.ok(finanzasService.getContratos());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createContrato(@Valid @RequestBody ContratoRequestDTO request) {
        Long id = finanzasService.createContrato(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "id", id));
    }

    @PostMapping("/{id}/activar")
    public ResponseEntity<Map<String, Object>> activarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "ACTIVO");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "CANCELADO");
        return ResponseEntity.ok(Map.of("success", true));
    }
}
