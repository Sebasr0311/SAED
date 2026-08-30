package com.saed.backend.sanciones.repository.impl;

import com.saed.backend.sanciones.dto.SancionDTO;
import com.saed.backend.sanciones.repository.SancionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SancionRepositoryImpl implements SancionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SancionRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SancionDTO> rowMapper = (rs, rowNum) -> {
        SancionDTO dto = new SancionDTO();
        dto.setIdSancion(rs.getLong("ID_SANCION"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setIdPersonaImputada(rs.getLong("ID_PERSONA_IMPUTADA"));
        
        long idIncidente = rs.getLong("ID_INCIDENTE_ORIGEN");
        if (!rs.wasNull()) { dto.setIdIncidenteOrigen(idIncidente); }

        dto.setNumeroExpediente(rs.getString("NUMERO_EXPEDIENTE"));
        dto.setTipoFalta(rs.getString("TIPO_FALTA"));
        dto.setGravedad(rs.getString("GRAVEDAD"));
        dto.setDescripcionHechos(rs.getString("DESCRIPCION_HECHOS"));
        dto.setArticuloReglamentoViolado(rs.getString("ARTICULO_REGLAMENTO_VIOLADO"));
        dto.setEvidenciasUrls(rs.getString("EVIDENCIAS_URLS"));
        dto.setTipoSancionPropuesta(rs.getString("TIPO_SANCION_PROPUESTA"));
        
        Timestamp apertura = rs.getTimestamp("FECHA_APERTURA_PLIEGO");
        if (apertura != null) dto.setFechaAperturaPliego(apertura.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        Date limite = rs.getDate("FECHA_LIMITE_DESCARGOS");
        if (limite != null) dto.setFechaLimiteDescargos(limite.toLocalDate());
        
        dto.setResolucionFinal(rs.getString("RESOLUCION_FINAL"));
        
        Timestamp resol = rs.getTimestamp("FECHA_RESOLUCION");
        if (resol != null) dto.setFechaResolucion(resol.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        dto.setEstado(rs.getString("ESTADO"));
        dto.setCreadoPor(rs.getLong("CREADO_POR"));
        return dto;
    };

    @Override
    public Long getIdPersonaFromUsuario(Long idUsuario) {
        String sql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, idUsuario);
    }

    @Override
    public List<SancionDTO> findAllSanciones() {
        String sql = "SELECT * FROM SANCIONES ORDER BY FECHA_APERTURA_PLIEGO DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<SancionDTO> findSancionesByPersona(Long idPersona) {
        String sql = "SELECT * FROM SANCIONES WHERE ID_PERSONA_IMPUTADA = ? ORDER BY FECHA_APERTURA_PLIEGO DESC";
        return jdbcTemplate.query(sql, rowMapper, idPersona);
    }

    @Override
    public Optional<SancionDTO> findById(Long idSancion, Long idPropiedad) {
        String sql = "SELECT * FROM SANCIONES WHERE ID_SANCION = ? AND ID_PROPIEDAD = ?";
        List<SancionDTO> result = jdbcTemplate.query(sql, rowMapper, idSancion, idPropiedad);
        return result.stream().findFirst();
    }

    @Override
    public Long createSancion(SancionDTO sancion, Long idPropiedad, Long creadoPor) {
        String sql = "INSERT INTO SANCIONES (ID_PROPIEDAD, ID_UNIDAD, ID_PERSONA_IMPUTADA, NUMERO_EXPEDIENTE, TIPO_FALTA, " +
                     "GRAVEDAD, DESCRIPCION_HECHOS, ARTICULO_REGLAMENTO_VIOLADO, EVIDENCIAS_URLS, TIPO_SANCION_PROPUESTA, " +
                     "FECHA_LIMITE_DESCARGOS, ESTADO, CREADO_POR) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_SANCION"});
            ps.setLong(1, idPropiedad);
            ps.setLong(2, sancion.getIdUnidad());
            ps.setLong(3, sancion.getIdPersonaImputada());
            ps.setString(4, sancion.getNumeroExpediente());
            ps.setString(5, sancion.getTipoFalta());
            ps.setString(6, sancion.getGravedad());
            ps.setString(7, sancion.getDescripcionHechos());
            ps.setString(8, sancion.getArticuloReglamentoViolado());
            ps.setString(9, sancion.getEvidenciasUrls());
            ps.setString(10, sancion.getTipoSancionPropuesta());
            ps.setDate(11, Date.valueOf(sancion.getFechaLimiteDescargos()));
            ps.setString(12, sancion.getEstado() != null ? sancion.getEstado() : "NOTIFICADA");
            ps.setLong(13, creadoPor);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstado(Long idSancion, Long idPropiedad, String estado, String resolucionFinal) {
        if (resolucionFinal != null) {
            String sql = "UPDATE SANCIONES SET ESTADO = ?, RESOLUCION_FINAL = ?, FECHA_RESOLUCION = SYSTIMESTAMP WHERE ID_SANCION = ? AND ID_PROPIEDAD = ?";
            jdbcTemplate.update(sql, estado, resolucionFinal, idSancion, idPropiedad);
        } else {
            String sql = "UPDATE SANCIONES SET ESTADO = ? WHERE ID_SANCION = ? AND ID_PROPIEDAD = ?";
            jdbcTemplate.update(sql, estado, idSancion, idPropiedad);
        }
    }

    @Override
    public void saveDescargos(Long idSancion, Long idPersonaPresenta, String argumentos, Long radicadoPorUsuario) {
        String sql = "INSERT INTO SANCION_DESCARGOS (ID_SANCION, ID_PERSONA_PRESENTA, ARGUMENTOS_DEFENSA, RADICADO_POR_USUARIO) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, idSancion, idPersonaPresenta, argumentos, radicadoPorUsuario);
    }
}
