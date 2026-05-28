package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.AlertaPagoDAO;
import com.edificio.admin.model.AlertaPago;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class AlertaHandler extends BaseHandler implements HttpHandler {

    private final AlertaPagoDAO dao = new AlertaPagoDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                boolean soloNoLeidas = query != null && query.contains("soloNoLeidas=true");
                List<AlertaPago> list = soloNoLeidas ? dao.findNoLeidas() : dao.findAll();
                sendJson(exchange, 200, list);
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "leer".equals(parts[4])) {
                int id = Integer.parseInt(parts[3]);
                AlertaPago a = dao.findById(id);
                if (a == null) throw new Exception("Alerta no encontrada");
                if (a.isLeida()) throw new Exception("La alerta ya fue leida");
                dao.marcarLeida(id);
                sendJson(exchange, 200, Map.of("mensaje", "Alerta marcada como leida"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
