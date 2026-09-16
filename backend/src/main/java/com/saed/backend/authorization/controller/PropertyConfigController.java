package com.saed.backend.authorization.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.authorization.dto.PropertyConfigDTO;
import com.saed.backend.authorization.dto.PropertyConfigUpdateDTO;
import com.saed.backend.authorization.service.PropertyConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Tag(name = "PropertyConfig", description = "API para la configuración y parámetros dinámicos de propiedades")
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/config")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class PropertyConfigController {

    private final PropertyConfigService propertyConfigService;

    public PropertyConfigController(PropertyConfigService propertyConfigService) {
        this.propertyConfigService = propertyConfigService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyConfigDTO>> findAll(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyConfigService.findAll(propertyId));
    }

    @GetMapping("/{key}")
    public ResponseEntity<PropertyConfigDTO> findByKey(
            @PathVariable Long propertyId,
            @PathVariable String key) {
        return propertyConfigService.findByKey(propertyId, key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Configuración no encontrada para la clave: " + key));
    }

    @PutMapping
    @Auditable(action = "UPDATE_CONFIG_BATCH", resource = "PROPIEDAD_CONFIGURACION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.WARN)
    public ResponseEntity<Map<String, Object>> updateBatch(
            @PathVariable Long propertyId,
            @RequestBody Map<String, String> configs) {
        propertyConfigService.saveBatch(propertyId, configs);
        return ResponseEntity.ok(Map.of("success", true, "message", "Configuración actualizada correctamente"));
    }

    @PutMapping("/{key}")
    @Auditable(action = "UPDATE_CONFIG", resource = "PROPIEDAD_CONFIGURACION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> updateOne(
            @PathVariable Long propertyId,
            @PathVariable String key,
            @Valid @RequestBody PropertyConfigUpdateDTO request) {
        propertyConfigService.saveOrUpdate(propertyId, key, request.getValor(), request.getDescripcion());
        return ResponseEntity.ok(Map.of("success", true, "message", "Parámetro " + key + " actualizado exitosamente"));
    }
}
