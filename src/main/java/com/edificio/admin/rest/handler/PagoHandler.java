package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.PagoService;
import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.MetodoPago;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class PagoHandler extends BaseHandler implements HttpHandler {

    private final PagoService service = new PagoService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && query != null && query.contains("cuota=")) {
                Integer idCuota = JsonUtil.extraerInt(query, "cuota");
                if (idCuota == null) throw new Exception("Par\u00e1metro cuota inv\u00e1lido");
                List<Pago> list = service.listarPagosPorCuota(idCuota);
                sendJson(exchange, 200, list);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Pago p = JsonUtil.fromJson(body, Pago.class);
                p.setRegistradoPor(((Number) claims.get("idUsuario")).intValue());
                Integer id = service.registrarPago(p);
                sendJson(exchange, 201, Map.of("id", id));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
