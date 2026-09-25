package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.platform.dto.ActualizarCredencialesRequestDTO;
import com.saed.backend.platform.dto.EmailDispatchResponseDTO;
import com.saed.backend.platform.dto.OnboardingAdminSummaryDTO;
import com.saed.backend.platform.dto.ReenviarCredencialesRequestDTO;
import com.saed.backend.platform.service.PlatformOnboardingAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Platform Onboarding Admin", description = "Gestión de Solicitudes de Onboarding, Suscripciones, Despacho de Correos y Credenciales para SUPERADMIN")
@RestController
@RequestMapping("/api/v1/platform/onboarding")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformOnboardingAdminController {

    private final PlatformOnboardingAdminService onboardingAdminService;

    public PlatformOnboardingAdminController(PlatformOnboardingAdminService onboardingAdminService) {
        this.onboardingAdminService = onboardingAdminService;
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<ApiResponse<List<OnboardingAdminSummaryDTO>>> listarSolicitudes() {
        List<OnboardingAdminSummaryDTO> list = onboardingAdminService.listarSolicitudes();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/reenviar-correo")
    @Auditable(action = "RESEND", resource = "ONBOARDING_CREDENTIALS", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<EmailDispatchResponseDTO>> reenviarCorreo(@RequestBody ReenviarCredencialesRequestDTO request) {
        EmailDispatchResponseDTO result = onboardingAdminService.reenviarCredenciales(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/credenciales")
    @Auditable(action = "UPDATE", resource = "ONBOARDING_ADMIN_CREDENTIALS", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> actualizarCredenciales(@Valid @RequestBody ActualizarCredencialesRequestDTO request) {
        Map<String, Object> result = onboardingAdminService.actualizarCredenciales(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/aprobar-manual")
    @Auditable(action = "MANUAL_APPROVAL", resource = "ONBOARDING_REGISTRATION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> aprobarManualmente(@RequestBody Map<String, String> payload) {
        String referencia = payload.get("referencia");
        Map<String, Object> result = onboardingAdminService.aprobarManualmente(referencia);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
