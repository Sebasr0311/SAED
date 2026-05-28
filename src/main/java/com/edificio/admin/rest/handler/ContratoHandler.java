package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.ContratoService;
import com.edificio.admin.service.ContratoPdfService;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.enums.TipoContrato;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

public class ContratoHandler extends BaseHandler implements HttpHandler {

    private final ContratoService service = new ContratoService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                List<Contrato> list = service.listarTodos();
                sendJson(exchange, 200, list);
            } else             if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                Contrato c = service.buscarPorId(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, c);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "sugerir-tipo".equals(parts[3])) {
                TipoContrato sugerido = service.sugerirTipo(Integer.parseInt(parts[4]));
                sendJson(exchange, 200, Map.of("tipoSugerido", sugerido.name()));
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "pdf".equals(parts[4])) {
                Integer id = Integer.parseInt(parts[3]);
                var detalle = service.obtenerDetalleParaPDF(id);
                ContratoPdfService pdfService = new ContratoPdfService();
                byte[] pdfBytes = pdfService.generarPdf(detalle);

                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"contrato_" + id + ".pdf\"");
                exchange.sendResponseHeaders(200, pdfBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(pdfBytes);
                }
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                Contrato c = new Contrato();
                Object idApto = data.get("idApartamento");
                if (idApto == null) throw new Exception("idApartamento es obligatorio");
                c.setIdApartamento(((Number) idApto).intValue());
                c.setIdRegistradoPor(((Number) claims.get("idUsuario")).intValue());
                String fechaInicio = (String) data.get("fechaInicio");
                if (fechaInicio == null || fechaInicio.isEmpty()) throw new Exception("fechaInicio es obligatorio");
                c.setFechaInicio(java.time.LocalDate.parse(fechaInicio));

                Object tipoContratoObj = data.get("tipoContrato");
                TipoContrato tipoContrato;
                if (tipoContratoObj != null) {
                    tipoContrato = TipoContrato.valueOf((String) tipoContratoObj);
                } else {
                    tipoContrato = service.sugerirTipo(c.getIdApartamento());
                }
                c.setTipoContrato(tipoContrato);

                String fechaFin = (String) data.get("fechaFin");
                if (fechaFin != null && !fechaFin.isEmpty()) {
                    c.setFechaFin(java.time.LocalDate.parse(fechaFin));
                } else if (tipoContrato != TipoContrato.PERMANENCIA) {
                    c.setFechaFin(service.calcularFechaFin(c.getFechaInicio(), tipoContrato));
                }

                Object valor = data.get("valorMensual");
                if (valor == null) throw new Exception("valorMensual es obligatorio");
                c.setValorMensual(new java.math.BigDecimal(valor.toString()));
                c.setNotas((String) data.get("notas"));
                Object idTutorObj = data.get("idTutor");
                if (idTutorObj != null) c.setIdTutor(((Number) idTutorObj).intValue());
                Object idRes = data.get("idResidente");
                if (idRes == null) throw new Exception("idResidente es obligatorio");
                Integer idResidente = ((Number) idRes).intValue();
                Integer id = service.crearContrato(c, idResidente);
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "activar".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                service.activar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Contrato activado"));
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "cancelar".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                service.cancelar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Contrato cancelado"));
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "renovar".equals(parts[4])) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                int id = Integer.parseInt(parts[3]);
                String fechaInicioR = (String) data.get("fechaInicio");
                String fechaFinR    = (String) data.get("fechaFin");
                if (fechaInicioR == null || fechaInicioR.isEmpty())
                    throw new Exception("fechaInicio es obligatorio");
                Object valorObj = data.get("valorMensual");
                java.math.BigDecimal nuevoValor = valorObj != null
                        ? new java.math.BigDecimal(valorObj.toString()) : null;
                Integer nuevoId = service.renovar(
                        id,
                        java.time.LocalDate.parse(fechaInicioR),
                        (fechaFinR != null && !fechaFinR.isEmpty()) ? java.time.LocalDate.parse(fechaFinR) : null,
                        nuevoValor,
                        (String) data.get("notas"));
                sendJson(exchange, 201, Map.of("id", nuevoId, "mensaje", "Contrato de renovacion creado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                if (!AuthMiddleware.hasRole(claims, "ADMINISTRADOR")) {
                    sendJson(exchange, 403, new ErrorResponse("Se requieren permisos de administrador"));
                    return;
                }
                service.cancelar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Contrato cancelado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }
}
