package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoMulta;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class MultaHandler extends BaseHandler implements HttpHandler {

    private static final BigDecimal MONTO_RUIDO = new BigDecimal("100000");
    private static final BigDecimal MONTO_PARQUEADERO = new BigDecimal("50000");

    private final MultaDAO multaDAO = new MultaDAO();
    private final BuzonDAO buzonDAO = new BuzonDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            String query = exchange.getRequestURI().getQuery();
            String rol = (String) claims.get("rol");

            if ("POST".equalsIgnoreCase(method) && path.endsWith("/generar")) {
                if (!"PORTERO".equals(rol) && !"ADMINISTRADOR".equals(rol))
                    throw new Exception("Solo porteros y administradores pueden generar multas");
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String tipo = (String) data.get("tipo");
                if (tipo == null || (!"RUIDO".equals(tipo) && !"PARQUEADERO".equals(tipo)))
                    throw new Exception("Tipo de multa invalido. Use RUIDO o PARQUEADERO");
                Object aptObj = data.get("idApartamento");
                if (aptObj == null) throw new Exception("idApartamento requerido");
                BigDecimal monto = "RUIDO".equals(tipo) ? MONTO_RUIDO : MONTO_PARQUEADERO;
                Multa m = new Multa();
                m.setIdApartamento(((Number) aptObj).intValue());
                m.setTipo(tipo);
                m.setMonto(monto);
                m.setEstado(EstadoMulta.PENDIENTE);
                m.setDescripcion((String) data.get("descripcion"));
                Object msgObj = data.get("idMensaje");
                if (msgObj != null) m.setIdMensaje(((Number) msgObj).intValue());

                // Validar que hayan pasado al menos 20 minutos desde el aviso de ruido
                if ("RUIDO".equals(tipo) && m.getIdMensaje() != null) {
                    Buzon aviso = buzonDAO.findById(m.getIdMensaje());
                    if (aviso != null && aviso.getFechaCreacion() != null) {
                        long minutosTranscurridos = java.time.Duration.between(aviso.getFechaCreacion(), java.time.LocalDateTime.now()).toMinutes();
                        if (minutosTranscurridos < 20) {
                            throw new Exception(
                                "Deben pasar al menos 20 minutos desde el aviso de ruido para generar la multa. " +
                                "Han transcurrido " + minutosTranscurridos + " minuto(s)."
                            );
                        }
                    }
                }
                m.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                Object fotoObj = data.get("fotoEvidencia");
                if (fotoObj instanceof String && !((String) fotoObj).isEmpty())
                    m.setFotoEvidencia((String) fotoObj);
                Integer id = multaDAO.insert(m);
                sendJson(exchange, 201, Map.of("idMulta", id, "monto", monto));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/todas")) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores");
                List<Multa> multas = multaDAO.findAllConResidente();
                List<Map<String, Object>> resp = new ArrayList<>();
                for (Multa m : multas) resp.add(toMap(m));
                sendJson(exchange, 200, resp);

            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                // Obtener detalle de una multa por ID
                int idMulta = Integer.parseInt(parts[3]);
                Multa multa = multaDAO.findById(idMulta);
                if (multa == null) throw new Exception("Multa no encontrada");
                sendJson(exchange, 200, toMapDetalle(multa));

            } else if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                String aptStr = query != null ? JsonUtil.extraerValor(query, "apartamento") : null;
                if (aptStr == null) throw new Exception("apartamento requerido");
                int idApartamento = Integer.parseInt(aptStr);
                List<Multa> multas = multaDAO.findByApartamento(idApartamento);
                List<Map<String, Object>> resp = new ArrayList<>();
                for (Multa m : multas) resp.add(toMap(m));
                sendJson(exchange, 200, resp);

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "pagar".equals(parts[4])) {
                if (!"RESIDENTE".equals(rol) && !"ADMINISTRADOR".equals(rol))
                    throw new Exception("No autorizado");
                int idMulta = Integer.parseInt(parts[3]);
                String metodoPago = null;
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                if (bodyBytes.length > 0) {
                    String body = new String(bodyBytes, "UTF-8");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                    if (data != null) {
                        metodoPago = (String) data.get("metodoPago");
                    }
                }
                Integer registradoPagoPor = ((Number) claims.get("idUsuario")).intValue();
                multaDAO.pagar(idMulta, registradoPagoPor, metodoPago);
                sendJson(exchange, 200, Map.of("mensaje", "Multa pagada"));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/notificar-todas")) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores");
                List<Multa> pendientes = multaDAO.findAllConResidente();
                int contador = 0;
                for (Multa m : pendientes) {
                    if (!"PENDIENTE".equals(m.getEstado().name())) continue;
                    Buzon b = new Buzon();
                    b.setIdApartamento(m.getIdApartamento());
                    b.setTipo("AVISO");
                    b.setTitulo("Multa pendiente - " + ("RUIDO".equals(m.getTipo()) ? "Ruido" : "Parqueadero"));
                    b.setCuerpo("Tiene una multa por $" + m.getMonto() + " COP (" + m.getFechaCreacion().toLocalDate() + "). Por favor paguela a la brevedad.");
                    b.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                    buzonDAO.insert(b);
                    contador++;
                }
                sendJson(exchange, 200, Map.of("mensaje", "Notificaciones enviadas a " + contador + " apartamento(s)"));

            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "notificar".equals(parts[4])) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores");
                int idMulta = Integer.parseInt(parts[3]);
                List<Multa> todas = multaDAO.findAllConResidente();
                Multa multa = null;
                for (Multa m : todas) { if (m.getIdMulta() == idMulta) { multa = m; break; } }
                if (multa == null) throw new Exception("Multa no encontrada");
                if (!"PENDIENTE".equals(multa.getEstado().name())) throw new Exception("La multa no esta pendiente");
                Buzon b = new Buzon();
                b.setIdApartamento(multa.getIdApartamento());
                b.setTipo("AVISO");
                b.setTitulo("Multa pendiente - " + ("RUIDO".equals(multa.getTipo()) ? "Ruido" : "Parqueadero"));
                b.setCuerpo("Tiene una multa por $" + multa.getMonto() + " COP (" + multa.getFechaCreacion().toLocalDate() + "). Por favor paguela a la brevedad.");
                b.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                buzonDAO.insert(b);
                sendJson(exchange, 200, Map.of("mensaje", "Notificacion enviada"));

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "anular".equals(parts[4])) {
                if (!"ADMINISTRADOR".equals(rol))
                    throw new Exception("Solo administradores pueden anular multas");
                multaDAO.anular(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Multa anulada"));

            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Multa m) {
        Map<String, Object> map = new HashMap<>();
        map.put("idMulta", m.getIdMulta());
        map.put("idApartamento", m.getIdApartamento());
        map.put("idMensaje", m.getIdMensaje());
        map.put("tipo", m.getTipo());
        map.put("monto", m.getMonto());
        map.put("estado", m.getEstado().name());
        map.put("descripcion", m.getDescripcion());
        map.put("fechaCreacion", m.getFechaCreacion() != null ? m.getFechaCreacion().toString() : null);
        map.put("fechaPago", m.getFechaPago() != null ? m.getFechaPago().toString() : null);
        map.put("numeroApartamento", m.getNumeroApartamento());
        map.put("nombreResidente", m.getNombreResidente());
        map.put("registradoPagoPor", m.getRegistradoPagoPor());
        map.put("metodoPago", m.getMetodoPago());
        return map;
    }

    private Map<String, Object> toMapDetalle(Multa m) {
        Map<String, Object> map = toMap(m);
        map.put("nombrePortero", m.getNombrePortero());
        map.put("fechaAvisoRuido", m.getFechaAvisoRuido() != null ? m.getFechaAvisoRuido().toString() : null);
        map.put("fotoEvidencia", m.getFotoEvidencia());
        return map;
    }
}
