package com.saed.backend.identity.controller;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.dto.RefreshRequest;
import com.saed.backend.identity.service.AuthService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import org.springframework.http.HttpStatus;

@Tag(name = "Auth", description = "API para la gestion de Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Long userId = SaedContextHolder.getContext().getUserId();
        if (userId != null) {
            authService.logout(userId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Object>> verifyPassword(@RequestBody(required = false) Map<String, String> body) {
        Long userId = SaedContextHolder.getContext().getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Usuario no autenticado"));
        }
        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "La contraseña es requerida"));
        }
        boolean valid = authService.verifyPassword(userId, password);
        if (!valid) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "Contraseña incorrecta"));
        }
        return ResponseEntity.ok(Map.of("valid", true, "message", "Contraseña verificada con éxito"));
    }

    @PostMapping("/verify-pin")
    public ResponseEntity<Map<String, Object>> verifyPin(@RequestBody(required = false) Map<String, String> body) {
        Long userId = SaedContextHolder.getContext().getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Usuario no autenticado"));
        }
        String pin = body != null ? (body.get("pin") != null ? body.get("pin") : body.get("password")) : null;
        if (pin == null || pin.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "El PIN o clave de seguridad es requerido"));
        }
        // Valida el PIN de seguridad o la contraseña maestra del administrador
        boolean valid = authService.verifyPassword(userId, pin);
        if (!valid) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "PIN o contraseña de seguridad incorrecta"));
        }
        return ResponseEntity.ok(Map.of("valid", true, "message", "PIN de seguridad verificado exitosamente"));
    }
}

