package com.edificio.admin.rest;

import com.edificio.admin.rest.dto.ErrorResponse;
import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AuthMiddleware {

    public static Map<String, Object> authenticate(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "token".equals(kv[0])) {
                        String decodedToken = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        Map<String, Object> claims = JwtUtil.validarToken(decodedToken);
                        if (claims != null) return claims;
                    }
                }
            }
            sendUnauthorized(exchange, "Token requerido");
            return null;
        }
        String token = auth.substring(7);
        Map<String, Object> claims = JwtUtil.validarToken(token);
        if (claims == null) {
            sendUnauthorized(exchange, "Token invalido o expirado");
            return null;
        }
        return claims;
    }

    public static boolean hasRole(Map<String, Object> claims, String role) {
        return claims != null && role != null && role.equals(claims.get("rol"));
    }

    private static void sendUnauthorized(HttpExchange exchange, String msg) {
        try {
            String json = JsonUtil.toJson(new ErrorResponse(msg));
            byte[] bytes = json.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        } catch (Exception e) {
            exchange.close();
        }
    }
}
