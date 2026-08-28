package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.service.FlujoCajaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "FlujoCaja", description = "API para la gestión del flujo de caja")
@RestController
@RequestMapping("/api/v1/flujo-caja")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class FlujoCajaController {

    private final FlujoCajaService service;

    public FlujoCajaController(FlujoCajaService service) {
        this.service = service;
    }

    @Operation(summary = "Resumen de flujo de caja")
    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<FlujoCajaResumenDTO>> resumen() {
        return ResponseEntity.ok(ApiResponse.success(service.getResumen()));
    }

    @Operation(summary = "Movimientos recientes (ingresos y egresos)")
    @GetMapping("/movimientos")
    public ResponseEntity<ApiResponse<List<FlujoCajaMovimientoDTO>>> movimientos(
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(ApiResponse.success(service.getMovimientosRecientes(limite)));
    }

    @Operation(summary = "Proyección de ingresos esperados y gastos programados")
    @GetMapping("/proyeccion")
    public ResponseEntity<ApiResponse<List<FlujoCajaMovimientoDTO>>> proyeccion() {
        return ResponseEntity.ok(ApiResponse.success(service.getProyeccion()));
    }
}
