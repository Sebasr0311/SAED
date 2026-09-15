package com.edificio.admin.rest.handler;

import com.edificio.admin.exception.*;
import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.*;
import com.edificio.admin.service.UsuarioService;
import com.edificio.admin.model.Usuario;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class AuthHandler extends BaseHandler implements HttpHandler {

    private final UsuarioService usuarioService = new UsuarioService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (!"POST".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
                return;
            }

            if (path.endsWith("/verify-password")) {
                handleVerifyPassword(exchange);
                return;
            }

            // Login
            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            LoginRequest req = JsonUtil.fromJson(body, LoginRequest.class);
            if (req == null || req.getUsername() == null || req.getPassword() == null) {
                sendJson(exchange, 400, new ErrorResponse("Username y password requeridos"));
                return;
            }
            Usuario usuario = usuarioService.autenticar(req.getUsername(), req.getPassword());
            String token = JwtUtil.generarToken(usuario.getIdUsuario(), usuario.getUsername(), usuario.getRol().name());

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("idUsuario", usuario.getIdUsuario());
            userMap.put("username", usuario.getUsername());
            userMap.put("rol", usuario.getRol().name());
            userMap.put("idResidente", usuario.getIdResidente());
            userMap.put("activo", usuario.isActivo());

            sendJson(exchange, 200, new LoginResponse(token, userMap));
        } catch (RegistroNoEncontradoException | DatosInvalidosException e) {
            sendJson(exchange, 401, new ErrorResponse(e.getMessage()));
        } catch (ConexionFallidaException | SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new ErrorResponse("Error interno del servidor"));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

    private void handleVerifyPassword(HttpExchange exchange) throws Exception {
        Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
        if (claims == null) return;

        String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
        String password = (String) data.get("password");
        if (password == null || password.isBlank()) {
            sendJson(exchange, 400, new ErrorResponse("La contrase\u00f1a es obligatoria"));
            return;
        }

        Integer idUsuario = ((Number) claims.get("idUsuario")).intValue();
        boolean valida = usuarioService.verificarPassword(idUsuario, password);
        if (!valida) {
            sendJson(exchange, 403, new ErrorResponse("Contrase\u00f1a incorrecta"));
            return;
        }

        sendJson(exchange, 200, Map.of("valido", true));
    }
}
