package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.TipoDocumentoDAO;
import com.edificio.admin.model.TipoDocumento;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class TipoDocumentoHandler extends BaseHandler implements HttpHandler {

    private final TipoDocumentoDAO dao = new TipoDocumentoDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                List<TipoDocumento> list = dao.findActivos();
                sendJson(exchange, 200, list);
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
