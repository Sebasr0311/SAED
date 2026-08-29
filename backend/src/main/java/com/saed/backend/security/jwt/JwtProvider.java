package com.saed.backend.security.jwt;

import com.saed.backend.context.SaedContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration.ms:86400000}")
    private int jwtExpirationMs;

    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret is not set. Configure JWT_SECRET env var before starting the application.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateIdentityToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        }
        return false;
    }
}
