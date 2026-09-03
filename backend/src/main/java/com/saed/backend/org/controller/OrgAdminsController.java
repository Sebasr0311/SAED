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

    public OrgAdminsController(NamedParameterJdbcTemplate jdbcTemplate,
                               PropertyRepository propertyRepository,
                               AssignmentManagementService assignmentManagementService,
                               PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.propertyRepository = propertyRepository;
        this.assignmentManagementService = assignmentManagementService;
        this.passwordEncoder = passwordEncoder;
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

        // BD-02: Validar que la propiedad pertenezca a la organización
        if (request.getIdPropiedad() != null) {
            PropertyDTO prop = propertyRepository.findById(request.getIdPropiedad())
                    .orElseThrow(() -> new IllegalArgumentException("La propiedad especificada no existe"));
            if (!prop.getIdOrganizacion().equals(orgId)) {
                throw new AccessDeniedException("No puede asignar administradores a propiedades fuera de su organización");
            }
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
        String sqlUsuario = """
            INSERT INTO USUARIOS (id_persona, nombre_usuario, email, hash_password, estado, intentos_fallidos)
            VALUES (:idPersona, :username, :email, :pwd, 'ACTIVO', 0)
        """;
        MapSqlParameterSource paramUsuario = new MapSqlParameterSource()
                .addValue("idPersona", idPersona)
                .addValue("username", request.getNombreUsuario())
                .addValue("email", request.getEmail())
                .addValue("pwd", passwordEncoder.encode(request.getPassword()));

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
        assignReq.setIdRol(request.getIdRol() != null ? request.getIdRol() : 2L); // 2 = ADMIN_PROPIEDAD
        assignReq.setIdOrganizacion(orgId);
        assignReq.setIdPropiedad(request.getIdPropiedad());

        Long idAsignacion = assignmentManagementService.create(assignReq);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "idUsuario", idUsuario,
                "idAsignacion", idAsignacion,
                "message", "Administrador creado y asignado exitosamente"
        ));
    }

    @PatchMapping("/{assignmentId}/status")
    @Auditable(action = "UPDATE_STATUS", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long assignmentId, @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        if (estado == null || estado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "El campo estado es requerido"));
        }
        assignmentManagementService.updateStatus(assignmentId, estado.toUpperCase());
        return ResponseEntity.ok(Map.of("success", true, "message", "Estado de asignación actualizado"));
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
