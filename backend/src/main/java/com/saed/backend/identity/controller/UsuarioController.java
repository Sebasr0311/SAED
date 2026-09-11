package com.saed.backend.identity.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema, porteros y residentes")
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider;

    public UsuarioController(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenServiceProvider = tokenServiceProvider;
    }

    @Operation(summary = "Listar usuarios del sistema con sus roles y personas vinculadas dentro del perímetro del tenant")
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public List<Map<String, Object>> listarUsuarios() {
        SaedContext ctx = SaedContextHolder.getContext();
        String roleCode = ctx != null ? ctx.getRoleCode() : "";
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT u.ID_USUARIO,
                   u.NOMBRE_USUARIO,
                   u.EMAIL,
                   u.ESTADO,
                   r.CODIGO AS ROL,
                   r.NOMBRE AS ROL_NOMBRE,
                   ua.ID_ASIGNACION,
                   ua.ESTADO AS ASIGNACION_ESTADO,
                   p.ID_PERSONA,
                   p.NUMERO_DOCUMENTO,
                   p.TELEFONO,
                   TRIM(p.PRIMER_NOMBRE || ' ' || COALESCE(p.SEGUNDO_NOMBRE, '') || ' ' ||
                        p.PRIMER_APELLIDO || ' ' || COALESCE(p.SEGUNDO_APELLIDO, '')) AS NOMBRE_COMPLETO
            FROM USUARIOS u
            LEFT JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
            LEFT JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
            LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
            WHERE 1=1
            """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if ("ADMIN_PROPIEDAD".equals(roleCode)) {
            if (propId != null) {
                sql.append(" AND ua.ID_PROPIEDAD = :propId");
                params.addValue("propId", propId);
            } else {
                sql.append(" AND 1=0");
            }
        } else if ("ADMIN_ORGANIZACION".equals(roleCode)) {
            if (orgId != null) {
                sql.append(" AND ua.ID_ORGANIZACION = :orgId");
                params.addValue("orgId", orgId);
            } else {
                sql.append(" AND 1=0");
            }
        } else if (!"SUPERADMIN".equals(roleCode)) {
            Long userId = ctx != null ? ctx.getUserId() : null;
            if (userId != null) {
                sql.append(" AND u.ID_USUARIO = :userId");
                params.addValue("userId", userId);
            } else {
                sql.append(" AND 1=0");
            }
        }

        sql.append(" ORDER BY u.ID_USUARIO DESC");
        return jdbcTemplate.queryForList(sql.toString(), params);
    }

    @Operation(summary = "Crear nuevo usuario (Portero, Residente o Administrador)")
    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearUsuario(@RequestBody Map<String, Object> payload) {
        String username = (String) payload.getOrDefault("username", payload.get("nombreUsuario"));
        if (username == null || username.trim().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El nombre de usuario es obligatorio"));
        }
        username = username.trim().toLowerCase();

        String rawPassword = (String) payload.getOrDefault("password", payload.get("passwordHash"));
        boolean enviarActivacion = Boolean.TRUE.equals(payload.get("enviarCorreoActivacion"))
                || Boolean.TRUE.equals(payload.get("activacionPorCorreo"))
                || "true".equalsIgnoreCase(String.valueOf(payload.get("enviarCorreoActivacion")));

        if (rawPassword == null || rawPassword.trim().isBlank()) {
            rawPassword = java.util.UUID.randomUUID().toString();
            enviarActivacion = true;
        }

        String rol = (String) payload.getOrDefault("rol", "PORTERO");
        if (rol == null || rol.trim().isBlank()) {
            rol = "PORTERO";
        }
        rol = rol.trim().toUpperCase();

        // Verificar unicidad de username
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u",
                new MapSqlParameterSource("u", username),
                Integer.class
        );
        if (count != null && count > 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El nombre de usuario '" + username + "' ya existe"));
        }

        SaedContext ctx = SaedContextHolder.getContext();
        String callerRole = ctx != null ? ctx.getRoleCode() : "";
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        // Anti-escalamiento de privilegios por rol
        if ("ADMIN_PROPIEDAD".equals(callerRole)) {
            if (!"PORTERO".equals(rol) && !"RESIDENTE".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Como Administrador de Propiedad solo puede registrar Porteros o Residentes"));
            }
            if (propId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No se encontró el identificador de la propiedad en el contexto"));
            }
        } else if ("ADMIN_ORGANIZACION".equals(callerRole)) {
            if ("SUPERADMIN".equals(rol) || "ADMIN_ORGANIZACION".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para asignar roles de nivel organización o plataforma"));
            }
            if (orgId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No se encontró el identificador de la organización en el contexto"));
            }
        }

        if (orgId == null) orgId = 1L;
        if (propId == null) propId = 1L;

        // 1. Resolver o Crear PERSONA
        Long idPersona = null;
        Object idPersonaObj = payload.getOrDefault("idPersona", payload.get("idResidente"));
        if (idPersonaObj != null && !idPersonaObj.toString().isBlank()) {
            idPersona = Long.valueOf(idPersonaObj.toString());
        } else {
            String doc = (String) payload.getOrDefault("numeroDocumento", "");
            if (doc != null && !doc.trim().isBlank()) {
                List<Long> existing = jdbcTemplate.query(
                        "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                        Map.of("doc", doc.trim()),
                        (rs, rowNum) -> rs.getLong("ID_PERSONA")
                );
                if (!existing.isEmpty()) {
                    idPersona = existing.get(0);
                }
            }

            if (idPersona == null) {
                String primerNombre = (String) payload.getOrDefault("primerNombre", username);
                String primerApellido = (String) payload.getOrDefault("primerApellido", "PORTERO".equals(rol) ? "Vigilancia" : "Usuario");
                String docFinal = (doc != null && !doc.trim().isBlank()) ? doc.trim() : "DOC-" + System.currentTimeMillis();
                String tel = (String) payload.getOrDefault("telefono", "");
                String email = (String) payload.getOrDefault("email", username + "@saed.com");

                KeyHolder khPersona = new GeneratedKeyHolder();
                String sqlPersona = """
                    INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, TELEFONO, ESTADO)
                    VALUES (1, :doc, 'NATURAL', :nombre, :apellido, :email, :tel, 'ACTIVO')
                    """;
                MapSqlParameterSource paramP = new MapSqlParameterSource()
                        .addValue("doc", docFinal)
                        .addValue("nombre", primerNombre)
                        .addValue("apellido", primerApellido)
                        .addValue("email", email)
                        .addValue("tel", tel);
                jdbcTemplate.update(sqlPersona, paramP, khPersona, new String[]{"ID_PERSONA"});
                Number pKey = khPersona.getKey();
                if (pKey != null) {
                    idPersona = pKey.longValue();
                }
            }
        }

        // 2. Insertar USUARIO
        String userEmail = (String) payload.getOrDefault("email", username + "@saed.com");
        KeyHolder khUsuario = new GeneratedKeyHolder();
        String sqlUsuario = """
            INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
            VALUES (:idPersona, :username, :email, :pwd, 'ACTIVO', 0)
            """;
        MapSqlParameterSource paramU = new MapSqlParameterSource()
                .addValue("idPersona", idPersona)
                .addValue("username", username)
                .addValue("email", userEmail)
                .addValue("pwd", passwordEncoder.encode(rawPassword.trim()));
        jdbcTemplate.update(sqlUsuario, paramU, khUsuario, new String[]{"ID_USUARIO"});
        Number uKey = khUsuario.getKey();
        if (uKey == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("No se pudo generar el ID del usuario"));
        }
        Long idUsuario = uKey.longValue();

        // 3. Obtener ID_ROL
        Long idRol = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = :cod",
                new MapSqlParameterSource("cod", rol),
                Long.class
        );
        if (idRol == null) {
            idRol = 5L; // PORTERO default
        }

        // 4. Crear Asignación
        Long idUnidad = null;
        if ("RESIDENTE".equals(rol)) {
            Object uObj = payload.get("idUnidad");
            if (uObj != null && !uObj.toString().isBlank()) {
                idUnidad = Long.valueOf(uObj.toString());
            } else {
                List<Long> uList = jdbcTemplate.queryForList(
                        "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :p AND ROWNUM = 1",
                        Map.of("p", idPersona),
                        Long.class
                );
                if (!uList.isEmpty()) {
                    idUnidad = uList.get(0);
                } else {
                    idUnidad = 1L;
                }
            }
        }

        String sqlAsig = """
            INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
            VALUES (:idUsuario, :idRol, :orgId, :propId, :idUnidad, 'ACTIVA', TRUNC(SYSDATE))
            """;
        MapSqlParameterSource paramA = new MapSqlParameterSource()
                .addValue("idUsuario", idUsuario)
                .addValue("idRol", idRol)
                .addValue("orgId", orgId)
                .addValue("propId", propId)
                .addValue("idUnidad", idUnidad);
        jdbcTemplate.update(sqlAsig, paramA);

        if (enviarActivacion) {
            final Long finalIdUsuario = idUsuario;
            tokenServiceProvider.ifAvailable(svc -> {
                try {
                    svc.generarYEnviarTokenActivacion(finalIdUsuario, "0.0.0.0");
                } catch (Exception ignored) {}
            });
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "idUsuario", idUsuario,
                "username", username,
                "rol", rol,
                "message", "Usuario creado y asignado exitosamente"
        )));
    }

    @Operation(summary = "Actualizar estado, contraseña o rol de un usuario dentro del perímetro del tenant")
    @PutMapping("/{id}")
    @Transactional
    @Auditable(action = "UPDATE", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> actualizarUsuario(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        SaedContext ctx = SaedContextHolder.getContext();
        String callerRole = ctx != null ? ctx.getRoleCode() : "";
        Long callerOrgId = ctx != null ? ctx.getOrganizationId() : null;
        Long callerPropId = ctx != null ? ctx.getPropertyId() : null;

        // Validar perímetro y permisos sobre el usuario destino
        if (!"SUPERADMIN".equals(callerRole)) {
            String checkSql = """
                SELECT ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, r.CODIGO AS ROL_CODIGO
                FROM USUARIO_ASIGNACIONES ua
                JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
                WHERE ua.ID_USUARIO = :id AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                """;
            List<Map<String, Object>> asigs = jdbcTemplate.queryForList(checkSql, Map.of("id", id));
            if (asigs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Usuario no encontrado en su ámbito de gestión"));
            }

            boolean isTargetSuperAdmin = asigs.stream().anyMatch(a -> "SUPERADMIN".equals(a.get("ROL_CODIGO")));
            if (isTargetSuperAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para modificar un Superadministrador"));
            }

            if ("ADMIN_PROPIEDAD".equals(callerRole)) {
                boolean belongsToProp = asigs.stream().anyMatch(a -> {
                    Object p = a.get("ID_PROPIEDAD");
                    return p != null && callerPropId != null && callerPropId.equals(((Number) p).longValue());
                });
                if (!belongsToProp) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("El usuario no pertenece a la propiedad asignada"));
                }

                // Evitar que modifique a otros administradores a menos que sea su propia cuenta
                boolean isOtherAdmin = asigs.stream().anyMatch(a ->
                    "ADMIN_ORGANIZACION".equals(a.get("ROL_CODIGO")) || "ADMIN_PROPIEDAD".equals(a.get("ROL_CODIGO"))
                );
                Long callerUserId = ctx != null ? ctx.getUserId() : null;
                if (isOtherAdmin && (callerUserId == null || !callerUserId.equals(id))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("No tiene permisos para modificar credenciales de otros administradores"));
                }
            } else if ("ADMIN_ORGANIZACION".equals(callerRole)) {
                boolean belongsToOrg = asigs.stream().anyMatch(a -> {
                    Object o = a.get("ID_ORGANIZACION");
                    return o != null && callerOrgId != null && callerOrgId.equals(((Number) o).longValue());
                });
                if (!belongsToOrg) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("El usuario no pertenece a su organización"));
                }
            }
        }

        String estado = (String) payload.get("estado");
        if (payload.containsKey("activo")) {
            estado = Boolean.TRUE.equals(payload.get("activo")) ? "ACTIVO" : "INACTIVO";
        }

        String rawPassword = (String) payload.getOrDefault("password", payload.get("passwordHash"));
        String rol = (String) payload.get("rol");

        // Anti-escalamiento de rol
        if (rol != null && !rol.trim().isBlank()) {
            rol = rol.trim().toUpperCase();
            if ("ADMIN_PROPIEDAD".equals(callerRole) && ("SUPERADMIN".equals(rol) || "ADMIN_ORGANIZACION".equals(rol) || "ADMIN_PROPIEDAD".equals(rol))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para otorgar roles administrativos"));
            }
            if ("ADMIN_ORGANIZACION".equals(callerRole) && "SUPERADMIN".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para asignar rol Superadministrador"));
            }
        }

        if (rawPassword != null && !rawPassword.trim().isBlank()) {
            jdbcTemplate.update(
                    "UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :id",
                    Map.of("pwd", passwordEncoder.encode(rawPassword.trim()), "id", id)
            );
        }

        if (estado != null) {
            String asignacionEstado = "ACTIVO".equals(estado) ? "ACTIVA" : "INACTIVA";
            if ("ADMIN_PROPIEDAD".equals(callerRole) && callerPropId != null) {
                jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = :est WHERE ID_USUARIO = :id AND ID_PROPIEDAD = :propId",
                        Map.of("est", asignacionEstado, "id", id, "propId", callerPropId)
                );
            } else if ("ADMIN_ORGANIZACION".equals(callerRole) && callerOrgId != null) {
                jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = :est WHERE ID_USUARIO = :id AND ID_ORGANIZACION = :orgId",
                        Map.of("est", asignacionEstado, "id", id, "orgId", callerOrgId)
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = :est WHERE ID_USUARIO = :id",
                        Map.of("est", asignacionEstado, "id", id)
                );
            }

            // Actualizar estado global del usuario solo si no quedan asignaciones activas o si se activa
            if ("ACTIVO".equals(estado)) {
                jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'ACTIVO' WHERE ID_USUARIO = :id", Map.of("id", id));
            } else {
                Integer remainingActive = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :id AND ESTADO IN ('ACTIVA', 'ACTIVO')",
                        Map.of("id", id),
                        Integer.class
                );
                if (remainingActive == null || remainingActive == 0) {
                    jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = :id", Map.of("id", id));
                }
            }
        }

        if (rol != null && !rol.trim().isBlank()) {
            Long idRol = jdbcTemplate.queryForObject(
                    "SELECT ID_ROL FROM ROLES WHERE CODIGO = :cod",
                    new MapSqlParameterSource("cod", rol),
                    Long.class
            );
            if (idRol != null) {
                if ("ADMIN_PROPIEDAD".equals(callerRole) && callerPropId != null) {
                    jdbcTemplate.update(
                            "UPDATE USUARIO_ASIGNACIONES SET ID_ROL = :idRol WHERE ID_USUARIO = :id AND ID_PROPIEDAD = :propId AND ESTADO = 'ACTIVA'",
                            Map.of("idRol", idRol, "id", id, "propId", callerPropId)
                    );
                } else if ("ADMIN_ORGANIZACION".equals(callerRole) && callerOrgId != null) {
                    jdbcTemplate.update(
                            "UPDATE USUARIO_ASIGNACIONES SET ID_ROL = :idRol WHERE ID_USUARIO = :id AND ID_ORGANIZACION = :orgId AND ESTADO = 'ACTIVA'",
                            Map.of("idRol", idRol, "id", id, "orgId", callerOrgId)
                    );
                } else {
                    jdbcTemplate.update(
                            "UPDATE USUARIO_ASIGNACIONES SET ID_ROL = :idRol WHERE ID_USUARIO = :id AND ESTADO = 'ACTIVA'",
                            Map.of("idRol", idRol, "id", id)
                    );
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Desactivar un usuario del sistema dentro del ámbito del tenant")
    @DeleteMapping("/{id}")
    @Transactional
    @Auditable(action = "DELETE", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        String callerRole = ctx != null ? ctx.getRoleCode() : "";
        Long callerOrgId = ctx != null ? ctx.getOrganizationId() : null;
        Long callerPropId = ctx != null ? ctx.getPropertyId() : null;
        Long callerUserId = ctx != null ? ctx.getUserId() : null;

        if (callerUserId != null && callerUserId.equals(id)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No puede desactivar su propia cuenta de usuario"));
        }

        if (!"SUPERADMIN".equals(callerRole)) {
            String checkSql = """
                SELECT ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, r.CODIGO AS ROL_CODIGO
                FROM USUARIO_ASIGNACIONES ua
                JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
                WHERE ua.ID_USUARIO = :id AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                """;
            List<Map<String, Object>> asigs = jdbcTemplate.queryForList(checkSql, Map.of("id", id));
            if (asigs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Usuario no encontrado en su ámbito de gestión"));
            }

            boolean isTargetSuperAdmin = asigs.stream().anyMatch(a -> "SUPERADMIN".equals(a.get("ROL_CODIGO")));
            if (isTargetSuperAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para desactivar un Superadministrador"));
            }

            if ("ADMIN_PROPIEDAD".equals(callerRole)) {
                boolean belongsToProp = asigs.stream().anyMatch(a -> {
                    Object p = a.get("ID_PROPIEDAD");
                    return p != null && callerPropId != null && callerPropId.equals(((Number) p).longValue());
                });
                if (!belongsToProp) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("El usuario no pertenece a la propiedad asignada"));
                }

                // Desactivar asignación para la propiedad
                jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'INACTIVA' WHERE ID_USUARIO = :id AND ID_PROPIEDAD = :propId",
                        Map.of("id", id, "propId", callerPropId)
                );
            } else if ("ADMIN_ORGANIZACION".equals(callerRole)) {
                boolean belongsToOrg = asigs.stream().anyMatch(a -> {
                    Object o = a.get("ID_ORGANIZACION");
                    return o != null && callerOrgId != null && callerOrgId.equals(((Number) o).longValue());
                });
                if (!belongsToOrg) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("El usuario no pertenece a su organización"));
                }

                jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'INACTIVA' WHERE ID_USUARIO = :id AND ID_ORGANIZACION = :orgId",
                        Map.of("id", id, "orgId", callerOrgId)
                );
            }

            // Si ya no quedan asignaciones activas, desactivar el usuario globalmente
            Integer remainingActive = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :id AND ESTADO IN ('ACTIVA', 'ACTIVO')",
                    Map.of("id", id),
                    Integer.class
            );
            if (remainingActive == null || remainingActive == 0) {
                jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = :id", Map.of("id", id));
            }
        } else {
            // SUPERADMIN
            jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = :id", Map.of("id", id));
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'INACTIVA' WHERE ID_USUARIO = :id", Map.of("id", id));
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
