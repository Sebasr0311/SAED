package com.saed.backend.authorization.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

import com.saed.backend.authorization.dto.PropertyDeletionDTOs;
import com.saed.backend.authorization.service.PropertyDeletionService;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "Property", description = "API para la gestion de Property")
@RestController
@RequestMapping("/api/v1/properties")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyDeletionService propertyDeletionService;

    public PropertyController(PropertyService propertyService, PropertyDeletionService propertyDeletionService) {
        this.propertyService = propertyService;
        this.propertyDeletionService = propertyDeletionService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyDTO>> findAll() {
        return ResponseEntity.ok(propertyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION')")
    @Auditable(action = "CREATE", resource = "PROPIEDAD", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody PropertyRequestDTO request) {
        Long id = propertyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "PROPIEDAD", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody PropertyRequestDTO request) {
        propertyService.update(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION')")
    @Auditable(action = "UPDATE_STATUS", resource = "PROPIEDAD", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        if (estado == null || estado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "El campo estado es requerido"));
        }
        propertyService.updateStatus(id, estado.toUpperCase());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * PASO 1-4: Solicitar eliminación segura de propiedad con desafío OTP por correo.
     * Exclusivo para ADMIN_ORGANIZACION.
     */
    @PostMapping("/{id}/deletion/request")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<PropertyDeletionDTOs.RequestResponse> requestDeletion(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        String ip = extractIp(request);
        String userAgent = request.getHeader("User-Agent");
        PropertyDeletionDTOs.RequestResponse response = propertyDeletionService.requestDeletion(id, ip, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * PASO 7: Verificar código OTP de 6 dígitos.
     * Exclusivo para ADMIN_ORGANIZACION.
     */
    @PostMapping("/{id}/deletion/verify")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<PropertyDeletionDTOs.VerifyResponse> verifyDeletionOtp(
            @PathVariable Long id,
            @Valid @RequestBody PropertyDeletionDTOs.VerifyRequest requestBody,
            HttpServletRequest request
    ) {
        String ip = extractIp(request);
        String userAgent = request.getHeader("User-Agent");
        PropertyDeletionDTOs.VerifyResponse response = propertyDeletionService.verifyOtp(id, requestBody, ip, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * PASO 9: Segunda confirmación obligatoria y ejecución final de la eliminación destructiva.
     * Exclusivo para ADMIN_ORGANIZACION.
     */
    @PostMapping("/{id}/deletion/confirm")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<PropertyDeletionDTOs.ConfirmResponse> confirmDeletion(
            @PathVariable Long id,
            @Valid @RequestBody PropertyDeletionDTOs.ConfirmRequest requestBody,
            HttpServletRequest request
    ) {
        String ip = extractIp(request);
        String userAgent = request.getHeader("User-Agent");
        PropertyDeletionDTOs.ConfirmResponse response = propertyDeletionService.confirmAndExecuteDeletion(id, requestBody, ip, userAgent);
        return ResponseEntity.ok(response);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}

