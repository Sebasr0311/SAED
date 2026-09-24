package com.saed.backend.reservas.repository.impl;

import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.repository.ReservasRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;

@Repository
public class ReservasRepositoryImpl implements ReservasRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReservasRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ZonaComunDTO> zonaMapper = (rs, rowNum) -> {
        ZonaComunDTO dto = new ZonaComunDTO();
        dto.setIdZona(rs.getLong("ID_ZONA"));
        dto.setNombre(rs.getString("NOMBRE"));
        dto.setTipo(rs.getString("TIPO"));
        int aforo = rs.getInt("AFORO_MAXIMO");
        dto.setAforoMaximo(rs.wasNull() ? null : aforo);
        dto.setRequiereReserva(rs.getString("REQUIERE_RESERVA"));
        dto.setCostoReserva(rs.getBigDecimal("COSTO_RESERVA"));
        dto.setEstado(rs.getString("ESTADO"));
        return dto;
    };

    private final RowMapper<ReservaDTO> reservaMapper = (rs, rowNum) -> {
        ReservaDTO dto = new ReservaDTO();
        dto.setIdReserva(rs.getLong("ID_RESERVA"));
        dto.setIdZona(rs.getLong("ID_ZONA"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setIdPersonaSolicita(rs.getLong("ID_PERSONA_SOLICITA"));
        
        Date fecha = rs.getDate("FECHA_RESERVA");
        if (fecha != null) dto.setFechaReserva(fecha.toLocalDate());
        
        dto.setHoraInicio(rs.getString("HORA_INICIO"));
        dto.setHoraFin(rs.getString("HORA_FIN"));
        dto.setCantidadAsistentes(rs.getInt("CANTIDAD_ASISTENTES"));
        dto.setCostoTotal(rs.getBigDecimal("COSTO_TOTAL"));
        dto.setObservaciones(rs.getString("OBSERVACIONES"));
        dto.setEstado(rs.getString("ESTADO"));
        
        Timestamp solicitud = rs.getTimestamp("FECHA_SOLICITUD");
        if (solicitud != null) dto.setFechaSolicitud(solicitud.toInstant().atZone(ZoneId.of("America/Bogota")));
        
        // Populate joined fields if available
        try {
            dto.setNombreZona(rs.getString("NOMBRE_ZONA"));
        } catch (Exception e) {
            // Field not in query
        }
        return dto;
    };

    @Override
    public List<ZonaComunDTO> findAllZonas() {
        return jdbcTemplate.query("SELECT * FROM ZONAS_COMUNES ORDER BY NOMBRE", zonaMapper);
    }

    @Override
    public List<ReservaDTO> findAllReservas() {
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "ORDER BY r.FECHA_RESERVA DESC, r.HORA_INICIO DESC";
        return jdbcTemplate.query(sql, reservaMapper);
    }

    private Long getIdPersonaFromUsuario(Long idUsuario) {
        if (idUsuario == null) return 1L;
        String sql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?";
        List<Long> personas = jdbcTemplate.queryForList(sql, Long.class, idUsuario);
        return (!personas.isEmpty() && personas.get(0) != null) ? personas.get(0) : idUsuario;
    }

    @Override
    public List<ReservaDTO> findReservasByPersona(Long idUsuario) {
        Long idPersona = getIdPersonaFromUsuario(idUsuario);
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "WHERE r.ID_PERSONA_SOLICITA = ? ORDER BY r.FECHA_RESERVA DESC";
        return jdbcTemplate.query(sql, reservaMapper, idPersona);
    }

    @Override
    public Optional<ReservaDTO> findReservaById(Long idReserva) {
        if (idReserva == null) return Optional.empty();
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "WHERE r.ID_RESERVA = ?";
        SaedContext prevCtx = SaedContextHolder.getContext();
        setElevatedContext();
        try {
            List<ReservaDTO> res = jdbcTemplate.query(sql, reservaMapper, idReserva);
            return res.isEmpty() ? Optional.empty() : Optional.of(res.get(0));
        } finally {
            restoreSaedContext(prevCtx);
        }
    }

    @Override
    public Long createReserva(ReservaDTO r, Long idPropiedad) {
        Long idPersona = getIdPersonaFromUsuario(r.getIdPersonaSolicita()); // We passed idUsuario into getIdPersonaSolicita in the service
        String sql = "INSERT INTO RESERVAS (ID_ZONA, ID_UNIDAD, ID_PERSONA_SOLICITA, FECHA_RESERVA, " +
                     "HORA_INICIO, HORA_FIN, CANTIDAD_ASISTENTES, COSTO_TOTAL, OBSERVACIONES, ESTADO) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_RESERVA"});
            ps.setLong(1, r.getIdZona());
            ps.setLong(2, r.getIdUnidad());
            ps.setLong(3, idPersona);
            ps.setDate(4, Date.valueOf(r.getFechaReserva()));
            ps.setString(5, r.getHoraInicio());
            ps.setString(6, r.getHoraFin());
            ps.setInt(7, r.getCantidadAsistentes() != null ? r.getCantidadAsistentes() : 1);
            ps.setBigDecimal(8, r.getCostoTotal() != null ? r.getCostoTotal() : java.math.BigDecimal.ZERO);
            ps.setString(9, r.getObservaciones());
            ps.setString(10, r.getEstado() != null ? r.getEstado() : "PENDIENTE");
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public void updateEstadoReserva(Long idReserva, String estado, Long aprobadoPor) {
        if (aprobadoPor != null) {
            jdbcTemplate.update("UPDATE RESERVAS SET ESTADO = ?, APROBADO_POR = ?, FECHA_APROBACION = SYSTIMESTAMP WHERE ID_RESERVA = ?", estado, aprobadoPor, idReserva);
        } else {
            jdbcTemplate.update("UPDATE RESERVAS SET ESTADO = ? WHERE ID_RESERVA = ?", estado, idReserva);
        }
    }

    @Override
    public boolean existsZonaInPropiedad(Long idZona, Long idPropiedad) {
        if (idZona == null || idPropiedad == null) return false;
        String sql = "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE ID_ZONA = ? AND ID_PROPIEDAD = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idZona, idPropiedad);
        return count != null && count > 0;
    }

    @Override
    public boolean existsUnidadInPropiedad(Long idUnidad, Long idPropiedad) {
        if (idUnidad == null || idPropiedad == null) return false;
        String sql = "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ? AND ID_PROPIEDAD = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idUnidad, idPropiedad);
        return count != null && count > 0;
    }

    @Override
    public List<ZonaComunDTO> findZonasByPropiedad(Long idPropiedad) {
        if (idPropiedad == null) return findAllZonas();
        String sql = "SELECT * FROM ZONAS_COMUNES WHERE ID_PROPIEDAD = ? ORDER BY NOMBRE";
        return jdbcTemplate.query(sql, zonaMapper, idPropiedad);
    }

    @Override
    public List<ReservaDTO> findReservasByPropiedad(Long idPropiedad) {
        if (idPropiedad == null) return findAllReservas();
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "WHERE z.ID_PROPIEDAD = ? " +
                     "ORDER BY r.FECHA_RESERVA DESC, r.HORA_INICIO DESC";
        return jdbcTemplate.query(sql, reservaMapper, idPropiedad);
    }

    @Override
    public List<ReservaDTO> findReservasByUnidad(Long idUnidad) {
        if (idUnidad == null) return List.of();
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "WHERE r.ID_UNIDAD = ? " +
                     "ORDER BY r.FECHA_RESERVA DESC, r.HORA_INICIO DESC";
        return jdbcTemplate.query(sql, reservaMapper, idUnidad);
    }

    @Override
    public boolean lockZonaForUpdate(Long idZona, Long idPropiedad) {
        if (idZona == null || idPropiedad == null) return false;
        String sql = "SELECT ID_ZONA FROM ZONAS_COMUNES WHERE ID_ZONA = ? AND ID_PROPIEDAD = ? FOR UPDATE";
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, idZona, idPropiedad);
        return !rows.isEmpty();
    }

    @Override
    public boolean hasOverlappingReserva(Long idZona, LocalDate fechaReserva, String horaInicio, String horaFin) {
        if (idZona == null || fechaReserva == null || horaInicio == null || horaFin == null) {
            return false;
        }
        String sql = "SELECT COUNT(1) FROM RESERVAS " +
                     "WHERE ID_ZONA = ? " +
                     "  AND FECHA_RESERVA = ? " +
                     "  AND ESTADO IN ('PENDIENTE', 'APROBADA', 'CONFIRMADA') " +
                     "  AND HORA_INICIO < ? " +
                     "  AND HORA_FIN > ?";

        SaedContext prevCtx = SaedContextHolder.getContext();
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    idZona,
                    Date.valueOf(fechaReserva),
                    horaFin,
                    horaInicio
            );
            return count != null && count > 0;
        } finally {
            restoreSaedContext(prevCtx);
        }
    }

    @Override
    public boolean hasOverlappingBloqueo(Long idZona, LocalDate fechaReserva, String horaInicio, String horaFin) {
        if (idZona == null || fechaReserva == null || horaInicio == null || horaFin == null) {
            return false;
        }
        String sql = "SELECT COUNT(1) FROM BLOQUEOS_ZONA " +
                     "WHERE ID_ZONA = ? " +
                     "  AND FECHA_INICIO < TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') " +
                     "  AND FECHA_FIN > TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS')";

        String strInicio = fechaReserva + " " + (horaInicio.length() == 5 ? horaInicio + ":00" : horaInicio);
        String strFin = fechaReserva + " " + (horaFin.length() == 5 ? horaFin + ":00" : horaFin);

        SaedContext prevCtx = SaedContextHolder.getContext();
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    idZona,
                    strFin,
                    strInicio
            );
            return count != null && count > 0;
        } finally {
            restoreSaedContext(prevCtx);
        }
    }

    @Override
    public boolean lockReservaForUpdate(Long idReserva) {
        if (idReserva == null) return false;
        String sql = "SELECT ID_RESERVA FROM RESERVAS WHERE ID_RESERVA = ? FOR UPDATE";
        SaedContext prevCtx = SaedContextHolder.getContext();
        setElevatedContext();
        try {
            List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, idReserva);
            return !rows.isEmpty();
        } finally {
            restoreSaedContext(prevCtx);
        }
    }

    @Override
    public Optional<ZonaComunDTO> findZonaByIdAndPropiedad(Long idZona, Long idPropiedad) {
        if (idZona == null || idPropiedad == null) return Optional.empty();
        String sql = "SELECT * FROM ZONAS_COMUNES WHERE ID_ZONA = ? AND ID_PROPIEDAD = ?";
        List<ZonaComunDTO> list = jdbcTemplate.query(sql, zonaMapper, idZona, idPropiedad);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Long saveZona(CreateZonaComunDTO dto, Long idPropiedad) {
        String sql = "INSERT INTO ZONAS_COMUNES (ID_PROPIEDAD, NOMBRE, TIPO, AFORO_MAXIMO, REQUIERE_RESERVA, COSTO_RESERVA, ESTADO) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_ZONA"});
            ps.setLong(1, idPropiedad);
            ps.setString(2, dto.getNombre().trim());
            ps.setString(3, dto.getTipo().trim());
            if (dto.getAforoMaximo() != null) {
                ps.setInt(4, dto.getAforoMaximo());
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            ps.setString(5, dto.getRequiereReservaDb());
            ps.setBigDecimal(6, dto.getCostoReserva() != null ? dto.getCostoReserva() : BigDecimal.ZERO);
            ps.setString(7, dto.getEstado() != null ? dto.getEstado() : "ACTIVA");
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public boolean updateZona(Long idZona, Long idPropiedad, UpdateZonaComunDTO dto) {
        String sql = "UPDATE ZONAS_COMUNES SET " +
                     "NOMBRE = ?, " +
                     "TIPO = ?, " +
                     "AFORO_MAXIMO = ?, " +
                     "REQUIERE_RESERVA = ?, " +
                     "COSTO_RESERVA = ?, " +
                     "ESTADO = ? " +
                     "WHERE ID_ZONA = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(
                sql,
                dto.getNombre().trim(),
                dto.getTipo().trim(),
                dto.getAforoMaximo(),
                dto.getRequiereReservaDb(),
                dto.getCostoReserva() != null ? dto.getCostoReserva() : BigDecimal.ZERO,
                dto.getEstado(),
                idZona,
                idPropiedad
        );
        return updated > 0;
    }

    @Override
    public boolean softDeleteZona(Long idZona, Long idPropiedad) {
        String sql = "UPDATE ZONAS_COMUNES SET ESTADO = 'INACTIVA' WHERE ID_ZONA = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, idZona, idPropiedad);
        return updated > 0;
    }

    @Override
    public boolean existsZonaNombreInPropiedad(String nombre, Long idPropiedad, Long excludeIdZona) {
        if (nombre == null || idPropiedad == null) return false;
        String sql;
        Integer count;
        if (excludeIdZona != null) {
            sql = "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE ID_PROPIEDAD = ? AND UPPER(NOMBRE) = UPPER(?) AND ID_ZONA != ?";
            count = jdbcTemplate.queryForObject(sql, Integer.class, idPropiedad, nombre.trim(), excludeIdZona);
        } else {
            sql = "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE ID_PROPIEDAD = ? AND UPPER(NOMBRE) = UPPER(?)";
            count = jdbcTemplate.queryForObject(sql, Integer.class, idPropiedad, nombre.trim());
        }
        return count != null && count > 0;
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private void restoreSaedContext(SaedContext ctx) {
        if (ctx != null) {
            SaedContextHolder.setContext(ctx);
        } else {
            SaedContextHolder.clearContext();
        }
        try {
            if (ctx == null || ctx.getUserId() == null) {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } else {
                long u = ctx.getUserId();
                String o = ctx.getOrganizationId() != null ? String.valueOf(ctx.getOrganizationId()) : "NULL";
                String p = ctx.getPropertyId() != null ? String.valueOf(ctx.getPropertyId()) : "NULL";
                String r = ctx.getRoleCode() != null ? ctx.getRoleCode() : "ANONYMOUS";
                jdbcTemplate.execute(
                    String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %s, %s, '%s'); END;", u, u, o, p, r)
                );
            }
        } catch (Exception ignored) {}
    }
}
