package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.ApartamentoService;
import com.edificio.admin.service.ContratoService;
import com.edificio.admin.dao.ResidenteDAO;
import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.Residente;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

public class ApartamentoHandler extends BaseHandler implements HttpHandler {

    private final ApartamentoService service = new ApartamentoService();
    private final ContratoService contratoService = new ContratoService();
    private final ResidenteDAO residenteDAO = new ResidenteDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                List<Apartamento> list = service.listarTodos();
                List<Residente> residentes = residenteDAO.findAll();
                
                // Crear lista con información adicional de conteo
                List<Map<String, Object>> response = new ArrayList<>();
                for (Apartamento apt : list) {
                    Map<String, Object> aptMap = new HashMap<>();
                    aptMap.put("idApartamento", apt.getIdApartamento());
                    aptMap.put("numero", apt.getNumero());
                    aptMap.put("piso", apt.getPiso());
                    aptMap.put("tipo", apt.getTipo());
                    aptMap.put("areaM2", apt.getAreaM2());
                    aptMap.put("capacidadMaxima", apt.getCapacidadMaxima());
                    aptMap.put("estado", apt.getEstado());
                    aptMap.put("administracion", apt.getAdministracion());

                    // Contar residentes activos en este apartamento
                    int conteo = 0;
                    for (Residente r : residentes) {
                        if (r.isActivo() && r.getIdApartamento() != null && r.getIdApartamento().equals(apt.getIdApartamento())) {
                            conteo++;
                        }
                    }
                    aptMap.put("cantidadResidentes", conteo);
                    
                    response.add(aptMap);
                }
                
                sendJson(exchange, 200, response);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                Apartamento a = service.buscarPorId(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, a);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Apartamento a = JsonUtil.fromJson(body, Apartamento.class);
                Integer id = service.registrar(a);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Apartamento a = JsonUtil.fromJson(body, Apartamento.class);
                a.setIdApartamento(Integer.parseInt(parts[3]));
                service.actualizar(a);
                sendJson(exchange, 200, Map.of("mensaje", "Apartamento actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 6 && "residentes".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                int idApartamento = Integer.parseInt(parts[3]);
                int idResidente = Integer.parseInt(parts[5]);
                contratoService.removerResidente(idApartamento, idResidente);
                sendJson(exchange, 200, Map.of("mensaje", "Residente eliminado del apartamento"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
