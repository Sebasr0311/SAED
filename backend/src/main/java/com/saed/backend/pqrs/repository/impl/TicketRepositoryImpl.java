package com.saed.backend.pqrs.repository.impl;

import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.repository.TicketRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TicketRepositoryImpl implements TicketRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TicketResponseDTO> rowMapper = (rs, rowNum) -> {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setIdTicket(rs.getLong("ID_TICKET"));
        dto.setNumeroRadicado(rs.getString("NUMERO_RADICADO"));
        dto.setTipo(rs.getString("TIPO"));
        dto.setCategoria(rs.getString("CATEGORIA"));
        dto.setPrioridad(rs.getString("PRIORIDAD"));
        dto.setAsunto(rs.getString("ASUNTO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setEstado(rs.getString("ESTADO"));
        
        Timestamp radicado = rs.getTimestamp("FECHA_RADICACION");
        if (radicado != null) dto.setFechaRadicacion(radicado.toInstant().atZone(ZoneId.of("America/Bogota")));
        
        Timestamp limite = rs.getTimestamp("FECHA_LIMITE_SLA");
        if (limite != null) dto.setFechaLimiteSla(limite.toInstant().atZone(ZoneId.of("America/Bogota")));
        
        long responsable = rs.getLong("RESPONSABLE_ASIGNADO");
        if (!rs.wasNull()) dto.setResponsableAsignado(responsable);
        
        return dto;
    };

    @Override
    public List<TicketResponseDTO> findAll() {
        // Oracle RLS will filter by property automatically
        String sql = "SELECT * FROM PQRS_TICKETS ORDER BY FECHA_RADICACION DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<TicketResponseDTO> findByPersona(Long idPersona) {
        String sql = "SELECT * FROM PQRS_TICKETS WHERE ID_PERSONA_RADICA = ? ORDER BY FECHA_RADICACION DESC";
        return jdbcTemplate.query(sql, rowMapper, idPersona);
    }

    @Override
    public Optional<TicketResponseDTO> findById(Long idTicket) {
        String sql = "SELECT * FROM PQRS_TICKETS WHERE ID_TICKET = ?";
        List<TicketResponseDTO> results = jdbcTemplate.query(sql, rowMapper, idTicket);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Long create(TicketRequestDTO request, Long idPropiedad, Long idPersonaRadica, String numeroRadicado, ZonedDateTime fechaLimiteSla) {
        String sql = "INSERT INTO PQRS_TICKETS (ID_PROPIEDAD, ID_PERSONA_RADICA, NUMERO_RADICADO, TIPO, CATEGORIA, PRIORIDAD, ASUNTO, DESCRIPCION, FECHA_LIMITE_SLA) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_TICKET"});
            ps.setLong(1, idPropiedad);
            ps.setLong(2, idPersonaRadica);
            ps.setString(3, numeroRadicado);
            ps.setString(4, request.getTipo());
            ps.setString(5, request.getCategoria());
            ps.setString(6, request.getPrioridad() != null ? request.getPrioridad() : "MEDIA");
            ps.setString(7, request.getAsunto());
            ps.setString(8, request.getDescripcion());
            ps.setTimestamp(9, Timestamp.from(fechaLimiteSla.toInstant()));
            return ps;
        }, keyHolder);
        
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public void updateEstado(Long idTicket, String estado) {
        String sql = "UPDATE PQRS_TICKETS SET ESTADO = ? WHERE ID_TICKET = ?";
        jdbcTemplate.update(sql, estado, idTicket);
    }
}
