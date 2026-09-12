package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Public Onboarding", description = "Endpoints públicos para adquisición de planes SaaS SAED y registro de organizaciones")
@RestController
@RequestMapping("/api/v1/auth/onboarding")
public class PublicOnboardingController {

    private final OnboardingService onboardingService;

    public PublicOnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Operation(summary = "Catálogo público de planes disponibles para suscripción")
    @GetMapping("/planes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarPlanesPublicos() {
        List<Map<String, Object>> planes = onboardingService.listarPlanesPublicos();
        return ResponseEntity.ok(ApiResponse.success(planes));
    }

    @Operation(summary = "Registro público de organización y suscripción a plan SaaS (Patrón Staging / Intención)")
    @PostMapping("/registro")
    @Auditable(action = "ONBOARDING_REGISTER", resource = "ORGANIZACION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> registrarOrganizacion(
            @Valid @RequestBody OnboardingRegistroRequestDTO request,
            HttpServletRequest httpRequest) {

        Map<String, Object> resp = onboardingService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @Operation(summary = "Consultar estado de transacción de membresía / onboarding")
    @GetMapping("/estado-pago")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consultarEstadoPago(@RequestParam String referencia) {
        Map<String, Object> resp = onboardingService.consultarEstadoPago(referencia);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @Operation(summary = "Mantenimiento: Purga manual de borradores y registros incompletos huérfanos")
    @PostMapping("/purgar-falsos")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purgarRegistrosFalsos() {
        int eliminados = onboardingService.purgarRegistrosFalsos();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "mensaje", "Purga de registros pendientes/huérfanos completada",
                "eliminados", eliminados
        )));
    }
}
