package com.edificio.admin.rest;

import com.edificio.admin.rest.dto.ErrorResponse;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

/**
 * Autorizacion server-side: helpers estaticos, simples y auditables.
 *
 * Regla general: ADMINISTRADOR siempre autorizado. Los helpers de "require*"
 * envian ellos mismos la respuesta 403 (JSON {"error": ...}) cuando la
 * verificacion falla y retornan false para que el handler corte la ejecucion:
 *
 *     if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
 *
 * Los ids de residente/apartamento se derivan SIEMPRE de los claims del token
 * (nunca de parametros del cliente): idResidente viene de USUARIOS.id_residente
 * y idApartamento de la cadena USUARIOS -> RESIDENTES -> CONTRATO_RESIDENTE
 * -> CONTRATOS (ACTIVO) -> APARTAMENTOS.
 */
public class AuthScope {

    private AuthScope() {}

    /** idUsuario del token. 0 si el claim falta o no es numerico. */
    public static int idUsuario(Map<String, Object> claims) {
        if (claims == null) return 0;
        Object v = claims.get("idUsuario");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /** rol del token. null si el claim falta. */
    public static String rol(Map<String, Object> claims) {
        if (claims == null) return null;
        Object v = claims.get("rol");
        return v != null ? v.toString() : null;
    }

    /** id_residente del usuario (USUARIOS). 0 si no tiene residente asociado. */
    public static int idResidente(Connection conn, Map<String, Object> claims) {
        int idUsuario = idUsuario(claims);
        if (idUsuario <= 0 || conn == null) return 0;
        String sql = "SELECT id_residente FROM USUARIOS WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? 0 : v;
                }
            }
        } catch (Exception e) {
            // sin residente -> 0; nunca se filtra el error
        }
        return 0;
    }

    /**
     * id_apartamento del usuario via contrato activo (misma cadena de JOIN
     * que BuzonHandler). 0 si no tiene apartamento asociado.
     */
    public static int idApartamento(Connection conn, Map<String, Object> claims) {
        int idUsuario = idUsuario(claims);
        if (idUsuario <= 0 || conn == null) return 0;
        String sql = "SELECT a.id_apartamento "
                   + "FROM   USUARIOS u "
                   + "JOIN   RESIDENTES r ON r.id_residente = u.id_residente "
                   + "JOIN   CONTRATO_RESIDENTE cr ON cr.id_residente = r.id_residente "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cr.id_contrato AND c.estado = 'ACTIVO' "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "WHERE  u.id_usuario = ? AND ROWNUM = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id_apartamento");
            }
        } catch (Exception e) {
            // sin apartamento -> 0; nunca se filtra el error
        }
        return 0;
    }

    /** 403 si claims es null o el rol no esta en la lista. true si autorizado. */
    public static boolean requireRole(HttpExchange ex, Map<String, Object> claims, String... roles) {
        String r = rol(claims);
        if (r == null) return sendForbidden(ex, "No autorizado");
        for (String allowed : roles) {
            if (allowed.equals(r)) return true;
        }
        return sendForbidden(ex, "No autorizado para el rol " + r);
    }

    /** 403 si el id del path no es el idResidente del token. */
    public static boolean requireOwnResident(HttpExchange ex, Connection conn, Map<String, Object> claims, int idPath) {
        if (rol(claims) == null) return sendForbidden(ex, "No autorizado");
        int idResidente = idResidente(conn, claims);
        if (idResidente <= 0) return sendForbidden(ex, "El usuario no tiene un residente asociado");
        if (idPath != idResidente) return sendForbidden(ex, "No autorizado para acceder a este residente");
        return true;
    }

    /** 403 si el id dado no es el idApartamento del token. */
    public static boolean requireOwnApartment(HttpExchange ex, Connection conn, Map<String, Object> claims, int idApto) {
        if (rol(claims) == null) return sendForbidden(ex, "No autorizado");
        int idApartamento = idApartamento(conn, claims);
        if (idApartamento <= 0) return sendForbidden(ex, "El usuario no tiene un apartamento asociado");
        if (idApto != idApartamento) return sendForbidden(ex, "No autorizado para este apartamento");
        return true;
    }

    /** Envia 403 {"error": msg}. Siempre retorna false para encadenar. */
    public static boolean sendForbidden(HttpExchange ex, String msg) {
        return sendError(ex, 403, msg);
    }

    /** Envia 401 {"error": msg}. Siempre retorna false para encadenar. */
    public static boolean sendUnauthorized(HttpExchange ex, String msg) {
        return sendError(ex, 401, msg);
    }

    private static boolean sendError(HttpExchange ex, int code, String msg) {
        try {
            byte[] bytes = JsonUtil.toJson(new ErrorResponse(msg)).getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        } catch (Exception e) {
            ex.close();
        }
        return false;
    }
}
