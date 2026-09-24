package com.saed.backend.dashboard.repository.impl;

import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoUpdateRequest;
import com.saed.backend.dashboard.repository.ReporteConfiguradoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReporteConfiguradoRepositoryImpl implements ReporteConfiguradoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReporteConfiguradoRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ReporteConfiguradoDTO> rowMapper = (rs, rowNum) -> {
        ReporteConfiguradoDTO dto = new ReporteConfiguradoDTO();
        dto.setIdReporteConfig(rs.getLong("ID_REPORTE_CONFIG"));
        dto.setCodigo(rs.getString("CODIGO"));
        dto.setNombre(rs.getString("NOMBRE"));
        dto.setModulo(rs.getString("MODULO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setFormatoSalidaDefecto(rs.getString("FORMATO_SALIDA_DEFECTO"));
        dto.setConsultaOrigenClave(rs.getString("CONSULTA_ORIGEN_CLAVE"));
        dto.setRolMinimoEjecucion(rs.getString("ROL_MINIMO_EJECUCION"));
        dto.setEstado(rs.getString("ESTADO"));

        long org = rs.getLong("ID_ORGANIZACION");
        dto.setIdOrganizacion(rs.wasNull() ? null : org);

        long prop = rs.getLong("ID_PROPIEDAD");
        dto.setIdPropiedad(rs.wasNull() ? null : prop);

        dto.setParametrosFiltroJson(rs.getString("PARAMETROS_FILTRO_JSON"));

        long user = rs.getLong("ID_USUARIO_CREO");
        dto.setIdUsuarioCreo(rs.wasNull() ? null : user);

        Timestamp fc = rs.getTimestamp("FECHA_CREACION");
        if (fc != null) {
            dto.setFechaCreacion(fc.toInstant().atOffset(ZoneOffset.UTC));
        }

        Timestamp fa = rs.getTimestamp("FECHA_ACTUALIZACION");
        if (fa != null) {
            dto.setFechaActualizacion(fa.toInstant().atOffset(ZoneOffset.UTC));
        }

        dto.setEsPlantillaSistema(dto.getIdOrganizacion() == null);
        return dto;
    };

    @Override
    public Optional<ReporteConfiguradoDTO> findById(Long id) {
        String sql = """
            SELECT ID_REPORTE_CONFIG, CODIGO, NOMBRE, MODULO, DESCRIPCION,
                   FORMATO_SALIDA_DEFECTO, CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION,
                   ESTADO, ID_ORGANIZACION, ID_PROPIEDAD, PARAMETROS_FILTRO_JSON,
                   ID_USUARIO_CREO, FECHA_CREACION, FECHA_ACTUALIZACION
            FROM REPORTES_CONFIGURADOS
            WHERE ID_REPORTE_CONFIG = ?
        """;
        List<ReporteConfiguradoDTO> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<ReporteConfiguradoDTO> findByCodigo(String codigo) {
        String sql = """
            SELECT ID_REPORTE_CONFIG, CODIGO, NOMBRE, MODULO, DESCRIPCION,
                   FORMATO_SALIDA_DEFECTO, CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION,
                   ESTADO, ID_ORGANIZACION, ID_PROPIEDAD, PARAMETROS_FILTRO_JSON,
                   ID_USUARIO_CREO, FECHA_CREACION, FECHA_ACTUALIZACION
            FROM REPORTES_CONFIGURADOS
            WHERE CODIGO = ?
        """;
        List<ReporteConfiguradoDTO> results = jdbcTemplate.query(sql, rowMapper, codigo);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<ReporteConfiguradoDTO> findAllVisible(Long orgId, Long propertyId) {
        StringBuilder sql = new StringBuilder("""
            SELECT ID_REPORTE_CONFIG, CODIGO, NOMBRE, MODULO, DESCRIPCION,
                   FORMATO_SALIDA_DEFECTO, CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION,
                   ESTADO, ID_ORGANIZACION, ID_PROPIEDAD, PARAMETROS_FILTRO_JSON,
                   ID_USUARIO_CREO, FECHA_CREACION, FECHA_ACTUALIZACION
            FROM REPORTES_CONFIGURADOS
            WHERE ESTADO != 'ELIMINADO'
        """);

        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            if (propertyId != null) {
                sql.append(" AND ((ID_ORGANIZACION = ? AND (ID_PROPIEDAD = ? OR ID_PROPIEDAD IS NULL)) OR ID_ORGANIZACION IS NULL)");
                params.add(orgId);
                params.add(propertyId);
            } else {
                sql.append(" AND (ID_ORGANIZACION = ? OR ID_ORGANIZACION IS NULL)");
                params.add(orgId);
            }
        }

        sql.append(" ORDER BY CASE WHEN ID_ORGANIZACION IS NULL THEN 0 ELSE 1 END, NOMBRE ASC");
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    @Override
    public ReporteConfiguradoDTO create(ReporteConfiguradoCreateRequest request, Long orgId, Long propId, Long userId) {
        String codigo = (request.getCodigo() != null && !request.getCodigo().isBlank())
                ? request.getCodigo().trim().toUpperCase()
                : "REP_" + System.currentTimeMillis();

        String modulo = (request.getModulo() != null && !request.getModulo().isBlank())
                ? request.getModulo().trim().toUpperCase()
                : "FINANZAS";

        String formato = (request.getFormatoSalidaDefecto() != null && !request.getFormatoSalidaDefecto().isBlank())
                ? request.getFormatoSalidaDefecto().trim().toUpperCase()
                : "PDF";

        String rol = (request.getRolMinimoEjecucion() != null && !request.getRolMinimoEjecucion().isBlank())
                ? request.getRolMinimoEjecucion().trim()
                : "ADMIN_PROPIEDAD";

        String sql = """
            INSERT INTO REPORTES_CONFIGURADOS (
                CODIGO, NOMBRE, MODULO, DESCRIPCION, FORMATO_SALIDA_DEFECTO,
                CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION, ESTADO,
                ID_ORGANIZACION, ID_PROPIEDAD, PARAMETROS_FILTRO_JSON,
                ID_USUARIO_CREO, FECHA_CREACION
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVO', ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_REPORTE_CONFIG"});
            ps.setString(1, codigo);
            ps.setString(2, request.getNombre().trim());
            ps.setString(3, modulo);
            ps.setString(4, request.getDescripcion());
            ps.setString(5, formato);
            ps.setString(6, request.getConsultaOrigenClave().trim().toUpperCase());
            ps.setString(7, rol);

            if (orgId != null) ps.setLong(8, orgId); else ps.setNull(8, Types.BIGINT);
            if (propId != null) ps.setLong(9, propId); else ps.setNull(9, Types.BIGINT);
            ps.setString(10, request.getParametrosFiltroJson());
            if (userId != null) ps.setLong(11, userId); else ps.setNull(11, Types.BIGINT);

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        Long newId = (generatedId != null) ? generatedId.longValue() : null;
        if (newId == null) {
            newId = jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = ?",
                Long.class,
                codigo
            );
        }

        return findById(newId).orElseThrow(() -> new IllegalStateException("Error al recuperar reporte configurado recién creado."));
    }

    @Override
    public ReporteConfiguradoDTO update(Long id, ReporteConfiguradoUpdateRequest request, Long userId) {
        StringBuilder sql = new StringBuilder("UPDATE REPORTES_CONFIGURADOS SET FECHA_ACTUALIZACION = CURRENT_TIMESTAMP");
        List<Object> params = new ArrayList<>();

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            sql.append(", NOMBRE = ?");
            params.add(request.getNombre().trim());
        }
        if (request.getDescripcion() != null) {
            sql.append(", DESCRIPCION = ?");
            params.add(request.getDescripcion().trim());
        }
        if (request.getFormatoSalidaDefecto() != null && !request.getFormatoSalidaDefecto().isBlank()) {
            sql.append(", FORMATO_SALIDA_DEFECTO = ?");
            params.add(request.getFormatoSalidaDefecto().trim().toUpperCase());
        }
        if (request.getConsultaOrigenClave() != null && !request.getConsultaOrigenClave().isBlank()) {
            sql.append(", CONSULTA_ORIGEN_CLAVE = ?");
            params.add(request.getConsultaOrigenClave().trim().toUpperCase());
        }
        if (request.getParametrosFiltroJson() != null) {
            sql.append(", PARAMETROS_FILTRO_JSON = ?");
            params.add(request.getParametrosFiltroJson());
        }
        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            sql.append(", ESTADO = ?");
            params.add(request.getEstado().trim().toUpperCase());
        }

        sql.append(" WHERE ID_REPORTE_CONFIG = ?");
        params.add(id);

        jdbcTemplate.update(sql.toString(), params.toArray());
        return findById(id).orElseThrow(() -> new IllegalStateException("Reporte configurado no encontrado tras actualización."));
    }

    @Override
    public boolean deactivate(Long id) {
        String sql = "UPDATE REPORTES_CONFIGURADOS SET ESTADO = 'INACTIVO', FECHA_ACTUALIZACION = CURRENT_TIMESTAMP WHERE ID_REPORTE_CONFIG = ?";
        int rows = jdbcTemplate.update(sql, id);
        return rows > 0;
    }
}
