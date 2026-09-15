package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.AuthMiddleware;
import com.edificio.admin.rest.AuthScope;
import com.edificio.admin.rest.JsonUtil;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.QuejaSugerenciaDAO;
import com.edificio.admin.model.QuejaSugerencia;
import com.edificio.admin.service.PagoService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Notificaciones agregadas del ADMINISTRADOR:
 *  - Quejas/solicitudes/reclamos pendientes de respuesta.
 *  - Pagos recibidos en los ultimos 7 dias (cuotas y multas).
 * GET /api/notificaciones — solo ADMINISTRADOR.
 * El "leido" se resuelve en el frontend por timestamp (localStorage).
 */
public class NotificacionHandler extends BaseHandler implements HttpHandler {

    private final QuejaSugerenciaDAO quejaDAO = new QuejaSugerenciaDAO();
    private final PagoService pagoService = new PagoService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;
            if (!"ADMINISTRADOR".equals(AuthScope.rol(claims))) {
                AuthScope.sendForbidden(exchange, "Solo el administrador tiene notificaciones centralizadas");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
                return;
            }

            List<Map<String, Object>> items = new ArrayList<>();

            // Quejas pendientes
            List<QuejaSugerencia> quejas = quejaDAO.findPendientes();
            for (QuejaSugerencia q : quejas) {
                Map<String, Object> it = new HashMap<>();
                it.put("tipo", "QUEJA");
                it.put("id", q.getIdQueja());
                it.put("titulo", "Queja pendiente: " + q.getTitulo());
                String cat = q.getCategoria() != null ? q.getCategoria() : "";
                String apto = q.getIdApartamento() != null ? "Apto " + q.getIdApartamento() : "";
                it.put("cuerpo", (cat + (apto.isEmpty() ? "" : " · " + apto)).trim());
                it.put("fecha", q.getFechaCreacion() != null
                    ? q.getFechaCreacion().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
                it.put("ruta", "/quejas-admin");
                items.add(it);
            }

            // Pagos recibidos en los ultimos 7 dias
            LocalDate hace7Dias = LocalDate.now().minusDays(7);
            try {
                List<Map<String, Object>> pagos = pagoService.listarPagosRegistrados();
                for (Map<String, Object> p : pagos) {
                    Object fechaObj = p.get("fecha");
                    if (fechaObj == null) continue;
                    LocalDate fecha;
                    if (fechaObj instanceof java.sql.Date) {
                        fecha = ((java.sql.Date) fechaObj).toLocalDate();
                    } else if (fechaObj instanceof java.sql.Timestamp) {
                        fecha = ((java.sql.Timestamp) fechaObj).toLocalDateTime().toLocalDate();
                    } else {
                        try { fecha = LocalDate.parse(String.valueOf(fechaObj).substring(0, 10)); }
                        catch (Exception e) { continue; }
                    }
                    if (fecha.isBefore(hace7Dias)) continue;

                    Map<String, Object> it = new HashMap<>();
                    it.put("tipo", "PAGO");
                    it.put("id", p.get("id"));
                    BigDecimal valor = p.get("valor") instanceof BigDecimal
                        ? (BigDecimal) p.get("valor") : new BigDecimal(String.valueOf(p.get("valor")));
                    String desc = String.valueOf(p.get("descripcion") != null ? p.get("descripcion") : "Pago");
                    String apto = String.valueOf(p.get("apartamento") != null ? p.get("apartamento") : "-");
                    String residente = String.valueOf(p.get("residente") != null ? p.get("residente") : "-");
                    it.put("titulo", "Pago recibido: " + desc);
                    it.put("cuerpo", "$" + valor.toBigInteger() + " · Apto " + apto + " · " + residente);
                    it.put("fecha", fecha.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    it.put("ruta", "/pagos");
                    items.add(it);
                }
            } catch (Exception e) {
                System.err.println("[Notificaciones] pagos: " + e.getMessage());
            }

            // Ordenar por fecha desc (mas recientes primero) y limitar
            items.sort((a, b) -> String.valueOf(b.get("fecha")).compareTo(String.valueOf(a.get("fecha"))));
            if (items.size() > 30) items = items.subList(0, 30);

            Map<String, Object> res = new HashMap<>();
            res.put("items", items);
            res.put("total", items.size());
            sendJson(exchange, 200, res);
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }
}
