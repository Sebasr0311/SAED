package com.saed.backend.dashboard.repository.impl;

import com.saed.backend.dashboard.dto.HistorialReporteDTO;
import com.saed.backend.dashboard.repository.HistorialReportesRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class HistorialReportesRepositoryImpl implements HistorialReportesRepository {

    private final JdbcTemplate jdbcTemplate;

    public HistorialReportesRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<HistorialReporteDTO> rowMapper = (rs, rowNum) -> {
        HistorialReporteDTO dto = new HistorialReporteDTO();
        dto.setIdHistorialReporte(rs.getLong("ID_HISTORIAL_REPORTE"));

        long idCfg = rs.getLong("ID_REPORTE_CONFIG");
        dto.setIdReporteConfig(rs.wasNull() ? null : idCfg);

        dto.setNombreReporte(rs.getString("NOMBRE_REPORTE"));
        dto.setIdOrganizacion(rs.getLong("ID_ORGANIZACION"));

        long idProp = rs.getLong("ID_PROPIEDAD");
        dto.setIdPropiedad(rs.wasNull() ? null : idProp);

        dto.setNombrePropiedad(rs.getString("NOMBRE_PROPIEDAD"));
        dto.setIdUsuarioEjecuto(rs.getLong("ID_USUARIO_EJECUTO"));
        dto.setEmailUsuario(rs.getString("EMAIL_USUARIO"));
        dto.setFormatoGenerado(rs.getString("FORMATO_GENERADO"));
        dto.setParametrosFiltroJson(rs.getString("PARAMETROS_FILTRO_JSON"));
        dto.setArchivoGeneradoUrl(rs.getString("ARCHIVO_GENERADO_URL"));
        dto.setArchivoSha256(rs.getString("ARCHIVO_SHA256"));

        long tiempo = rs.getLong("TIEMPO_GENERACION_MS");
        dto.setTiempoGeneracionMs(rs.wasNull() ? null : tiempo);

        int rows = rs.getInt("REGISTROS_PROCESADOS");
        dto.setRegistrosProcesados(rs.wasNull() ? null : rows);

        Timestamp fe = rs.getTimestamp("FECHA_EJECUCION");
        if (fe != null) {
            dto.setFechaEjecucion(fe.toInstant().atOffset(ZoneOffset.UTC));
        }

        return dto;
    };

    @Override
    public HistorialReporteDTO save(HistorialReporteDTO dto) {
        String sql = """
            INSERT INTO HISTORIAL_REPORTES (
                ID_REPORTE_CONFIG, ID_ORGANIZACION, ID_PROPIEDAD, ID_USUARIO_EJECUTO,
                FORMATO_GENERADO, PARAMETROS_FILTRO_JSON, ARCHIVO_GENERADO_URL,
                ARCHIVO_SHA256, TIEMPO_GENERACION_MS, REGISTROS_PROCESADOS, FECHA_EJECUCION
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_HISTORIAL_REPORTE"});
            if (dto.getIdReporteConfig() != null) ps.setLong(1, dto.getIdReporteConfig()); else ps.setNull(1, Types.BIGINT);
            ps.setLong(2, dto.getIdOrganizacion());
            if (dto.getIdPropiedad() != null) ps.setLong(3, dto.getIdPropiedad()); else ps.setNull(3, Types.BIGINT);
            ps.setLong(4, dto.getIdUsuarioEjecuto());
            ps.setString(5, dto.getFormatoGenerado() != null ? dto.getFormatoGenerado().toUpperCase() : "PDF");
            ps.setString(6, dto.getParametrosFiltroJson());
            ps.setString(7, dto.getArchivoGeneradoUrl());
            ps.setString(8, dto.getArchivoSha256());

            if (dto.getTiempoGeneracionMs() != null) ps.setLong(9, dto.getTiempoGeneracionMs()); else ps.setNull(9, Types.BIGINT);
            if (dto.getRegistrosProcesados() != null) ps.setInt(10, dto.getRegistrosProcesados()); else ps.setInt(10, 0);

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        Long newId = (generatedId != null) ? generatedId.longValue() : null;

        if (newId != null) {
            dto.setIdHistorialReporte(newId);
            return findById(newId).orElse(dto);
        }
        return dto;
    }

    @Override
    public Optional<HistorialReporteDTO> findById(Long id) {
        String sql = """
            SELECT h.ID_HISTORIAL_REPORTE, h.ID_REPORTE_CONFIG,
                   COALESCE(rc.NOMBRE, 'Reporte de Sistema') AS NOMBRE_REPORTE,
                   h.ID_ORGANIZACION, h.ID_PROPIEDAD, p.NOMBRE AS NOMBRE_PROPIEDAD,
                   h.ID_USUARIO_EJECUTO, u.EMAIL AS EMAIL_USUARIO,
                   h.FORMATO_GENERADO, h.PARAMETROS_FILTRO_JSON,
                   h.ARCHIVO_GENERADO_URL, h.ARCHIVO_SHA256,
                   h.TIEMPO_GENERACION_MS, h.REGISTROS_PROCESADOS, h.FECHA_EJECUCION
            FROM HISTORIAL_REPORTES h
            LEFT JOIN REPORTES_CONFIGURADOS rc ON h.ID_REPORTE_CONFIG = rc.ID_REPORTE_CONFIG
            LEFT JOIN PROPIEDADES p ON h.ID_PROPIEDAD = p.ID_PROPIEDAD
            LEFT JOIN USUARIOS u ON h.ID_USUARIO_EJECUTO = u.ID_USUARIO
            WHERE h.ID_HISTORIAL_REPORTE = ?
        """;
        List<HistorialReporteDTO> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<HistorialReporteDTO> findHistorial(Long orgId, Long propertyId, int page, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT h.ID_HISTORIAL_REPORTE, h.ID_REPORTE_CONFIG,
                   COALESCE(rc.NOMBRE, 'Reporte de Sistema') AS NOMBRE_REPORTE,
                   h.ID_ORGANIZACION, h.ID_PROPIEDAD, p.NOMBRE AS NOMBRE_PROPIEDAD,
                   h.ID_USUARIO_EJECUTO, u.EMAIL AS EMAIL_USUARIO,
                   h.FORMATO_GENERADO, h.PARAMETROS_FILTRO_JSON,
                   h.ARCHIVO_GENERADO_URL, h.ARCHIVO_SHA256,
                   h.TIEMPO_GENERACION_MS, h.REGISTROS_PROCESADOS, h.FECHA_EJECUCION
            FROM HISTORIAL_REPORTES h
            LEFT JOIN REPORTES_CONFIGURADOS rc ON h.ID_REPORTE_CONFIG = rc.ID_REPORTE_CONFIG
            LEFT JOIN PROPIEDADES p ON h.ID_PROPIEDAD = p.ID_PROPIEDAD
            LEFT JOIN USUARIOS u ON h.ID_USUARIO_EJECUTO = u.ID_USUARIO
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            sql.append(" AND h.ID_ORGANIZACION = ?");
            params.add(orgId);
        }

        if (propertyId != null) {
            sql.append(" AND h.ID_PROPIEDAD = ?");
            params.add(propertyId);
        }

        sql.append(" ORDER BY h.FECHA_EJECUCION DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(page * size);
        params.add(size);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }
}
