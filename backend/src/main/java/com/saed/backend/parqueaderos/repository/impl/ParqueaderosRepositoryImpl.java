package com.saed.backend.parqueaderos.repository.impl;

import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.repository.ParqueaderosRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ParqueaderosRepositoryImpl implements ParqueaderosRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ParqueaderosRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ParqueaderoDTO> parqueaderoRowMapper = (rs, rowNum) -> {
        String tipoVal = rs.getString("TIPO");
        String numVal = rs.getString("NUMERO_PARQUEADERO");
        String placa = null;
        try { placa = rs.getString("PLACA_VEHICULO"); } catch (Exception ignored) {}
        String numApto = null;
        try { numApto = rs.getString("NUMERO_APARTAMENTO"); } catch (Exception ignored) {}
        Long idApto = null;
        try { idApto = rs.getObject("ID_APARTAMENTO") != null ? rs.getLong("ID_APARTAMENTO") : null; } catch (Exception ignored) {}

        return new ParqueaderoDTO(
            rs.getLong("ID_PARQUEADERO"),
            rs.getLong("ID_PROPIEDAD"),
            numVal,
            tipoVal,
            rs.getString("ESTADO"),
            numVal,
            "VISITANTES".equalsIgnoreCase(tipoVal),
            placa,
            numApto,
            idApto
        );
    };

    private final RowMapper<AsignacionParqueaderoDTO> asignacionRowMapper = (rs, rowNum) -> {
        Date fechaFinSql = rs.getDate("FECHA_FIN");
        LocalDate fechaFin = fechaFinSql != null ? fechaFinSql.toLocalDate() : null;
        
        return new AsignacionParqueaderoDTO(
            rs.getLong("ID_ASIGNACION_PARQUEADERO"),
            rs.getLong("ID_PARQUEADERO"),
            rs.getString("numeroParqueadero"),
            rs.getLong("ID_UNIDAD"),
            rs.getString("numeroApartamento"),
            rs.getObject("ID_VEHICULO") != null ? rs.getLong("ID_VEHICULO") : null,
            rs.getString("placaVehiculo"),
            rs.getString("TIPO_ASIGNACION"),
            rs.getBigDecimal("CANON_MENSUAL"),
            rs.getDate("FECHA_INICIO").toLocalDate(),
            fechaFin,
            rs.getString("ESTADO")
        );
    };

    private String getBaseParqueaderoQuery() {
        return "SELECT p.ID_PARQUEADERO, p.ID_PROPIEDAD, p.NUMERO_PARQUEADERO, p.TIPO, p.ESTADO, " +
               "       COALESCE(vv.PLACA, v.PLACA) AS PLACA_VEHICULO, " +
               "       u.IDENTIFICADOR AS NUMERO_APARTAMENTO, " +
               "       u.ID_UNIDAD AS ID_APARTAMENTO " +
               "FROM PARQUEADEROS p " +
               "LEFT JOIN VEHICULOS_VISITA vv ON p.ID_PARQUEADERO = vv.ID_PARQUEADERO AND vv.ESTADO = 'DENTRO' " +
               "LEFT JOIN ASIGNACIONES_PARQUEADERO ap ON p.ID_PARQUEADERO = ap.ID_PARQUEADERO AND ap.ESTADO = 'ACTIVA' " +
               "LEFT JOIN UNIDADES u ON ap.ID_UNIDAD = u.ID_UNIDAD " +
               "LEFT JOIN VEHICULOS v ON ap.ID_VEHICULO = v.ID_VEHICULO ";
    }

    @Override
    public List<ParqueaderoDTO> getParqueaderos() {
        return getParqueaderos(null, null);
    }

    @Override
    public List<ParqueaderoDTO> getParqueaderos(String estado, String tipo) {
        StringBuilder sql = new StringBuilder(getBaseParqueaderoQuery()).append("WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (estado != null && !estado.isBlank()) {
            sql.append("AND UPPER(p.ESTADO) = :estado ");
            params.addValue("estado", estado.trim().toUpperCase());
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND UPPER(p.TIPO) = :tipo ");
            params.addValue("tipo", tipo.trim().toUpperCase());
        }
        sql.append("ORDER BY p.NUMERO_PARQUEADERO ASC");
        return jdbcTemplate.query(sql.toString(), params, parqueaderoRowMapper);
    }

    @Override
    public Optional<ParqueaderoDTO> getParqueaderoById(Long id) {
        String sql = getBaseParqueaderoQuery() + "WHERE p.ID_PARQUEADERO = :id";
        List<ParqueaderoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), parqueaderoRowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public ParqueaderoDTO registrarParqueadero(ParqueaderoRequestDTO request, Long idPropiedad) {
        String sql = "INSERT INTO PARQUEADEROS (ID_PROPIEDAD, NUMERO_PARQUEADERO, TIPO, ESTADO) VALUES (:propiedad, :numero, :tipo, :estado)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propiedad", idPropiedad)
                .addValue("numero", request.numeroParqueadero())
                .addValue("tipo", request.tipo())
                .addValue("estado", request.estado());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PARQUEADERO"});
        return getParqueaderoById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public ParqueaderoDTO actualizarParqueadero(Long id, ParqueaderoRequestDTO request) {
        String sql = "UPDATE PARQUEADEROS SET NUMERO_PARQUEADERO = :numero, TIPO = :tipo, ESTADO = :estado WHERE ID_PARQUEADERO = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("numero", request.numeroParqueadero())
                .addValue("tipo", request.tipo())
                .addValue("estado", request.estado());
        jdbcTemplate.update(sql, params);
        return getParqueaderoById(id).orElseThrow();
    }

    @Override
    public void eliminarParqueadero(Long id) {
        String sql = "DELETE FROM PARQUEADEROS WHERE ID_PARQUEADERO = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    @Override
    public List<AsignacionParqueaderoDTO> getAsignaciones() {
        String sql = "SELECT a.*, p.NUMERO_PARQUEADERO as numeroParqueadero, u.IDENTIFICADOR as numeroApartamento, v.PLACA as placaVehiculo " +
                     "FROM ASIGNACIONES_PARQUEADERO a " +
                     "JOIN PARQUEADEROS p ON a.ID_PARQUEADERO = p.ID_PARQUEADERO " +
                     "JOIN UNIDADES u ON a.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN VEHICULOS v ON a.ID_VEHICULO = v.ID_VEHICULO " +
                     "ORDER BY a.ESTADO ASC, a.FECHA_INICIO DESC";
        return jdbcTemplate.query(sql, asignacionRowMapper);
    }

    @Override
    public Optional<AsignacionParqueaderoDTO> getAsignacionById(Long id) {
        String sql = "SELECT a.*, p.NUMERO_PARQUEADERO as numeroParqueadero, u.IDENTIFICADOR as numeroApartamento, v.PLACA as placaVehiculo " +
                     "FROM ASIGNACIONES_PARQUEADERO a " +
                     "JOIN PARQUEADEROS p ON a.ID_PARQUEADERO = p.ID_PARQUEADERO " +
                     "JOIN UNIDADES u ON a.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN VEHICULOS v ON a.ID_VEHICULO = v.ID_VEHICULO " +
                     "WHERE a.ID_ASIGNACION_PARQUEADERO = :id";
        List<AsignacionParqueaderoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), asignacionRowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public AsignacionParqueaderoDTO crearAsignacion(AsignacionParqueaderoRequestDTO request) {
        String sql = "INSERT INTO ASIGNACIONES_PARQUEADERO (ID_PARQUEADERO, ID_UNIDAD, ID_VEHICULO, TIPO_ASIGNACION, CANON_MENSUAL, ESTADO) " +
                     "VALUES (:parqueadero, :unidad, :vehiculo, :tipo, :canon, 'ACTIVA')";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parqueadero", request.idParqueadero())
                .addValue("unidad", request.idUnidad())
                .addValue("vehiculo", request.idVehiculo())
                .addValue("tipo", request.tipoAsignacion())
                .addValue("canon", request.canonMensual());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ASIGNACION_PARQUEADERO"});
        return getAsignacionById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public void finalizarAsignacion(Long id) {
        String sql = "UPDATE ASIGNACIONES_PARQUEADERO SET ESTADO = 'FINALIZADA', FECHA_FIN = TRUNC(SYSDATE) WHERE ID_ASIGNACION_PARQUEADERO = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    @Override
    public void actualizarEstadoParqueadero(Long idParqueadero, String estado) {
        String sql = "UPDATE PARQUEADEROS SET ESTADO = :estado WHERE ID_PARQUEADERO = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", idParqueadero).addValue("estado", estado));
    }
}
