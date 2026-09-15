package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.UsuarioService;
import com.edificio.admin.model.Usuario;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.List;
import java.util.Map;

public class UsuarioHandler extends BaseHandler implements HttpHandler {

    private final UsuarioService service = new UsuarioService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                List<Usuario> list = service.listarTodos();
                for (Usuario u : list) u.setPasswordHash(null);
                sendJson(exchange, 200, list);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                Usuario u = service.buscarPorId(Integer.parseInt(parts[3]));
                if (u != null) u.setPasswordHash(null);
                sendJson(exchange, 200, u);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Usuario u = JsonUtil.fromJson(body, Usuario.class);
                Integer id = service.registrar(u);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Usuario u = JsonUtil.fromJson(body, Usuario.class);
                u.setIdUsuario(Integer.parseInt(parts[3]));
                service.actualizar(u);
                sendJson(exchange, 200, Map.of("mensaje", "Usuario actualizado"));
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "toggle-activo".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                service.toggleActivo(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Estado actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 6 && "eliminar".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String adminPassword = (String) data.get("adminPassword");
                int idAdmin = ((Number) claims.get("idUsuario")).intValue();
                service.eliminar(Integer.parseInt(parts[3]), idAdmin, adminPassword);
                sendJson(exchange, 200, Map.of("mensaje", "Usuario eliminado permanentemente"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                service.desactivar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Usuario desactivado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
