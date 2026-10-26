package com.saed.backend.authorization.service;

import com.saed.backend.authorization.service.PropertyDeletionChallengeService.ChallengeState;
import com.saed.backend.authorization.service.PropertyDeletionChallengeService.DeletionChallenge;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable, tenant-scoped storage of deletion challenges (table DESAFIOS_ELIM_PROPIEDAD, V5.56). Every method runs
 * in its own short transaction (REQUIRES_NEW), so a failed or rolled-back request still keeps its counted attempt
 * and state, and the row lock of a conditional UPDATE serializes concurrent requests on any instance. Expiry is
 * decided with the database clock (SYSDATE). The OTP itself is never stored, only its salted hash.
 */
@Repository
public class JdbcDeletionChallengeStore implements DeletionChallengeStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcDeletionChallengeStore(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, Clock.systemUTC());
    }

    JdbcDeletionChallengeStore(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void insert(DeletionChallenge challenge, Duration ttl) {
        tx.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO DESAFIOS_ELIM_PROPIEDAD (ID_DESAFIO, ID_PROPIEDAD, ID_ORGANIZACION, ID_USUARIO, OTP_HASH, SALT, "
                        + "INTENTOS, ESTADO, FECHA_CREACION, FECHA_EXPIRACION) "
                        + "VALUES (:id, :prop, :org, :usr, :hash, :salt, 0, 'PENDIENTE', SYSDATE, SYSDATE + :ttlSeconds / 86400)",
                new MapSqlParameterSource("id", challenge.getChallengeId())
                        .addValue("prop", challenge.getPropertyId())
                        .addValue("org", challenge.getOrganizationId())
                        .addValue("usr", challenge.getUserId())
                        .addValue("hash", challenge.getOtpHash())
                        .addValue("salt", challenge.getSalt())
                        .addValue("ttlSeconds", ttl.toSeconds())));
    }

    @Override
    public Optional<DeletionChallenge> find(String challengeId) {
        if (challengeId == null) {
            return Optional.empty();
        }
        List<DeletionChallenge> rows = tx.execute(status -> jdbc.query(
                "SELECT ID_DESAFIO, ID_PROPIEDAD, ID_ORGANIZACION, ID_USUARIO, OTP_HASH, SALT, INTENTOS, ESTADO, "
                        + "(SYSDATE - FECHA_CREACION) * 86400 AS EDAD_SEGUNDOS, "
                        + "(FECHA_EXPIRACION - SYSDATE) * 86400 AS RESTANTE_SEGUNDOS "
                        + "FROM DESAFIOS_ELIM_PROPIEDAD WHERE ID_DESAFIO = :id",
                new MapSqlParameterSource("id", challengeId),
                (rs, i) -> {
                    // Durations come from the database clock; they are anchored on the local clock only to build
                    // Instants, so a skew between servers never changes the expiry decision.
                    Instant now = clock.instant();
                    return new DeletionChallenge(rs.getString("ID_DESAFIO"), rs.getLong("ID_PROPIEDAD"),
                            rs.getLong("ID_ORGANIZACION"), rs.getLong("ID_USUARIO"), null, null,
                            rs.getString("OTP_HASH"), rs.getString("SALT"),
                            now.minusMillis((long) (rs.getDouble("EDAD_SEGUNDOS") * 1000)),
                            now.plusMillis((long) (rs.getDouble("RESTANTE_SEGUNDOS") * 1000)),
                            rs.getInt("INTENTOS"), toState(rs.getString("ESTADO")));
                }));
        return rows == null ? Optional.empty() : rows.stream().findFirst();
    }

    @Override
    public void delete(String challengeId) {
        tx.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM DESAFIOS_ELIM_PROPIEDAD WHERE ID_DESAFIO = :id", new MapSqlParameterSource("id", challengeId)));
    }

    @Override
    public void revokeOpen(Long propertyId, Long userId) {
        tx.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM DESAFIOS_ELIM_PROPIEDAD WHERE ID_PROPIEDAD = :prop AND ID_USUARIO = :usr "
                        + "AND ESTADO IN ('PENDIENTE', 'VERIFICADO')",
                new MapSqlParameterSource("prop", propertyId).addValue("usr", userId)));
    }

    @Override
    public void purgeStale(Instant now) {
        tx.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM DESAFIOS_ELIM_PROPIEDAD WHERE FECHA_EXPIRACION < SYSDATE "
                        + "OR ESTADO IN ('CONSUMIDO', 'EXPIRADO', 'BLOQUEADO')",
                new MapSqlParameterSource()));
    }

    @Override
    public int registerAttempt(String challengeId) {
        Integer attempts = tx.execute(status -> {
            int updated = jdbc.update(
                    "UPDATE DESAFIOS_ELIM_PROPIEDAD SET INTENTOS = INTENTOS + 1 "
                            + "WHERE ID_DESAFIO = :id AND ESTADO = 'PENDIENTE'",
                    new MapSqlParameterSource("id", challengeId));
            if (updated == 0) {
                return 0;
            }
            Integer value = jdbc.queryForObject("SELECT INTENTOS FROM DESAFIOS_ELIM_PROPIEDAD WHERE ID_DESAFIO = :id",
                    new MapSqlParameterSource("id", challengeId), Integer.class);
            return value == null ? 0 : value;
        });
        return attempts == null ? 0 : attempts;
    }

    @Override
    public boolean transition(String challengeId, ChallengeState from, ChallengeState to, Instant now) {
        boolean needsLiveChallenge = to == ChallengeState.VERIFIED || to == ChallengeState.CONSUMED;
        String sql = "UPDATE DESAFIOS_ELIM_PROPIEDAD SET ESTADO = :to WHERE ID_DESAFIO = :id AND ESTADO = :from"
                + (needsLiveChallenge ? " AND FECHA_EXPIRACION >= SYSDATE" : "");
        Integer updated = tx.execute(status -> jdbc.update(sql, new MapSqlParameterSource("id", challengeId)
                .addValue("from", toColumn(from)).addValue("to", toColumn(to))));
        return updated != null && updated == 1;
    }

    @Override
    public int count() {
        Integer total = tx.execute(status -> jdbc.queryForObject(
                "SELECT COUNT(1) FROM DESAFIOS_ELIM_PROPIEDAD", new MapSqlParameterSource(), Integer.class));
        return total == null ? 0 : total;
    }

    static String toColumn(ChallengeState state) {
        return switch (state) {
            case PENDING -> "PENDIENTE";
            case VERIFIED -> "VERIFICADO";
            case CONSUMED -> "CONSUMIDO";
            case EXPIRED -> "EXPIRADO";
            case BLOCKED -> "BLOQUEADO";
        };
    }

    static ChallengeState toState(String column) {
        return switch (column) {
            case "PENDIENTE" -> ChallengeState.PENDING;
            case "VERIFICADO" -> ChallengeState.VERIFIED;
            case "CONSUMIDO" -> ChallengeState.CONSUMED;
            case "EXPIRADO" -> ChallengeState.EXPIRED;
            case "BLOQUEADO" -> ChallengeState.BLOCKED;
            default -> throw new IllegalStateException("Estado de desafío desconocido");
        };
    }
}
