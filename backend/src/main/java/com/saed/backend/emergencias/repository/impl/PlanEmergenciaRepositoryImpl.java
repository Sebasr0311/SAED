package com.saed.backend.emergencias.repository.impl;

import com.saed.backend.emergencias.dto.PlanEmergenciaDTO;
import com.saed.backend.emergencias.dto.PlanEmergenciaRequestDTO;
import com.saed.backend.emergencias.repository.PlanEmergenciaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PlanEmergenciaRepositoryImpl implements PlanEmergenciaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlanEmergenciaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PlanEmergenciaDTO> rowMapper = new RowMapper<PlanEmergenciaDTO>() {
        @Override
        public PlanEmergenciaDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            PlanEmergenciaDTO dto = new PlanEmergenciaDTO();
            dto.setIdPlanEmergencia(rs.getLong("ID_PLAN_EMERGENCIA"));
            dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
            dto.setTitulo(rs.getString("TITULO"));
            dto.setTipoContingencia(rs.getString("TIPO_CONTINGENCIA"));
            dto.setPuntosEncuentro(rs.getString("PUNTOS_ENCUENTRO"));
            dto.setRutasEvacuacionDesc(rs.getString("RUTAS_EVACUACION_DESC"));
            dto.setMapaEvacuacionUrl(rs.getString("MAPA_EVACUACION_URL"));
            dto.setDocumentoPlanUrl(rs.getString("DOCUMENTO_PLAN_URL"));
            Date revDate = rs.getDate("FECHA_ULTIMA_REVISION");
            if (revDate != null) {
                dto.setFechaUltimaRevision(revDate.toLocalDate());
            }
            dto.setEstado(rs.getString("ESTADO"));
            return dto;
        }
    };

    @Override
    public List<PlanEmergenciaDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT * FROM PLANES_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad ORDER BY ID_PLAN_EMERGENCIA DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public Optional<PlanEmergenciaDTO> findByIdAndPropiedad(Long idPlanEmergencia, Long idPropiedad) {
        String sql = "SELECT * FROM PLANES_EMERGENCIA WHERE ID_PLAN_EMERGENCIA = :idPlan AND ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPlan", idPlanEmergencia)
                .addValue("idPropiedad", idPropiedad);
        List<PlanEmergenciaDTO> list = jdbcTemplate.query(sql, params, rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Long insert(Long idPropiedad, PlanEmergenciaRequestDTO dto) {
        String sql = "INSERT INTO PLANES_EMERGENCIA (ID_PROPIEDAD, TITULO, TIPO_CONTINGENCIA, PUNTOS_ENCUENTRO, " +
                     "RUTAS_EVACUACION_DESC, MAPA_EVACUACION_URL, DOCUMENTO_PLAN_URL, FECHA_ULTIMA_REVISION, ESTADO) " +
                     "VALUES (:idPropiedad, :titulo, :tipoContingencia, :puntosEncuentro, :rutasEvacuacion, " +
                     ":mapaUrl, :docUrl, :fechaRevision, :estado)";

        LocalDate fechaRevision = dto.getFechaUltimaRevision() != null ? dto.getFechaUltimaRevision() : LocalDate.now();
        String estado = (dto.getEstado() != null && !dto.getEstado().isBlank()) ? dto.getEstado().toUpperCase() : "ACTIVO";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("titulo", dto.getTitulo())
                .addValue("tipoContingencia", dto.getTipoContingencia().toUpperCase())
                .addValue("puntosEncuentro", dto.getPuntosEncuentro())
                .addValue("rutasEvacuacion", dto.getRutasEvacuacionDesc())
                .addValue("mapaUrl", dto.getMapaEvacuacionUrl())
                .addValue("docUrl", dto.getDocumentoPlanUrl())
                .addValue("fechaRevision", Date.valueOf(fechaRevision))
                .addValue("estado", estado);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLAN_EMERGENCIA"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public void update(Long idPlanEmergencia, Long idPropiedad, PlanEmergenciaRequestDTO dto) {
        String sql = "UPDATE PLANES_EMERGENCIA SET TITULO = :titulo, TIPO_CONTINGENCIA = :tipoContingencia, " +
                     "PUNTOS_ENCUENTRO = :puntosEncuentro, RUTAS_EVACUACION_DESC = :rutasEvacuacion, " +
                     "MAPA_EVACUACION_URL = :mapaUrl, DOCUMENTO_PLAN_URL = :docUrl, " +
                     "FECHA_ULTIMA_REVISION = :fechaRevision, ESTADO = :estado " +
                     "WHERE ID_PLAN_EMERGENCIA = :idPlan AND ID_PROPIEDAD = :idPropiedad";

        LocalDate fechaRevision = dto.getFechaUltimaRevision() != null ? dto.getFechaUltimaRevision() : LocalDate.now();
        String estado = (dto.getEstado() != null && !dto.getEstado().isBlank()) ? dto.getEstado().toUpperCase() : "ACTIVO";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPlan", idPlanEmergencia)
                .addValue("idPropiedad", idPropiedad)
                .addValue("titulo", dto.getTitulo())
                .addValue("tipoContingencia", dto.getTipoContingencia().toUpperCase())
                .addValue("puntosEncuentro", dto.getPuntosEncuentro())
                .addValue("rutasEvacuacion", dto.getRutasEvacuacionDesc())
                .addValue("mapaUrl", dto.getMapaEvacuacionUrl())
                .addValue("docUrl", dto.getDocumentoPlanUrl())
                .addValue("fechaRevision", Date.valueOf(fechaRevision))
                .addValue("estado", estado);

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void delete(Long idPlanEmergencia, Long idPropiedad) {
        String sql = "DELETE FROM PLANES_EMERGENCIA WHERE ID_PLAN_EMERGENCIA = :idPlan AND ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPlan", idPlanEmergencia)
                .addValue("idPropiedad", idPropiedad);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public int countTotalByPropiedad(Long idPropiedad) {
        String sql = "SELECT COUNT(*) FROM PLANES_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int countActivosByPropiedad(Long idPropiedad) {
        String sql = "SELECT COUNT(*) FROM PLANES_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad AND ESTADO = 'ACTIVO'";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), Integer.class);
        return count != null ? count : 0;
    }
}
