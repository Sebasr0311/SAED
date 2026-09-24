package com.saed.backend.mantenimiento.repository.impl;

import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;
import com.saed.backend.mantenimiento.repository.MantenimientoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MantenimientoRepositoryImpl implements MantenimientoRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MantenimientoRepositoryImpl(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    private final RowMapper<MantenimientoDTO> mantenimientoMapper = (rs, rowNum) -> {
        MantenimientoDTO dto = new MantenimientoDTO();
        dto.setIdMantenimiento(rs.getLong("ID_MANTENIMIENTO"));

        long idActivo = rs.getLong("ID_ACTIVO");
        dto.setIdActivo(rs.wasNull() ? null : idActivo);

        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setTipoMantenimiento(rs.getString("TIPO_MANTENIMIENTO"));
        dto.setPrioridad(rs.getString("PRIORIDAD"));
        dto.setTitulo(rs.getString("TITULO"));
        dto.setDescripcionTrabajo(rs.getString("DESCRIPCION_TRABAJO"));

        Date fechaProg = rs.getDate("FECHA_PROGRAMADA");
        if (fechaProg != null) {
            dto.setFechaProgramada(fechaProg.toLocalDate());
        }

        Timestamp fechaEj = rs.getTimestamp("FECHA_EJECUCION");
        if (fechaEj != null) {
            dto.setFechaEjecucion(fechaEj.toInstant());
        }

        dto.setCostoEstimado(rs.getBigDecimal("COSTO_ESTIMADO"));
        dto.setCostoReal(rs.getBigDecimal("COSTO_REAL"));
        dto.setTecnicoResponsable(rs.getString("TECNICO_RESPONSABLE"));

        long idProv = rs.getLong("ID_PROVEEDOR_SERVICIO");
        dto.setIdProveedorServicio(rs.wasNull() ? null : idProv);

        dto.setEvidenciaAntesUrl(rs.getString("EVIDENCIA_ANTES_URL"));
        dto.setEvidenciaDespuesUrl(rs.getString("EVIDENCIA_DESPUES_URL"));
        dto.setInformeTecnicoUrl(rs.getString("INFORME_TECNICO_URL"));
        dto.setNotasCierre(rs.getString("NOTAS_CIERRE"));
        dto.setEstado(rs.getString("ESTADO"));

        long solPor = rs.getLong("SOLICITADO_POR");
        dto.setSolicitadoPor(rs.wasNull() ? null : solPor);

        Timestamp fechaCreac = rs.getTimestamp("FECHA_CREACION");
        if (fechaCreac != null) {
            dto.setFechaCreacion(fechaCreac.toInstant());
        }

        // Joined columns
        try {
            dto.setCodigoActivo(rs.getString("CODIGO_ACTIVO"));
            dto.setNombreActivo(rs.getString("NOMBRE_ACTIVO"));
        } catch (Exception ignored) {}

        try {
            dto.setRazonSocialProveedor(rs.getString("RAZON_SOCIAL_PROVEEDOR"));
            dto.setNitProveedor(rs.getString("NIT_PROVEEDOR"));
        } catch (Exception ignored) {}

        try {
            long idBloq = rs.getLong("ID_BLOQUEO");
            if (!rs.wasNull()) {
                dto.setIdBloqueoZona(idBloq);
                dto.setIdZonaBloqueada(rs.getLong("ID_ZONA_BLOQUEADA"));
                dto.setNombreZonaBloqueada(rs.getString("NOMBRE_ZONA_BLOQUEADA"));
                Timestamp bIni = rs.getTimestamp("FECHA_INICIO_BLOQUEO");
                if (bIni != null) dto.setFechaInicioBloqueo(bIni.toInstant());
                Timestamp bFin = rs.getTimestamp("FECHA_FIN_BLOQUEO");
                if (bFin != null) dto.setFechaFinBloqueo(bFin.toInstant());
                dto.setMotivoBloqueo(rs.getString("MOTIVO_BLOQUEO"));
            }
        } catch (Exception ignored) {}

        return dto;
    };

    private static final String BASE_SELECT =
            "SELECT m.ID_MANTENIMIENTO, m.ID_ACTIVO, m.ID_PROPIEDAD, m.TIPO_MANTENIMIENTO, m.PRIORIDAD, " +
            "       m.TITULO, m.DESCRIPCION_TRABAJO, m.FECHA_PROGRAMADA, m.FECHA_EJECUCION, " +
            "       m.COSTO_ESTIMADO, m.COSTO_REAL, m.TECNICO_RESPONSABLE, m.ID_PROVEEDOR_SERVICIO, " +
            "       m.EVIDENCIA_ANTES_URL, m.EVIDENCIA_DESPUES_URL, m.INFORME_TECNICO_URL, m.NOTAS_CIERRE, " +
            "       m.ESTADO, m.SOLICITADO_POR, m.FECHA_CREACION, " +
            "       a.CODIGO_ACTIVO, a.NOMBRE AS NOMBRE_ACTIVO, " +
            "       p.RAZON_SOCIAL AS RAZON_SOCIAL_PROVEEDOR, p.NIT_IDENTIFICACION AS NIT_PROVEEDOR, " +
            "       b.ID_BLOQUEO, b.ID_ZONA AS ID_ZONA_BLOQUEADA, z.NOMBRE AS NOMBRE_ZONA_BLOQUEADA, " +
            "       b.FECHA_INICIO AS FECHA_INICIO_BLOQUEO, b.FECHA_FIN AS FECHA_FIN_BLOQUEO, b.MOTIVO AS MOTIVO_BLOQUEO " +
            "FROM MANTENIMIENTOS m " +
            "LEFT JOIN ACTIVOS a ON m.ID_ACTIVO = a.ID_ACTIVO " +
            "LEFT JOIN PROVEEDORES p ON m.ID_PROVEEDOR_SERVICIO = p.ID_PROVEEDOR " +
            "LEFT JOIN BLOQUEOS_ZONA b ON m.ID_MANTENIMIENTO = b.ID_MANTENIMIENTO " +
            "LEFT JOIN ZONAS_COMUNES z ON b.ID_ZONA = z.ID_ZONA ";

    @Override
    public Optional<MantenimientoDTO> findByIdAndPropiedad(Long id, Long idPropiedad) {
        if (id == null || idPropiedad == null) {
            return Optional.empty();
        }
        String sql = BASE_SELECT + "WHERE m.ID_MANTENIMIENTO = :id AND m.ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idPropiedad", idPropiedad);

        List<MantenimientoDTO> results = namedParameterJdbcTemplate.query(sql, params, mantenimientoMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<MantenimientoDTO> findAllByPropiedad(Long idPropiedad, String estado, String tipo, String prioridad, Long idActivo, String search) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append("WHERE m.ID_PROPIEDAD = :idPropiedad ");
        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);

        if (estado != null && !estado.isBlank()) {
            sql.append("AND UPPER(m.ESTADO) = UPPER(:estado) ");
            params.addValue("estado", estado.trim());
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND UPPER(m.TIPO_MANTENIMIENTO) = UPPER(:tipo) ");
            params.addValue("tipo", tipo.trim());
        }
        if (prioridad != null && !prioridad.isBlank()) {
            sql.append("AND UPPER(m.PRIORIDAD) = UPPER(:prioridad) ");
            params.addValue("prioridad", prioridad.trim());
        }
        if (idActivo != null) {
            sql.append("AND m.ID_ACTIVO = :idActivo ");
            params.addValue("idActivo", idActivo);
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (UPPER(m.TITULO) LIKE UPPER(:search) OR UPPER(m.TECNICO_RESPONSABLE) LIKE UPPER(:search) OR UPPER(a.NOMBRE) LIKE UPPER(:search)) ");
            params.addValue("search", "%" + search.trim() + "%");
        }

        sql.append("ORDER BY m.FECHA_PROGRAMADA DESC, m.ID_MANTENIMIENTO DESC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, mantenimientoMapper);
    }

    @Override
    public Long create(MantenimientoCreateDTO dto, Long idPropiedad, Long solicitadoPor) {
        String sql = "INSERT INTO MANTENIMIENTOS (" +
                "  ID_ACTIVO, ID_PROPIEDAD, TIPO_MANTENIMIENTO, PRIORIDAD, TITULO, DESCRIPCION_TRABAJO, " +
                "  FECHA_PROGRAMADA, COSTO_ESTIMADO, TECNICO_RESPONSABLE, ID_PROVEEDOR_SERVICIO, " +
                "  EVIDENCIA_ANTES_URL, ESTADO, SOLICITADO_POR" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROGRAMADO', ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_MANTENIMIENTO"});
            if (dto.getIdActivo() != null) {
                ps.setLong(1, dto.getIdActivo());
            } else {
                ps.setNull(1, java.sql.Types.NUMERIC);
            }
            ps.setLong(2, idPropiedad);
            ps.setString(3, dto.getTipoMantenimiento().toUpperCase().trim());
            ps.setString(4, dto.getPrioridad() != null ? dto.getPrioridad().toUpperCase().trim() : "MEDIA");
            ps.setString(5, dto.getTitulo().trim());
            ps.setString(6, dto.getDescripcionTrabajo().trim());
            ps.setDate(7, Date.valueOf(dto.getFechaProgramada()));
            ps.setBigDecimal(8, dto.getCostoEstimado() != null ? dto.getCostoEstimado() : BigDecimal.ZERO);
            ps.setString(9, dto.getTecnicoResponsable());
            if (dto.getIdProveedorServicio() != null) {
                ps.setLong(10, dto.getIdProveedorServicio());
            } else {
                ps.setNull(10, java.sql.Types.NUMERIC);
            }
            ps.setString(11, dto.getEvidenciaAntesUrl());
            if (solicitadoPor != null) {
                ps.setLong(12, solicitadoPor);
            } else {
                ps.setNull(12, java.sql.Types.NUMERIC);
            }
            return ps;
        }, keyHolder);

        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public void update(Long id, Long idPropiedad, MantenimientoUpdateDTO dto) {
        StringBuilder sql = new StringBuilder("UPDATE MANTENIMIENTOS SET ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idPropiedad", idPropiedad);

        boolean first = true;
        if (dto.getTitulo() != null) {
            sql.append("TITULO = :titulo ");
            params.addValue("titulo", dto.getTitulo().trim());
            first = false;
        }
        if (dto.getDescripcionTrabajo() != null) {
            if (!first) sql.append(", ");
            sql.append("DESCRIPCION_TRABAJO = :desc ");
            params.addValue("desc", dto.getDescripcionTrabajo().trim());
            first = false;
        }
        if (dto.getTipoMantenimiento() != null) {
            if (!first) sql.append(", ");
            sql.append("TIPO_MANTENIMIENTO = :tipo ");
            params.addValue("tipo", dto.getTipoMantenimiento().toUpperCase().trim());
            first = false;
        }
        if (dto.getPrioridad() != null) {
            if (!first) sql.append(", ");
            sql.append("PRIORIDAD = :prio ");
            params.addValue("prio", dto.getPrioridad().toUpperCase().trim());
            first = false;
        }
        if (dto.getFechaProgramada() != null) {
            if (!first) sql.append(", ");
            sql.append("FECHA_PROGRAMADA = :fProg ");
            params.addValue("fProg", Date.valueOf(dto.getFechaProgramada()));
            first = false;
        }
        if (dto.getCostoEstimado() != null) {
            if (!first) sql.append(", ");
            sql.append("COSTO_ESTIMADO = :costoEst ");
            params.addValue("costoEst", dto.getCostoEstimado());
            first = false;
        }
        if (dto.getTecnicoResponsable() != null) {
            if (!first) sql.append(", ");
            sql.append("TECNICO_RESPONSABLE = :tec ");
            params.addValue("tec", dto.getTecnicoResponsable().trim());
            first = false;
        }
        if (dto.getIdProveedorServicio() != null) {
            if (!first) sql.append(", ");
            sql.append("ID_PROVEEDOR_SERVICIO = :idProv ");
            params.addValue("idProv", dto.getIdProveedorServicio());
            first = false;
        }
        if (dto.getIdActivo() != null) {
            if (!first) sql.append(", ");
            sql.append("ID_ACTIVO = :idActivo ");
            params.addValue("idActivo", dto.getIdActivo());
            first = false;
        }
        if (dto.getEvidenciaAntesUrl() != null) {
            if (!first) sql.append(", ");
            sql.append("EVIDENCIA_ANTES_URL = :evidAntes ");
            params.addValue("evidAntes", dto.getEvidenciaAntesUrl().trim());
            first = false;
        }

        if (first) {
            return; // Nothing to update
        }

        sql.append("WHERE ID_MANTENIMIENTO = :id AND ID_PROPIEDAD = :idPropiedad");
        namedParameterJdbcTemplate.update(sql.toString(), params);
    }

    @Override
    public void updateEstado(Long id, Long idPropiedad, String nuevoEstado, BigDecimal costoReal, Instant fechaEjecucion, String notasCierre, String informeTecnicoUrl, String evidenciaDespuesUrl) {
        StringBuilder sql = new StringBuilder("UPDATE MANTENIMIENTOS SET ESTADO = :estado ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idPropiedad", idPropiedad)
                .addValue("estado", nuevoEstado.toUpperCase().trim());

        if (costoReal != null) {
            sql.append(", COSTO_REAL = :costoReal ");
            params.addValue("costoReal", costoReal);
        }
        if (fechaEjecucion != null) {
            sql.append(", FECHA_EJECUCION = :fechaEjecucion ");
            params.addValue("fechaEjecucion", Timestamp.from(fechaEjecucion));
        } else if ("COMPLETADO".equalsIgnoreCase(nuevoEstado)) {
            sql.append(", FECHA_EJECUCION = SYSTIMESTAMP ");
        }
        if (notasCierre != null) {
            sql.append(", NOTAS_CIERRE = :notasCierre ");
            params.addValue("notasCierre", notasCierre.trim());
        }
        if (informeTecnicoUrl != null) {
            sql.append(", INFORME_TECNICO_URL = :informeTecnicoUrl ");
            params.addValue("informeTecnicoUrl", informeTecnicoUrl.trim());
        }
        if (evidenciaDespuesUrl != null) {
            sql.append(", EVIDENCIA_DESPUES_URL = :evidenciaDespuesUrl ");
            params.addValue("evidenciaDespuesUrl", evidenciaDespuesUrl.trim());
        }

        sql.append("WHERE ID_MANTENIMIENTO = :id AND ID_PROPIEDAD = :idPropiedad");
        namedParameterJdbcTemplate.update(sql.toString(), params);
    }

    @Override
    public void reprogramar(Long id, Long idPropiedad, LocalDate nuevaFechaProgramada, String notas) {
        String sql = "UPDATE MANTENIMIENTOS SET ESTADO = 'REPROGRAMADO', FECHA_PROGRAMADA = :fProg, " +
                     "NOTAS_CIERRE = CASE WHEN :notas IS NOT NULL THEN :notas ELSE NOTAS_CIERRE END " +
                     "WHERE ID_MANTENIMIENTO = :id AND ID_PROPIEDAD = :idPropiedad";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idPropiedad", idPropiedad)
                .addValue("fProg", Date.valueOf(nuevaFechaProgramada))
                .addValue("notas", notas);

        namedParameterJdbcTemplate.update(sql, params);
    }

    @Override
    public boolean lockMantenimientoForUpdate(Long id, Long idPropiedad) {
        if (id == null || idPropiedad == null) return false;
        String sql = "SELECT ID_MANTENIMIENTO FROM MANTENIMIENTOS WHERE ID_MANTENIMIENTO = ? AND ID_PROPIEDAD = ? FOR UPDATE";
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, id, idPropiedad);
        return !rows.isEmpty();
    }

    @Override
    public boolean lockActivoForUpdate(Long idActivo, Long idPropiedad) {
        if (idActivo == null || idPropiedad == null) return false;
        String sql = "SELECT ID_ACTIVO FROM ACTIVOS WHERE ID_ACTIVO = ? AND ID_PROPIEDAD = ? FOR UPDATE";
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, idActivo, idPropiedad);
        return !rows.isEmpty();
    }

    @Override
    public void updateEstadoActivo(Long idActivo, Long idPropiedad, String nuevoEstado) {
        if (idActivo == null || idPropiedad == null || nuevoEstado == null) return;
        String sql = "UPDATE ACTIVOS SET ESTADO = ? WHERE ID_ACTIVO = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, nuevoEstado.toUpperCase().trim(), idActivo, idPropiedad);
    }

    @Override
    public Optional<String> getEstadoActivo(Long idActivo, Long idPropiedad) {
        if (idActivo == null || idPropiedad == null) return Optional.empty();
        String sql = "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ? AND ID_PROPIEDAD = ?";
        List<String> list = jdbcTemplate.queryForList(sql, String.class, idActivo, idPropiedad);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public boolean hasActiveMaintenanceOnActivo(Long idActivo, Long idPropiedad, Long excludeIdMantenimiento) {
        if (idActivo == null || idPropiedad == null) return false;
        String sql = "SELECT COUNT(1) FROM MANTENIMIENTOS " +
                     "WHERE ID_ACTIVO = ? AND ID_PROPIEDAD = ? " +
                     "  AND ESTADO IN ('PROGRAMADO', 'EN_PROCESO', 'REPROGRAMADO') " +
                     "  AND (? IS NULL OR ID_MANTENIMIENTO != ?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idActivo, idPropiedad, excludeIdMantenimiento, excludeIdMantenimiento);
        return count != null && count > 0;
    }

    @Override
    public Long createBloqueoZona(Long idZona, Long idPropiedad, Long idMantenimiento, String motivo, Instant fechaInicio, Instant fechaFin, Long bloqueadoPor) {
        String sql = "INSERT INTO BLOQUEOS_ZONA (ID_ZONA, ID_PROPIEDAD, ID_MANTENIMIENTO, MOTIVO, FECHA_INICIO, FECHA_FIN, BLOQUEADO_POR) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_BLOQUEO"});
            ps.setLong(1, idZona);
            ps.setLong(2, idPropiedad);
            if (idMantenimiento != null) {
                ps.setLong(3, idMantenimiento);
            } else {
                ps.setNull(3, java.sql.Types.NUMERIC);
            }
            ps.setString(4, motivo != null ? motivo.trim() : "Mantenimiento programado");
            ps.setTimestamp(5, Timestamp.from(fechaInicio));
            ps.setTimestamp(6, Timestamp.from(fechaFin));
            if (bloqueadoPor != null) {
                ps.setLong(7, bloqueadoPor);
            } else {
                ps.setNull(7, java.sql.Types.NUMERIC);
            }
            return ps;
        }, keyHolder);

        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public void updateBloqueoFechas(Long idMantenimiento, Long idPropiedad, Instant fechaInicio, Instant fechaFin) {
        if (idMantenimiento == null || idPropiedad == null) return;
        String sql = "UPDATE BLOQUEOS_ZONA SET FECHA_INICIO = ?, FECHA_FIN = ? WHERE ID_MANTENIMIENTO = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, Timestamp.from(fechaInicio), Timestamp.from(fechaFin), idMantenimiento, idPropiedad);
    }

    @Override
    public void deleteBloqueoByMantenimiento(Long idMantenimiento, Long idPropiedad) {
        if (idMantenimiento == null || idPropiedad == null) return;
        String sql = "DELETE FROM BLOQUEOS_ZONA WHERE ID_MANTENIMIENTO = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, idMantenimiento, idPropiedad);
    }

    @Override
    public Optional<Long> findBloqueoIdByMantenimiento(Long idMantenimiento, Long idPropiedad) {
        if (idMantenimiento == null || idPropiedad == null) return Optional.empty();
        String sql = "SELECT ID_BLOQUEO FROM BLOQUEOS_ZONA WHERE ID_MANTENIMIENTO = ? AND ID_PROPIEDAD = ?";
        List<Long> list = jdbcTemplate.queryForList(sql, Long.class, idMantenimiento, idPropiedad);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public boolean hasOverlappingBloqueo(Long idZona, Instant inicio, Instant fin, Long excludeIdMantenimiento) {
        if (idZona == null || inicio == null || fin == null) return false;
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(1) FROM BLOQUEOS_ZONA " +
                "WHERE ID_ZONA = :idZona " +
                "  AND FECHA_INICIO < :fin " +
                "  AND FECHA_FIN > :inicio"
        );
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idZona", idZona)
                .addValue("fin", Timestamp.from(fin))
                .addValue("inicio", Timestamp.from(inicio));

        if (excludeIdMantenimiento != null) {
            sql.append(" AND ID_MANTENIMIENTO != :excludeId");
            params.addValue("excludeId", excludeIdMantenimiento);
        }

        Integer count = namedParameterJdbcTemplate.queryForObject(sql.toString(), params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Map<String, Object> getKpis(Long idPropiedad) {
        String sql = "SELECT " +
                     "  COUNT(1) AS TOTAL, " +
                     "  COUNT(CASE WHEN ESTADO = 'PROGRAMADO' THEN 1 END) AS PROGRAMADOS, " +
                     "  COUNT(CASE WHEN ESTADO = 'EN_PROCESO' THEN 1 END) AS EN_PROCESO, " +
                     "  COUNT(CASE WHEN ESTADO = 'COMPLETADO' THEN 1 END) AS COMPLETADOS, " +
                     "  COUNT(CASE WHEN ESTADO = 'REPROGRAMADO' THEN 1 END) AS REPROGRAMADOS, " +
                     "  COUNT(CASE WHEN ESTADO = 'CANCELADO' THEN 1 END) AS CANCELADOS, " +
                     "  COUNT(CASE WHEN PRIORIDAD = 'URGENTE' AND ESTADO NOT IN ('COMPLETADO','CANCELADO') THEN 1 END) AS URGENTES, " +
                     "  COALESCE(SUM(CASE WHEN ESTADO = 'COMPLETADO' THEN COSTO_REAL ELSE 0 END), 0) AS GASTO_TOTAL_REAL, " +
                     "  COALESCE(SUM(CASE WHEN ESTADO NOT IN ('COMPLETADO','CANCELADO') THEN COSTO_ESTIMADO ELSE 0 END), 0) AS COSTO_ESTIMADO_PENDIENTE " +
                     "FROM MANTENIMIENTOS WHERE ID_PROPIEDAD = ?";

        return jdbcTemplate.queryForMap(sql, idPropiedad);
    }
}
