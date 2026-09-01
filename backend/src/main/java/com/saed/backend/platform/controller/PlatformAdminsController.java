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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Platform Admins", description = "Gestión de Administradores y Operadores de la Plataforma SAED")
@RestController
@RequestMapping("/api/v1/platform/admins")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformAdminsController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminsController(NamedParameterJdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAdmins() {
        String sql = """
            SELECT u.ID_USUARIO AS "idUsuario",
                   u.NOMBRE_USUARIO AS "nombreUsuario",
                   u.EMAIL AS "email",
                   u.ESTADO AS "estado",
                   u.ULTIMO_LOGIN AS "ultimoLogin",
                   p.PRIMER_NOMBRE AS "primerNombre",
                   p.PRIMER_APELLIDO AS "primerApellido",
                   r.CODIGO AS "rol",
                   r.NOMBRE AS "rolNombre",
                   NVL(sa.NIVEL, 'SOPORTE') AS "nivel"
            FROM USUARIOS u
            JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
            JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO AND ua.ESTADO = 'ACTIVA'
            JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
            LEFT JOIN ADMINISTRADORES_SAED sa ON u.ID_USUARIO = sa.ID_USUARIO
            WHERE r.CODIGO = 'SUPERADMIN'
            ORDER BY u.ID_USUARIO ASC
            """;
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "ADMIN_PLATAFORMA", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAdmin(@RequestBody Map<String, Object> payload) {
        String nombreUsuario = (String) payload.get("nombreUsuario");
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        String primerNombre = (String) payload.getOrDefault("primerNombre", "Operador");
        String primerApellido = (String) payload.getOrDefault("primerApellido", "SAED");
        String nivel = (String) payload.getOrDefault("nivel", "SOPORTE");

        if (nombreUsuario == null || email == null || password == null || nombreUsuario.isBlank() || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("nombreUsuario, email y password son requeridos"));
        }

        // 1. Verificar si ya existe el usuario
        String checkSql = "SELECT COUNT(*) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = LOWER(:usr) OR LOWER(EMAIL) = LOWER(:email)";
        Number count = jdbcTemplate.queryForObject(checkSql, new MapSqlParameterSource("usr", nombreUsuario).addValue("email", email), Number.class);
        if (count != null && count.intValue() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("El nombre de usuario o email ya está registrado"));
        }

        // 2. Insertar PERSONA
        String insertPersona = """
            INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO)
            VALUES (1, :doc, :primerNombre, :primerApellido, :email, 'ACTIVO')
            """;
        String docRandom = "SAED-" + System.currentTimeMillis() % 1000000;
        KeyHolder personaKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertPersona, new MapSqlParameterSource()
                .addValue("doc", docRandom)
                .addValue("primerNombre", primerNombre)
                .addValue("primerApellido", primerApellido)
                .addValue("email", email), personaKeyHolder, new String[]{"ID_PERSONA"});
        Long idPersona = personaKeyHolder.getKey().longValue();

        // 3. Insertar USUARIOS
        String hashedPassword = passwordEncoder.encode(password);
        String insertUsuario = """
            INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO)
            VALUES (:idPersona, :nombreUsuario, :email, :passwordHash, 'ACTIVO')
            """;
        KeyHolder usuarioKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertUsuario, new MapSqlParameterSource()
                .addValue("idPersona", idPersona)
                .addValue("nombreUsuario", nombreUsuario)
                .addValue("email", email)
                .addValue("passwordHash", hashedPassword), usuarioKeyHolder, new String[]{"ID_USUARIO"});
        Long idUsuario = usuarioKeyHolder.getKey().longValue();

        // 4. Insertar ADMINISTRADORES_SAED
        String insertAdminSaed = """
            INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, NIVEL, ESTADO)
            VALUES (:idUsuario, :nivel, 'ACTIVO')
            """;
        jdbcTemplate.update(insertAdminSaed, new MapSqlParameterSource("idUsuario", idUsuario).addValue("nivel", nivel.toUpperCase()));

        // 5. Insertar USUARIO_ASIGNACIONES (Rol SUPERADMIN = 1)
        String findRolSql = "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'SUPERADMIN'";
        Long idRol = jdbcTemplate.queryForObject(findRolSql, new MapSqlParameterSource(), Long.class);

        String insertAsignacion = """
            INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ESTADO)
            VALUES (:idUsuario, :idRol, 'ACTIVA')
            """;
        jdbcTemplate.update(insertAsignacion, new MapSqlParameterSource("idUsuario", idUsuario).addValue("idRol", idRol));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "idUsuario", idUsuario,
                "nombreUsuario", nombreUsuario,
                "email", email,
                "estado", "ACTIVO",
                "rol", "SUPERADMIN",
                "nivel", nivel.toUpperCase()
        )));
    }

    @PutMapping("/{id}/estado")
    @Auditable(action = "UPDATE", resource = "ADMIN_PLATAFORMA", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAdminStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> payload) {
        
        String nuevoEstado = payload.getOrDefault("estado", "ACTIVO");

        // Regla Crítica: Protección del último SUPERADMIN activo
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

        int rows = jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = :estado WHERE ID_USUARIO = :id",
                new MapSqlParameterSource("estado", nuevoEstado).addValue("id", id));
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        // Sincronizar en ADMINISTRADORES_SAED
        jdbcTemplate.update("UPDATE ADMINISTRADORES_SAED SET ESTADO = :estado WHERE ID_USUARIO = :id",
                new MapSqlParameterSource("estado", nuevoEstado).addValue("id", id));

        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "estado", nuevoEstado)));
    }
}
