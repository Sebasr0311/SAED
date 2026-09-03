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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
                .addValue("email", request.getEmailContacto())
                .addValue("telefono", request.getTelefonoContacto())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("pais", request.getPais());

        int updated = jdbcTemplate.update(sql, params);
        if (updated == 0) {
            throw new java.util.NoSuchElementException("Organización no encontrada para actualizar");
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Perfil de organización actualizado correctamente"));
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
