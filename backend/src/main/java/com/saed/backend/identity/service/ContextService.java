package com.saed.backend.identity.service;

import com.saed.backend.context.SaedContext;
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
        return dto;
    };

    public List<UserAssignmentDTO> getUserContexts(Long userId) {
        // NOTE: If RLS blocks this query because the context is not set, 
        // a database change will be required to create a secure AUTH view.
        String sql = "SELECT ua.id_asignacion, ua.id_organizacion, ua.id_propiedad, ua.id_unidad, " +
                     "r.codigo, r.alcance " +
                     "FROM USUARIO_ASIGNACIONES ua " +
                     "JOIN ROLES r ON ua.id_rol = r.id_rol " +
                     "WHERE ua.id_usuario = ? AND ua.estado = 'ACTIVA'";
        return jdbcTemplate.query(sql, assignmentRowMapper, userId);
    }

    public SaedContext resolveContext(Long userId, Long assignmentId) {
        // Resolve the SaedContext securely from the DB
        String sql = "SELECT ua.id_asignacion, ua.id_organizacion, ua.id_propiedad, ua.id_unidad, " +
                     "r.codigo, r.alcance " +
                     "FROM USUARIO_ASIGNACIONES ua " +
                     "JOIN ROLES r ON ua.id_rol = r.id_rol " +
                     "WHERE ua.id_usuario = ? AND ua.id_asignacion = ? AND ua.estado = 'ACTIVA'";
        try {
            UserAssignmentDTO assignment = jdbcTemplate.queryForObject(sql, assignmentRowMapper, userId, assignmentId);
            
            return SaedContext.builder()
                    .userId(userId)
                    .organizationId(assignment.getIdOrganizacion())
                    .propertyId(assignment.getIdPropiedad())
                    .unitId(assignment.getIdUnidad())
                    .roleCode(assignment.getRoleCode())
                    .build();
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Asignación inválida o no pertenece al usuario");
        }
    }
}
