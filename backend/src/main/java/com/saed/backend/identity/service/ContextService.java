package com.saed.backend.identity.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.UserAssignmentDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
        SaedContext previousContext = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(userId).build());
            return jdbcTemplate.execute((ConnectionCallback<List<UserAssignmentDTO>>) conn -> {
                try (CallableStatement cs = conn.prepareCall("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?); EXCEPTION WHEN OTHERS THEN NULL; END;")) {
                    cs.setLong(1, userId);
                    cs.execute();
                }

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

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<UserAssignmentDTO> list = new ArrayList<>();
                        int rowNum = 0;
                        while (rs.next()) {
                            list.add(assignmentRowMapper.mapRow(rs, rowNum++));
                        }
                        return list;
                    }
                }
            });
        } finally {
            SaedContextHolder.setContext(previousContext);
        }
    }

    public SaedContext resolveContext(Long userId, Long assignmentId) {
        SaedContext previousContext = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(userId).build());
            return jdbcTemplate.execute((ConnectionCallback<SaedContext>) conn -> {
                try (CallableStatement cs = conn.prepareCall("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?); EXCEPTION WHEN OTHERS THEN NULL; END;")) {
                    cs.setLong(1, userId);
                    cs.execute();
                }

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

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ps.setLong(2, assignmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new RuntimeException("Asignación inválida o no pertenece al usuario");
                        }
                        UserAssignmentDTO assignment = assignmentRowMapper.mapRow(rs, 0);
                        return SaedContext.builder()
                                .userId(userId)
                                .organizationId(assignment.getIdOrganizacion())
                                .propertyId(assignment.getIdPropiedad())
                                .unitId(assignment.getIdUnidad())
                                .roleCode(assignment.getRoleCode())
                                .roleScope(assignment.getScope())
                                .build();
                    }
                }
            });
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Asignación inválida")) {
                throw e;
            }
            throw new RuntimeException("Asignación inválida o no pertenece al usuario", e);
        } finally {
            SaedContextHolder.setContext(previousContext);
        }
    }
}
