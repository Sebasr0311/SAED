package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.ParqueaderoService;
import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.model.Parqueadero;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class ParqueaderoHandler extends BaseHandler implements HttpHandler {

    private final ParqueaderoService service = new ParqueaderoService();
    private final ParqueaderoDAO dao = new ParqueaderoDAO();

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
                if (query != null && (query.contains("estado=") || query.contains("tipo="))) {
                    String estado = JsonUtil.extraerValor(query, "estado");
                    String tipo = JsonUtil.extraerValor(query, "tipo");
                    List<Parqueadero> list = dao.findConFiltros(estado, tipo, null);
                    sendJson(exchange, 200, list);
                } else {
                    List<Parqueadero> list = service.listarTodos();
                    sendJson(exchange, 200, list);
                }
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                Parqueadero p = service.buscarPorId(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, p);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Parqueadero p = JsonUtil.fromJson(body, Parqueadero.class);
                Integer id = service.registrar(p);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Parqueadero p = JsonUtil.fromJson(body, Parqueadero.class);
                p.setIdParqueadero(Integer.parseInt(parts[3]));
                service.actualizar(p);
                sendJson(exchange, 200, Map.of("mensaje", "Parqueadero actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                service.desactivar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Parqueadero eliminado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
