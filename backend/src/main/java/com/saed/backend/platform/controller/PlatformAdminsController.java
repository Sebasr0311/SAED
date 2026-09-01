package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Platform Admins", description = "Gestión de Administradores y Operadores de la Plataforma SAED")
@RestController
@RequestMapping("/api/v1/platform/admins")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformAdminsController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlatformAdminsController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAdmins() {
        String sql = """
            SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL, u.ESTADO, u.ULTIMO_LOGIN,
                   p.PRIMER_NOMBRE, p.PRIMER_APELLIDO, r.CODIGO AS ROL, r.NOMBRE AS ROL_NOMBRE
            FROM USUARIOS u
            JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
            JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO AND ua.ESTADO = 'ACTIVA'
            JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
            WHERE r.CODIGO = 'SUPERADMIN'
            """;
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    @PutMapping("/{id}/estado")
    @Auditable(action = "UPDATE", resource = "ADMIN_PLATAFORMA", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAdminStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> payload) {
        
        String nuevoEstado = payload.getOrDefault("estado", "ACTIVO");

        // Regla Crítica: Protección del último SUPERADMIN
        if ("INACTIVO".equalsIgnoreCase(nuevoEstado) || "BLOQUEADO".equalsIgnoreCase(nuevoEstado)) {
            String countSql = """
                SELECT COUNT(*) FROM USUARIOS u
                JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO AND ua.ESTADO = 'ACTIVA'
                JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
                WHERE r.CODIGO = 'SUPERADMIN' AND u.ESTADO = 'ACTIVO'
                """;
            Number activeCount = jdbcTemplate.queryForObject(countSql, new MapSqlParameterSource(), Number.class);
            if (activeCount != null && activeCount.intValue() <= 1) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("No es posible desactivar al único SUPERADMIN activo de la plataforma."));
            }
        }

        jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = :estado WHERE ID_USUARIO = :id",
                new MapSqlParameterSource("estado", nuevoEstado).addValue("id", id));

        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "estado", nuevoEstado)));
    }
}
