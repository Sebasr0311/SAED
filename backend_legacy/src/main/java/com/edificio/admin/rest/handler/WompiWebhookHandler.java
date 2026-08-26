package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.JsonUtil;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.WompiService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Webhook de Wompi — contexto SIN autenticacion JWT (a diferencia del resto de
 * /api). La autenticidad se valida internamente con la firma asimetrica
 * (checksum SHA256 + WOMPI_EVENTS_SECRET) dentro de WompiService.procesarWebhook.
 *
 * Contrato de respuesta: 200 para reconocer el evento (Wompi reintenta max 3
 * veces en 24h si la respuesta no es 200). Un checksum invalido se responde 200
 * pero el evento se IGNORA (no se procesa).
 */
public class WompiWebhookHandler extends BaseHandler implements HttpHandler {

    private final WompiService service = new WompiService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            service.procesarWebhook(body);
            sendJson(exchange, 200, Map.of("ok", true));
        } catch (Exception e) {
            System.err.println("[WompiWebhook] error procesando evento: " + e.getMessage());
            // 400 -> Wompi reintentará (máx 3 veces en 24 h)
            sendJson(exchange, 400, new ErrorResponse("Error procesando webhook: " + e.getMessage()));
        }
    }
}
