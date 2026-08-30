package com.saed.backend.reservas.repository.impl;

import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.repository.ReservasRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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
        dto.setAforoMaximo(rs.getInt("AFORO_MAXIMO"));
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
        String sql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, idUsuario);
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
        String sql = "SELECT r.*, z.NOMBRE as NOMBRE_ZONA FROM RESERVAS r " +
                     "JOIN ZONAS_COMUNES z ON r.ID_ZONA = z.ID_ZONA " +
                     "WHERE r.ID_RESERVA = ?";
        List<ReservaDTO> res = jdbcTemplate.query(sql, reservaMapper, idReserva);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.get(0));
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
            ps.setInt(7, r.getCantidadAsistentes());
            ps.setBigDecimal(8, r.getCostoTotal());
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
}
