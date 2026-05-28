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
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new com.edificio.admin.rest.dto.ErrorResponse("Metodo no permitido"));
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            LoginRequest req = JsonUtil.fromJson(body, LoginRequest.class);
            if (req == null || req.getUsername() == null || req.getPassword() == null) {
                sendJson(exchange, 400, new com.edificio.admin.rest.dto.ErrorResponse("Username y password requeridos"));
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
            sendJson(exchange, 401, new com.edificio.admin.rest.dto.ErrorResponse(e.getMessage()));
        } catch (ConexionFallidaException | SQLException e) {
            sendJson(exchange, 500, new com.edificio.admin.rest.dto.ErrorResponse("Error interno del servidor"));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new com.edificio.admin.rest.dto.ErrorResponse(e.getMessage()));
        }
    }

}
