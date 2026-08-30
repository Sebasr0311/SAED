package com.saed.backend.incidentes.repository.impl;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class IncidenteRepositoryImpl implements IncidenteRepository {

    private final JdbcTemplate jdbcTemplate;

    public IncidenteRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<IncidenteDTO> rowMapper = (rs, rowNum) -> {
        IncidenteDTO dto = new IncidenteDTO();
        dto.setIdIncidente(rs.getLong("ID_INCIDENTE"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        
        long idPorteria = rs.getLong("ID_PORTERIA");
        if (!rs.wasNull()) dto.setIdPorteria(idPorteria);
        
        long idZona = rs.getLong("ID_ZONA_COMUN");
        if (!rs.wasNull()) dto.setIdZonaComun(idZona);
        
        long idUnidad = rs.getLong("ID_UNIDAD");
        if (!rs.wasNull()) dto.setIdUnidad(idUnidad);
        
        dto.setTitulo(rs.getString("TITULO"));
        dto.setTipoIncidente(rs.getString("TIPO_INCIDENTE"));
        dto.setNivelSeveridad(rs.getString("NIVEL_SEVERIDAD"));
        dto.setDescripcionHechos(rs.getString("DESCRIPCION_HECHOS"));
        
        Timestamp fhi = rs.getTimestamp("FECHA_HORA_INCIDENTE");
        if (fhi != null) dto.setFechaHoraIncidente(fhi.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        dto.setRegistradoPor(rs.getLong("REGISTRADO_POR"));
        dto.setRequirioAutoridades(rs.getString("REQUIRIO_AUTORIDADES"));
        dto.setEntidadAutoridad(rs.getString("ENTIDAD_AUTORIDAD"));
        dto.setNumeroDenunciaPolicia(rs.getString("NUMERO_DENUNCIA_POLICIA"));
        dto.setEvidenciasUrls(rs.getString("EVIDENCIAS_URLS"));
        dto.setAccionesInmediatas(rs.getString("ACCIONES_INMEDIATAS"));
        dto.setEstado(rs.getString("ESTADO"));
        
        Timestamp fc = rs.getTimestamp("FECHA_CIERRE");
        if (fc != null) dto.setFechaCierre(fc.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        dto.setConclusionesCierre(rs.getString("CONCLUSIONES_CIERRE"));
        
        Timestamp fr = rs.getTimestamp("FECHA_REGISTRO");
        if (fr != null) dto.setFechaRegistro(fr.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        return dto;
    };

    @Override
    public List<IncidenteDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT * FROM INCIDENTES WHERE ID_PROPIEDAD = ? ORDER BY FECHA_HORA_INCIDENTE DESC";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }

    @Override
    public List<IncidenteDTO> findAllByUnidad(Long idUnidad, Long idPropiedad) {
        String sql = "SELECT * FROM INCIDENTES WHERE ID_UNIDAD = ? AND ID_PROPIEDAD = ? ORDER BY FECHA_HORA_INCIDENTE DESC";
        return jdbcTemplate.query(sql, rowMapper, idUnidad, idPropiedad);
    }

    @Override
    public Optional<IncidenteDTO> findById(Long idIncidente, Long idPropiedad) {
        String sql = "SELECT * FROM INCIDENTES WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        List<IncidenteDTO> list = jdbcTemplate.query(sql, rowMapper, idIncidente, idPropiedad);
        return list.stream().findFirst();
    }

    @Override
    public Long createIncidente(IncidenteDTO incidente, Long idPropiedad, Long registradoPor) {
        if (incidente.getIdUnidad() != null) {
            String checkSql = "SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ? AND ID_PROPIEDAD = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, incidente.getIdUnidad(), idPropiedad);
            if (count == null || count == 0) {
                throw new SecurityException("Unidad no pertenece a la propiedad actual.");
            }
        }

        String sql = "INSERT INTO INCIDENTES (ID_PROPIEDAD, ID_PORTERIA, ID_ZONA_COMUN, ID_UNIDAD, TITULO, " +
                     "TIPO_INCIDENTE, NIVEL_SEVERIDAD, DESCRIPCION_HECHOS, FECHA_HORA_INCIDENTE, REGISTRADO_POR, " +
                     "REQUIRIO_AUTORIDADES, ENTIDAD_AUTORIDAD, NUMERO_DENUNCIA_POLICIA, EVIDENCIAS_URLS, " +
                     "ACCIONES_INMEDIATAS, ESTADO) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_INCIDENTE"});
            ps.setLong(1, idPropiedad);
            
            if (incidente.getIdPorteria() != null) ps.setLong(2, incidente.getIdPorteria());
            else ps.setNull(2, Types.NUMERIC);
            
            if (incidente.getIdZonaComun() != null) ps.setLong(3, incidente.getIdZonaComun());
            else ps.setNull(3, Types.NUMERIC);
            
            if (incidente.getIdUnidad() != null) ps.setLong(4, incidente.getIdUnidad());
            else ps.setNull(4, Types.NUMERIC);
            
            ps.setString(5, incidente.getTitulo());
            ps.setString(6, incidente.getTipoIncidente());
            ps.setString(7, incidente.getNivelSeveridad() != null ? incidente.getNivelSeveridad() : "MODERADA");
            ps.setString(8, incidente.getDescripcionHechos());
            ps.setTimestamp(9, Timestamp.from(incidente.getFechaHoraIncidente().toInstant()));
            ps.setLong(10, registradoPor);
            ps.setString(11, incidente.getRequirioAutoridades() != null ? incidente.getRequirioAutoridades() : "N");
            ps.setString(12, incidente.getEntidadAutoridad());
            ps.setString(13, incidente.getNumeroDenunciaPolicia());
            ps.setString(14, incidente.getEvidenciasUrls());
            ps.setString(15, incidente.getAccionesInmediatas());
            ps.setString(16, "REPORTADO");
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstado(Long idIncidente, Long idPropiedad, String estado, String conclusiones) {
        String sql = "UPDATE INCIDENTES SET ESTADO = ?, CONCLUSIONES_CIERRE = ?, FECHA_CIERRE = SYSTIMESTAMP " +
                     "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, estado, conclusiones, idIncidente, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }
}
