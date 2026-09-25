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
            ORDER BY p.primer_apellido, p.primer_nombre
        """;

        List<OrgAdminDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
        return ApiResponse.success(list);
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

        // En /org/admins, el rol a asignar es ADMIN_PROPIEDAD (idRol = 3).
        // Si el cliente envía idRol = 2 (código previo) o viene nulo, normalizamos a 3.
        if (requestedRoleId == null || (requestedRoleId == 2L && !"SUPERADMIN".equalsIgnoreCase(callerCode))) {
            requestedRoleId = 3L;
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

        // Requerir propiedad asignada obligatoria
        if (request.getIdPropiedad() == null) {
            throw new IllegalArgumentException("Debe especificar la propiedad a la que se asignará el administrador.");
        }

        // BD-02: Validar que la propiedad pertenezca a la organización
        PropertyDTO prop = propertyRepository.findById(request.getIdPropiedad())
                .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada no existe"));
        if (!prop.getIdOrganizacion().equals(orgId)) {
            throw new AccessDeniedException("No puede asignar administradores a propiedades fuera de su organización");
        }

        // 1. Insertar PERSONA
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
        Long idPersona = idPersonaNum.longValue();

        // 2. Insertar USUARIO
        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.trim().isBlank()) {
            rawPassword = com.saed.backend.common.util.PasswordGenerator.generate();
        }

        String sqlUsuario = """
            INSERT INTO USUARIOS (id_persona, nombre_usuario, email, hash_password, estado, intentos_fallidos)
            VALUES (:idPersona, :username, :email, :pwd, 'ACTIVO', 0)
        """;
        MapSqlParameterSource paramUsuario = new MapSqlParameterSource()
                .addValue("idPersona", idPersona)
                .addValue("username", request.getNombreUsuario())
                .addValue("email", request.getEmail())
                .addValue("pwd", passwordEncoder.encode(rawPassword));

        KeyHolder khUsuario = new GeneratedKeyHolder();
        jdbcTemplate.update(sqlUsuario, paramUsuario, khUsuario, new String[]{"ID_USUARIO"});
        Number idUsuarioNum = khUsuario.getKey();
        if (idUsuarioNum == null) {
            throw new IllegalStateException("No se pudo generar el ID para el usuario");
        }
        Long idUsuario = idUsuarioNum.longValue();

        // 3. Crear Asignación vía AssignmentManagementService
        AssignmentRequestDTO assignReq = new AssignmentRequestDTO();
        assignReq.setIdUsuario(idUsuario);
        assignReq.setIdRol(requestedRoleId); // 3 = ADMIN_PROPIEDAD
        assignReq.setIdOrganizacion(orgId);
        assignReq.setIdPropiedad(request.getIdPropiedad());

        Long idAsignacion = assignmentManagementService.create(assignReq);

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

        String propName = prop != null ? prop.getNombre() : null;

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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "idUsuario", idUsuario,
                "idAsignacion", idAsignacion,
                "message", "Administrador creado exitosamente. Se han enviado las credenciales de acceso a " + request.getEmail()
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
