package com.saed.backend.identity.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.UserAssignmentDTO;
import com.saed.backend.identity.service.ContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Me", description = "API para el perfil y contexto del usuario autenticado")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ContextService contextService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public MeController(ContextService contextService,
                        NamedParameterJdbcTemplate jdbcTemplate,
                        PasswordEncoder passwordEncoder) {
        this.contextService = contextService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Obtener ID y perfil del usuario autenticado")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Long userId) {
        Long targetId = userId != null ? userId : (SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null);
        return ResponseEntity.ok(Map.of("id", targetId != null ? targetId : 0L));
    }

    @Operation(summary = "Obtener contextos y asignaciones del usuario autenticado")
    @GetMapping("/contexts")
    public ResponseEntity<List<UserAssignmentDTO>> getContexts(@AuthenticationPrincipal Long userId) {
        Long targetId = userId != null ? userId : (SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null);
        List<UserAssignmentDTO> contexts = contextService.getUserContexts(targetId);
        return ResponseEntity.ok(contexts);
    }

    @Operation(summary = "Cambiar contraseña del usuario autenticado")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_SUPERADMIN')")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changePassword(
            @AuthenticationPrincipal Long authUserId,
            @RequestBody Map<String, String> payload) {

        Long userId = authUserId != null ? authUserId : (SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Usuario no autenticado"));
        }

        String passwordActual = payload.getOrDefault("passwordActual", payload.get("currentPassword"));
        String nuevaPassword = payload.getOrDefault("nuevaPassword", payload.get("newPassword"));

        if (passwordActual == null || passwordActual.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La contraseña actual es obligatoria"));
        }
        if (nuevaPassword == null || nuevaPassword.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La nueva contraseña es obligatoria"));
        }
        if (nuevaPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La nueva contraseña debe tener al menos 6 caracteres"));
        }

        List<String> currentHashes = jdbcTemplate.query(
                "SELECT HASH_PASSWORD FROM USUARIOS WHERE ID_USUARIO = :uid",
                new MapSqlParameterSource("uid", userId),
                (rs, rowNum) -> rs.getString("HASH_PASSWORD")
        );

        if (currentHashes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Usuario no encontrado"));
        }

        String currentHash = currentHashes.get(0);
        if (!passwordEncoder.matches(passwordActual, currentHash)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La contraseña actual ingresada es incorrecta"));
        }

        String newHash = passwordEncoder.encode(nuevaPassword.trim());
        jdbcTemplate.update(
                "UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid",
                new MapSqlParameterSource("pwd", newHash).addValue("uid", userId)
        );

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "Contraseña actualizada exitosamente"
        )));
    }
}
