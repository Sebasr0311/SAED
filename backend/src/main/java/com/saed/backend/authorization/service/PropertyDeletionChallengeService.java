package com.saed.backend.authorization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Map<String, DeletionChallenge> challengeStore = new ConcurrentHashMap<>();

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
        private final AtomicInteger attempts = new AtomicInteger(0);
        private volatile ChallengeState state = ChallengeState.PENDING;

        public DeletionChallenge(String challengeId, Long propertyId, Long organizationId, Long userId,
                                 String userEmail, String propertyName, String otpHash, String salt,
                                 Instant createdAt, Instant expiresAt) {
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
        public int getAttempts() { return attempts.get(); }
        public ChallengeState getState() { return state; }
        public void setState(ChallengeState state) { this.state = state; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public int incrementAttempts() {
            return attempts.incrementAndGet();
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
        // 1. Invalidar desafíos previos activos
        challengeStore.values().removeIf(c -> 
                c.getPropertyId().equals(propertyId) && 
                c.getUserId().equals(userId) && 
                (c.getState() == ChallengeState.PENDING || c.getState() == ChallengeState.VERIFIED)
        );

        // 2. Generar OTP criptográfico no predecible de 6 dígitos
        String rawOtp = generateSecureOtp();
        String salt = UUID.randomUUID().toString();
        String otpHash = hashOtp(rawOtp, salt);
        String challengeId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(EXPIRATION_MINUTES));

        DeletionChallenge challenge = new DeletionChallenge(
                challengeId, propertyId, organizationId, userId, userEmail, propertyName,
                otpHash, salt, now, expiresAt
        );

        challengeStore.put(challengeId, challenge);
        log.info("Desafío OTP generado para propiedad {} por usuario {}. Expira en {} min", propertyId, userId, EXPIRATION_MINUTES);

        return new GeneratedChallenge(challengeId, rawOtp, EXPIRATION_MINUTES * 60, challenge);
    }

    /**
     * Valida el OTP ingresado. Si es correcto, transiciona el estado a VERIFIED.
     */
    public boolean verifyOtp(String challengeId, Long propertyId, Long organizationId, Long userId, String submittedOtp) {
        DeletionChallenge challenge = challengeStore.get(challengeId);
        if (challenge == null) {
            log.warn("Verificación fallida: Desafío {} no existe", challengeId);
            throw new IllegalArgumentException("El desafío de eliminación no existe o ya caducó.");
        }

        // Validar correspondencia estricta de tenant, usuario y propiedad
        if (!challenge.getPropertyId().equals(propertyId) ||
            !challenge.getOrganizationId().equals(organizationId) ||
            !challenge.getUserId().equals(userId)) {
            log.error("Violación de seguridad en desafío {}: contexto no coincide", challengeId);
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para este desafío de seguridad.");
        }

        // Validar expiración
        if (challenge.isExpired()) {
            challenge.setState(ChallengeState.EXPIRED);
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
        if (challenge.getState() == ChallengeState.VERIFIED) {
            return true; // Ya verificado
        }

        // Rate limiting de intentos
        int attempts = challenge.incrementAttempts();
        if (attempts > MAX_ATTEMPTS) {
            challenge.setState(ChallengeState.BLOCKED);
            log.warn("Desafío {} bloqueado por exceder límite de {} intentos", challengeId, MAX_ATTEMPTS);
            throw new IllegalStateException("Número máximo de intentos excedido. El desafío fue bloqueado.");
        }

        // Comprobación criptográfica del hash
        String computedHash = hashOtp(submittedOtp.trim(), challenge.salt);
        if (!MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8), challenge.otpHash.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Código incorrecto para desafío {}. Intento {}/{}", challengeId, attempts, MAX_ATTEMPTS);
            return false;
        }

        // Código correcto -> Habilitar para segunda confirmación
        challenge.setState(ChallengeState.VERIFIED);
        log.info("Desafío {} verificado correctamente. Listo para confirmación final.", challengeId);
        return true;
    }

    /**
     * Consume el desafío tras la segunda confirmación. Garantiza idempotencia.
     */
    public DeletionChallenge consumeChallengeForFinalExecution(String challengeId, Long propertyId, Long organizationId, Long userId) {
        DeletionChallenge challenge = challengeStore.get(challengeId);
        if (challenge == null) {
            throw new IllegalArgumentException("Desafío de eliminación inválido o inexistente.");
        }

        synchronized (challenge) {
            if (!challenge.getPropertyId().equals(propertyId) ||
                !challenge.getOrganizationId().equals(organizationId) ||
                !challenge.getUserId().equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException("Contexto de seguridad no coincide con el desafío.");
            }

            if (challenge.isExpired()) {
                challenge.setState(ChallengeState.EXPIRED);
                throw new IllegalStateException("El código de verificación ha expirado.");
            }

            if (challenge.getState() == ChallengeState.CONSUMED) {
                throw new IllegalStateException("Este desafío ya fue consumido. La propiedad ya fue procesada.");
            }

            if (challenge.getState() != ChallengeState.VERIFIED) {
                throw new IllegalStateException("El desafío no ha sido verificado con el código OTP.");
            }

            // Marcar como consumido inmediatamente para evitar ejecuciones concurrentes
            challenge.setState(ChallengeState.CONSUMED);
            return challenge;
        }
    }

    public Optional<DeletionChallenge> getChallenge(String challengeId) {
        return Optional.ofNullable(challengeStore.get(challengeId));
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
            throw new RuntimeException("Error en algoritmo criptográfico SHA-256", e);
        }
    }
}
