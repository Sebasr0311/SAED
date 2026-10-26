package com.saed.backend.mantenimiento.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.mantenimiento.plan.PlanMantenimientoDTO;
import com.saed.backend.mantenimiento.plan.PlanMantenimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Tag(name = "Planes de mantenimiento", description = "Mantenimiento preventivo recurrente (F09-01)")
@RestController
@RequestMapping("/api/v1/mantenimientos/planes")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class PlanMantenimientoController {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final PlanMantenimientoService service;

    public PlanMantenimientoController(PlanMantenimientoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar planes de mantenimiento de la copropiedad")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanMantenimientoDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.success(service.listarPlanes()));
    }

    @Operation(summary = "Crear un plan de mantenimiento recurrente")
    @PostMapping
    @Auditable(action = "CREATE", resource = "PLAN_MANTENIMIENTO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Map<String, Long>>> crear(@RequestBody PlanMantenimientoDTO plan) {
        Long id = service.crearPlan(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("idPlan", id)));
    }

    @Operation(summary = "Desactivar un plan de mantenimiento")
    @PatchMapping("/{id}/desactivar")
    @Auditable(action = "DEACTIVATE", resource = "PLAN_MANTENIMIENTO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        service.desactivarPlan(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Generar las órdenes programadas vencidas de la copropiedad (idempotente)")
    @PostMapping("/generar")
    @Auditable(action = "GENERATE_ORDERS", resource = "PLAN_MANTENIMIENTO", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Map<String, Integer>>> generar() {
        Long idPropiedad = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        int creadas = service.generarOrdenesProgramadas(idPropiedad, LocalDate.now(BOGOTA));
        return ResponseEntity.ok(ApiResponse.success(Map.of("ordenesCreadas", creadas)));
    }
}
