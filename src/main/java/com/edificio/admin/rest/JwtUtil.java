package com.edificio.admin.rest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public class JwtUtil {

    private static final String SECRET;
    private static final long EXPIRATION_HOURS = 8;

    static {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32];
        random.nextBytes(key);
        SECRET = Base64.getEncoder().encodeToString(key);
    }

    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    public static String generarToken(Integer idUsuario, String username, String rol) {
        long exp = Instant.now().plusSeconds(EXPIRATION_HOURS * 3600).getEpochSecond();
        String payload = JsonUtil.toJson(Map.of(
            "idUsuario", idUsuario,
            "username", username,
            "rol", rol,
            "exp", exp
        ));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha256(HEADER + "." + payloadB64);
        return HEADER + "." + payloadB64 + "." + signature;
    }

    public static Map<String, Object> validarToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String expectedSig = hmacSha256(parts[0] + "." + parts[1]);
        if (!expectedSig.equals(parts[2])) return null;
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = JsonUtil.fromJson(payloadJson, Map.class);
            Number expVal = (Number) claims.get("exp");
            if (expVal == null) return null;
            if (Instant.now().getEpochSecond() > expVal.longValue()) return null;
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error al generar HMAC", e);
        }
    }
}
