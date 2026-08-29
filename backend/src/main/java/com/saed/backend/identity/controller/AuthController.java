package com.saed.backend.identity.controller;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.dto.RefreshRequest;
import com.saed.backend.identity.service.AuthService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

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
}

