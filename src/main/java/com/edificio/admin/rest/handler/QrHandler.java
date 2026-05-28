package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoParqueadero;
import com.edificio.admin.model.enums.TipoVehiculo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class QrHandler extends BaseHandler implements HttpHandler {

    private final QRAccesoDAO qrDAO = new QRAccesoDAO();
    private final RegistroAccesoDAO registroDAO = new RegistroAccesoDAO();
    private final BuzonDAO buzonDAO = new BuzonDAO();
    private final ParqueaderoDAO parqDAO = new ParqueaderoDAO();
    private final VehiculoVisitaDAO vehiculoDAO = new VehiculoVisitaDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("POST".equalsIgnoreCase(method) && parts.length == 4 && "validar".equals(parts[3])) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String codigoQr = (String) data.get("codigoQr");
                QRAcceso qr = qrDAO.findByCodigo(codigoQr);
                if (qr == null) throw new Exception("QR no encontrado");
                if (qr.isUsado()) throw new Exception("QR ya fue usado");
                if (qr.getFechaExpiracion() != null && qr.getFechaExpiracion().isBefore(LocalDateTime.now()))
                    throw new Exception("QR expirado");

                Map<String, Object> res = qrDAO.findValidationData(codigoQr);
                if (res == null) throw new Exception("QR inv\u00e1lido: datos incompletos de la visita");
                sendJson(exchange, 200, res);

            } else if ("POST".equalsIgnoreCase(method) && parts.length == 4 && "notificar".equals(parts[3])) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String codigoQr = (String) data.get("codigoQr");
                String fotoCaptura = (String) data.get("fotoCaptura");

                QRAcceso qr = qrDAO.findByCodigo(codigoQr);
                if (qr == null) throw new Exception("QR no encontrado");
                if (qr.isUsado()) throw new Exception("QR ya fue usado");

                Map<String, Object> valData = qrDAO.findValidationData(codigoQr);
                if (valData == null) throw new Exception("Datos de visita no encontrados");

                int idVigilante = ((Number) claims.get("idUsuario")).intValue();
                int idApartamento = ((Number) valData.get("idApartamento")).intValue();
                String nombreVisitante = (String) valData.get("nombreVisitante");

                Buzon b = new Buzon();
                b.setIdApartamento(idApartamento);
                b.setIdVisita(qr.getIdVisita());
                b.setTipo("CONFIRMAR_VISITA");
                b.setTitulo("Solicitud de acceso - Visitante en porter\u00eda");
                b.setCuerpo("El visitante " + (nombreVisitante != null ? nombreVisitante : "") + " se encuentra en porter\u00eda esperando confirmaci\u00f3n.");
                b.setFotoCaptura(fotoCaptura);
                b.setCreadoPor(idVigilante);
                Integer idMensaje = buzonDAO.insert(b);

                sendJson(exchange, 200, Map.of("idMensaje", idMensaje, "idVisita", qr.getIdVisita()));

            } else if ("POST".equalsIgnoreCase(method) && parts.length == 4 && "entrada".equals(parts[3])) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String codigoQr = (String) data.get("codigoQr");
                String medioTransporte = (String) data.get("medioTransporte");
                String placa = (String) data.get("placa");
                String descripcion = (String) data.get("descripcion");

                QRAcceso qr = qrDAO.findByCodigo(codigoQr);
                if (qr == null) throw new Exception("QR no encontrado");

                // Verificar si ya existe un registro de acceso activo para esta visita
                if (qr.getIdVisita() != null) {
                    RegistroAcceso existente = registroDAO.findByVisita(qr.getIdVisita());
                    if (existente != null && existente.getHoraSalida() == null) {
                        throw new Exception("Esta visita ya tiene un registro de entrada activo. No se puede registrar nuevamente.");
                    }
                }

                int idVigilante = ((Number) claims.get("idUsuario")).intValue();
                boolean marcado = qrDAO.marcarUsado(qr.getIdQr(), idVigilante);
                if (!marcado) throw new Exception("El código QR ya fue usado anteriormente. Si necesita registrar la entrada, contacte al administrador.");

                RegistroAcceso ra = new RegistroAcceso();
                ra.setIdVisita(qr.getIdVisita());
                ra.setIdVigilante(idVigilante);
                ra.setHoraEntrada(LocalDateTime.now());
                Integer id = registroDAO.insert(ra);

                String parqAsignado = null;
                if ("CARRO".equals(medioTransporte) || "MOTO".equals(medioTransporte)) {
                    TipoVehiculo tv = "CARRO".equals(medioTransporte) ? TipoVehiculo.VEHICULO : TipoVehiculo.MOTO;

                    List<Parqueadero> disponibles = parqDAO.findDisponiblesParaVisitantes();
                    Parqueadero parq = null;
                    for (Parqueadero p : disponibles) {
                        if ("MOTO".equals(medioTransporte) && "MOTO".equals(p.getTipo().name())) {
                            parq = p; break;
                        } else if ("CARRO".equals(medioTransporte) && "VEHICULO".equals(p.getTipo().name())) {
                            parq = p; break;
                        }
                    }
                    if (parq == null && !disponibles.isEmpty()) {
                        parq = disponibles.get(0);
                    }

                    if (parq != null) {
                        parq.setEstado(EstadoParqueadero.OCUPADO);
                        parqDAO.update(parq);
                        parqAsignado = parq.getCodigo();

                        List<VehiculoVisita> existentes = vehiculoDAO.findByVisita(qr.getIdVisita());
                        VehiculoVisita vv;
                        if (!existentes.isEmpty()) {
                            vv = existentes.get(0);
                            vv.setPlaca(placa != null ? placa : "");
                            vv.setTipo(tv);
                            vv.setIdParqueadero(parq.getIdParqueadero());
                            vehiculoDAO.update(vv);
                        } else {
                            vv = new VehiculoVisita();
                            vv.setIdVisita(qr.getIdVisita());
                            vv.setPlaca(placa != null ? placa : "");
                            vv.setTipo(tv);
                            vv.setIdParqueadero(parq.getIdParqueadero());
                            vehiculoDAO.insert(vv);
                        }
                    }
                } else if ("OTRO".equals(medioTransporte)) {
                    List<VehiculoVisita> existentes = vehiculoDAO.findByVisita(qr.getIdVisita());
                    if (existentes.isEmpty()) {
                        VehiculoVisita vv = new VehiculoVisita();
                        vv.setIdVisita(qr.getIdVisita());
                        vv.setTipo(TipoVehiculo.OTRO);
                        vv.setDescripcionTipo(descripcion);
                        vehiculoDAO.insert(vv);
                    }
                }

                Map<String, Object> resp = new HashMap<>();
                resp.put("idAcceso", id);
                resp.put("mensaje", "Entrada registrada");
                if (parqAsignado != null) resp.put("parqueadero", parqAsignado);
                sendJson(exchange, 200, resp);
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
