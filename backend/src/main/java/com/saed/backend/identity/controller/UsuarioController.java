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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsuarioController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider;
    private final com.saed.backend.common.service.EmailService emailService;
    private final com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService;

    public UsuarioController(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider,
            com.saed.backend.common.service.EmailService emailService,
            com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenServiceProvider = tokenServiceProvider;
        this.emailService = emailService;
        this.convivienteQuotaService = convivienteQuotaService;
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

    @Operation(summary = "Crear nuevo usuario (Portero, Residente, Administrador o Residente de Convivencia)")
    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
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
            rawPassword = com.saed.backend.common.util.PasswordGenerator.generate();
            enviarActivacion = true;
        }

        String rol = (String) payload.getOrDefault("rol", "PORTERO");
        if (rol == null || rol.trim().isBlank()) {
            rol = "PORTERO";
        }
        rol = rol.trim().toUpperCase();

        String userEmail = (String) payload.getOrDefault("email", username + "@saed.com");
        if (userEmail != null && !userEmail.trim().isBlank()) {
            userEmail = userEmail.trim().toLowerCase();
        } else {
            userEmail = username + "@saed.com";
        }


        SaedContext ctx = SaedContextHolder.getContext();
        String callerRole = ctx != null ? ctx.getRoleCode() : "";
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        Long callerUnitId = ctx != null ? ctx.getUnitId() : null;

        Long targetUnitId = null;

        // Anti-escalamiento de privilegios por rol
        if ("RESIDENTE".equals(callerRole)) {
            if (!"RESIDENTE_CONVIVENCIA".equals(rol) && !"RESIDENTE".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Como Residente solo puede registrar personas para su unidad de convivencia"));
            }
            rol = "RESIDENTE_CONVIVENCIA";
            if (callerUnitId == null && ctx.getUserId() != null) {
                try {
                    List<Long> uList = jdbcTemplate.queryForList(
                        "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :uid AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1",
                        Map.of("uid", ctx.getUserId()), Long.class
                    );
                    if (!uList.isEmpty()) callerUnitId = uList.get(0);
                    if (callerUnitId == null) {
                        List<Long> aList = jdbcTemplate.queryForList(
                            "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ESTADO = 'ACTIVA' AND ID_UNIDAD IS NOT NULL AND ROWNUM = 1",
                            Map.of("uid", ctx.getUserId()), Long.class
                        );
                        if (!aList.isEmpty()) callerUnitId = aList.get(0);
                    }
                } catch (Exception ignored) {}
            }
            if (callerUnitId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No se encontró una unidad asignada a su usuario de residente"));
            }
            targetUnitId = callerUnitId;
        } else if ("ADMIN_PROPIEDAD".equals(callerRole)) {
            if (!"PORTERO".equals(rol) && !"RESIDENTE".equals(rol) && !"PROPIETARIO".equals(rol) && !"RESIDENTE_CONVIVENCIA".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Como Administrador de Propiedad solo puede registrar Porteros, Residentes o Propietarios"));
            }
            if (propId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No se encontró el identificador de la propiedad en el contexto"));
            }
            if ("RESIDENTE_CONVIVENCIA".equals(rol) && payload.get("idUnidad") != null) {
                try {
                    targetUnitId = Long.parseLong(payload.get("idUnidad").toString().trim());
                } catch (NumberFormatException ignored) {}
            }
        } else if ("ADMIN_ORGANIZACION".equals(callerRole)) {
            if ("SUPERADMIN".equals(rol) || "ADMIN_ORGANIZACION".equals(rol)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("No tiene permisos para asignar roles de nivel organización o plataforma"));
            }
            if (orgId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No se encontró el identificador de la organización en el contexto"));
            }
            if ("RESIDENTE_CONVIVENCIA".equals(rol) && payload.get("idUnidad") != null) {
                try {
                    targetUnitId = Long.parseLong(payload.get("idUnidad").toString().trim());
                } catch (NumberFormatException ignored) {}
            }
        } else if ("SUPERADMIN".equals(callerRole)) {
            if ("RESIDENTE_CONVIVENCIA".equals(rol) && payload.get("idUnidad") != null) {
                try {
                    targetUnitId = Long.parseLong(payload.get("idUnidad").toString().trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Si es registro de habitante de convivencia, validar y bloquear cuota con bloqueo pesimista
        if ("RESIDENTE_CONVIVENCIA".equals(rol) && targetUnitId != null) {
            convivienteQuotaService.validateAndLockQuota(targetUnitId);
        }

        if (orgId == null) orgId = 1L;
        if (propId == null) propId = 1L;

        SaedContext prevCtx = SaedContextHolder.getContext();
        Long effectiveOrgId = orgId;
        Long effectivePropId = propId;
        SaedContext systemCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(effectiveOrgId)
                .propertyId(effectivePropId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(systemCtx);
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.getJdbcOperations().execute(
                String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", effectiveOrgId, effectivePropId)
            );
        } catch (Exception ignored) {}

        Long idPersona = null;
        Long idUsuario = null;
        Long existingUserToReactivate = null;
        String creatorName = "Un usuario de SAED";
        String orgName = null;
        String propName = null;
        String unidadNombre = null;

        try {
            // 1. Resolver o Crear PERSONA
            Object idPersonaObj = payload.getOrDefault("idPersona", payload.get("idResidente"));
            if (idPersonaObj != null && !idPersonaObj.toString().isBlank()) {
                try { idPersona = Long.valueOf(idPersonaObj.toString()); } catch (Exception ignored) {}
            }
            String doc = (String) payload.getOrDefault("numeroDocumento", "");
            if (idPersona == null && doc != null && !doc.trim().isBlank()) {
                List<Long> existing = jdbcTemplate.query(
                        "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                        Map.of("doc", doc.trim()),
                        (rs, rowNum) -> rs.getLong("ID_PERSONA")
                );
                if (!existing.isEmpty()) {
                    idPersona = existing.get(0);
                }
            }

            if (idPersona != null) {
                List<Map<String, Object>> existingUsers = jdbcTemplate.queryForList(
                        "SELECT ID_USUARIO, NOMBRE_USUARIO, EMAIL, ESTADO FROM USUARIOS WHERE ID_PERSONA = :p",
                        Map.of("p", idPersona)
                );
                if (!existingUsers.isEmpty()) {
                    Long foundUid = ((Number) existingUsers.get(0).get("ID_USUARIO")).longValue();
                    // Verificar asignaciones activas
                    List<Map<String, Object>> activeAssignments = jdbcTemplate.queryForList(
                            "SELECT ua.ID_ASIGNACION, ua.ID_UNIDAD, ua.ID_PROPIEDAD, r.CODIGO AS ROL_CODIGO " +
                            "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
                            "WHERE ua.ID_USUARIO = :uid AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')",
                            Map.of("uid", foundUid)
                    );

                    if ("RESIDENTE".equals(callerRole)) {
                        Long callerUserId = ctx != null ? ctx.getUserId() : null;
                        if (callerUserId != null && callerUserId.equals(foundUid)) {
                            return ResponseEntity.badRequest()
                                    .body(ApiResponse.error("El residente titular principal no puede registrarse a sí mismo como conviviente"));
                        }

                        boolean hasAdminRole = false;
                        boolean activeInOtherUnit = false;
                        for (Map<String, Object> a : activeAssignments) {
                            String rCode = (String) a.get("ROL_CODIGO");
                            if ("SUPERADMIN".equals(rCode) || "ADMIN_ORGANIZACION".equals(rCode) || "ADMIN_PROPIEDAD".equals(rCode)) {
                                hasAdminRole = true;
                            }
                            Object u = a.get("ID_UNIDAD");
                            if (u != null && targetUnitId != null && !targetUnitId.equals(((Number) u).longValue())) {
                                activeInOtherUnit = true;
                            }
                        }
                        if (hasAdminRole) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(ApiResponse.error("La persona cuenta con un rol administrativo en el sistema y no puede ser vinculada como conviviente"));
                        }
                        if (activeInOtherUnit) {
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(ApiResponse.error("La persona ya cuenta con un usuario activo vinculado a otra unidad residencial"));
                        }
                    }
                    existingUserToReactivate = foundUid;
                }
            }

            // Verificar unicidad de username (permitiendo reutilización si es la misma cuenta que se reactiva)
            List<Long> uCheck = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u",
                    new MapSqlParameterSource("u", username),
                    (rs, r) -> rs.getLong(1)
            );
            if (!uCheck.isEmpty()) {
                Long foundUid = uCheck.get(0);
                if (existingUserToReactivate == null || !existingUserToReactivate.equals(foundUid)) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("El nombre de usuario '" + username + "' ya existe"));
                }
            }

            // Verificar unicidad de email (permitiendo reutilización si es la misma cuenta que se reactiva)
            if (userEmail != null && !userEmail.trim().isBlank()) {
                List<Long> eCheck = jdbcTemplate.query(
                        "SELECT ID_USUARIO FROM USUARIOS WHERE LOWER(EMAIL) = :e",
                        Map.of("e", userEmail.trim().toLowerCase()),
                        (rs, r) -> rs.getLong(1)
                );
                if (!eCheck.isEmpty()) {
                    Long foundEUid = eCheck.get(0);
                    if (existingUserToReactivate == null || !existingUserToReactivate.equals(foundEUid)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error("El correo electrónico '" + userEmail + "' ya se encuentra registrado en otra cuenta"));
                    }
                }
            }

            String primerNombre = (String) payload.getOrDefault("primerNombre", username);
            String primerApellido = (String) payload.getOrDefault("primerApellido", "PORTERO".equals(rol) ? "Vigilancia" : "Usuario");
            String tel = (String) payload.getOrDefault("telefono", "");

            if (idPersona == null) {
                String docFinal = (doc != null && !doc.trim().isBlank()) ? doc.trim() : "DOC-" + System.currentTimeMillis();
                Object tipoDocObj = payload.getOrDefault("tipoDocumentoId", payload.get("idTipoDocumento"));
                Long idTipoDoc = 1L;
                if (tipoDocObj != null) {
                    try { idTipoDoc = Long.valueOf(tipoDocObj.toString()); } catch (Exception ignored) {}
                }

                KeyHolder khPersona = new GeneratedKeyHolder();
                String sqlPersona = """
                    INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, TELEFONO, ESTADO)
                    VALUES (:tipoDoc, :doc, 'NATURAL', :nombre, :apellido, :email, :tel, 'ACTIVO')
                    """;
                MapSqlParameterSource paramP = new MapSqlParameterSource()
                        .addValue("tipoDoc", idTipoDoc)
                        .addValue("doc", docFinal)
                        .addValue("nombre", primerNombre)
                        .addValue("apellido", primerApellido)
                        .addValue("email", userEmail)
                        .addValue("tel", tel);
                jdbcTemplate.update(sqlPersona, paramP, khPersona, new String[]{"ID_PERSONA"});
                idPersona = extractGeneratedKey(khPersona, "ID_PERSONA");
                if (idPersona == null) {
                    List<Long> pIds = jdbcTemplate.query(
                        "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                        Map.of("doc", docFinal),
                        (rs, r) -> rs.getLong("ID_PERSONA")
                    );
                    if (!pIds.isEmpty()) idPersona = pIds.get(0);
                }
            } else {
                jdbcTemplate.update("""
                    UPDATE PERSONAS
                    SET PRIMER_NOMBRE = COALESCE(:nombre, PRIMER_NOMBRE),
                        PRIMER_APELLIDO = COALESCE(:apellido, PRIMER_APELLIDO),
                        EMAIL = :email,
                        TELEFONO = COALESCE(:tel, TELEFONO),
                        ESTADO = 'ACTIVO'
                    WHERE ID_PERSONA = :pid
                """, Map.of(
                    "nombre", primerNombre,
                    "apellido", primerApellido,
                    "email", userEmail,
                    "tel", tel,
                    "pid", idPersona
                ));
            }

            // 2. Insertar o Reactivar USUARIO
            if (existingUserToReactivate != null) {
                idUsuario = existingUserToReactivate;
                jdbcTemplate.update("""
                    UPDATE USUARIOS
                    SET NOMBRE_USUARIO = :username,
                        EMAIL = :email,
                        HASH_PASSWORD = :pwd,
                        ESTADO = 'ACTIVO',
                        INTENTOS_FALLIDOS = 0
                    WHERE ID_USUARIO = :id
                """, Map.of(
                    "username", username,
                    "email", userEmail,
                    "pwd", passwordEncoder.encode(rawPassword.trim()),
                    "id", idUsuario
                ));
            } else {
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
                idUsuario = extractGeneratedKey(khUsuario, "ID_USUARIO");
                if (idUsuario == null) {
                    List<Long> uIds = jdbcTemplate.query(
                        "SELECT ID_USUARIO FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u",
                        Map.of("u", username),
                        (rs, r) -> rs.getLong("ID_USUARIO")
                    );
                    if (!uIds.isEmpty()) idUsuario = uIds.get(0);
                }
                if (idUsuario == null) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error("No se pudo generar el ID del usuario"));
                }
            }

            // 3. Obtener ID_ROL
            Long idRol = null;
            try {
                List<Long> rList = jdbcTemplate.query(
                    "SELECT ID_ROL FROM ROLES WHERE CODIGO = :cod",
                    Map.of("cod", rol),
                    (rs, rowNum) -> rs.getLong(1)
                );
                if (!rList.isEmpty()) idRol = rList.get(0);
            } catch (Exception ignored) {}
            if (idRol == null && "RESIDENTE_CONVIVENCIA".equals(rol)) {
                try {
                    try {
                        jdbcTemplate.getJdbcOperations().execute("ALTER TABLE ROLES DROP CONSTRAINT CK_ROLES_CODIGO");
                        jdbcTemplate.getJdbcOperations().execute("ALTER TABLE ROLES ADD CONSTRAINT CK_ROLES_CODIGO CHECK (codigo IN ('SUPERADMIN', 'ADMIN_ORGANIZACION', 'PROPIETARIO', 'ADMIN_GENERAL', 'ADMIN_PROPIEDAD', 'PORTERO', 'VIGILANTE', 'RESIDENTE', 'RESIDENTE_CONVIVENCIA', 'PROPIETARIO_UNIDAD'))");
                    } catch (Exception ignored) {}

                    jdbcTemplate.getJdbcOperations().execute("""
                        MERGE INTO ROLES r USING (
                            SELECT 'RESIDENTE_CONVIVENCIA' AS CODIGO, 'Residente Conviviente' AS NOMBRE, 'UNIDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                        ) s ON (r.CODIGO = s.CODIGO)
                        WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO)
                    """);
                    List<Long> rList = jdbcTemplate.query(
                        "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA'",
                        (rs, rowNum) -> rs.getLong(1)
                    );
                    if (!rList.isEmpty()) idRol = rList.get(0);
                } catch (Exception e) {
                    log.error("Error al asegurar rol RESIDENTE_CONVIVENCIA: {}", e.getMessage());
                }
            }
            if (idRol == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("El rol especificado no existe o no está activo en el sistema: " + rol));
            }

            // 4. Crear Asignación
            Long idUnidad = targetUnitId;
            if (idUnidad == null && ("RESIDENTE".equals(rol) || "PROPIETARIO".equals(rol) || "RESIDENTE_CONVIVENCIA".equals(rol))) {
                Object uObj = payload.get("idUnidad");
                if (uObj != null && !uObj.toString().isBlank()) {
                    idUnidad = Long.valueOf(uObj.toString());
                } else if (callerUnitId != null) {
                    idUnidad = callerUnitId;
                } else {
                    List<Long> uList = jdbcTemplate.queryForList(
                            "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :p AND ROWNUM = 1",
                            Map.of("p", idPersona),
                            Long.class
                    );
                    if (uList.isEmpty()) {
                        uList = jdbcTemplate.queryForList(
                                "SELECT ID_UNIDAD FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA = :p AND ROWNUM = 1",
                                Map.of("p", idPersona),
                                Long.class
                        );
                    }
                    if (!uList.isEmpty()) {
                        idUnidad = uList.get(0);
                    } else {
                        idUnidad = 1L;
                    }
                }
            }

            // Alinear propiedad y organización con la unidad para satisfacer TRG_ASIGNACION_VALIDA_JERARQUIA
            if (idUnidad != null) {
                try {
                    List<Map<String, Object>> uProps = jdbcTemplate.query(
                        "SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION FROM UNIDADES u JOIN PROPIEDADES p ON p.ID_PROPIEDAD = u.ID_PROPIEDAD WHERE u.ID_UNIDAD = :u",
                        Map.of("u", idUnidad),
                        (rs, rowNum) -> Map.of(
                            "ID_PROPIEDAD", rs.getLong("ID_PROPIEDAD"),
                            "ID_ORGANIZACION", rs.getLong("ID_ORGANIZACION")
                        )
                    );
                    if (!uProps.isEmpty()) {
                        Number pId = (Number) uProps.get(0).get("ID_PROPIEDAD");
                        Number oId = (Number) uProps.get(0).get("ID_ORGANIZACION");
                        if (pId != null) effectivePropId = pId.longValue();
                        if (oId != null) effectiveOrgId = oId.longValue();
                    }
                } catch (Exception ignored) {}
            }

            // Verificar si ya existe asignación previa para esta unidad o si se crea nueva
            List<Long> asigExistentes = List.of();
            if (idUnidad != null) {
                asigExistentes = jdbcTemplate.query(
                    "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ID_UNIDAD = :u",
                    Map.of("uid", idUsuario, "u", idUnidad),
                    (rs, r) -> rs.getLong(1)
                );
            }

            if (!asigExistentes.isEmpty()) {
                jdbcTemplate.update("""
                    UPDATE USUARIO_ASIGNACIONES
                    SET ID_ROL = :idRol,
                        ID_ORGANIZACION = :orgId,
                        ID_PROPIEDAD = :propId,
                        ESTADO = 'ACTIVA',
                        FECHA_INICIO = TRUNC(SYSDATE),
                        FECHA_FIN = NULL
                    WHERE ID_ASIGNACION = :asigId
                """, Map.of(
                    "idRol", idRol,
                    "orgId", effectiveOrgId,
                    "propId", effectivePropId,
                    "asigId", asigExistentes.get(0)
                ));
            } else {
                String sqlAsig = """
                    INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                    VALUES (:idUsuario, :idRol, :orgId, :propId, :idUnidad, 'ACTIVA', TRUNC(SYSDATE))
                    """;
                MapSqlParameterSource paramA = new MapSqlParameterSource()
                        .addValue("idUsuario", idUsuario)
                        .addValue("idRol", idRol)
                        .addValue("orgId", effectiveOrgId)
                        .addValue("propId", effectivePropId)
                        .addValue("idUnidad", idUnidad);
                jdbcTemplate.update(sqlAsig, paramA);
            }

            // Si es habitante de unidad, asegurar registro en RESIDENTES_UNIDAD
            if (idUnidad != null && ("RESIDENTE".equals(rol) || "RESIDENTE_CONVIVENCIA".equals(rol))) {
                try {
                    Integer ruCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :u AND ID_PERSONA = :p",
                        Map.of("u", idUnidad, "p", idPersona),
                        Integer.class
                    );
                    String tipoRes = "RESIDENTE_CONVIVENCIA".equals(rol) ? "CONVIVIENTE" : "TITULAR";
                    if (ruCount == null || ruCount == 0) {
                        jdbcTemplate.update(
                            "INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) VALUES (:u, :p, :tipo, TRUNC(SYSDATE), 'ACTIVO')",
                            Map.of("u", idUnidad, "p", idPersona, "tipo", tipoRes)
                        );
                    } else {
                        jdbcTemplate.update(
                            "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'ACTIVO', FECHA_FIN = NULL, TIPO_RESIDENTE = :tipo WHERE ID_UNIDAD = :u AND ID_PERSONA = :p",
                            Map.of("u", idUnidad, "p", idPersona, "tipo", tipoRes)
                        );
                    }
                } catch (Exception e) {
                    log.warn("Aviso al asegurar RESIDENTES_UNIDAD para persona {} y unidad {}: {}", idPersona, idUnidad, e.getMessage());
                }
            }

            // Datos para correo
            try {
                if (ctx != null && ctx.getUserId() != null) {
                    List<String> names = jdbcTemplate.query(
                        "SELECT TRIM(p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO) FROM PERSONAS p JOIN USUARIOS u ON u.ID_PERSONA = p.ID_PERSONA WHERE u.ID_USUARIO = :uid",
                        Map.of("uid", ctx.getUserId()),
                        (rs, rowNum) -> rs.getString(1)
                    );
                    if (!names.isEmpty() && names.get(0) != null && !names.get(0).isBlank()) {
                        creatorName = names.get(0);
                    }
                }
            } catch (Exception ignored) {}

            try {
                List<String> oNames = jdbcTemplate.query("SELECT NOMBRE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :oid", Map.of("oid", effectiveOrgId), (rs, r) -> rs.getString(1));
                if (!oNames.isEmpty()) orgName = oNames.get(0);
            } catch (Exception ignored) {}

            try {
                List<String> pNames = jdbcTemplate.query("SELECT NOMBRE FROM PROPIEDADES WHERE ID_PROPIEDAD = :pid", Map.of("pid", effectivePropId), (rs, r) -> rs.getString(1));
                if (!pNames.isEmpty()) propName = pNames.get(0);
            } catch (Exception ignored) {}

            if (idUnidad != null) {
                try {
                    List<String> uNames = jdbcTemplate.query("SELECT IDENTIFICADOR FROM UNIDADES WHERE ID_UNIDAD = :uid", Map.of("uid", idUnidad), (rs, r) -> rs.getString(1));
                    if (!uNames.isEmpty()) unidadNombre = uNames.get(0);
                } catch (Exception ignored) {}
            }
        } finally {
            restoreSaedContext(prevCtx);
        }

        String recipientFullName = ((payload.get("primerNombre") != null ? payload.get("primerNombre") : username) + " " +
                                     (payload.get("primerApellido") != null ? payload.get("primerApellido") : "")).trim();

        try {
            emailService.enviarCredencialesCreadoPorUsuarioAsync(
                userEmail,
                recipientFullName,
                creatorName,
                callerRole,
                orgName,
                propName,
                unidadNombre,
                rol,
                username,
                rawPassword,
                "https://saedfront.vercel.app/login"
            );
        } catch (Exception ignored) {}

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "idUsuario", idUsuario,
                "username", username,
                "rol", rol,
                "passwordGenerada", rawPassword,
                "email", userEmail,
                "message", "Usuario creado exitosamente. Se han enviado las credenciales de acceso a " + userEmail
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

        SaedContext prevCtx = SaedContextHolder.getContext();
        Long effectiveOrgId = callerOrgId != null ? callerOrgId : 1L;
        Long effectivePropId = callerPropId != null ? callerPropId : 1L;
        SaedContext systemCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(effectiveOrgId)
                .propertyId(effectivePropId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(systemCtx);
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.getJdbcOperations().execute(
                String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", effectiveOrgId, effectivePropId)
            );
        } catch (Exception ignored) {}

        try {
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
        } finally {
            restoreSaedContext(prevCtx);
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

        SaedContext prevCtx = SaedContextHolder.getContext();
        Long effectiveOrgId = callerOrgId != null ? callerOrgId : 1L;
        Long effectivePropId = callerPropId != null ? callerPropId : 1L;
        SaedContext systemCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(effectiveOrgId)
                .propertyId(effectivePropId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(systemCtx);
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.getJdbcOperations().execute(
                String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", effectiveOrgId, effectivePropId)
            );
        } catch (Exception ignored) {}

        try {
            if (!"SUPERADMIN".equals(callerRole)) {
                if ("ADMIN_PROPIEDAD".equals(callerRole)) {
                    // Desactivar asignación para la propiedad
                    jdbcTemplate.update(
                            "UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'INACTIVA' WHERE ID_USUARIO = :id AND ID_PROPIEDAD = :propId",
                            Map.of("id", id, "propId", callerPropId)
                    );
                } else if ("ADMIN_ORGANIZACION".equals(callerRole)) {
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
        } finally {
            restoreSaedContext(prevCtx);
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long extractGeneratedKey(KeyHolder kh, String columnName) {
        if (kh == null) return null;
        if (kh.getKey() != null) return kh.getKey().longValue();
        if (kh.getKeys() != null) {
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
        }
        if (kh.getKeyList() != null) {
            for (Map<String, Object> map : kh.getKeyList()) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (!entry.getKey().equalsIgnoreCase("ROWID") && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
            }
        }
        return null;
    }

    private void restoreSaedContext(SaedContext prevCtx) {
        if (prevCtx != null) {
            SaedContextHolder.setContext(prevCtx);
            if (prevCtx.getUserId() != null && prevCtx.getRoleCode() != null) {
                try {
                    Long orgId = prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 0L;
                    Long propId = prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 0L;
                    String role = prevCtx.getRoleCode();
                    jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %d, %d, '%s'); END;",
                            prevCtx.getUserId(), prevCtx.getUserId(), orgId, propId, role)
                    );
                } catch (Exception ignored) {}
            }
        } else {
            SaedContextHolder.clearContext();
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
        }
    }
}
