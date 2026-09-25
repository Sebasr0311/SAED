package com.saed.backend.org.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.authorization.service.AssignmentManagementService;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.org.dto.CreateOrgAdminRequestDTO;
import com.saed.backend.org.dto.OrgAdminDTO;
import com.saed.backend.org.dto.UpdateOrgAdminRequestDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;


@Tag(name = "Organization Administrators", description = "Gestión de Administradores de Propiedad para la Organización cliente")
@RestController
@RequestMapping("/api/v1/org/admins")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgAdminsController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PropertyRepository propertyRepository;
    private final AssignmentManagementService assignmentManagementService;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider;
    private final com.saed.backend.common.service.EmailService emailService;

    public OrgAdminsController(NamedParameterJdbcTemplate jdbcTemplate,
                               PropertyRepository propertyRepository,
                               AssignmentManagementService assignmentManagementService,
                               PasswordEncoder passwordEncoder,
                               org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.service.TokenActivacionService> tokenServiceProvider,
                               com.saed.backend.common.service.EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.propertyRepository = propertyRepository;
        this.assignmentManagementService = assignmentManagementService;
        this.passwordEncoder = passwordEncoder;
        this.tokenServiceProvider = tokenServiceProvider;
        this.emailService = emailService;
    }

    @GetMapping
    public ApiResponse<List<OrgAdminDTO>> listAdmins() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        String sql = """
            SELECT u.id_usuario, u.nombre_usuario, u.email, u.estado AS usuario_estado,
                   p.primer_nombre, p.primer_apellido, p.telefono,
                   ua.id_asignacion, ua.id_rol, r.codigo AS rol_codigo, r.nombre AS rol_nombre,
                   ua.id_propiedad, pr.nombre AS propiedad_nombre, ua.estado AS asignacion_estado,
                   ua.fecha_inicio, ua.fecha_fin
            FROM USUARIO_ASIGNACIONES ua
            JOIN USUARIOS u ON u.id_usuario = ua.id_usuario
            JOIN PERSONAS p ON p.id_persona = u.id_persona
            JOIN ROLES r ON r.id_rol = ua.id_rol
            LEFT JOIN PROPIEDADES pr ON pr.id_propiedad = ua.id_propiedad
            WHERE ua.id_organizacion = :orgId
              AND r.codigo IN ('ADMIN_PROPIEDAD', 'ADMIN_ORGANIZACION')
            ORDER BY p.primer_apellido, p.primer_nombre, ua.id_asignacion
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, Map.of("orgId", orgId));
        Map<Long, OrgAdminDTO> adminMap = new LinkedHashMap<>();

        for (Map<String, Object> r : rows) {
            Long userId = ((Number) r.get("id_usuario")).longValue();
            OrgAdminDTO dto = adminMap.computeIfAbsent(userId, k -> {
                OrgAdminDTO d = new OrgAdminDTO();
                d.setIdUsuario(userId);
                d.setNombreUsuario((String) r.get("nombre_usuario"));
                d.setEmail((String) r.get("email"));
                d.setUsuarioEstado((String) r.get("usuario_estado"));
                d.setPrimerNombre((String) r.get("primer_nombre"));
                d.setPrimerApellido((String) r.get("primer_apellido"));
                d.setTelefono((String) r.get("telefono"));
                d.setIdRol(((Number) r.get("id_rol")).longValue());
                d.setRolCodigo((String) r.get("rol_codigo"));
                d.setRolNombre((String) r.get("rol_nombre"));
                d.setIdAsignacion(((Number) r.get("id_asignacion")).longValue());
                d.setAsignacionEstado("INACTIVA");

                Timestamp tsInicio = (Timestamp) r.get("fecha_inicio");
                if (tsInicio != null) d.setFechaInicio(tsInicio.toInstant().atZone(ZoneId.of("America/Bogota")));
                Timestamp tsFin = (Timestamp) r.get("fecha_fin");
                if (tsFin != null) d.setFechaFin(tsFin.toInstant().atZone(ZoneId.of("America/Bogota")));
                return d;
            });

            Long asigId = ((Number) r.get("id_asignacion")).longValue();
            String asigEstado = (String) r.get("asignacion_estado");
            Long propId = r.get("id_propiedad") != null ? ((Number) r.get("id_propiedad")).longValue() : null;
            String propNombre = (String) r.get("propiedad_nombre");

            if (propId != null) {
                if (!dto.getIdPropiedades().contains(propId)) {
                    dto.getIdPropiedades().add(propId);
                    if (propNombre != null) {
                        dto.getPropiedadesNombres().add(propNombre);
                    }
                    dto.getPropiedades().add(new OrgAdminDTO.AdminPropertyAssignmentDTO(asigId, propId, propNombre, asigEstado));
                }
            }

            if ("ADMIN_ORGANIZACION".equals(dto.getRolCodigo())) {
                dto.setAsignacionEstado("ACTIVA");
            } else if ("ACTIVA".equalsIgnoreCase(asigEstado) && propId != null) {
                dto.setAsignacionEstado("ACTIVA");
                dto.setIdAsignacion(asigId);
            }
        }

        for (OrgAdminDTO dto : adminMap.values()) {
            if ("ADMIN_ORGANIZACION".equals(dto.getRolCodigo())) {
                dto.setPropiedadNombre("Toda la Organización");
            } else if (dto.getIdPropiedades().isEmpty()) {
                dto.setPropiedadNombre(null);
                dto.setAsignacionEstado("INACTIVA");
            } else if (dto.getIdPropiedades().size() == 1) {
                dto.setIdPropiedad(dto.getIdPropiedades().get(0));
                dto.setPropiedadNombre(dto.getPropiedadesNombres().isEmpty() ? null : dto.getPropiedadesNombres().get(0));
            } else {
                dto.setIdPropiedad(dto.getIdPropiedades().get(0));
                dto.setPropiedadNombre(String.join(", ", dto.getPropiedadesNombres()));
            }
        }

        return ApiResponse.success(new ArrayList<>(adminMap.values()));
    }

    @PostMapping
    @Auditable(action = "CREATE", resource = "USUARIO", category = AuditCategory.SECURITY, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> createAdmin(@Valid @RequestBody CreateOrgAdminRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        // Anti-escalation: ADMIN_ORGANIZACION no puede crear otro ADMIN_ORGANIZACION ni SUPERADMIN
        String callerCode = ctx.getRoleCode();
        Long requestedRoleId = request.getIdRol();

        // En /org/admins, el rol a asignar es ADMIN_PROPIEDAD.
        // Resolver dinámicamente el ID del rol ADMIN_PROPIEDAD según el catálogo canónico
        Long adminPropiedadRoleId = jdbcTemplate.query(
            "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD' AND ESTADO = 'ACTIVO'",
            (rs, rowNum) -> rs.getLong("ID_ROL")
        ).stream().findFirst().orElse(2L);

        if (requestedRoleId == null || requestedRoleId == 2L || requestedRoleId == 3L) {
            requestedRoleId = adminPropiedadRoleId;
        }

        if (!"SUPERADMIN".equalsIgnoreCase(callerCode)) {
            List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                    "SELECT CODIGO, ALCANCE FROM ROLES WHERE ID_ROL = :idRol",
                    Map.of("idRol", requestedRoleId)
            );
            if (!roleRows.isEmpty()) {
                String targetCodigo = (String) roleRows.get(0).get("CODIGO");
                String targetAlcance = (String) roleRows.get(0).get("ALCANCE");
                if ("ADMIN_ORGANIZACION".equalsIgnoreCase(targetCodigo) || "ORGANIZACION".equalsIgnoreCase(targetAlcance)
                        || "SUPERADMIN".equalsIgnoreCase(targetCodigo) || "GLOBAL".equalsIgnoreCase(targetAlcance)) {
                    throw new AccessDeniedException("Un Administrador de Organización no puede crear otros Administradores de Organización.");
                }
            }
        }

        List<Long> targetPropIds = request.getResolvedPropiedades();
        List<String> assignedPropNames = new ArrayList<>();

        // BD-02: Validar que cada propiedad pertenezca a la organización
        for (Long pId : targetPropIds) {
            PropertyDTO prop = propertyRepository.findById(pId)
                    .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada (ID: " + pId + ") no existe"));
            if (!prop.getIdOrganizacion().equals(orgId)) {
                throw new AccessDeniedException("No puede asignar administradores a propiedades fuera de su organización");
            }
            assignedPropNames.add(prop.getNombre());
        }


        // 1. Resolver o Crear PERSONA
        Long idPersona = null;
        String doc = request.getNumeroDocumento() != null ? request.getNumeroDocumento().trim() : "";
        if (!doc.isBlank()) {
            List<Long> existingPersonas = jdbcTemplate.query(
                "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                Map.of("doc", doc),
                (rs, rowNum) -> rs.getLong("ID_PERSONA")
            );
            if (!existingPersonas.isEmpty()) {
                idPersona = existingPersonas.get(0);
                try {
                    jdbcTemplate.update("""
                        UPDATE PERSONAS 
                        SET primer_nombre = COALESCE(:nombre, primer_nombre),
                            primer_apellido = COALESCE(:apellido, primer_apellido),
                            email = COALESCE(:email, email),
                            telefono = COALESCE(:tel, telefono)
                        WHERE id_persona = :idPersona
                    """, new MapSqlParameterSource()
                        .addValue("idPersona", idPersona)
                        .addValue("nombre", request.getPrimerNombre())
                        .addValue("apellido", request.getPrimerApellido())
                        .addValue("email", request.getEmail())
                        .addValue("tel", request.getTelefono())
                    );
                } catch (Exception ignored) {}
            }
        }

        if (idPersona == null) {
            String sqlPersona = """
                INSERT INTO PERSONAS (id_tipo_documento, numero_documento, primer_nombre, primer_apellido, email, telefono, estado)
                VALUES (:idTipoDoc, :numDoc, :nombre, :apellido, :email, :tel, 'ACTIVO')
            """;
            MapSqlParameterSource paramPersona = new MapSqlParameterSource()
                    .addValue("idTipoDoc", request.getIdTipoDocumento() != null ? request.getIdTipoDocumento() : 1L)
                    .addValue("numDoc", request.getNumeroDocumento())
                    .addValue("nombre", request.getPrimerNombre())
                    .addValue("apellido", request.getPrimerApellido())
                    .addValue("email", request.getEmail())
                    .addValue("tel", request.getTelefono());

            KeyHolder khPersona = new GeneratedKeyHolder();
            jdbcTemplate.update(sqlPersona, paramPersona, khPersona, new String[]{"ID_PERSONA"});
            Number idPersonaNum = khPersona.getKey();
            if (idPersonaNum == null) {
                throw new IllegalStateException("No se pudo generar el ID para la persona");
            }
            idPersona = idPersonaNum.longValue();
        }

        // 2. Resolver o Crear USUARIO
        Long idUsuario = null;
        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.trim().isBlank()) {
            rawPassword = com.saed.backend.common.util.PasswordGenerator.generate();
        }

        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + ctx.getUserId() + "); EXCEPTION WHEN OTHERS THEN NULL; END;");
        } catch (Exception ignored) {}

        List<Map<String, Object>> existingUsers = jdbcTemplate.queryForList(
            "SELECT ID_USUARIO, NOMBRE_USUARIO, EMAIL FROM USUARIOS WHERE ID_PERSONA = :p",
            Map.of("p", idPersona)
        );
        if (existingUsers.isEmpty() && request.getEmail() != null && !request.getEmail().trim().isBlank()) {
            existingUsers = jdbcTemplate.queryForList(
                "SELECT ID_USUARIO, NOMBRE_USUARIO, EMAIL FROM USUARIOS WHERE LOWER(EMAIL) = LOWER(:email)",
                Map.of("email", request.getEmail().trim())
            );
        }

        if (!existingUsers.isEmpty()) {
            idUsuario = ((Number) existingUsers.get(0).get("ID_USUARIO")).longValue();
            jdbcTemplate.update(
                "UPDATE USUARIOS SET ID_PERSONA = :p WHERE ID_USUARIO = :uid AND ID_PERSONA IS NULL",
                Map.of("p", idPersona, "uid", idUsuario)
            );
            if (request.getPassword() != null && !request.getPassword().trim().isBlank()) {
                jdbcTemplate.update(
                    "UPDATE USUARIOS SET HASH_PASSWORD = :pwd WHERE ID_USUARIO = :uid",
                    Map.of("pwd", passwordEncoder.encode(rawPassword), "uid", idUsuario)
                );
            }
        } else {
            String username = request.getNombreUsuario();
            if (username == null || username.trim().isBlank()) {
                username = request.getEmail().split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", ".");
            }
            Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = LOWER(:u)",
                Map.of("u", username.trim()),
                Integer.class
            );
            if (usernameCount != null && usernameCount > 0) {
                username = username.trim() + "." + ((int)(Math.random() * 900) + 100);
            }

            String sqlUsuario = """
                INSERT INTO USUARIOS (id_persona, nombre_usuario, email, hash_password, estado, intentos_fallidos)
                VALUES (:idPersona, :username, :email, :pwd, 'ACTIVO', 0)
            """;
            MapSqlParameterSource paramUsuario = new MapSqlParameterSource()
                    .addValue("idPersona", idPersona)
                    .addValue("username", username)
                    .addValue("email", request.getEmail())
                    .addValue("pwd", passwordEncoder.encode(rawPassword));

            KeyHolder khUsuario = new GeneratedKeyHolder();
            jdbcTemplate.update(sqlUsuario, paramUsuario, khUsuario, new String[]{"ID_USUARIO"});
            Number idUsuarioNum = khUsuario.getKey();
            if (idUsuarioNum == null) {
                throw new IllegalStateException("No se pudo generar el ID para el usuario");
            }
            idUsuario = idUsuarioNum.longValue();
        }

        // 3. Crear Asignaciones (soporte para múltiples propiedades o inactiva si no se seleccionó ninguna)
        Long primaryAsignacionId = null;
        if (targetPropIds.isEmpty()) {
            List<Long> existingInactive = jdbcTemplate.query(
                "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ID_ORGANIZACION = :orgId AND ID_PROPIEDAD IS NULL",
                Map.of("uid", idUsuario, "orgId", orgId),
                (rs, rowNum) -> rs.getLong("ID_ASIGNACION")
            );
            if (!existingInactive.isEmpty()) {
                primaryAsignacionId = existingInactive.get(0);
            } else {
                KeyHolder khAsig = new GeneratedKeyHolder();
                jdbcTemplate.update("""
                    INSERT INTO USUARIO_ASIGNACIONES (id_usuario, id_rol, id_organizacion, id_propiedad, estado, fecha_inicio)
                    VALUES (:uid, :rolId, :orgId, NULL, 'INACTIVA', TRUNC(CURRENT_DATE))
                """, new MapSqlParameterSource()
                    .addValue("uid", idUsuario)
                    .addValue("rolId", requestedRoleId)
                    .addValue("orgId", orgId),
                    khAsig, new String[]{"ID_ASIGNACION"}
                );
                Number asigKey = khAsig.getKey();
                primaryAsignacionId = asigKey != null ? asigKey.longValue() : null;
            }
        } else {
            jdbcTemplate.update(
                "DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ID_ORGANIZACION = :orgId AND ID_PROPIEDAD IS NULL",
                Map.of("uid", idUsuario, "orgId", orgId)
            );

            for (Long pId : targetPropIds) {
                List<Long> existingAssignments = jdbcTemplate.query(
                    "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ID_PROPIEDAD = :propId AND ID_ROL = :rolId",
                    Map.of("uid", idUsuario, "propId", pId, "rolId", requestedRoleId),
                    (rs, rowNum) -> rs.getLong("ID_ASIGNACION")
                );

                Long asigId;
                if (!existingAssignments.isEmpty()) {
                    asigId = existingAssignments.get(0);
                    jdbcTemplate.update(
                        "UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = :asigId",
                        Map.of("asigId", asigId)
                    );
                } else {
                    AssignmentRequestDTO assignReq = new AssignmentRequestDTO();
                    assignReq.setIdUsuario(idUsuario);
                    assignReq.setIdRol(requestedRoleId);
                    assignReq.setIdOrganizacion(orgId);
                    assignReq.setIdPropiedad(pId);
                    asigId = assignmentManagementService.create(assignReq);
                }
                if (primaryAsignacionId == null) {
                    primaryAsignacionId = asigId;
                }
            }
        }

        // Restaurar contexto organizacional normal
        try {
            jdbcTemplate.getJdbcOperations().execute(String.format(
                "BEGIN PKG_SAED_SESSION.SET_CONTEXT(%d, %d, NULL, '%s'); EXCEPTION WHEN OTHERS THEN NULL; END;",
                ctx.getUserId(), orgId, ctx.getRoleCode()
            ));
        } catch (Exception ignored) {}

        // 4. Enviar correo con credenciales de acceso creadas por el usuario
        String creatorName = "Administrador de Organización";
        try {
            if (ctx.getUserId() != null) {
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

        String orgName = "Tu Organización";
        try {
            List<String> oNames = jdbcTemplate.query(
                "SELECT NOMBRE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :oid",
                Map.of("oid", orgId),
                (rs, rowNum) -> rs.getString(1)
            );
            if (!oNames.isEmpty()) orgName = oNames.get(0);
        } catch (Exception ignored) {}

        String propName = assignedPropNames.isEmpty() 
            ? "Sin propiedades asignadas (Inactivo)" 
            : String.join(", ", assignedPropNames);

        String fullName = (request.getPrimerNombre().trim() + " " + request.getPrimerApellido().trim()).trim();
        try {
            emailService.enviarCredencialesCreadoPorUsuarioAsync(
                request.getEmail(),
                fullName,
                creatorName,
                "ADMIN_ORGANIZACION",
                orgName,
                propName,
                null,
                "ADMIN_PROPIEDAD",
                request.getNombreUsuario(),
                rawPassword,
                "https://saedfront.vercel.app/login"
            );
        } catch (Exception ignored) {}

        String respMessage = targetPropIds.isEmpty()
            ? "Administrador creado exitosamente en estado inactivo (sin propiedades a su cargo)."
            : "Administrador creado y asignado exitosamente a " + targetPropIds.size() + " propiedad(es). Se enviaron credenciales a " + request.getEmail();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "idUsuario", idUsuario,
                "idAsignacion", primaryAsignacionId != null ? primaryAsignacionId : 0L,
                "message", respMessage
        ));
    }

    @PutMapping("/{assignmentId}")
    @Auditable(action = "UPDATE", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAdmin(
            @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateOrgAdminRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        // 1. Consultar la asignación actual
        List<Map<String, Object>> asigRows = jdbcTemplate.queryForList(
            "SELECT ua.ID_USUARIO, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_ROL, r.CODIGO AS ROL_CODIGO, u.ID_PERSONA " +
            "FROM USUARIO_ASIGNACIONES ua " +
            "JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
            "JOIN USUARIOS u ON u.ID_USUARIO = ua.ID_USUARIO " +
            "WHERE ua.ID_ASIGNACION = :asigId",
            Map.of("asigId", assignmentId)
        );

        if (asigRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Asignación no encontrada"));
        }

        Map<String, Object> asig = asigRows.get(0);
        Long asigOrgId = ((Number) asig.get("ID_ORGANIZACION")).longValue();
        String rolCodigo = (String) asig.get("ROL_CODIGO");
        Long targetUserId = ((Number) asig.get("ID_USUARIO")).longValue();
        Long roleId = ((Number) asig.get("ID_ROL")).longValue();
        Long currentPropId = asig.get("ID_PROPIEDAD") != null ? ((Number) asig.get("ID_PROPIEDAD")).longValue() : null;
        Long idPersona = asig.get("ID_PERSONA") != null ? ((Number) asig.get("ID_PERSONA")).longValue() : null;

        if (!orgId.equals(asigOrgId)) {
            throw new AccessDeniedException("No tiene permisos para modificar administradores de otra organización");
        }

        if (!"SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode())) {
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(rolCodigo) || targetUserId.equals(ctx.getUserId())) {
                throw new AccessDeniedException("Un Administrador de Organización no puede editar su propia cuenta ni otros administradores organizacionales.");
            }
            if (!"ADMIN_PROPIEDAD".equalsIgnoreCase(rolCodigo)) {
                throw new AccessDeniedException("Solo se permite editar cuentas de Administrador de Propiedad.");
            }
        }

        // 2. Si se especificaron propiedades (múltiples o lista vacía), sincronizar
        if (request.getResolvedPropiedades() != null) {
            List<Long> newPropIds = request.getResolvedPropiedades();
            for (Long pId : newPropIds) {
                PropertyDTO prop = propertyRepository.findById(pId)
                        .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada (ID: " + pId + ") no existe"));
                if (!prop.getIdOrganizacion().equals(orgId)) {
                    throw new AccessDeniedException("No puede asignar administradores a propiedades fuera de su organización");
                }
            }

            // Obtener asignaciones actuales del usuario en esta organización para ADMIN_PROPIEDAD
            List<Map<String, Object>> currentAssignments = jdbcTemplate.queryForList("""
                SELECT ID_ASIGNACION, ID_PROPIEDAD, ESTADO 
                FROM USUARIO_ASIGNACIONES 
                WHERE ID_USUARIO = :uid AND ID_ORGANIZACION = :orgId AND ID_ROL = :rolId
            """, Map.of("uid", targetUserId, "orgId", orgId, "rolId", roleId));

            Set<Long> existingPropIds = new HashSet<>();
            for (Map<String, Object> ca : currentAssignments) {
                Long cpId = ca.get("ID_PROPIEDAD") != null ? ((Number) ca.get("ID_PROPIEDAD")).longValue() : null;
                Long asigItem = ((Number) ca.get("ID_ASIGNACION")).longValue();
                if (cpId == null) {
                    if (!newPropIds.isEmpty()) {
                        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = :asigId", Map.of("asigId", asigItem));
                    }
                } else {
                    existingPropIds.add(cpId);
                    if (!newPropIds.contains(cpId)) {
                        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = :asigId", Map.of("asigId", asigItem));
                    } else {
                        jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = :asigId", Map.of("asigId", asigItem));
                    }
                }
            }

            for (Long pId : newPropIds) {
                if (!existingPropIds.contains(pId)) {
                    AssignmentRequestDTO assignReq = new AssignmentRequestDTO();
                    assignReq.setIdUsuario(targetUserId);
                    assignReq.setIdRol(roleId);
                    assignReq.setIdOrganizacion(orgId);
                    assignReq.setIdPropiedad(pId);
                    assignmentManagementService.create(assignReq);
                }
            }

            if (newPropIds.isEmpty()) {
                Integer placeholderCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM USUARIO_ASIGNACIONES 
                    WHERE ID_USUARIO = :uid AND ID_ORGANIZACION = :orgId AND ID_PROPIEDAD IS NULL
                """, Map.of("uid", targetUserId, "orgId", orgId), Integer.class);

                if (placeholderCount == null || placeholderCount == 0) {
                    jdbcTemplate.update("""
                        INSERT INTO USUARIO_ASIGNACIONES (id_usuario, id_rol, id_organizacion, id_propiedad, estado, fecha_inicio)
                        VALUES (:uid, :rolId, :orgId, NULL, 'INACTIVA', TRUNC(CURRENT_DATE))
                    """, Map.of("uid", targetUserId, "rolId", roleId, "orgId", orgId));
                }
            }
        } else if (request.getIdPropiedad() != null && !request.getIdPropiedad().equals(currentPropId)) {
            PropertyDTO prop = propertyRepository.findById(request.getIdPropiedad())
                    .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada no existe"));
            if (!prop.getIdOrganizacion().equals(orgId)) {
                throw new AccessDeniedException("No puede asignar administradores a propiedades fuera de su organización");
            }

            List<Long> duplicate = jdbcTemplate.query(
                "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ID_PROPIEDAD = :pId AND ID_ROL = :rId AND ID_ASIGNACION != :asigId",
                Map.of("uid", targetUserId, "pId", request.getIdPropiedad(), "rId", roleId, "asigId", assignmentId),
                (rs, rowNum) -> rs.getLong("ID_ASIGNACION")
            );
            if (!duplicate.isEmpty()) {
                throw new IllegalArgumentException("El administrador ya se encuentra asignado a esta propiedad.");
            }

            jdbcTemplate.update(
                "UPDATE USUARIO_ASIGNACIONES SET ID_PROPIEDAD = :propId WHERE ID_ASIGNACION = :asigId",
                Map.of("propId", request.getIdPropiedad(), "asigId", assignmentId)
            );
        }


        // 3. Actualizar datos de persona si se proporcionaron
        if (idPersona != null) {
            MapSqlParameterSource personParams = new MapSqlParameterSource()
                    .addValue("pId", idPersona)
                    .addValue("nombre", request.getPrimerNombre() != null && !request.getPrimerNombre().isBlank() ? request.getPrimerNombre().trim() : null)
                    .addValue("apellido", request.getPrimerApellido() != null && !request.getPrimerApellido().isBlank() ? request.getPrimerApellido().trim() : null)
                    .addValue("tel", request.getTelefono() != null && !request.getTelefono().isBlank() ? request.getTelefono().trim() : null)
                    .addValue("email", request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null);
            jdbcTemplate.update("""
                UPDATE PERSONAS
                SET primer_nombre = COALESCE(:nombre, primer_nombre),
                    primer_apellido = COALESCE(:apellido, primer_apellido),
                    telefono = COALESCE(:tel, telefono),
                    email = COALESCE(:email, email)
                WHERE id_persona = :pId
            """, personParams);
        }

        // 4. Si se actualizó el email, actualizar en USUARIOS
        if (request.getEmail() != null && !request.getEmail().trim().isBlank()) {
            String newEmail = request.getEmail().trim();
            List<Long> userWithEmail = jdbcTemplate.query(
                "SELECT ID_USUARIO FROM USUARIOS WHERE LOWER(EMAIL) = LOWER(:email) AND ID_USUARIO != :uid",
                Map.of("email", newEmail, "uid", targetUserId),
                (rs, rowNum) -> rs.getLong("ID_USUARIO")
            );
            if (!userWithEmail.isEmpty()) {
                throw new IllegalArgumentException("El correo electrónico ya se encuentra registrado para otro usuario.");
            }
            jdbcTemplate.update(
                "UPDATE USUARIOS SET EMAIL = :email WHERE ID_USUARIO = :uid",
                Map.of("email", newEmail, "uid", targetUserId)
            );
        }

        // 5. Si se actualizó la contraseña
        if (request.getPassword() != null && !request.getPassword().trim().isBlank()) {
            jdbcTemplate.update(
                "UPDATE USUARIOS SET HASH_PASSWORD = :pwd WHERE ID_USUARIO = :uid",
                Map.of("pwd", passwordEncoder.encode(request.getPassword().trim()), "uid", targetUserId)
            );
        }

        // 6. Si se envió estado para la asignación
        if (request.getEstado() != null && !request.getEstado().trim().isBlank()) {
            String est = request.getEstado().trim().toUpperCase();
            if ("ACTIVA".equals(est) || "SUSPENDIDA".equals(est) || "INACTIVA".equals(est)) {
                assignmentManagementService.updateStatus(assignmentId, est);
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Administrador de propiedad actualizado exitosamente"
        ));
    }

    @DeleteMapping("/{userId}/propiedades/{propertyId}")
    @Auditable(action = "DELETE", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> unassignProperty(
            @PathVariable Long userId,
            @PathVariable Long propertyId) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        // Validar propiedad
        PropertyDTO prop = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada no existe"));
        if (!prop.getIdOrganizacion().equals(orgId)) {
            throw new AccessDeniedException("No tiene permisos sobre propiedades de otra organización");
        }

        // Eliminar la asignación de esa propiedad
        jdbcTemplate.update("""
            DELETE FROM USUARIO_ASIGNACIONES 
            WHERE ID_USUARIO = :uid 
              AND ID_ORGANIZACION = :orgId 
              AND ID_PROPIEDAD = :propId
        """, Map.of("uid", userId, "orgId", orgId, "propId", propertyId));

        // Contar cuántas asignaciones con propiedad le quedan activas
        Integer remainingCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(1) 
            FROM USUARIO_ASIGNACIONES 
            WHERE ID_USUARIO = :uid 
              AND ID_ORGANIZACION = :orgId 
              AND ID_PROPIEDAD IS NOT NULL 
              AND ESTADO IN ('ACTIVA', 'ACTIVO')
        """, Map.of("uid", userId, "orgId", orgId), Integer.class);

        boolean isNowInactive = (remainingCount == null || remainingCount == 0);
        if (isNowInactive) {
            Long adminPropRoleId = jdbcTemplate.query(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD' AND ESTADO = 'ACTIVO'",
                (rs, rowNum) -> rs.getLong("ID_ROL")
            ).stream().findFirst().orElse(2L);

            Integer placeholderCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM USUARIO_ASIGNACIONES 
                WHERE ID_USUARIO = :uid AND ID_ORGANIZACION = :orgId AND ID_PROPIEDAD IS NULL
            """, Map.of("uid", userId, "orgId", orgId), Integer.class);

            if (placeholderCount == null || placeholderCount == 0) {
                jdbcTemplate.update("""
                    INSERT INTO USUARIO_ASIGNACIONES (id_usuario, id_rol, id_organizacion, id_propiedad, estado, fecha_inicio)
                    VALUES (:uid, :rolId, :orgId, NULL, 'INACTIVA', TRUNC(CURRENT_DATE))
                """, Map.of("uid", userId, "rolId", adminPropRoleId, "orgId", orgId));
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "isInactive", isNowInactive,
            "remainingProperties", remainingCount != null ? remainingCount : 0,
            "message", isNowInactive 
                ? "Administrador desvinculado de la propiedad. Al no tener más propiedades a cargo, su perfil queda como inactivo."
                : "Administrador desvinculado exitosamente de la propiedad."
        ));
    }

    @PatchMapping("/{assignmentId}/status")
    @Auditable(action = "UPDATE_STATUS", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long assignmentId, @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        if (estado == null || estado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "El campo estado es requerido"));
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        // Consultar la asignación y su rol objetivo
        List<Map<String, Object>> asigRows = jdbcTemplate.queryForList(
            "SELECT ua.ID_USUARIO, ua.ID_ORGANIZACION, r.CODIGO AS ROL_CODIGO " +
            "FROM USUARIO_ASIGNACIONES ua " +
            "JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
            "WHERE ua.ID_ASIGNACION = :asigId",
            Map.of("asigId", assignmentId)
        );

        if (asigRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Asignación no encontrada"));
        }

        Map<String, Object> asig = asigRows.get(0);
        Long asigOrgId = ((Number) asig.get("ID_ORGANIZACION")).longValue();
        String rolCodigo = (String) asig.get("ROL_CODIGO");
        Long targetUserId = ((Number) asig.get("ID_USUARIO")).longValue();

        if (!orgId.equals(asigOrgId)) {
            throw new AccessDeniedException("No tiene permisos para modificar asignaciones de otra organización");
        }

        // Regla: un admin organizacional no puede suspender su propia cuenta ni cuentas de ADMIN_ORGANIZACION
        if (!"SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode())) {
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(rolCodigo) || targetUserId.equals(ctx.getUserId())) {
                throw new AccessDeniedException("Un Administrador de Organización no puede suspender su propia cuenta ni administradores organizacionales. Solo puede gestionar Administradores de Propiedad.");
            }
            if (!"ADMIN_PROPIEDAD".equalsIgnoreCase(rolCodigo)) {
                throw new AccessDeniedException("Solo se permite suspender o activar Administradores de Propiedad.");
            }
        }

        assignmentManagementService.updateStatus(assignmentId, estado.toUpperCase());
        return ResponseEntity.ok(Map.of("success", true, "message", "Estado de asignación actualizado a " + estado.toUpperCase()));
    }

    @DeleteMapping("/{assignmentId}")
    @Auditable(action = "DELETE", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAdmin(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) Map<String, String> body) {
        return executeDeleteAdmin(assignmentId, body);
    }

    @PostMapping("/{assignmentId}/eliminar")
    @Auditable(action = "DELETE", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAdminPost(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) Map<String, String> body) {
        return executeDeleteAdmin(assignmentId, body);
    }

    private ResponseEntity<Map<String, Object>> executeDeleteAdmin(Long assignmentId, Map<String, String> body) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        Long callerUserId = ctx.getUserId();
        if (orgId == null || callerUserId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización o usuario activo");
        }

        String password = body != null ? body.get("password") : null;
        if (password == null || password.trim().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "message", "Se requiere doble autorización: ingrese su contraseña para confirmar la eliminación."
            ));
        }

        // 1. Validar la contraseña del admin autorizador
        List<String> callerPwdList = jdbcTemplate.query(
            "SELECT HASH_PASSWORD FROM USUARIOS WHERE ID_USUARIO = :uid",
            Map.of("uid", callerUserId),
            (rs, rowNum) -> rs.getString("HASH_PASSWORD")
        );
        if (callerPwdList.isEmpty() || !passwordEncoder.matches(password.trim(), callerPwdList.get(0))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Contraseña de autorización incorrecta. Verifique su contraseña actual."
            ));
        }

        // 2. Consultar la asignación a eliminar
        List<Map<String, Object>> asigRows = jdbcTemplate.queryForList(
            "SELECT ua.ID_USUARIO, ua.ID_ORGANIZACION, r.CODIGO AS ROL_CODIGO " +
            "FROM USUARIO_ASIGNACIONES ua " +
            "JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
            "WHERE ua.ID_ASIGNACION = :asigId",
            Map.of("asigId", assignmentId)
        );

        if (asigRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Asignación no encontrada"));
        }

        Map<String, Object> asig = asigRows.get(0);
        Long asigOrgId = ((Number) asig.get("ID_ORGANIZACION")).longValue();
        String rolCodigo = (String) asig.get("ROL_CODIGO");
        Long targetUserId = ((Number) asig.get("ID_USUARIO")).longValue();

        if (!orgId.equals(asigOrgId)) {
            throw new AccessDeniedException("No tiene permisos sobre administradores de otra organización");
        }

        if (!"SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode())) {
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(rolCodigo) || targetUserId.equals(callerUserId)) {
                throw new AccessDeniedException("Un Administrador de Organización no puede eliminar su propia cuenta ni administradores organizacionales.");
            }
            if (!"ADMIN_PROPIEDAD".equalsIgnoreCase(rolCodigo)) {
                throw new AccessDeniedException("Solo se permite eliminar cuentas de Administrador de Propiedad.");
            }
        }

        // 3. Eliminar la asignación
        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = :asigId", Map.of("asigId", assignmentId));

        // 4. Si el usuario ya no tiene más asignaciones, marcarlo como INACTIVO
        Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid",
            Map.of("uid", targetUserId),
            Integer.class
        );
        if (activeCount == null || activeCount == 0) {
            jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = :uid", Map.of("uid", targetUserId));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Administrador de propiedad eliminado exitosamente mediante doble autorización."
        ));
    }

    private OrgAdminDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        OrgAdminDTO dto = new OrgAdminDTO();
        dto.setIdUsuario(rs.getLong("id_usuario"));
        dto.setNombreUsuario(rs.getString("nombre_usuario"));
        dto.setEmail(rs.getString("email"));
        dto.setUsuarioEstado(rs.getString("usuario_estado"));
        dto.setPrimerNombre(rs.getString("primer_nombre"));
        dto.setPrimerApellido(rs.getString("primer_apellido"));
        dto.setTelefono(rs.getString("telefono"));
        dto.setIdAsignacion(rs.getLong("id_asignacion"));
        dto.setIdRol(rs.getLong("id_rol"));
        dto.setRolCodigo(rs.getString("rol_codigo"));
        dto.setRolNombre(rs.getString("rol_nombre"));

        long idProp = rs.getLong("id_propiedad");
        if (!rs.wasNull()) dto.setIdPropiedad(idProp);
        dto.setPropiedadNombre(rs.getString("propiedad_nombre"));
        dto.setAsignacionEstado(rs.getString("asignacion_estado"));

        Timestamp tsInicio = rs.getTimestamp("fecha_inicio");
        if (tsInicio != null) dto.setFechaInicio(tsInicio.toInstant().atZone(ZoneId.of("America/Bogota")));

        Timestamp tsFin = rs.getTimestamp("fecha_fin");
        if (tsFin != null) dto.setFechaFin(tsFin.toInstant().atZone(ZoneId.of("America/Bogota")));

        return dto;
    }
}
