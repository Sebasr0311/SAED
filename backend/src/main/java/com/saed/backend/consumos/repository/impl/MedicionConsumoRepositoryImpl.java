package com.saed.backend.consumos.repository.impl;

import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.consumos.repository.MedicionConsumoRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MedicionConsumoRepositoryImpl implements MedicionConsumoRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MedicionConsumoRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MedicionConsumoDTO> rowMapper = new RowMapper<MedicionConsumoDTO>() {
        @Override
        public MedicionConsumoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            MedicionConsumoDTO dto = new MedicionConsumoDTO();
            dto.setIdMedicion(rs.getLong("ID_MEDICION"));
            dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
            long idUnidad = rs.getLong("ID_UNIDAD");
            if (!rs.wasNull()) {
                dto.setIdUnidad(idUnidad);
            }
            try {
                dto.setUnidadIdentificador(rs.getString("UNIDAD_IDENTIFICADOR"));
            } catch (SQLException ignored) {}
            dto.setTipoServicio(rs.getString("TIPO_SERVICIO"));
            dto.setNumeroMedidor(rs.getString("NUMERO_MEDIDOR"));
            dto.setPeriodo(rs.getString("PERIODO"));
            dto.setLecturaAnterior(rs.getBigDecimal("LECTURA_ANTERIOR"));
            dto.setLecturaActual(rs.getBigDecimal("LECTURA_ACTUAL"));

            BigDecimal consumoCalc = (dto.getLecturaActual() != null && dto.getLecturaAnterior() != null)
                    ? dto.getLecturaActual().subtract(dto.getLecturaAnterior())
                    : BigDecimal.ZERO;
            dto.setConsumoCalculado(consumoCalc);

            dto.setUnidadMedida(rs.getString("UNIDAD_MEDIDA"));
            dto.setTarifaUnitaria(rs.getBigDecimal("TARIFA_UNITARIA"));
            dto.setCostoTotal(rs.getBigDecimal("COSTO_TOTAL"));
            dto.setFotoMedidorUrl(rs.getString("FOTO_MEDIDOR_URL"));
            dto.setAnomaliaDetectada(rs.getString("ANOMALIA_DETECTADA"));
            dto.setObservacionAnomalia(rs.getString("OBSERVACION_ANOMALIA"));
            long leidoPor = rs.getLong("LEIDO_POR");
            if (!rs.wasNull()) {
                dto.setLeidoPor(leidoPor);
            }
            Date toma = rs.getDate("FECHA_TOMA_LECTURA");
            if (toma != null) {
                dto.setFechaTomaLectura(toma.toLocalDate());
            }
            return dto;
        }
    };

    @Override
    public List<MedicionConsumoDTO> findAllByPropiedad(Long idPropiedad, String periodo, String tipoServicio, Long idUnidad) {
        StringBuilder sql = new StringBuilder(
                "SELECT m.*, u.IDENTIFICADOR AS UNIDAD_IDENTIFICADOR " +
                "FROM MEDICIONES_CONSUMO m " +
                "LEFT JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE m.ID_PROPIEDAD = :idPropiedad "
        );

        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);

        if (periodo != null && !periodo.isBlank()) {
            sql.append("AND m.PERIODO = :periodo ");
            params.addValue("periodo", periodo);
        }
        if (tipoServicio != null && !tipoServicio.isBlank() && !"TODOS".equalsIgnoreCase(tipoServicio)) {
            sql.append("AND m.TIPO_SERVICIO = :tipoServicio ");
            params.addValue("tipoServicio", tipoServicio.toUpperCase());
        }
        if (idUnidad != null) {
            sql.append("AND m.ID_UNIDAD = :idUnidad ");
            params.addValue("idUnidad", idUnidad);
        }

        sql.append("ORDER BY m.PERIODO DESC, m.ID_MEDICION DESC");
        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public Optional<MedicionConsumoDTO> findByIdAndPropiedad(Long idMedicion, Long idPropiedad) {
        String sql = "SELECT m.*, u.IDENTIFICADOR AS UNIDAD_IDENTIFICADOR " +
                     "FROM MEDICIONES_CONSUMO m " +
                     "LEFT JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                     "WHERE m.ID_MEDICION = :idMedicion AND m.ID_PROPIEDAD = :idPropiedad";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idMedicion", idMedicion)
                .addValue("idPropiedad", idPropiedad);

        List<MedicionConsumoDTO> list = jdbcTemplate.query(sql, params, rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<BigDecimal> findUltimaLectura(Long idPropiedad, Long idUnidad, String numeroMedidor, String tipoServicio) {
        StringBuilder sql = new StringBuilder(
                "SELECT LECTURA_ACTUAL FROM ( " +
                "  SELECT LECTURA_ACTUAL FROM MEDICIONES_CONSUMO " +
                "  WHERE ID_PROPIEDAD = :idPropiedad AND UPPER(NUMERO_MEDIDOR) = UPPER(:numeroMedidor) "
        );
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("numeroMedidor", numeroMedidor);

        if (idUnidad != null) {
            sql.append("AND ID_UNIDAD = :idUnidad ");
            params.addValue("idUnidad", idUnidad);
        }
        if (tipoServicio != null && !tipoServicio.isBlank()) {
            sql.append("AND TIPO_SERVICIO = :tipoServicio ");
            params.addValue("tipoServicio", tipoServicio.toUpperCase());
        }
        sql.append("  ORDER BY PERIODO DESC, FECHA_TOMA_LECTURA DESC, ID_MEDICION DESC " +
                   ") WHERE ROWNUM = 1");

        List<BigDecimal> list = jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> rs.getBigDecimal(1));
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    @Override
    public BigDecimal calcularPromedioHistorico(Long idPropiedad, Long idUnidad, String numeroMedidor, String tipoServicio) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(AVG(LECTURA_ACTUAL - LECTURA_ANTERIOR), 0) FROM MEDICIONES_CONSUMO " +
                "WHERE ID_PROPIEDAD = :idPropiedad AND UPPER(NUMERO_MEDIDOR) = UPPER(:numeroMedidor) "
        );
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("numeroMedidor", numeroMedidor);

        if (idUnidad != null) {
            sql.append("AND ID_UNIDAD = :idUnidad ");
            params.addValue("idUnidad", idUnidad);
        }
        if (tipoServicio != null && !tipoServicio.isBlank()) {
            sql.append("AND TIPO_SERVICIO = :tipoServicio ");
            params.addValue("tipoServicio", tipoServicio.toUpperCase());
        }

        BigDecimal avg = jdbcTemplate.queryForObject(sql.toString(), params, BigDecimal.class);
        return avg != null ? avg : BigDecimal.ZERO;
    }

    @Override
    public Long insert(Long idPropiedad, Long userId, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion) {
        String sql = "INSERT INTO MEDICIONES_CONSUMO (" +
                     "ID_PROPIEDAD, ID_UNIDAD, TIPO_SERVICIO, NUMERO_MEDIDOR, PERIODO, " +
                     "LECTURA_ANTERIOR, LECTURA_ACTUAL, UNIDAD_MEDIDA, TARIFA_UNITARIA, " +
                     "COSTO_TOTAL, FOTO_MEDIDOR_URL, ANOMALIA_DETECTADA, OBSERVACION_ANOMALIA, " +
                     "LEIDO_POR, FECHA_TOMA_LECTURA" +
                     ") VALUES (" +
                     ":idPropiedad, :idUnidad, :tipoServicio, :numeroMedidor, :periodo, " +
                     ":lecturaAnterior, :lecturaActual, :unidadMedida, :tarifaUnitaria, " +
                     ":costoTotal, :fotoUrl, :anomalia, :observacion, " +
                     ":leidoPor, :fechaToma" +
                     ")";

        String unidadMedida = (dto.getUnidadMedida() != null && !dto.getUnidadMedida().isBlank())
                ? dto.getUnidadMedida().toUpperCase()
                : ("ENERGIA".equalsIgnoreCase(dto.getTipoServicio()) ? "KWH" : "M3");

        BigDecimal tarifa = dto.getTarifaUnitaria() != null ? dto.getTarifaUnitaria() : BigDecimal.ZERO;
        LocalDate fechaToma = dto.getFechaTomaLectura() != null ? dto.getFechaTomaLectura() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("idUnidad", dto.getIdUnidad())
                .addValue("tipoServicio", dto.getTipoServicio().toUpperCase())
                .addValue("numeroMedidor", dto.getNumeroMedidor().trim())
                .addValue("periodo", dto.getPeriodo().trim())
                .addValue("lecturaAnterior", dto.getLecturaAnterior())
                .addValue("lecturaActual", dto.getLecturaActual())
                .addValue("unidadMedida", unidadMedida)
                .addValue("tarifaUnitaria", tarifa)
                .addValue("costoTotal", costoTotal)
                .addValue("fotoUrl", dto.getFotoMedidorUrl())
                .addValue("anomalia", anomalia)
                .addValue("observacion", observacion)
                .addValue("leidoPor", userId)
                .addValue("fechaToma", Date.valueOf(fechaToma));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_MEDICION"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public void update(Long idMedicion, Long idPropiedad, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion) {
        String sql = "UPDATE MEDICIONES_CONSUMO SET " +
                     "ID_UNIDAD = :idUnidad, TIPO_SERVICIO = :tipoServicio, NUMERO_MEDIDOR = :numeroMedidor, " +
                     "PERIODO = :periodo, LECTURA_ANTERIOR = :lecturaAnterior, LECTURA_ACTUAL = :lecturaActual, " +
                     "UNIDAD_MEDIDA = :unidadMedida, TARIFA_UNITARIA = :tarifaUnitaria, COSTO_TOTAL = :costoTotal, " +
                     "FOTO_MEDIDOR_URL = :fotoUrl, ANOMALIA_DETECTADA = :anomalia, OBSERVACION_ANOMALIA = :observacion, " +
                     "FECHA_TOMA_LECTURA = :fechaToma " +
                     "WHERE ID_MEDICION = :idMedicion AND ID_PROPIEDAD = :idPropiedad";

        String unidadMedida = (dto.getUnidadMedida() != null && !dto.getUnidadMedida().isBlank())
                ? dto.getUnidadMedida().toUpperCase()
                : ("ENERGIA".equalsIgnoreCase(dto.getTipoServicio()) ? "KWH" : "M3");

        BigDecimal tarifa = dto.getTarifaUnitaria() != null ? dto.getTarifaUnitaria() : BigDecimal.ZERO;
        LocalDate fechaToma = dto.getFechaTomaLectura() != null ? dto.getFechaTomaLectura() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idMedicion", idMedicion)
                .addValue("idPropiedad", idPropiedad)
                .addValue("idUnidad", dto.getIdUnidad())
                .addValue("tipoServicio", dto.getTipoServicio().toUpperCase())
                .addValue("numeroMedidor", dto.getNumeroMedidor().trim())
                .addValue("periodo", dto.getPeriodo().trim())
                .addValue("lecturaAnterior", dto.getLecturaAnterior())
                .addValue("lecturaActual", dto.getLecturaActual())
                .addValue("unidadMedida", unidadMedida)
                .addValue("tarifaUnitaria", tarifa)
                .addValue("costoTotal", costoTotal)
                .addValue("fotoUrl", dto.getFotoMedidorUrl())
                .addValue("anomalia", anomalia)
                .addValue("observacion", observacion)
                .addValue("fechaToma", Date.valueOf(fechaToma));

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void delete(Long idMedicion, Long idPropiedad) {
        String sql = "DELETE FROM MEDICIONES_CONSUMO WHERE ID_MEDICION = :idMedicion AND ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idMedicion", idMedicion)
                .addValue("idPropiedad", idPropiedad);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public ConsumosSummaryDTO getSummary(Long idPropiedad, String periodo) {
        StringBuilder sql = new StringBuilder(
                "SELECT " +
                "COUNT(*) AS TOTAL, " +
                "COALESCE(SUM(CASE WHEN TIPO_SERVICIO = 'AGUA' THEN (LECTURA_ACTUAL - LECTURA_ANTERIOR) ELSE 0 END), 0) AS AGUA, " +
                "COALESCE(SUM(CASE WHEN TIPO_SERVICIO = 'ENERGIA' THEN (LECTURA_ACTUAL - LECTURA_ANTERIOR) ELSE 0 END), 0) AS ENERGIA, " +
                "COALESCE(SUM(CASE WHEN TIPO_SERVICIO = 'GAS' THEN (LECTURA_ACTUAL - LECTURA_ANTERIOR) ELSE 0 END), 0) AS GAS, " +
                "COALESCE(SUM(COSTO_TOTAL), 0) AS COSTO_TOTAL, " +
                "COUNT(CASE WHEN ANOMALIA_DETECTADA = 'S' THEN 1 END) AS ANOMALIAS " +
                "FROM MEDICIONES_CONSUMO WHERE ID_PROPIEDAD = :idPropiedad "
        );

        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);
        if (periodo != null && !periodo.isBlank()) {
            sql.append("AND PERIODO = :periodo ");
            params.addValue("periodo", periodo.trim());
        }

        return jdbcTemplate.queryForObject(sql.toString(), params, (rs, rowNum) -> {
            ConsumosSummaryDTO dto = new ConsumosSummaryDTO();
            dto.setTotalMediciones(rs.getInt("TOTAL"));
            dto.setConsumoTotalAgua(rs.getBigDecimal("AGUA"));
            dto.setConsumoTotalEnergia(rs.getBigDecimal("ENERGIA"));
            dto.setConsumoTotalGas(rs.getBigDecimal("GAS"));
            dto.setCostoTotalPeriodo(rs.getBigDecimal("COSTO_TOTAL"));
            dto.setAnomaliasDetectadas(rs.getInt("ANOMALIAS"));
            return dto;
        });
    }

    @Override
    public List<ConsumoTendenciaDTO> getTendencias(Long idPropiedad, String tipoServicio, int ultimosMeses) {
        StringBuilder sql = new StringBuilder(
                "SELECT PERIODO, TIPO_SERVICIO, " +
                "COALESCE(SUM(LECTURA_ACTUAL - LECTURA_ANTERIOR), 0) AS CONSUMO, " +
                "COALESCE(SUM(COSTO_TOTAL), 0) AS COSTO, " +
                "COUNT(*) AS CANTIDAD " +
                "FROM MEDICIONES_CONSUMO WHERE ID_PROPIEDAD = :idPropiedad "
        );

        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);

        if (tipoServicio != null && !tipoServicio.isBlank() && !"TODOS".equalsIgnoreCase(tipoServicio)) {
            sql.append("AND TIPO_SERVICIO = :tipoServicio ");
            params.addValue("tipoServicio", tipoServicio.toUpperCase());
        }

        sql.append("GROUP BY PERIODO, TIPO_SERVICIO ORDER BY PERIODO ASC");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            ConsumoTendenciaDTO dto = new ConsumoTendenciaDTO();
            dto.setPeriodo(rs.getString("PERIODO"));
            dto.setTipoServicio(rs.getString("TIPO_SERVICIO"));
            dto.setConsumoTotal(rs.getBigDecimal("CONSUMO"));
            dto.setCostoTotal(rs.getBigDecimal("COSTO"));
            dto.setCantidadMediciones(rs.getInt("CANTIDAD"));
            return dto;
        });
    }
}
