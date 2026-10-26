package com.saed.backend.authorization.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F4-02: deletion challenges live in a shared store, so they survive a restart and work across instances; the JDBC
 * store keeps the OTP only as a hash, decides expiry with the database clock and changes state with conditional
 * updates in their own transactions. No database.
 */
class PropertyDeletionChallengeDurabilityTest {

    private static final Long PROP = 77L;
    private static final Long ORG = 5L;
    private static final Long USER = 9L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);

    private static String wrong(String otp) {
        return otp.equals("000000") ? "111111" : "000000";
    }

    @Test
    void aChallengeCreatedByOneInstanceIsVerifiedAndConsumedByAnother() {
        DeletionChallengeStore shared = new InMemoryDeletionChallengeStore();
        PropertyDeletionChallengeService instanceA = new PropertyDeletionChallengeService(shared, CLOCK);
        PropertyDeletionChallengeService instanceB = new PropertyDeletionChallengeService(shared, CLOCK);

        var gen = instanceA.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");

        assertTrue(instanceB.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
        assertNotNull(instanceA.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER));
        assertThrows(IllegalStateException.class,
                () -> instanceB.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER));
    }

    @Test
    void wrongAttemptsCountedByDifferentInstancesShareOneLimit() throws Exception {
        DeletionChallengeStore shared = new InMemoryDeletionChallengeStore();
        PropertyDeletionChallengeService instanceA = new PropertyDeletionChallengeService(shared, CLOCK);
        PropertyDeletionChallengeService instanceB = new PropertyDeletionChallengeService(shared, CLOCK);
        var gen = instanceA.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");

        AtomicInteger evaluated = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                PropertyDeletionChallengeService target = i % 2 == 0 ? instanceA : instanceB;
                Callable<Void> task = () -> {
                    try {
                        if (!target.verifyOtp(gen.challengeId(), PROP, ORG, USER, wrong(gen.rawOtp()))) {
                            evaluated.incrementAndGet();
                        }
                    } catch (IllegalStateException blocked) {
                        // blocked after the limit
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, evaluated.get());
        assertThrows(IllegalStateException.class,
                () -> instanceA.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
    }

    @Test
    void theStoreNeverHoldsTheRawOtp() {
        DeletionChallengeStore shared = new InMemoryDeletionChallengeStore();
        var gen = new PropertyDeletionChallengeService(shared, CLOCK).createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        var stored = shared.find(gen.challengeId()).orElseThrow();
        assertFalse(stored.getOtpHash().contains(gen.rawOtp()));
        assertEquals(44, stored.getOtpHash().length());
    }

    // ---- JDBC store ----

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    private final JdbcDeletionChallengeStore jdbcStore = new JdbcDeletionChallengeStore(jdbc, tm, CLOCK);

    @Test
    void everyStoreOperationRunsInItsOwnTransaction() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        jdbcStore.revokeOpen(PROP, USER);

        ArgumentCaptor<TransactionDefinition> def = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(tm).getTransaction(def.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, def.getValue().getPropagationBehavior());
    }

    @Test
    void insertStoresTheHashWithTheDatabaseClockAndNeverTheOtp() {
        var gen = new PropertyDeletionChallengeService(new InMemoryDeletionChallengeStore(), CLOCK)
                .createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        var challenge = gen.challenge();

        jdbcStore.insert(challenge, Duration.ofMinutes(5));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("INSERT INTO DESAFIOS_ELIM_PROPIEDAD"));
        assertTrue(sql.getValue().contains("SYSDATE + :ttlSeconds / 86400"));
        assertFalse(sql.getValue().contains("CURRENT_DATE"));
        assertEquals(300L, params.getValue().getValue("ttlSeconds"));
        assertEquals(challenge.getOtpHash(), params.getValue().getValue("hash"));
        for (String name : params.getValue().getParameterNames()) {
            assertFalse(String.valueOf(params.getValue().getValue(name)).equals(gen.rawOtp()), name);
        }
    }

    @Test
    void attemptsAreCountedOnlyWhilePendingAndTransitionsAreConditional() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(3);

        assertEquals(3, jdbcStore.registerAttempt("c1"));
        assertTrue(jdbcStore.transition("c1", PropertyDeletionChallengeService.ChallengeState.VERIFIED,
                PropertyDeletionChallengeService.ChallengeState.CONSUMED, CLOCK.instant()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), any(SqlParameterSource.class));
        assertTrue(sql.getAllValues().get(0).contains("SET INTENTOS = INTENTOS + 1"));
        assertTrue(sql.getAllValues().get(0).contains("ESTADO = 'PENDIENTE'"));
        assertTrue(sql.getAllValues().get(1).contains("AND ESTADO = :from"));
        assertTrue(sql.getAllValues().get(1).contains("FECHA_EXPIRACION >= SYSDATE"));
    }

    @Test
    void anAttemptOnAChallengeThatIsNoLongerPendingCountsForNothing() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        assertEquals(0, jdbcStore.registerAttempt("c1"));
        assertFalse(jdbcStore.transition("c1", PropertyDeletionChallengeService.ChallengeState.PENDING,
                PropertyDeletionChallengeService.ChallengeState.BLOCKED, CLOCK.instant()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiryComesFromTheDatabaseClock() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("ID_DESAFIO")).thenReturn("c1");
            when(rs.getLong("ID_PROPIEDAD")).thenReturn(PROP);
            when(rs.getLong("ID_ORGANIZACION")).thenReturn(ORG);
            when(rs.getLong("ID_USUARIO")).thenReturn(USER);
            when(rs.getString("OTP_HASH")).thenReturn("h");
            when(rs.getString("SALT")).thenReturn("s");
            when(rs.getInt("INTENTOS")).thenReturn(2);
            when(rs.getString("ESTADO")).thenReturn("PENDIENTE");
            when(rs.getDouble("EDAD_SEGUNDOS")).thenReturn(60.0);
            when(rs.getDouble("RESTANTE_SEGUNDOS")).thenReturn(-1.0);
            return List.of(mapper.mapRow(rs, 0));
        });

        var challenge = jdbcStore.find("c1").orElseThrow();

        assertEquals(2, challenge.getAttempts());
        assertTrue(challenge.isExpired(CLOCK.instant()), "a challenge past its database expiry is expired");
    }
}
