package com.saed.backend.emergencias.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.emergencias.dto.*;
import com.saed.backend.emergencias.service.EmergenciasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Emergencias", description = "Gestión de planes de contingencia y directorio de emergencias")
@RestController
@RequestMapping("/api/v1/emergencias")
public class EmergenciasController {

    private final EmergenciasService service;

    public EmergenciasController(EmergenciasService service) {
        this.service = service;
    }

    // ==========================================
    // RESUMEN Y KPIS
    // ==========================================

    @Operation(summary = "Resumen de planes y contactos de emergencia")
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<EmergenciasSummaryDTO> getResumen() {
        return ResponseEntity.ok(service.getResumen());
    }

    // ==========================================
    // PLANES DE EMERGENCIA
    // ==========================================

    @Operation(summary = "Listar planes de emergencia de la copropiedad")
    @GetMapping("/planes")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<PlanEmergenciaDTO>> getAllPlanes() {
        return ResponseEntity.ok(service.getAllPlanes());
    }

    @Operation(summary = "Obtener detalle de un plan de emergencia")
    @GetMapping("/planes/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<PlanEmergenciaDTO> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPlanById(id));
    }

    @Operation(summary = "Crear nuevo plan de emergencia")
    @PostMapping("/planes")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "PLAN_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> createPlan(@Valid @RequestBody PlanEmergenciaRequestDTO dto) {
        Long id = service.createPlan(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idPlanEmergencia", id, "message", "Plan creado exitosamente"));
    }

    @Operation(summary = "Actualizar plan de emergencia")
    @PutMapping("/planes/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "PLAN_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, String>> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanEmergenciaRequestDTO dto) {
        service.updatePlan(id, dto);
        return ResponseEntity.ok(Map.of("message", "Plan actualizado exitosamente"));
    }

    @Operation(summary = "Eliminar plan de emergencia")
    @DeleteMapping("/planes/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE", resource = "PLAN_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        service.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // CONTACTOS DE EMERGENCIA
    // ==========================================

    @Operation(summary = "Listar todos los contactos de emergencia")
    @GetMapping("/contactos")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<ContactoEmergenciaDTO>> getAllContactos() {
        return ResponseEntity.ok(service.getAllContactos());
    }

    @Operation(summary = "Directorio rápido / minuta de portería (contactos prioritarios)")
    @GetMapping("/contactos/minuta")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<ContactoEmergenciaDTO>> getContactosMinuta() {
        return ResponseEntity.ok(service.getContactosMinuta());
    }

    @Operation(summary = "Obtener detalle de un contacto de emergencia")
    @GetMapping("/contactos/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")
    public ResponseEntity<ContactoEmergenciaDTO> getContactoById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getContactoById(id));
    }

    @Operation(summary = "Crear contacto de emergencia")
    @PostMapping("/contactos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "CONTACTO_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> createContacto(@Valid @RequestBody ContactoEmergenciaRequestDTO dto) {
        Long id = service.createContacto(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idContactoEmergencia", id, "message", "Contacto creado exitosamente"));
    }

    @Operation(summary = "Actualizar contacto de emergencia")
    @PutMapping("/contactos/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "CONTACTO_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, String>> updateContacto(@PathVariable Long id, @Valid @RequestBody ContactoEmergenciaRequestDTO dto) {
        service.updateContacto(id, dto);
        return ResponseEntity.ok(Map.of("message", "Contacto actualizado exitosamente"));
    }

    @Operation(summary = "Eliminar contacto de emergencia")
    @DeleteMapping("/contactos/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE", resource = "CONTACTO_EMERGENCIA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public ResponseEntity<Void> deleteContacto(@PathVariable Long id) {
        service.deleteContacto(id);
        return ResponseEntity.noContent().build();
    }
}
