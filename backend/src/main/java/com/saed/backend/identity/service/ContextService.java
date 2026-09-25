package com.saed.backend.identity.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.UserAssignmentDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextService {

    private final JdbcTemplate jdbcTemplate;

    public ContextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserAssignmentDTO> assignmentRowMapper = (rs, rowNum) -> {
        UserAssignmentDTO dto = new UserAssignmentDTO();
        dto.setIdAsignacion(rs.getLong("id_asignacion"));
        
        long orgId = rs.getLong("id_organizacion");
        if (!rs.wasNull()) dto.setIdOrganizacion(orgId);
        
        long propId = rs.getLong("id_propiedad");
        if (!rs.wasNull()) dto.setIdPropiedad(propId);
        
        long unitId = rs.getLong("id_unidad");
        if (!rs.wasNull()) dto.setIdUnidad(unitId);
        
        dto.setRoleCode(rs.getString("codigo"));
        dto.setScope(rs.getString("alcance"));

        try {
            dto.setNombreOrganizacion(rs.getString("nombre_organizacion"));
        } catch (Exception ignored) {}
        try {
            dto.setNombrePropiedad(rs.getString("nombre_propiedad"));
        } catch (Exception ignored) {}
        try {
            dto.setIdentificadorUnidad(rs.getString("identificador_unidad"));
        } catch (Exception ignored) {}

        return dto;
    };

    public List<UserAssignmentDTO> getUserContexts(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + userId + "); EXCEPTION WHEN OTHERS THEN NULL; END;");
            String sql = "SELECT ua.id_asignacion, ua.id_organizacion, ua.id_propiedad, ua.id_unidad, " +
                         "r.codigo, r.alcance, " +
                         "o.nombre AS nombre_organizacion, " +
                         "p.nombre AS nombre_propiedad, " +
                         "u.identificador AS identificador_unidad " +
                         "FROM USUARIO_ASIGNACIONES ua " +
                         "JOIN ROLES r ON ua.id_rol = r.id_rol " +
                         "LEFT JOIN ORGANIZACIONES o ON ua.id_organizacion = o.id_organizacion " +
                         "LEFT JOIN PROPIEDADES p ON ua.id_propiedad = p.id_propiedad " +
                         "LEFT JOIN UNIDADES u ON ua.id_unidad = u.id_unidad " +
                         "WHERE ua.id_usuario = ? AND ua.estado = 'ACTIVA' " +
                         "ORDER BY ua.id_asignacion ASC";
            return jdbcTemplate.query(sql, assignmentRowMapper, userId);
        } finally {
            SaedContext current = SaedContextHolder.getContext();
            if (current != null && current.getRoleCode() != null) {
                try {
                    String plsql = "BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?); PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?); END;";
                    jdbcTemplate.update(plsql, current.getUserId(), current.getUserId(), current.getOrganizationId(), current.getPropertyId(), current.getRoleCode());
                } catch (Exception ignored) {}
            }
        }
    }

    public SaedContext resolveContext(Long userId, Long assignmentId) {
        String sql = "SELECT ua.id_asignacion, ua.id_organizacion, ua.id_propiedad, ua.id_unidad, " +
                     "r.codigo, r.alcance, " +
                     "o.nombre AS nombre_organizacion, " +
                     "p.nombre AS nombre_propiedad, " +
                     "u.identificador AS identificador_unidad " +
                     "FROM USUARIO_ASIGNACIONES ua " +
                     "JOIN ROLES r ON ua.id_rol = r.id_rol " +
                     "LEFT JOIN ORGANIZACIONES o ON ua.id_organizacion = o.id_organizacion " +
                     "LEFT JOIN PROPIEDADES p ON ua.id_propiedad = p.id_propiedad " +
                     "LEFT JOIN UNIDADES u ON ua.id_unidad = u.id_unidad " +
                     "WHERE ua.id_usuario = ? AND ua.id_asignacion = ? AND ua.estado = 'ACTIVA'";
        try {
            UserAssignmentDTO assignment = jdbcTemplate.queryForObject(sql, assignmentRowMapper, userId, assignmentId);
            
            return SaedContext.builder()
                    .userId(userId)
                    .organizationId(assignment.getIdOrganizacion())
                    .propertyId(assignment.getIdPropiedad())
                    .unitId(assignment.getIdUnidad())
                    .roleCode(assignment.getRoleCode())
                    .roleScope(assignment.getScope())
                    .build();
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Asignación inválida o no pertenece al usuario");
        }
    }
}
