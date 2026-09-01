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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Tag(name = "Platform Memberships", description = "Gestión de Membresías SaaS por Organización para SUPERADMIN")
@RestController
@RequestMapping("/api/v1/platform/memberships")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformMembershipsController {

    private static final Map<Long, Map<String, Object>> MEMBERSHIPS_DB = new ConcurrentHashMap<>();
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    static {
        MEMBERSHIPS_DB.put(1L, Map.of(
            "id", 1L,
            "organizacionId", 1L,
            "organizacionNombre", "SAED Global S.A.S.",
            "planId", 2L,
            "planNombre", "Plan Profesional",
            "fechaInicio", LocalDate.now().minusMonths(2).toString(),
            "fechaFin", LocalDate.now().plusMonths(10).toString(),
            "estado", "ACTIVA",
            "valorMensual", 350000
        ));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getMemberships() {
        return ApiResponse.success(new ArrayList<>(MEMBERSHIPS_DB.values()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMembershipById(@PathVariable Long id) {
        Map<String, Object> item = MEMBERSHIPS_DB.get(id);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(item));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "MEMBRESIA_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMembership(@RequestBody Map<String, Object> payload) {
        long newId = ID_GEN.incrementAndGet();
        Map<String, Object> newItem = new ConcurrentHashMap<>(payload);
        newItem.put("id", newId);
        newItem.putIfAbsent("fechaInicio", LocalDate.now().toString());
        newItem.putIfAbsent("fechaFin", LocalDate.now().plusYears(1).toString());
        newItem.putIfAbsent("estado", "ACTIVA");
        MEMBERSHIPS_DB.put(newId, newItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(newItem));
    }

    @PutMapping("/{id}/estado")
    @Auditable(action = "UPDATE", resource = "MEMBRESIA_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Map<String, Object> item = MEMBERSHIPS_DB.get(id);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        String nuevoEstado = payload.getOrDefault("estado", "ACTIVA");
        Map<String, Object> updated = new ConcurrentHashMap<>(item);
        updated.put("estado", nuevoEstado);
        MEMBERSHIPS_DB.put(id, updated);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
