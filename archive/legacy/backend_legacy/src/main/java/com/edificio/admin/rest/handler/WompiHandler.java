package com.edificio.admin.rest.handler;

import com.edificio.admin.dao.ConexionBD;
import com.edificio.admin.dao.WompiPagoDAO;
import com.edificio.admin.model.WompiPago;
import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.WompiService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.util.*;

/**
 * Endpoints de pagos con Wompi protegidos por JWT:
 *  POST /api/pagos/wompi/solicitud        -> crea intencion (RESIDENTE/ADMIN)
 *  GET  /api/pagos/wompi/estado?referencia= -> estado de una intencion
 *  GET  /api/pagos/wompi/historial        -> historial del RESIDENTE
 *  GET  /api/pagos/wompi/historial-todos  -> historial completo (ADMIN)
 * El webhook (sin JWT, firma HMAC) vive en un contexto aparte: /api/wompi/webhook.
 */
public class WompiHandler extends BaseHandler implements HttpHandler {

    private final WompiService service = new WompiService();
    private final WompiPagoDAO wompiDAO = new WompiPagoDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String query  = exchange.getRequestURI().getQuery();
            String path   = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            String sub = parts.length >= 5 ? parts[4] : "";

            if ("POST".equalsIgnoreCase(method) && "solicitud".equals(sub)) {
                handleSolicitud(exchange, claims);
            } else if ("POST".equalsIgnoreCase(method) && "transaccion".equals(sub)) {
                handleRegistrarTransaccion(exchange, claims);
            } else if ("GET".equalsIgnoreCase(method) && "estado".equals(sub)) {
                handleEstado(exchange, claims, query);
            } else if ("GET".equalsIgnoreCase(method) && "historial".equals(sub)) {
                handleHistorial(exchange, claims);
            } else if ("GET".equalsIgnoreCase(method) && "historial-todos".equals(sub)) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                List<Map<String, Object>> out = new ArrayList<>();
                for (WompiPago w : wompiDAO.findAll()) out.add(toMap(w, w.getEstado()));
                sendJson(exchange, 200, out);
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

    private void handleSolicitud(HttpExchange exchange, Map<String, Object> claims) throws Exception {
        String rol = AuthScope.rol(claims);
        if (!"RESIDENTE".equals(rol) && !"ADMINISTRADOR".equals(rol)) {
            AuthScope.sendForbidden(exchange, "Los porteros no pueden iniciar pagos Wompi");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
        String concepto = data.get("concepto") != null ? String.valueOf(data.get("concepto")).toUpperCase() : null;
        // Gson parsea numeros como Double en Map.class -> cast numerico robusto
        Object idRaw = data.get("id");
        Integer idItem = idRaw instanceof Number ? ((Number) idRaw).intValue()
                        : (idRaw != null && String.valueOf(idRaw).matches("\\d+")
                            ? Integer.valueOf(String.valueOf(idRaw)) : null);

        Integer idApartamento = service.apartamentoDe(concepto, idItem);
        if (idApartamento == null)
            throw new Exception("No se pudo identificar el apartamento del item.");
        if ("RESIDENTE".equals(rol)) {
            Connection conn = ConexionBD.getInstancia().getConexion();
            if (!AuthScope.requireOwnApartment(exchange, conn, claims, idApartamento)) return;
        }

        Integer idUsuario = Integer.valueOf(AuthScope.idUsuario(claims));
        Map<String, Object> res = service.crearIntencion(concepto, idItem, idApartamento, idUsuario);
        sendJson(exchange, 201, res);
    }

    /**
     * POST /pagos/wompi/transaccion — el frontend reporta el idTransaccionWompi
     * que el widget devolvio en su callback, para que el polling /estado pueda
     * consultar el estado real en Wompi aunque el webhook no llegue.
     */
    private void handleRegistrarTransaccion(HttpExchange exchange, Map<String, Object> claims) throws Exception {
        String rol = AuthScope.rol(claims);
        if (!"RESIDENTE".equals(rol) && !"ADMINISTRADOR".equals(rol)) {
            AuthScope.sendForbidden(exchange, "Los porteros no pueden actualizar pagos Wompi");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
        String referencia = data.get("referencia") != null ? String.valueOf(data.get("referencia")) : null;
        String idTx = data.get("idTransaccionWompi") != null ? String.valueOf(data.get("idTransaccionWompi")) : null;
        service.registrarTransaccion(referencia, idTx);
        sendJson(exchange, 200, Map.of("ok", true));
    }

    private void handleEstado(HttpExchange exchange, Map<String, Object> claims, String query) throws Exception {
        String referencia = JsonUtil.extraerValor(query, "referencia");
        if (referencia == null || referencia.isBlank())
            throw new Exception("Parametro referencia requerido");
        WompiPago w = wompiDAO.findByReferencia(referencia);
        if (w == null) throw new Exception("Intencion no encontrada: " + referencia);
        if (!permitidoVer(exchange, claims, w)) return;

        // Refresca PENDIENTES contra Wompi si ya hay transaccion; persiste el
        // cambio y ejecuta el negocio cuando el pago fue aprobado.
        String estado = service.refrescarEstado(referencia);
        sendJson(exchange, 200, toMap(w, estado));
    }

    private void handleHistorial(HttpExchange exchange, Map<String, Object> claims) throws Exception {
        String rol = AuthScope.rol(claims);
        List<WompiPago> lista;
        if ("RESIDENTE".equals(rol)) {
            Connection conn = ConexionBD.getInstancia().getConexion();
            int apto = AuthScope.idApartamento(conn, claims);
            if (apto <= 0) {
                AuthScope.sendForbidden(exchange, "El usuario no tiene un apartamento asociado");
                return;
            }
            lista = wompiDAO.findByApartamento(apto);
        } else if ("ADMINISTRADOR".equals(rol)) {
            lista = wompiDAO.findAll();
        } else {
            AuthScope.sendForbidden(exchange, "No autorizado para este rol");
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (WompiPago w : lista) out.add(toMap(w, w.getEstado()));
        sendJson(exchange, 200, out);
    }

    /** RESIDENTE solo su apartamento; ADMIN todo. */
    private boolean permitidoVer(HttpExchange exchange, Map<String, Object> claims, WompiPago w) throws Exception {
        String rol = AuthScope.rol(claims);
        if ("ADMINISTRADOR".equals(rol)) return true;
        if ("RESIDENTE".equals(rol)) {
            Connection conn = ConexionBD.getInstancia().getConexion();
            return AuthScope.requireOwnApartment(exchange, conn, claims, w.getIdApartamento());
        }
        return AuthScope.sendForbidden(exchange, "No autorizado");
    }

    private String mapearEstado(String statusWompi) {
        switch (String.valueOf(statusWompi).toUpperCase()) {
            case "APPROVED": return "APROBADO";
            case "DECLINED": return "RECHAZADO";
            case "VOIDED":   return "VENCIDO";
            case "ERROR":    return "ERROR";
            default:         return "PENDIENTE";
        }
    }

    private Map<String, Object> toMap(WompiPago w, String estado) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", w.getId());
        m.put("referencia", w.getReferencia());
        m.put("apartamento", w.getIdApartamento());
        m.put("concepto", w.getConcepto());
        m.put("idCuota", w.getIdCuota());
        m.put("idMulta", w.getIdMulta());
        m.put("montoCentavos", w.getMontoCentavos());
        m.put("estado", estado);
        m.put("idTransaccionWompi", w.getIdTransaccionWompi());
        m.put("metodoPagoWompi", w.getMetodoPagoWompi());
        m.put("fechaCreacion", w.getFechaCreacion() != null ? w.getFechaCreacion().toString() : null);
        m.put("fechaConfirmacion", w.getFechaConfirmacion() != null ? w.getFechaConfirmacion().toString() : null);
        return m;
    }
}
