package com.saed.backend.authorization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Servicio de gestión criptográfica de desafíos OTP para la eliminación segura de propiedades.
 * Implementa control de expiración, rate limiting (fuerza bruta), no reutilización y doble confirmación.
 */
@Service
public class PropertyDeletionChallengeService {

    private static final Logger log = LoggerFactory.getLogger(PropertyDeletionChallengeService.class);
    private static final int OTP_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final DeletionChallengeStore store;
    private final Clock clock;

    /** Production wiring: challenges are durable and shared by every instance (F4-02). */
    @org.springframework.beans.factory.annotation.Autowired
    public PropertyDeletionChallengeService(DeletionChallengeStore store) {
        this(store, Clock.systemUTC());
    }

    /** Tests: an in-memory store and an injectable clock. */
    PropertyDeletionChallengeService(Clock clock) {
        this(new InMemoryDeletionChallengeStore(), clock);
    }

    PropertyDeletionChallengeService(DeletionChallengeStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    int storedChallengeCount() {
        return store.count();
    }

    /** Removes a challenge whose OTP could not be delivered, so it can never be used. */
    public void revokeChallenge(String challengeId) {
        if (challengeId != null) {
            store.delete(challengeId);
        }
    }

    public enum ChallengeState {
        PENDING,
        VERIFIED,
        CONSUMED,
        EXPIRED,
        BLOCKED
    }

    public static class DeletionChallenge {
        private final String challengeId;
        private final Long propertyId;
        private final Long organizationId;
        private final Long userId;
        private final String userEmail;
        private final String propertyName;
        private final String otpHash;
        private final String salt;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final int attempts;
        private final ChallengeState state;

        public DeletionChallenge(String challengeId, Long propertyId, Long organizationId, Long userId,
                                 String userEmail, String propertyName, String otpHash, String salt,
                                 Instant createdAt, Instant expiresAt) {
            this(challengeId, propertyId, organizationId, userId, userEmail, propertyName, otpHash, salt,
                    createdAt, expiresAt, 0, ChallengeState.PENDING);
        }

        public DeletionChallenge(String challengeId, Long propertyId, Long organizationId, Long userId,
                                 String userEmail, String propertyName, String otpHash, String salt,
                                 Instant createdAt, Instant expiresAt, int attempts, ChallengeState state) {
            this.attempts = attempts;
            this.state = state;
            this.challengeId = challengeId;
            this.propertyId = propertyId;
            this.organizationId = organizationId;
            this.userId = userId;
            this.userEmail = userEmail;
            this.propertyName = propertyName;
            this.otpHash = otpHash;
            this.salt = salt;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public String getChallengeId() { return challengeId; }
        public Long getPropertyId() { return propertyId; }
        public Long getOrganizationId() { return organizationId; }
        public Long getUserId() { return userId; }
        public String getUserEmail() { return userEmail; }
        public String getPropertyName() { return propertyName; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public int getAttempts() { return attempts; }
        public ChallengeState getState() { return state; }
        public String getOtpHash() { return otpHash; }
        public String getSalt() { return salt; }

        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public record GeneratedChallenge(
            String challengeId,
            String rawOtp,
            int expiresInSeconds,
            DeletionChallenge challenge
    ) {}

    /**
     * Invalida desafíos previos para la misma propiedad y usuario, y crea uno nuevo.
     */
    public GeneratedChallenge createChallenge(Long propertyId, Long organizationId, Long userId, String userEmail, String propertyName) {
        // 1. Invalidar desafíos previos activos y purgar los caducados o terminados
        store.purgeStale(clock.instant());
        store.revokeOpen(propertyId, userId);

        // 2. Generar OTP criptográfico no predecible de 6 dígitos
        String rawOtp = generateSecureOtp();
        String salt = UUID.randomUUID().toString();
        String otpHash = hashOtp(rawOtp, salt);
        String challengeId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(EXPIRATION_MINUTES));

        DeletionChallenge challenge = new DeletionChallenge(
                challengeId, propertyId, organizationId, userId, userEmail, propertyName,
                otpHash, salt, now, expiresAt
        );

        store.insert(challenge, Duration.ofMinutes(EXPIRATION_MINUTES));
        log.info("Desafío OTP generado para propiedad {} por usuario {}. Expira en {} min", propertyId, userId, EXPIRATION_MINUTES);

        return new GeneratedChallenge(challengeId, rawOtp, EXPIRATION_MINUTES * 60, challenge);
    }

    /**
     * Valida el OTP ingresado. Si es correcto, transiciona el estado a VERIFIED.
     * Every state change is an atomic conditional update in the store, so concurrent requests (also on other
     * instances) cannot exceed the attempt limit, verify twice or consume twice.
     */
    public boolean verifyOtp(String challengeId, Long propertyId, Long organizationId, Long userId, String submittedOtp) {
        DeletionChallenge challenge = store.find(challengeId).orElse(null);
        if (challenge == null) {
            log.warn("Verificación fallida: Desafío {} no existe", challengeId);
            throw new IllegalArgumentException("El desafío de eliminación no existe o ya caducó.");
        }
        return verifyLoaded(challenge, challengeId, propertyId, organizationId, userId, submittedOtp);
    }

    private boolean verifyLoaded(DeletionChallenge challenge, String challengeId, Long propertyId, Long organizationId,
                                 Long userId, String submittedOtp) {
        // Validar correspondencia estricta de tenant, usuario y propiedad
        if (!challenge.getPropertyId().equals(propertyId) ||
            !challenge.getOrganizationId().equals(organizationId) ||
            !challenge.getUserId().equals(userId)) {
            log.error("Violación de seguridad en desafío {}: contexto no coincide", challengeId);
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para este desafío de seguridad.");
        }

        // Validar expiración
        if (challenge.isExpired(clock.instant())) {
            store.transition(challengeId, challenge.getState(), ChallengeState.EXPIRED, clock.instant());
            log.warn("Desafío {} expiró el {}", challengeId, challenge.getExpiresAt());
            throw new IllegalStateException("El código de verificación ha expirado. Solicite uno nuevo.");
        }

        // Validar estado previo
        if (challenge.getState() == ChallengeState.BLOCKED) {
            throw new IllegalStateException("El desafío ha sido bloqueado por superar el límite de intentos.");
        }
        if (challenge.getState() == ChallengeState.CONSUMED) {
            throw new IllegalStateException("Este desafío ya fue utilizado previamente.");
        }
        if (challenge.getState() == ChallengeState.EXPIRED) {
            throw new IllegalStateException("El código de verificación ha expirado. Solicite uno nuevo.");
        }
        if (challenge.getState() == ChallengeState.VERIFIED) {
            return true; // Ya verificado
        }

        // Rate limiting de intentos (atómico: solo cuenta mientras el desafío sigue PENDING)
        int attempts = store.registerAttempt(challengeId);
        if (attempts == 0) {
            DeletionChallenge current = store.find(challengeId).orElse(null);
            if (current != null && current.getState() == ChallengeState.VERIFIED) {
                return true;
            }
            throw new IllegalStateException("El desafío ya no está disponible. Solicite uno nuevo.");
        }
        if (attempts > MAX_ATTEMPTS) {
            store.transition(challengeId, ChallengeState.PENDING, ChallengeState.BLOCKED, clock.instant());
            log.warn("Desafío {} bloqueado por exceder límite de {} intentos", challengeId, MAX_ATTEMPTS);
            throw new IllegalStateException("Número máximo de intentos excedido. El desafío fue bloqueado.");
        }

        // Comprobación criptográfica del hash
        if (submittedOtp == null || submittedOtp.isBlank()) {
            blockIfExhausted(challengeId, attempts);
            return false;
        }
        String computedHash = hashOtp(submittedOtp.trim(), challenge.getSalt());
        if (!MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8), challenge.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Código incorrecto para desafío {}. Intento {}/{}", challengeId, attempts, MAX_ATTEMPTS);
            blockIfExhausted(challengeId, attempts);
            return false;
        }

        // Código correcto -> Habilitar para segunda confirmación
        if (!store.transition(challengeId, ChallengeState.PENDING, ChallengeState.VERIFIED, clock.instant())) {
            DeletionChallenge current = store.find(challengeId).orElse(null);
            if (current != null && current.getState() == ChallengeState.VERIFIED) {
                return true;
            }
            throw new IllegalStateException("El desafío ya no está disponible. Solicite uno nuevo.");
        }
        log.info("Desafío {} verificado correctamente. Listo para confirmación final.", challengeId);
        return true;
    }

    private void blockIfExhausted(String challengeId, int attempts) {
        if (attempts >= MAX_ATTEMPTS) {
            store.transition(challengeId, ChallengeState.PENDING, ChallengeState.BLOCKED, clock.instant());
        }
    }

    /**
     * Consume el desafío tras la segunda confirmación. Garantiza idempotencia.
     */
    public DeletionChallenge consumeChallengeForFinalExecution(String challengeId, Long propertyId, Long organizationId, Long userId) {
        DeletionChallenge challenge = store.find(challengeId).orElse(null);
        if (challenge == null) {
            throw new IllegalArgumentException("Desafío de eliminación inválido o inexistente.");
        }

        if (!challenge.getPropertyId().equals(propertyId) ||
            !challenge.getOrganizationId().equals(organizationId) ||
            !challenge.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Contexto de seguridad no coincide con el desafío.");
        }

        if (challenge.isExpired(clock.instant())) {
            store.transition(challengeId, challenge.getState(), ChallengeState.EXPIRED, clock.instant());
            throw new IllegalStateException("El código de verificación ha expirado.");
        }

        if (challenge.getState() == ChallengeState.CONSUMED) {
            throw new IllegalStateException("Este desafío ya fue consumido. La propiedad ya fue procesada.");
        }

        if (challenge.getState() != ChallengeState.VERIFIED) {
            throw new IllegalStateException("El desafío no ha sido verificado con el código OTP.");
        }

        // Marcar como consumido de forma atómica: solo una ejecución concurrente gana la transición
        if (!store.transition(challengeId, ChallengeState.VERIFIED, ChallengeState.CONSUMED, clock.instant())) {
            throw new IllegalStateException("Este desafío ya fue consumido. La propiedad ya fue procesada.");
        }
        return new DeletionChallenge(challenge.getChallengeId(), challenge.getPropertyId(), challenge.getOrganizationId(),
                challenge.getUserId(), challenge.getUserEmail(), challenge.getPropertyName(), challenge.getOtpHash(),
                challenge.getSalt(), challenge.getCreatedAt(), challenge.getExpiresAt(), challenge.getAttempts(),
                ChallengeState.CONSUMED);
    }

    public Optional<DeletionChallenge> getChallenge(String challengeId) {
        return store.find(challengeId);
    }

    private String generateSecureOtp() {
        int code = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }

    private String hashOtp(String otp, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error en algoritmo criptográfico SHA-256", e);
        }
    }
}
