package com.saed.backend.authorization.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-02: deletion OTP challenges. Expired, wrong, replayed and concurrent challenges are rejected and the
 * raw OTP is never logged.
 */
class PropertyDeletionChallengeServiceTest {

    private static final Long PROP = 77L;
    private static final Long ORG = 5L;
    private static final Long USER = 9L;

    /** Mutable clock for expiry tests. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T12:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static String wrong(String otp) {
        return otp.equals("000000") ? "111111" : "000000";
    }

    @Test
    void expiredChallengeIsRejectedOnVerifyAndOnConsume() {
        MutableClock clock = new MutableClock();
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(clock);
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        clock.advance(Duration.ofMinutes(6));

        assertThrows(IllegalStateException.class,
                () -> svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));

        PropertyDeletionChallengeService svc2 = new PropertyDeletionChallengeService(clock);
        var gen2 = svc2.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertTrue(svc2.verifyOtp(gen2.challengeId(), PROP, ORG, USER, gen2.rawOtp()));
        clock.advance(Duration.ofMinutes(6));
        assertThrows(IllegalStateException.class,
                () -> svc2.consumeChallengeForFinalExecution(gen2.challengeId(), PROP, ORG, USER));
    }

    @Test
    void wrongCodeIsRejectedAndChallengeBlocksAfterMaxAttemptsEvenForTheRightCode() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        for (int i = 0; i < 5; i++) {
            assertFalse(svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, wrong(gen.rawOtp())));
        }
        assertThrows(IllegalStateException.class,
                () -> svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
        assertThrows(IllegalStateException.class,
                () -> svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER));
    }

    @Test
    void nullOrBlankCodeIsAWrongAttemptNotAServerError() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertFalse(svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, null));
        assertFalse(svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, "  "));
    }

    @Test
    void unverifiedChallengeCannotBeConsumed() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertThrows(IllegalStateException.class,
                () -> svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER));
    }

    @Test
    void replayedChallengeIsRejectedAfterConsumption() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertTrue(svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
        svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER);

        assertThrows(RuntimeException.class,
                () -> svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER));
        assertThrows(RuntimeException.class,
                () -> svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
    }

    @Test
    void challengeOfAnotherUserOrPropertyIsDenied() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertThrows(AccessDeniedException.class,
                () -> svc.verifyOtp(gen.challengeId(), PROP, ORG, 10L, gen.rawOtp()));
        assertThrows(AccessDeniedException.class,
                () -> svc.verifyOtp(gen.challengeId(), 78L, ORG, USER, gen.rawOtp()));
    }

    @Test
    void newRequestInvalidatesThePreviousChallengeOfTheSameUserAndProperty() {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var first = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertThrows(IllegalArgumentException.class,
                () -> svc.verifyOtp(first.challengeId(), PROP, ORG, USER, first.rawOtp()));
    }

    @Test
    void expiredAndFinishedChallengesArePurged() {
        MutableClock clock = new MutableClock();
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(clock);
        svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        svc.createChallenge(78L, ORG, USER, "a@b.co", "Otra");
        clock.advance(Duration.ofMinutes(30));
        svc.createChallenge(79L, ORG, USER, "a@b.co", "Tercera");
        assertEquals(1, svc.storedChallengeCount());
    }

    @Test
    void concurrentConsumptionOfOneVerifiedChallengeSucceedsExactlyOnce() throws Exception {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        assertTrue(svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));

        AtomicInteger ok = new AtomicInteger();
        runConcurrently(16, () -> {
            try {
                svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER);
                ok.incrementAndGet();
            } catch (IllegalStateException | IllegalArgumentException expected) {
                // rejected replay
            }
            return null;
        });
        assertEquals(1, ok.get());
    }

    @Test
    void concurrentWrongGuessesNeverExceedTheAttemptLimit() throws Exception {
        PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
        var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
        AtomicInteger evaluated = new AtomicInteger();
        runConcurrently(40, () -> {
            try {
                if (!svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, wrong(gen.rawOtp()))) {
                    evaluated.incrementAndGet();
                }
            } catch (IllegalStateException blocked) {
                // blocked after the limit
            }
            return null;
        });
        assertEquals(5, evaluated.get());
        assertThrows(IllegalStateException.class,
                () -> svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp()));
    }

    @Test
    void rawOtpIsNeverLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(PropertyDeletionChallengeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            PropertyDeletionChallengeService svc = new PropertyDeletionChallengeService(new MutableClock());
            var gen = svc.createChallenge(PROP, ORG, USER, "a@b.co", "Torre");
            svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, wrong(gen.rawOtp()));
            svc.verifyOtp(gen.challengeId(), PROP, ORG, USER, gen.rawOtp());
            svc.consumeChallengeForFinalExecution(gen.challengeId(), PROP, ORG, USER);
            for (ILoggingEvent e : appender.list) {
                assertFalse(e.getFormattedMessage().contains(gen.rawOtp()), "OTP must never be logged");
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static void runConcurrently(int threads, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
