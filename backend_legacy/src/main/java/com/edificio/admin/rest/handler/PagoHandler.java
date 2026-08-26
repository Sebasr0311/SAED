package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.PagoService;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.CuotaArriendo;
import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.MetodoPago;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public class PagoHandler extends BaseHandler implements HttpHandler {

    private final PagoService service = new PagoService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method)) {
                if (query != null && query.contains("cuota=")) {
                    Integer idCuota = JsonUtil.extraerInt(query, "cuota");
                    if (idCuota == null) throw new Exception("Par\u00e1metro cuota inv\u00e1lido");
                    if ("RESIDENTE".equals(AuthScope.rol(claims))) {
                        CuotaArriendo cuota = new CuotaArriendoDAO().findById(idCuota);
                        if (cuota == null || cuota.getIdContrato() == null) {
                            AuthScope.sendForbidden(exchange, "No autorizado para consultar esta cuota");
                            return;
                        }
                        Contrato contrato = new ContratoDAO().findById(cuota.getIdContrato());
                        if (contrato == null || contrato.getIdApartamento() == null) {
                            AuthScope.sendForbidden(exchange, "No autorizado para consultar esta cuota");
                            return;
                        }
                        Connection conn = ConexionBD.getInstancia().getConexion();
                        if (!AuthScope.requireOwnApartment(exchange, conn, claims, contrato.getIdApartamento())) return;
                    } else if ("PORTERO".equals(AuthScope.rol(claims))) {
                        AuthScope.sendForbidden(exchange, "No autorizado para este rol");
                        return;
                    }
                    List<Pago> list = service.listarPagosPorCuota(idCuota);
                    sendJson(exchange, 200, list);
                } else if (path.contains("/ganancias")) {
                    if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                    Map<String, Object> resumen = service.obtenerResumenGanancias();
                    sendJson(exchange, 200, resumen);
                } else if (path.contains("/registrados")) {
                    if ("RESIDENTE".equals(AuthScope.rol(claims))) {
                        Connection conn = ConexionBD.getInstancia().getConexion();
                        int idApartamento = AuthScope.idApartamento(conn, claims);
                        if (idApartamento <= 0) {
                            AuthScope.sendForbidden(exchange, "El usuario no tiene un apartamento asociado");
                            return;
                        }
                        List<Map<String, Object>> list = service.listarPagosRegistrados(idApartamento);
                        sendJson(exchange, 200, list);
                    } else {
                        if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                        List<Map<String, Object>> list = service.listarPagosRegistrados();
                        sendJson(exchange, 200, list);
                    }
                } else {
                    sendJson(exchange, 400, new ErrorResponse("Par\u00e1metro requerido: cuota="));
                }
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Pago p = JsonUtil.fromJson(body, Pago.class);
                p.setRegistradoPor(((Number) claims.get("idUsuario")).intValue());
                Integer id = service.registrarPago(p);
                sendJson(exchange, 201, Map.of("id", id));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
