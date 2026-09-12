package com.saed.backend.automatizaciones.repository.impl;

import com.saed.backend.automatizaciones.dto.AccionDTO;
import com.saed.backend.automatizaciones.dto.AutomatizacionesSummaryDTO;
import com.saed.backend.automatizaciones.dto.EjecucionDTO;
import com.saed.backend.automatizaciones.dto.EventoDTO;
import com.saed.backend.automatizaciones.dto.ReglaDTO;
import com.saed.backend.automatizaciones.repository.AutomatizacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AutomatizacionRepositoryImpl implements AutomatizacionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final RowMapper<EventoDTO> eventoRowMapper = (rs, rowNum) -> EventoDTO.builder()
            .idEvento(rs.getLong("ID_EVENTO"))
            .codigo(rs.getString("CODIGO"))
            .nombre(rs.getString("NOMBRE"))
            .moduloOrigen(rs.getString("MODULO_ORIGEN"))
            .descripcion(rs.getString("DESCRIPCION"))
            .variablesPayload(rs.getString("VARIABLES_PAYLOAD"))
            .build();

    private final RowMapper<AccionDTO> accionRowMapper = (rs, rowNum) -> AccionDTO.builder()
            .idAccion(rs.getLong("ID_ACCION"))
            .idRegla(rs.getLong("ID_REGLA"))
            .tipoAccion(rs.getString("TIPO_ACCION"))
            .parametrosJson(rs.getString("PARAMETROS_JSON"))
            .ordenEjecucion(rs.getInt("ORDEN_EJECUCION"))
            .build();

    private final RowMapper<ReglaDTO> reglaRowMapper = new RowMapper<>() {
        @Override
        public ReglaDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long propId = rs.getObject("ID_PROPIEDAD") != null ? rs.getLong("ID_PROPIEDAD") : null;
            Long creadoPor = rs.getObject("CREADO_POR") != null ? rs.getLong("CREADO_POR") : null;
            OffsetDateTime fechaCreacion = null;
            try {
                fechaCreacion = rs.getObject("FECHA_CREACION", OffsetDateTime.class);
            } catch (Exception ignored) {}

            return ReglaDTO.builder()
                    .idRegla(rs.getLong("ID_REGLA"))
                    .idOrganizacion(rs.getLong("ID_ORGANIZACION"))
                    .idPropiedad(propId)
                    .nombrePropiedad(rs.getString("NOMBRE_PROPIEDAD"))
                    .idEvento(rs.getLong("ID_EVENTO"))
                    .codigoEvento(rs.getString("CODIGO_EVENTO"))
                    .nombreEvento(rs.getString("NOMBRE_EVENTO"))
                    .moduloOrigen(rs.getString("MODULO_ORIGEN"))
                    .variablesPayload(rs.getString("VARIABLES_PAYLOAD"))
                    .nombre(rs.getString("NOMBRE"))
                    .descripcion(rs.getString("DESCRIPCION"))
                    .condicionJson(rs.getString("CONDICION_JSON"))
                    .estado(rs.getString("ESTADO"))
                    .fechaCreacion(fechaCreacion)
                    .creadoPor(creadoPor)
                    .creadorNombre(rs.getString("CREADOR_NOMBRE"))
                    .acciones(new ArrayList<>())
                    .build();
        }
    };

    private final RowMapper<EjecucionDTO> ejecucionRowMapper = (rs, rowNum) -> {
        Long entidadOrigenId = rs.getObject("ID_ENTIDAD_ORIGEN") != null ? rs.getLong("ID_ENTIDAD_ORIGEN") : null;
        Integer tiempoMs = rs.getObject("TIEMPO_MS") != null ? rs.getInt("TIEMPO_MS") : null;
        OffsetDateTime fechaEjecucion = null;
        try {
            fechaEjecucion = rs.getObject("FECHA_EJECUCION", OffsetDateTime.class);
        } catch (Exception ignored) {}

        return EjecucionDTO.builder()
                .idEjecucion(rs.getLong("ID_EJECUCION"))
                .idRegla(rs.getLong("ID_REGLA"))
                .nombreRegla(rs.getString("NOMBRE_REGLA"))
                .codigoEvento(rs.getString("CODIGO_EVENTO"))
                .idEntidadOrigen(entidadOrigenId)
                .tipoEntidadOrigen(rs.getString("TIPO_ENTIDAD_ORIGEN"))
                .resultado(rs.getString("RESULTADO"))
                .logDetalle(rs.getString("LOG_DETALLE"))
                .tiempoMs(tiempoMs)
                .fechaEjecucion(fechaEjecucion)
                .build();
    };

    @Override
    public List<EventoDTO> findAllEventos() {
        String sql = "SELECT ID_EVENTO, CODIGO, NOMBRE, MODULO_ORIGEN, DESCRIPCION, VARIABLES_PAYLOAD " +
                     "FROM EVENTOS_SISTEMA ORDER BY MODULO_ORIGEN, NOMBRE";
        return jdbcTemplate.query(sql, eventoRowMapper);
    }

    @Override
    public Optional<EventoDTO> findEventoById(Long id) {
        String sql = "SELECT ID_EVENTO, CODIGO, NOMBRE, MODULO_ORIGEN, DESCRIPCION, VARIABLES_PAYLOAD " +
                     "FROM EVENTOS_SISTEMA WHERE ID_EVENTO = :id";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("id", id), eventoRowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<EventoDTO> findEventoByCodigo(String codigo) {
        String sql = "SELECT ID_EVENTO, CODIGO, NOMBRE, MODULO_ORIGEN, DESCRIPCION, VARIABLES_PAYLOAD " +
                     "FROM EVENTOS_SISTEMA WHERE UPPER(CODIGO) = UPPER(:codigo)";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("codigo", codigo), eventoRowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ReglaDTO> findReglas(Long orgId, Long propId, String estado, Long eventoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.ID_REGLA, r.ID_ORGANIZACION, r.ID_PROPIEDAD, p.NOMBRE AS NOMBRE_PROPIEDAD, " +
                "       r.ID_EVENTO, e.CODIGO AS CODIGO_EVENTO, e.NOMBRE AS NOMBRE_EVENTO, e.MODULO_ORIGEN, e.VARIABLES_PAYLOAD, " +
                "       r.NOMBRE, r.DESCRIPCION, r.CONDICION_JSON, r.ESTADO, r.FECHA_CREACION, r.CREADO_POR, u.NOMBRE_USUARIO AS CREADOR_NOMBRE " +
                "FROM REGLAS_AUTOMATIZACION r " +
                "JOIN EVENTOS_SISTEMA e ON r.ID_EVENTO = e.ID_EVENTO " +
                "LEFT JOIN PROPIEDADES p ON r.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                "LEFT JOIN USUARIOS u ON r.CREADO_POR = u.ID_USUARIO " +
                "WHERE r.ID_ORGANIZACION = :orgId "
        );

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);

        if (propId != null) {
            sql.append("AND (r.ID_PROPIEDAD = :propId OR r.ID_PROPIEDAD IS NULL) ");
            params.addValue("propId", propId);
        }

        if (estado != null && !estado.isBlank()) {
            sql.append("AND r.ESTADO = :estado ");
            params.addValue("estado", estado.toUpperCase());
        }

        if (eventoId != null) {
            sql.append("AND r.ID_EVENTO = :eventoId ");
            params.addValue("eventoId", eventoId);
        }

        sql.append("ORDER BY r.ESTADO ASC, r.FECHA_CREACION DESC");

        List<ReglaDTO> reglas = jdbcTemplate.query(sql.toString(), params, reglaRowMapper);
        for (ReglaDTO regla : reglas) {
            regla.setAcciones(findAccionesByReglaId(regla.getIdRegla()));
        }
        return reglas;
    }

    @Override
    public Optional<ReglaDTO> findReglaById(Long id, Long orgId) {
        String sql = "SELECT r.ID_REGLA, r.ID_ORGANIZACION, r.ID_PROPIEDAD, p.NOMBRE AS NOMBRE_PROPIEDAD, " +
                     "       r.ID_EVENTO, e.CODIGO AS CODIGO_EVENTO, e.NOMBRE AS NOMBRE_EVENTO, e.MODULO_ORIGEN, e.VARIABLES_PAYLOAD, " +
                     "       r.NOMBRE, r.DESCRIPCION, r.CONDICION_JSON, r.ESTADO, r.FECHA_CREACION, r.CREADO_POR, u.NOMBRE_USUARIO AS CREADOR_NOMBRE " +
                     "FROM REGLAS_AUTOMATIZACION r " +
                     "JOIN EVENTOS_SISTEMA e ON r.ID_EVENTO = e.ID_EVENTO " +
                     "LEFT JOIN PROPIEDADES p ON r.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                     "LEFT JOIN USUARIOS u ON r.CREADO_POR = u.ID_USUARIO " +
                     "WHERE r.ID_REGLA = :id AND (:orgId IS NULL OR r.ID_ORGANIZACION = :orgId)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("orgId", orgId);

        try {
            ReglaDTO regla = jdbcTemplate.queryForObject(sql, params, reglaRowMapper);
            if (regla != null) {
                regla.setAcciones(findAccionesByReglaId(regla.getIdRegla()));
            }
            return Optional.ofNullable(regla);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Long createRegla(Long orgId, Long propId, Long eventoId, String nombre, String descripcion, String condicionJson, String estado, Long creadoPor) {
        String sql = "INSERT INTO REGLAS_AUTOMATIZACION (ID_ORGANIZACION, ID_PROPIEDAD, ID_EVENTO, NOMBRE, DESCRIPCION, CONDICION_JSON, ESTADO, CREADO_POR) " +
                     "VALUES (:orgId, :propId, :eventoId, :nombre, :descripcion, :condicionJson, :estado, :creadoPor)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("propId", propId)
                .addValue("eventoId", eventoId)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("condicionJson", (condicionJson != null && !condicionJson.isBlank()) ? condicionJson : null)
                .addValue("estado", estado != null ? estado : "ACTIVA")
                .addValue("creadoPor", creadoPor);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_REGLA"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Override
    public void updateRegla(Long idRegla, Long propId, Long eventoId, String nombre, String descripcion, String condicionJson, String estado) {
        String sql = "UPDATE REGLAS_AUTOMATIZACION SET " +
                     "ID_PROPIEDAD = :propId, " +
                     "ID_EVENTO = :eventoId, " +
                     "NOMBRE = :nombre, " +
                     "DESCRIPCION = :descripcion, " +
                     "CONDICION_JSON = :condicionJson, " +
                     "ESTADO = :estado " +
                     "WHERE ID_REGLA = :idRegla";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idRegla", idRegla)
                .addValue("propId", propId)
                .addValue("eventoId", eventoId)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("condicionJson", (condicionJson != null && !condicionJson.isBlank()) ? condicionJson : null)
                .addValue("estado", estado);

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateEstadoRegla(Long idRegla, String estado) {
        String sql = "UPDATE REGLAS_AUTOMATIZACION SET ESTADO = :estado WHERE ID_REGLA = :idRegla";
        jdbcTemplate.update(sql, new MapSqlParameterSource("idRegla", idRegla).addValue("estado", estado));
    }

    @Override
    public void deleteRegla(Long idRegla) {
        // En cascada las acciones y ejecuciones se manejan o limpian
        deleteAccionesByReglaId(idRegla);
        String sql = "DELETE FROM REGLAS_AUTOMATIZACION WHERE ID_REGLA = :idRegla";
        jdbcTemplate.update(sql, new MapSqlParameterSource("idRegla", idRegla));
    }

    @Override
    public List<AccionDTO> findAccionesByReglaId(Long idRegla) {
        String sql = "SELECT ID_ACCION, ID_REGLA, TIPO_ACCION, PARAMETROS_JSON, ORDEN_EJECUCION " +
                     "FROM ACCIONES_AUTOMATIZACION WHERE ID_REGLA = :idRegla ORDER BY ORDEN_EJECUCION ASC, ID_ACCION ASC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("idRegla", idRegla), accionRowMapper);
    }

    @Override
    public Long createAccion(Long idRegla, String tipoAccion, String parametrosJson, Integer orden) {
        String sql = "INSERT INTO ACCIONES_AUTOMATIZACION (ID_REGLA, TIPO_ACCION, PARAMETROS_JSON, ORDEN_EJECUCION) " +
                     "VALUES (:idRegla, :tipoAccion, :parametrosJson, :orden)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idRegla", idRegla)
                .addValue("tipoAccion", tipoAccion)
                .addValue("parametrosJson", parametrosJson)
                .addValue("orden", orden != null ? orden : 1);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ACCION"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Override
    public void deleteAccionesByReglaId(Long idRegla) {
        String sql = "DELETE FROM ACCIONES_AUTOMATIZACION WHERE ID_REGLA = :idRegla";
        jdbcTemplate.update(sql, new MapSqlParameterSource("idRegla", idRegla));
    }

    @Override
    public Long createEjecucion(Long idRegla, Long entidadOrigenId, String tipoEntidadOrigen, String resultado, String logDetalle, Integer tiempoMs) {
        String sql = "INSERT INTO EJECUCIONES_AUTOMATIZACION (ID_REGLA, ID_ENTIDAD_ORIGEN, TIPO_ENTIDAD_ORIGEN, RESULTADO, LOG_DETALLE, TIEMPO_MS) " +
                     "VALUES (:idRegla, :entidadId, :tipoEntidad, :resultado, :logDetalle, :tiempoMs)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idRegla", idRegla)
                .addValue("entidadId", entidadOrigenId)
                .addValue("tipoEntidad", tipoEntidadOrigen)
                .addValue("resultado", resultado != null ? resultado : "EXITOSA")
                .addValue("logDetalle", logDetalle)
                .addValue("tiempoMs", tiempoMs != null ? tiempoMs : 0);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_EJECUCION"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Override
    public int countEjecucionesByReglaId(Long idRegla) {
        String sql = "SELECT COUNT(*) FROM EJECUCIONES_AUTOMATIZACION WHERE ID_REGLA = :idRegla";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idRegla", idRegla), Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public List<EjecucionDTO> findEjecuciones(Long orgId, Long propId, Long reglaId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.ID_EJECUCION, e.ID_REGLA, r.NOMBRE AS NOMBRE_REGLA, evt.CODIGO AS CODIGO_EVENTO, " +
                "       e.ID_ENTIDAD_ORIGEN, e.TIPO_ENTIDAD_ORIGEN, e.RESULTADO, e.LOG_DETALLE, e.TIEMPO_MS, e.FECHA_EJECUCION " +
                "FROM EJECUCIONES_AUTOMATIZACION e " +
                "JOIN REGLAS_AUTOMATIZACION r ON e.ID_REGLA = r.ID_REGLA " +
                "JOIN EVENTOS_SISTEMA evt ON r.ID_EVENTO = evt.ID_EVENTO " +
                "WHERE (:orgId IS NULL OR r.ID_ORGANIZACION = :orgId) "
        );

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);

        if (propId != null) {
            sql.append("AND (r.ID_PROPIEDAD = :propId OR r.ID_PROPIEDAD IS NULL) ");
            params.addValue("propId", propId);
        }

        if (reglaId != null) {
            sql.append("AND e.ID_REGLA = :reglaId ");
            params.addValue("reglaId", reglaId);
        }

        sql.append("ORDER BY e.FECHA_EJECUCION DESC FETCH FIRST :limit ROWS ONLY");
        params.addValue("limit", limit > 0 ? limit : 50);

        return jdbcTemplate.query(sql.toString(), params, ejecucionRowMapper);
    }

    @Override
    public AutomatizacionesSummaryDTO getSummary(Long orgId, Long propId) {
        String sqlReglas = "SELECT " +
                "COUNT(*) AS TOTAL_REGLAS, " +
                "COUNT(CASE WHEN ESTADO = 'ACTIVA' THEN 1 END) AS REGLAS_ACTIVAS, " +
                "COUNT(CASE WHEN ESTADO = 'INACTIVA' THEN 1 END) AS REGLAS_INACTIVAS " +
                "FROM REGLAS_AUTOMATIZACION WHERE ID_ORGANIZACION = :orgId " +
                (propId != null ? "AND (ID_PROPIEDAD = :propId OR ID_PROPIEDAD IS NULL)" : "");

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
        if (propId != null) params.addValue("propId", propId);

        long totalReglas = 0;
        long reglasActivas = 0;
        long reglasInactivas = 0;

        try {
            var map = jdbcTemplate.queryForMap(sqlReglas, params);
            totalReglas = ((Number) map.get("TOTAL_REGLAS")).longValue();
            reglasActivas = ((Number) map.get("REGLAS_ACTIVAS")).longValue();
            reglasInactivas = ((Number) map.get("REGLAS_INACTIVAS")).longValue();
        } catch (Exception ignored) {}

        String sqlEventos = "SELECT COUNT(*) FROM EVENTOS_SISTEMA";
        long totalEventos = 0;
        try {
            Long count = jdbcTemplate.getJdbcTemplate().queryForObject(sqlEventos, Long.class);
            totalEventos = count != null ? count : 0;
        } catch (Exception ignored) {}

        String sqlEjecuciones = "SELECT " +
                "COUNT(*) AS TOTAL_EJECUCIONES, " +
                "COUNT(CASE WHEN e.RESULTADO = 'EXITOSA' THEN 1 END) AS EXITOSAS, " +
                "COUNT(CASE WHEN e.RESULTADO = 'FALLIDA' THEN 1 END) AS FALLIDAS " +
                "FROM EJECUCIONES_AUTOMATIZACION e " +
                "JOIN REGLAS_AUTOMATIZACION r ON e.ID_REGLA = r.ID_REGLA " +
                "WHERE r.ID_ORGANIZACION = :orgId " +
                "AND e.FECHA_EJECUCION >= TRUNC(SYSDATE, 'MM') " +
                (propId != null ? "AND (r.ID_PROPIEDAD = :propId OR r.ID_PROPIEDAD IS NULL)" : "");

        long totalEjecucionesMes = 0;
        long exitosas = 0;
        long fallidas = 0;

        try {
            var map = jdbcTemplate.queryForMap(sqlEjecuciones, params);
            totalEjecucionesMes = ((Number) map.get("TOTAL_EJECUCIONES")).longValue();
            exitosas = ((Number) map.get("EXITOSAS")).longValue();
            fallidas = ((Number) map.get("FALLIDAS")).longValue();
        } catch (Exception ignored) {}

        double tasaExito = totalEjecucionesMes > 0 ? (double) (exitosas * 100) / totalEjecucionesMes : 100.0;

        return AutomatizacionesSummaryDTO.builder()
                .totalReglas(totalReglas)
                .reglasActivas(reglasActivas)
                .reglasInactivas(reglasInactivas)
                .totalEventos(totalEventos)
                .totalEjecucionesMes(totalEjecucionesMes)
                .ejecucionesExitosas(exitosas)
                .ejecucionesFallidas(fallidas)
                .tasaExito(Math.round(tasaExito * 10.0) / 10.0)
                .build();
    }
}
