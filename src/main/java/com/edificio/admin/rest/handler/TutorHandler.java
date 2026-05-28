package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

public class TutorHandler extends BaseHandler implements HttpHandler {

    private final TutorDAO tutorDAO = new TutorDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                int idResidente = Integer.parseInt(parts[3]);
                Tutor t = tutorDAO.findByResidenteMenor(idResidente);
                sendJson(exchange, 200, t != null ? t : Map.of());
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Tutor t = JsonUtil.fromJson(body, Tutor.class);
                Integer id = tutorDAO.insert(t);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Tutor t = JsonUtil.fromJson(body, Tutor.class);
                t.setIdTutor(Integer.parseInt(parts[3]));
                tutorDAO.update(t);
                sendJson(exchange, 200, Map.of("mensaje", "Tutor actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                tutorDAO.delete(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Tutor eliminado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
