package com.saed.backend.org.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.org.dto.OrgProfileDTO;
import com.saed.backend.org.dto.OrgProfileUpdateRequestDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Organization Profile", description = "Gestión del perfil institucional de la Organización cliente")
@RestController
@RequestMapping("/api/v1/org/profile")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgProfileController {

    private static final Logger log = LoggerFactory.getLogger(OrgProfileController.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrgProfileController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<OrgProfileDTO> getProfile() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        String sql = """
            SELECT id_organizacion, nombre, identificacion_fiscal, email_contacto,
                   telefono_contacto, direccion, ciudad, pais, estado, fecha_creacion
            FROM ORGANIZACIONES
            WHERE id_organizacion = :orgId
        """;

        List<OrgProfileDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
        if (results.isEmpty()) {
            throw new java.util.NoSuchElementException("Organización no encontrada");
        }
        return ApiResponse.success(results.get(0));
    }

    @PutMapping
    @Auditable(action = "UPDATE", resource = "ORGANIZACION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    @Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(@Valid @RequestBody OrgProfileUpdateRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx.getOrganizationId();
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        String nuevoEmail = request.getEmailContacto() != null ? request.getEmailContacto().trim().toLowerCase() : null;

        // 1. Identificar el usuario administrador organizacional asociado a la organización
        Long currentUserId = ctx.getUserId();
        List<Long> adminUserIds = jdbcTemplate.query(
            """
            SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua
            JOIN ROLES r ON ua.ID_ROL = r.ID_ROL AND r.CODIGO = 'ADMIN_ORGANIZACION'
            WHERE ua.ID_ORGANIZACION = :orgId AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
            ORDER BY ua.ID_ASIGNACION ASC
            """,
            new MapSqlParameterSource("orgId", orgId),
            (rs, rowNum) -> rs.getLong("ID_USUARIO")
        );

        Long targetAdminUserId = (currentUserId != null && adminUserIds.contains(currentUserId))
            ? currentUserId
            : (!adminUserIds.isEmpty() ? adminUserIds.get(0) : null);

        // 2. Si se proporciona nuevo email, validar colisión con otros usuarios en el sistema
        if (nuevoEmail != null && !nuevoEmail.isBlank() && targetAdminUserId != null) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(EMAIL) = :email AND ID_USUARIO != :targetId",
                new MapSqlParameterSource("email", nuevoEmail).addValue("targetId", targetAdminUserId),
                Integer.class
            );
            if (count != null && count > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo electrónico ya se encuentra registrado para otro usuario en el sistema.");
            }
        }

        // 3. Actualizar datos en la entidad ORGANIZACIONES
        String sql = """
            UPDATE ORGANIZACIONES
            SET email_contacto = COALESCE(:email, email_contacto),
                telefono_contacto = COALESCE(:telefono, telefono_contacto),
                direccion = COALESCE(:direccion, direccion),
                ciudad = COALESCE(:ciudad, ciudad),
                pais = COALESCE(:pais, pais)
            WHERE id_organizacion = :orgId
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("email", nuevoEmail)
                .addValue("telefono", request.getTelefonoContacto())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("pais", request.getPais());

        int updated = jdbcTemplate.update(sql, params);
        if (updated == 0) {
            throw new java.util.NoSuchElementException("Organización no encontrada para actualizar");
        }

        // 4. Sincronizar el correo en USUARIOS, PERSONAS y ONBOARDING_INTENCIONES para que cuando el Superadmin reenvíe credenciales lleguen al nuevo correo
        if (nuevoEmail != null && !nuevoEmail.isBlank() && targetAdminUserId != null) {
            try {
                jdbcTemplate.update(
                    "UPDATE USUARIOS SET EMAIL = :email WHERE ID_USUARIO = :id",
                    new MapSqlParameterSource("email", nuevoEmail).addValue("id", targetAdminUserId)
                );

                jdbcTemplate.update(
                    """
                    UPDATE PERSONAS
                    SET EMAIL = :email,
                        TELEFONO = COALESCE(:telefono, TELEFONO)
                    WHERE ID_PERSONA = (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :id)
                    """,
                    new MapSqlParameterSource("email", nuevoEmail)
                            .addValue("telefono", request.getTelefonoContacto())
                            .addValue("id", targetAdminUserId)
                );

                jdbcTemplate.update(
                    """
                    UPDATE ONBOARDING_INTENCIONES
                    SET CORREO_DETALLE = 'Correo institucional actualizado por la organización a ' || :email
                    WHERE ID_ORGANIZACION = :orgId OR ID_USUARIO = :id
                    """,
                    new MapSqlParameterSource("email", nuevoEmail)
                            .addValue("orgId", orgId)
                            .addValue("id", targetAdminUserId)
                );
                log.info("[OrgProfile] Sincronizado nuevo correo {} para admin organizacional {} en org {}", nuevoEmail, targetAdminUserId, orgId);
            } catch (Exception e) {
                log.warn("[OrgProfile] Aviso al sincronizar correo del usuario admin organizacional {}: {}", targetAdminUserId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Perfil de organización y canales de contacto actualizados correctamente"));
    }

    private OrgProfileDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_creacion");
        ZonedDateTime fecha = ts != null ? ts.toInstant().atZone(ZoneId.of("America/Bogota")) : null;

        return new OrgProfileDTO(
                rs.getLong("id_organizacion"),
                rs.getString("nombre"),
                rs.getString("identificacion_fiscal"),
                rs.getString("email_contacto"),
                rs.getString("telefono_contacto"),
                rs.getString("direccion"),
                rs.getString("ciudad"),
                rs.getString("pais"),
                rs.getString("estado"),
                fecha
        );
    }
}
