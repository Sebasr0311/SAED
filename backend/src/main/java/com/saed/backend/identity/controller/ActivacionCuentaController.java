package com.saed.backend.identity.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.identity.dto.ConfirmarActivacionRequestDTO;
import com.saed.backend.identity.dto.TokenActivacionInfoDTO;
import com.saed.backend.identity.service.TokenActivacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "ActivacionCuenta", description = "Endpoints de activacion de cuenta y configuracion de contrasena inicial")
@RestController
@RequestMapping("/api/v1/auth/activar")
public class ActivacionCuentaController {

    private final TokenActivacionService tokenService;

    public ActivacionCuentaController(TokenActivacionService tokenService) {
        this.tokenService = tokenService;
    }

    @Operation(summary = "Validar token de activacion de cuenta")
    @GetMapping("/validar")
    public ResponseEntity<ApiResponse<TokenActivacionInfoDTO>> validarToken(@RequestParam String token) {
        TokenActivacionInfoDTO info = tokenService.validarToken(token);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @Operation(summary = "Confirmar contrasena y activar cuenta de usuario")
    @PostMapping("/confirmar")
    @Auditable(action = "ACTIVATE_ACCOUNT", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmarActivacion(
            @Valid @RequestBody ConfirmarActivacionRequestDTO request) {
        tokenService.activarCuenta(request.token(), request.password(), request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "success", true,
                "message", "Tu cuenta ha sido activada exitosamente. Ya puedes iniciar sesión con tu nueva contraseña."
        )));
    }

    @Operation(summary = "Solicitar reenvio de enlace de activacion")
    @PostMapping("/solicitar-reenvio")
    public ResponseEntity<ApiResponse<Map<String, Object>>> solicitarReenvio(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String identificador = body != null ? body.get("identificador") : null;
        String ip = request != null ? request.getRemoteAddr() : "0.0.0.0";
        tokenService.solicitarReenvio(identificador, ip);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "success", true,
                "message", "Si la cuenta existe y está pendiente de activación, recibirás un nuevo enlace por correo electrónico."
        )));
    }
}
