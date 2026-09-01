package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Tag(name = "Platform Plans", description = "Administración de Planes SaaS de SAED para SUPERADMIN")
@RestController
@RequestMapping("/api/v1/platform/plans")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformPlansController {

    private static final Map<Long, Map<String, Object>> PLANS_DB = new ConcurrentHashMap<>();
    private static final AtomicLong ID_GEN = new AtomicLong(3);

    static {
        PLANS_DB.put(1L, Map.of(
            "id", 1L,
            "nombre", "Plan Básico",
            "descripcion", "Ideal para conjuntos pequeños o edificios de hasta 50 unidades",
            "precioMensual", 150000,
            "maxPropiedades", 1,
            "maxUnidades", 50,
            "maxUsuarios", 100,
            "modulos", List.of("RESIDENTES", "VISITAS", "PORTERIA", "PQRS"),
            "estado", "ACTIVO"
        ));
        PLANS_DB.put(2L, Map.of(
            "id", 2L,
            "nombre", "Plan Profesional",
            "descripcion", "Para copropiedades medianas con finanzas, reservas y asambleas",
            "precioMensual", 350000,
            "maxPropiedades", 3,
            "maxUnidades", 250,
            "maxUsuarios", 500,
            "modulos", List.of("RESIDENTES", "VISITAS", "PORTERIA", "PQRS", "FINANZAS", "RESERVAS", "ASAMBLEAS", "MANTENIMIENTO"),
            "estado", "ACTIVO"
        ));
        PLANS_DB.put(3L, Map.of(
            "id", 3L,
            "nombre", "Plan Enterprise",
            "descripcion", "Plataforma ilimitada con módulos avanzados de seguros y emergencias",
            "precioMensual", 750000,
            "maxPropiedades", 10,
            "maxUnidades", 1000,
            "maxUsuarios", 2500,
            "modulos", List.of("TODOS"),
            "estado", "ACTIVO"
        ));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getPlans() {
        return ApiResponse.success(new ArrayList<>(PLANS_DB.values()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlanById(@PathVariable Long id) {
        Map<String, Object> plan = PLANS_DB.get(id);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPlan(@RequestBody Map<String, Object> payload) {
        long newId = ID_GEN.incrementAndGet();
        Map<String, Object> newPlan = new ConcurrentHashMap<>(payload);
        newPlan.put("id", newId);
        newPlan.putIfAbsent("estado", "ACTIVO");
        PLANS_DB.put(newId, newPlan);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(newPlan));
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePlan(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        if (!PLANS_DB.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> updatedPlan = new ConcurrentHashMap<>(payload);
        updatedPlan.put("id", id);
        PLANS_DB.put(id, updatedPlan);
        return ResponseEntity.ok(ApiResponse.success(updatedPlan));
    }
}
