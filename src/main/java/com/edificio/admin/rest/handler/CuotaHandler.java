package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.PagoService;
import com.edificio.admin.dao.ConexionBD;
import com.edificio.admin.dao.ContratoDAO;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.CuotaArriendo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public class CuotaHandler extends BaseHandler implements HttpHandler {

    private final PagoService service = new PagoService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();

            if ("GET".equalsIgnoreCase(method)) {
                if (query != null && query.contains("contrato=")) {
                    Integer idContrato = JsonUtil.extraerInt(query, "contrato");
                    if (idContrato == null) throw new Exception("Par\u00e1metro contrato inv\u00e1lido");
                    if ("RESIDENTE".equals(AuthScope.rol(claims))) {
                        Contrato contrato = new ContratoDAO().findById(idContrato);
                        if (contrato == null || contrato.getIdApartamento() == null) {
                            AuthScope.sendForbidden(exchange, "No autorizado para consultar este contrato");
                            return;
                        }
                        Connection conn = ConexionBD.getInstancia().getConexion();
                        if (!AuthScope.requireOwnApartment(exchange, conn, claims, contrato.getIdApartamento())) return;
                    } else if ("PORTERO".equals(AuthScope.rol(claims))) {
                        AuthScope.sendForbidden(exchange, "No autorizado para este rol");
                        return;
                    }
                    List<CuotaArriendo> list = service.listarCuotasPorContrato(idContrato);
                    sendJson(exchange, 200, list);
                } else if (query != null && query.contains("pendientes")) {
                    if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                    List<CuotaArriendo> list = service.listarCuotasPendientes();
                    sendJson(exchange, 200, list);
                } else {
                    sendJson(exchange, 400, new ErrorResponse("Parametro requerido: contrato= o pendientes"));
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                CuotaArriendo cuota = JsonUtil.fromJson(body, CuotaArriendo.class);
                if (cuota.getTipoCuota() == null) cuota.setTipoCuota("ARRIENDO");
                Integer id = service.generarCuota(cuota);
                sendJson(exchange, 201, Map.of("id", id));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
