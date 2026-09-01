package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssignmentRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public AssignmentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AssignmentResponseDTO> rowMapper = (rs, rowNum) -> {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(rs.getLong("id_asignacion"));

        RoleDTO rol = new RoleDTO(rs.getString("rol_codigo"), rs.getString("rol_alcance"));
        dto.setRol(rol);

        if (rs.getObject("id_organizacion") != null) {
            dto.setOrganizacion(new OrganizationDTO(rs.getLong("id_organizacion"), rs.getString("organizacion_nombre")));
        }

        if (rs.getObject("id_propiedad") != null) {
            dto.setPropiedad(new PropertyDTO(rs.getLong("id_propiedad"), rs.getString("propiedad_nombre")));
        }

        if (rs.getObject("id_unidad") != null) {
            dto.setUnidad(new UnitDTO(rs.getLong("id_unidad"), rs.getString("identificador_unidad")));
        }

        return dto;
    };

    private static final String BASE_QUERY = """
        SELECT 
            ua.id_asignacion,
            r.codigo as rol_codigo,
            r.alcance as rol_alcance,
            o.id_organizacion, o.nombre as organizacion_nombre,
            p.id_propiedad, p.nombre as propiedad_nombre,
            u.id_unidad, u.identificador as identificador_unidad
        FROM USUARIO_ASIGNACIONES ua
        JOIN ROLES r ON ua.id_rol = r.id_rol
        LEFT JOIN ORGANIZACIONES o ON ua.id_organizacion = o.id_organizacion
        LEFT JOIN PROPIEDADES p ON ua.id_propiedad = p.id_propiedad
        LEFT JOIN UNIDADES u ON ua.id_unidad = u.id_unidad
        WHERE ua.id_usuario = ? 
          AND ua.estado = 'ACTIVA' 
          AND TRUNC(CURRENT_DATE) BETWEEN ua.fecha_inicio AND COALESCE(ua.fecha_fin, TRUNC(CURRENT_DATE))
    """;

    @Override
    public List<AssignmentResponseDTO> findAssignmentsByUsuarioId(Long idUsuario) {
        return jdbcTemplate.query(BASE_QUERY, rowMapper, idUsuario);
    }

    @Override
    public Optional<AssignmentResponseDTO> findByIdAndUsuarioId(Long idAsignacion, Long idUsuario) {
        // Validacion via PKG_AUTH_BOOTSTRAP (AUTHID DEFINER + EXEMPT ACCESS
        // POLICY): el query directo bajo RLS devuelve 0 filas cuando el
        // contexto de sesion aun no esta establecido (el filtro JWT valida
        // la asignacion ANTES de armar el SaedContext).
        try {
            var call = new org.springframework.jdbc.core.simple.SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("GET_ASSIGNMENT_CONTEXT")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                    new org.springframework.jdbc.core.SqlParameter("p_id_usuario", java.sql.Types.NUMERIC),
                    new org.springframework.jdbc.core.SqlParameter("p_id_asignacion", java.sql.Types.NUMERIC),
                    new org.springframework.jdbc.core.SqlOutParameter("p_org_id", java.sql.Types.NUMERIC),
                    new org.springframework.jdbc.core.SqlOutParameter("p_prop_id", java.sql.Types.NUMERIC),
                    new org.springframework.jdbc.core.SqlOutParameter("p_unidad_id", java.sql.Types.NUMERIC),
                    new org.springframework.jdbc.core.SqlOutParameter("p_rol_codigo", java.sql.Types.VARCHAR),
                    new org.springframework.jdbc.core.SqlOutParameter("p_alcance", java.sql.Types.VARCHAR)
                );
            java.util.Map<String, Object> out = call.execute(java.util.Map.of(
                "p_id_usuario", idUsuario,
                "p_id_asignacion", idAsignacion
            ));

            String rolCodigo = (String) out.get("p_rol_codigo");
            if (rolCodigo == null) {
                return Optional.empty();
            }

            AssignmentResponseDTO dto = new AssignmentResponseDTO();
            dto.setIdAsignacion(idAsignacion);
            dto.setRol(new RoleDTO(rolCodigo, (String) out.get("p_alcance")));

            Number orgId = (Number) out.get("p_org_id");
            if (orgId != null) {
                dto.setOrganizacion(new OrganizationDTO(orgId.longValue(), null));
            }
            Number propId = (Number) out.get("p_prop_id");
            if (propId != null) {
                dto.setPropiedad(new PropertyDTO(propId.longValue(), null));
            }
            Number unidadId = (Number) out.get("p_unidad_id");
            if (unidadId != null) {
                dto.setUnidad(new UnitDTO(unidadId.longValue(), null));
            }
            return Optional.of(dto);
        } catch (Exception e) {
            log.error("GET_ASSIGNMENT_CONTEXT fallo para usuario={} asignacion={}", idUsuario, idAsignacion, e);
            return Optional.empty();
        }
    }

    @Override
    public Long create(com.saed.backend.authorization.dto.AssignmentRequestDTO request, Long asignadoPor) {
        String sql = "INSERT INTO USUARIO_ASIGNACIONES (id_usuario, id_rol, id_organizacion, id_propiedad, id_unidad, asignado_por) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_ASIGNACION"});
            ps.setLong(1, request.getIdUsuario());
            ps.setLong(2, request.getIdRol());
            if (request.getIdOrganizacion() != null) ps.setLong(3, request.getIdOrganizacion()); else ps.setNull(3, java.sql.Types.NUMERIC);
            if (request.getIdPropiedad() != null) ps.setLong(4, request.getIdPropiedad()); else ps.setNull(4, java.sql.Types.NUMERIC);
            if (request.getIdUnidad() != null) ps.setLong(5, request.getIdUnidad()); else ps.setNull(5, java.sql.Types.NUMERIC);
            if (asignadoPor != null) ps.setLong(6, asignadoPor); else ps.setNull(6, java.sql.Types.NUMERIC);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateStatus(Long id, String estado) {
        jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET estado = ? WHERE id_asignacion = ?", estado, id);
    }
}
