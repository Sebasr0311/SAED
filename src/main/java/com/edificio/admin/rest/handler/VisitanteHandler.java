package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.VisitanteDAO;
import com.edificio.admin.model.Visitante;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class VisitanteHandler extends BaseHandler implements HttpHandler {

    private final VisitanteDAO dao = new VisitanteDAO();

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
                if (query != null && query.contains("documento=")) {
                    String doc = JsonUtil.extraerValor(query, "documento");
                    if (doc != null && !doc.isBlank()) {
                        Visitante v = dao.findByNumeroDocumento(doc);
                        sendJson(exchange, v != null ? 200 : 404, v != null ? v : Map.of());
                        return;
                    }
                }
                List<Visitante> list = dao.findAll();
                sendJson(exchange, 200, list);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                Visitante v = dao.findById(Integer.parseInt(parts[3]));
                if (v == null) {
                    sendJson(exchange, 404, new ErrorResponse("Visitante no encontrado"));
                    return;
                }
                sendJson(exchange, 200, v);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Visitante v = JsonUtil.fromJson(body, Visitante.class);
                if (v.getNumeroDocumento() == null || v.getNumeroDocumento().isBlank())
                    throw new IllegalArgumentException("numeroDocumento es obligatorio");
                if (v.getIdTipoDoc() == null || v.getIdTipoDoc() <= 0)
                    throw new IllegalArgumentException("idTipoDoc es obligatorio");
                Integer id = dao.insert(v);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Visitante v = JsonUtil.fromJson(body, Visitante.class);
                v.setId(Integer.parseInt(parts[3]));
                dao.update(v);
                sendJson(exchange, 200, Map.of("mensaje", "Visitante actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                dao.delete(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Visitante eliminado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, new ErrorResponse("Error interno: " + e.getMessage()));
        }
    }
}
