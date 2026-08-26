package com.saed.backend.security.jwt;

import com.saed.backend.context.SaedContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtProvider {

    @Value("${jwt.secret:dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itc2FlZC0yLjAtc2VjcmV0}")
    private String jwtSecret;

    @Value("${jwt.expiration.ms:86400000}")
    private int jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(SaedContext context) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", context.getUserId());
        claims.put("organizationId", context.getOrganizationId());
        claims.put("propertyId", context.getPropertyId());
        claims.put("unitId", context.getUnitId());
        claims.put("roleCode", context.getRoleCode());

        return Jwts.builder()
                .claims(claims)
                .subject(context.getUserId().toString())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public SaedContext getContextFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return SaedContext.builder()
                .userId(claims.get("userId", Long.class))
                .organizationId(claims.get("organizationId", Long.class))
                .propertyId(claims.get("propertyId", Long.class))
                .unitId(claims.get("unitId", Long.class))
                .roleCode(claims.get("roleCode", String.class))
                .build();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            System.err.println("Invalid JWT signature: " + e.getMessage());
        }
        return false;
    }
}
