package com.saed.backend.porteria.intercom;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class IntercomRepositoryImpl implements IntercomRepository {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String COLUMNS = "ID_SOLICITUD, ID_PROPIEDAD, ID_UNIDAD, ID_VISITA, NOMBRE_VISITANTE, ESTADO, "
        + "FECHA_SOLICITUD, FECHA_EXPIRACION, RESPONDIDO_POR, FECHA_RESPUESTA";

    private final NamedParameterJdbcTemplate jdbc;

    public IntercomRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static ZonedDateTime zdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atZone(BOGOTA);
    }

    private static final RowMapper<IntercomRequest> MAPPER = (rs, n) -> new IntercomRequest(
        rs.getLong("ID_SOLICITUD"),
        rs.getLong("ID_PROPIEDAD"),
        rs.getLong("ID_UNIDAD"),
        rs.getObject("ID_VISITA") != null ? rs.getLong("ID_VISITA") : null,
        rs.getString("NOMBRE_VISITANTE"),
        rs.getString("ESTADO"),
        zdt(rs.getTimestamp("FECHA_SOLICITUD")),
        zdt(rs.getTimestamp("FECHA_EXPIRACION")),
        rs.getObject("RESPONDIDO_POR") != null ? rs.getLong("RESPONDIDO_POR") : null,
        zdt(rs.getTimestamp("FECHA_RESPUESTA"))
    );

    @Override
    public long create(Long propId, Long unitId, Long visitaId, Long solicitadoPor, String nombreVisitante, int ttlMinutes) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(
            "INSERT INTO INTERCOM_SOLICITUDES (ID_PROPIEDAD, ID_UNIDAD, ID_VISITA, SOLICITADO_POR, NOMBRE_VISITANTE, "
                + "ESTADO, FECHA_EXPIRACION) VALUES (:propId, :unitId, :visitaId, :por, :nombre, 'PENDIENTE', "
                + "SYSTIMESTAMP + NUMTODSINTERVAL(:ttl, 'MINUTE'))",
            new MapSqlParameterSource("propId", propId).addValue("unitId", unitId).addValue("visitaId", visitaId)
                .addValue("por", solicitadoPor)
                .addValue("nombre", nombreVisitante != null && nombreVisitante.length() > 200
                    ? nombreVisitante.substring(0, 200) : nombreVisitante)
                .addValue("ttl", ttlMinutes),
            kh, new String[]{"ID_SOLICITUD"});
        return kh.getKey().longValue();
    }

    @Override
    public Optional<IntercomRequest> findById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM INTERCOM_SOLICITUDES WHERE ID_SOLICITUD = :id",
            new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    @Override
    public Optional<IntercomRequest> findOpenByVisita(Long visitaId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM INTERCOM_SOLICITUDES WHERE ID_VISITA = :id "
                + "AND ESTADO = 'PENDIENTE' AND FECHA_EXPIRACION > SYSTIMESTAMP ORDER BY ID_SOLICITUD DESC",
            new MapSqlParameterSource("id", visitaId), MAPPER).stream().findFirst();
    }

    @Override
    public Optional<IntercomRequest> findLatestByVisita(Long visitaId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM INTERCOM_SOLICITUDES WHERE ID_VISITA = :id "
                + "ORDER BY ID_SOLICITUD DESC FETCH FIRST 1 ROWS ONLY",
            new MapSqlParameterSource("id", visitaId), MAPPER).stream().findFirst();
    }

    @Override
    public List<IntercomRequest> findPendingByUnits(List<Long> unitIds) {
        return jdbc.query("SELECT " + COLUMNS + " FROM INTERCOM_SOLICITUDES WHERE ID_UNIDAD IN (:units) "
                + "AND ESTADO = 'PENDIENTE' AND FECHA_EXPIRACION > SYSTIMESTAMP ORDER BY ID_SOLICITUD",
            new MapSqlParameterSource("units", unitIds), MAPPER);
    }

    @Override
    public int decide(Long id, String estado, Long respondidoPor) {
        return jdbc.update("UPDATE INTERCOM_SOLICITUDES SET ESTADO = :estado, RESPONDIDO_POR = :por, "
                + "FECHA_RESPUESTA = SYSTIMESTAMP WHERE ID_SOLICITUD = :id AND ESTADO = 'PENDIENTE' "
                + "AND FECHA_EXPIRACION > SYSTIMESTAMP",
            new MapSqlParameterSource("id", id).addValue("estado", estado).addValue("por", respondidoPor));
    }

    @Override
    public int cancel(Long id) {
        return jdbc.update("UPDATE INTERCOM_SOLICITUDES SET ESTADO = 'CANCELADA' WHERE ID_SOLICITUD = :id "
                + "AND ESTADO = 'PENDIENTE'", new MapSqlParameterSource("id", id));
    }

    @Override
    public int expireIfDue(Long id) {
        return jdbc.update("UPDATE INTERCOM_SOLICITUDES SET ESTADO = 'EXPIRADA' WHERE ID_SOLICITUD = :id "
                + "AND ESTADO = 'PENDIENTE' AND FECHA_EXPIRACION <= SYSTIMESTAMP", new MapSqlParameterSource("id", id));
    }
}
