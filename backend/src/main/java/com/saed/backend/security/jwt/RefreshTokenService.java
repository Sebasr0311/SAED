package com.saed.backend.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory refresh token store. Tokens are UUID-based, expire after 7 days,
 * and are single-use (rotated on each refresh). For production, replace with
 * a database-backed store for multi-instance deployments.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final long REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final ConcurrentHashMap<String, TokenEntry> store = new ConcurrentHashMap<>();

    public String createRefreshToken(Long userId) {
        // Invalidate any existing refresh tokens for this user
        store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));

        String token = UUID.randomUUID().toString();
        store.put(token, new TokenEntry(userId, System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY_MS));
        log.debug("Created refresh token for userId={}", userId);
        return token;
    }

    /**
     * Validate and consume a refresh token. Returns the userId if valid, null otherwise.
     * Tokens are single-use: valid token is rotated (old deleted, new created by caller).
     */
    public Long validateAndConsume(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return null;

        TokenEntry entry = store.remove(refreshToken); // atomic remove = consume
        if (entry == null) {
            log.warn("Refresh token not found or already used");
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            log.warn("Refresh token expired for userId={}", entry.userId());
            return null;
        }
        return entry.userId();
    }

    public void invalidateAllForUser(Long userId) {
        store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
    }

    private record TokenEntry(Long userId, long expiresAt) {}
}
