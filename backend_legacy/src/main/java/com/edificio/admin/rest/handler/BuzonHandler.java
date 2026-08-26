package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BuzonHandler extends BaseHandler implements HttpHandler {

    private final BuzonDAO buzonDAO = new BuzonDAO();
    private final ContratoDAO contratoDAO = new ContratoDAO();
    private final ContratoResidenteDAO contratoResidenteDAO = new ContratoResidenteDAO();

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

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/pendientes")) {
                Connection conn = ConexionBD.getInstancia().getConexion();
                int idApartamento = AuthScope.idApartamento(conn, claims);
                if (idApartamento <= 0) throw new Exception("No se encontro apartamento para el usuario");
                List<Buzon> lista = buzonDAO.findPendientesByApartamento(idApartamento);
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/confirmar-pendiente")) {
                Connection conn = ConexionBD.getInstancia().getConexion();
                int idApartamento = AuthScope.idApartamento(conn, claims);
                if (idApartamento <= 0) throw new Exception("No se encontro apartamento para el usuario");
                List<Buzon> lista = buzonDAO.findPendientesByApartamento(idApartamento);
                List<Map<String, Object>> res = new ArrayList<>();
                for (Buzon b : lista) {
                    if ("CONFIRMAR_VISITA".equals(b.getTipo()) && b.getConfirmado() == null)
                        res.add(toMap(b));
                }
                sendJson(exchange, 200, res);

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/resultado-notificar")) {
                String idVisitaStr = query != null ? JsonUtil.extraerValor(query, "idVisita") : null;
                if (idVisitaStr == null) throw new Exception("idVisita requerido");
                int idVisita = Integer.parseInt(idVisitaStr);
                if ("RESIDENTE".equals(rol)) {
                    Connection conn = ConexionBD.getInstancia().getConexion();
                    int idResidente = AuthScope.idResidente(conn, claims);
                    Visita visita = new VisitaDAO().findById(idVisita);
                    if (visita == null || visita.getIdResidente() == null || visita.getIdResidente() != idResidente) {
                        AuthScope.sendForbidden(exchange, "No autorizado para consultar esta visita");
                        return;
                    }
                }
                Buzon b = buzonDAO.findByVisitaAndPendiente(idVisita);
                if (b == null) {
                    Buzon existente = buzonDAO.findByVisita(idVisita);
                    if (existente != null) {
                        // Map.of no admite null — usar HashMap para permitir confirmado=null
                        Map<String, Object> r = new HashMap<>();
                        r.put("confirmado", existente.getConfirmado());
                        r.put("idMensaje", existente.getIdMensaje());
                        sendJson(exchange, 200, r);
                    } else {
                        Map<String, Object> r = new HashMap<>();
                        r.put("confirmado", null);
                        sendJson(exchange, 200, r);
                    }
                } else {
                    Map<String, Object> r = new HashMap<>();
                    r.put("confirmado", null);
                    r.put("idMensaje", b.getIdMensaje());
                    sendJson(exchange, 200, r);
                }

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/paquetes-pendientes")) {
                if (!AuthScope.requireRole(exchange, claims, "PORTERO")) return;
                int count = buzonDAO.countPaquetesPendientes();
                sendJson(exchange, 200, Map.of("count", count));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/paquetes")) {
                if ("PORTERO".equals(rol)) {
                    List<Buzon> lista = buzonDAO.findAllPaquetesPendientes();
                    sendJson(exchange, 200, toMapList(lista));
                } else if ("ADMINISTRADOR".equals(rol)) {
                    List<Buzon> lista = buzonDAO.findAllPaquetes();
                    sendJson(exchange, 200, toMapList(lista));
                } else if ("RESIDENTE".equals(rol)) {
                    Connection conn = ConexionBD.getInstancia().getConexion();
                    int idApartamento = AuthScope.idApartamento(conn, claims);
                    if (idApartamento <= 0) {
                        AuthScope.sendForbidden(exchange, "El usuario no tiene un apartamento asociado");
                        return;
                    }
                    List<Buzon> lista = buzonDAO.findByApartamento(idApartamento);
                    List<Buzon> paquetes = new ArrayList<>();
                    for (Buzon b : lista) {
                        if ("PAQUETE".equals(b.getTipo())) paquetes.add(b);
                    }
                    sendJson(exchange, 200, toMapList(paquetes));
                } else {
                    AuthScope.sendForbidden(exchange, "No autorizado");
                    return;
                }

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/quejas-ruido-pendientes")) {
                if (!AuthScope.requireRole(exchange, claims, "PORTERO")) return;
                List<Buzon> lista = buzonDAO.findQuejasRuidoPendientesHoy();
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/avisos")) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                List<Buzon> lista = buzonDAO.findAllAvisos();
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method)) {
                int idApartamento;
                if ("RESIDENTE".equals(rol)) {
                    Connection conn = ConexionBD.getInstancia().getConexion();
                    idApartamento = AuthScope.idApartamento(conn, claims);
                    if (idApartamento <= 0) {
                        AuthScope.sendForbidden(exchange, "El usuario no tiene un apartamento asociado");
                        return;
                    }
                } else {
                    String idApartamentoStr = query != null ? JsonUtil.extraerValor(query, "idApartamento") : null;
                    if (idApartamentoStr == null) throw new Exception("idApartamento requerido para este rol");
                    idApartamento = Integer.parseInt(idApartamentoStr);
                }
                List<Buzon> lista = buzonDAO.findByApartamento(idApartamento);
                sendJson(exchange, 200, toMapList(lista));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/paquete")) {
                if (!AuthScope.requireRole(exchange, claims, "PORTERO")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                Buzon b = new Buzon();
                Object aptId = data.get("idApartamento");
                if (aptId == null) throw new Exception("idApartamento requerido");
                int idApartamento = ((Number) aptId).intValue();
                Contrato cActivo = contratoDAO.findActivoByApartamento(idApartamento);
                if (cActivo == null)
                    throw new Exception("El apartamento no tiene un contrato activo. No se pueden registrar paquetes.");
                List<ContratoResidente> residentes = contratoResidenteDAO.findByContrato(cActivo.getIdContrato());
                if (residentes == null || residentes.isEmpty())
                    throw new Exception("El apartamento no tiene residentes asignados. No se pueden registrar paquetes.");
                b.setIdApartamento(idApartamento);
                b.setTipo("PAQUETE");
                b.setTitulo((String) data.get("titulo"));
                b.setCuerpo((String) data.get("cuerpo"));
                b.setFotoCaptura((String) data.get("fotoCaptura"));
                b.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                Integer id = buzonDAO.insert(b);
                sendJson(exchange, 201, Map.of("idMensaje", id));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/aviso-ruido")) {
                if (!AuthScope.requireRole(exchange, claims, "PORTERO")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                Buzon b = new Buzon();
                Object aptId = data.get("idApartamento");
                if (aptId == null) throw new Exception("idApartamento requerido");
                int idApartamento = ((Number) aptId).intValue();
                Contrato cActivo = contratoDAO.findActivoByApartamento(idApartamento);
                if (cActivo == null)
                    throw new Exception("El apartamento no tiene un contrato activo. No se pueden enviar avisos de ruido.");
                List<ContratoResidente> residentes = contratoResidenteDAO.findByContrato(cActivo.getIdContrato());
                if (residentes == null || residentes.isEmpty())
                    throw new Exception("El apartamento no tiene residentes asignados. No se pueden enviar avisos de ruido.");
                b.setIdApartamento(idApartamento);
                b.setTipo("QUEJA_RUIDO");
                b.setTitulo("Aviso de Ruido");
                b.setCuerpo((String) data.get("cuerpo"));
                b.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                Integer id = buzonDAO.insert(b);
                sendJson(exchange, 201, Map.of("idMensaje", id));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/aviso")) {
                if (!AuthScope.requireRole(exchange, claims, "ADMINISTRADOR")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                String titulo = (String) data.get("titulo");
                String cuerpo = (String) data.get("cuerpo");
                int creadoPor = ((Number) claims.get("idUsuario")).intValue();
                @SuppressWarnings("unchecked")
                List<Object> aptIds = (List<Object>) data.get("idApartamentos");
                if (aptIds != null && !aptIds.isEmpty()) {
                    for (Object obj : aptIds) {
                        Buzon b = new Buzon();
                        b.setIdApartamento(((Number) obj).intValue());
                        b.setTipo("AVISO");
                        b.setTitulo(titulo);
                        b.setCuerpo(cuerpo);
                        b.setCreadoPor(creadoPor);
                        buzonDAO.insert(b);
                    }
                } else {
                    Buzon b = new Buzon();
                    Object aptObj = data.get("idApartamento");
                    if (aptObj != null) b.setIdApartamento(((Number) aptObj).intValue());
                    b.setTipo("AVISO");
                    b.setTitulo(titulo);
                    b.setCuerpo(cuerpo);
                    b.setCreadoPor(creadoPor);
                    buzonDAO.insert(b);
                }
                sendJson(exchange, 201, Map.of("mensaje", "Aviso(s) enviado(s)"));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/confirmar")) {
                if (!AuthScope.requireRole(exchange, claims, "RESIDENTE")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                int idMensaje = ((Number) data.get("idMensaje")).intValue();
                int confirmado = ((Number) data.get("confirmado")).intValue();
                Connection conn = ConexionBD.getInstancia().getConexion();
                Buzon msg = buzonDAO.findById(idMensaje);
                int idApartamento = AuthScope.idApartamento(conn, claims);
                if (msg == null || msg.getIdApartamento() == null || msg.getIdApartamento() != idApartamento) {
                    AuthScope.sendForbidden(exchange, "No autorizado para confirmar este mensaje");
                    return;
                }
                buzonDAO.confirmarVisita(idMensaje, confirmado);
                sendJson(exchange, 200, Map.of("mensaje", confirmado == 1 ? "Visita confirmada" : "Visita rechazada"));

            } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/vaciar-multi")) {
                if (!AuthScope.requireRole(exchange, claims, "RESIDENTE")) return;
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                @SuppressWarnings("unchecked")
                List<Object> rawIds = (List<Object>) data.get("ids");
                List<Integer> ids = new ArrayList<>();
                for (Object o : rawIds) ids.add(((Number) o).intValue());
                Connection conn = ConexionBD.getInstancia().getConexion();
                int idApartamento = AuthScope.idApartamento(conn, claims);
                for (int id : ids) {
                    Buzon msg = buzonDAO.findById(id);
                    if (msg == null || msg.getIdApartamento() == null || msg.getIdApartamento() != idApartamento) {
                        AuthScope.sendForbidden(exchange, "No autorizado para vaciar uno de los mensajes");
                        return;
                    }
                }
                buzonDAO.marcarMultiLeidoYEntregado(ids);
                sendJson(exchange, 200, Map.of("mensaje", "Mensajes eliminados"));

            } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/vaciar")) {
                if (!AuthScope.requireRole(exchange, claims, "RESIDENTE")) return;
                Connection conn = ConexionBD.getInstancia().getConexion();
                int idApartamento = AuthScope.idApartamento(conn, claims);
                if (idApartamento <= 0) throw new Exception("No se encontro apartamento para el usuario");
                buzonDAO.marcarTodoLeidoYEntregado(idApartamento);
                sendJson(exchange, 200, Map.of("mensaje", "Buzon vaciado"));

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "leido".equals(parts[4])) {
                int idMensaje = Integer.parseInt(parts[3]);
                if ("RESIDENTE".equals(rol)) {
                    Connection conn = ConexionBD.getInstancia().getConexion();
                    Buzon msg = buzonDAO.findById(idMensaje);
                    int idApartamento = AuthScope.idApartamento(conn, claims);
                    if (msg == null || msg.getIdApartamento() == null || msg.getIdApartamento() != idApartamento) {
                        AuthScope.sendForbidden(exchange, "No autorizado para marcar este mensaje");
                        return;
                    }
                } else if ("PORTERO".equals(rol)) {
                    AuthScope.sendForbidden(exchange, "Los porteros no pueden marcar mensajes como leidos");
                    return;
                } else if (!"ADMINISTRADOR".equals(rol)) {
                    AuthScope.sendForbidden(exchange, "No autorizado");
                    return;
                }
                buzonDAO.marcarLeido(idMensaje);
                sendJson(exchange, 200, Map.of("mensaje", "Marcado como leido"));

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "entregado".equals(parts[4])) {
                if ("RESIDENTE".equals(rol)) {
                    AuthScope.sendForbidden(exchange, "Los residentes no pueden marcar mensajes como entregados");
                    return;
                }
                buzonDAO.marcarEntregado(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Marcado como entregado"));

            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

    private List<Map<String, Object>> toMapList(List<Buzon> lista) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Buzon b : lista) res.add(toMap(b));
        return res;
    }

    private Map<String, Object> toMap(Buzon b) {
        Map<String, Object> m = new HashMap<>();
        m.put("idMensaje", b.getIdMensaje());
        m.put("idApartamento", b.getIdApartamento());
        m.put("idVisita", b.getIdVisita());
        m.put("tipo", b.getTipo());
        m.put("titulo", b.getTitulo());
        m.put("cuerpo", b.getCuerpo());
        m.put("fotoCaptura", b.getFotoCaptura());
        m.put("empresaMensajeria", b.getEmpresaMensajeria());
        m.put("numeroGuia", b.getNumeroGuia());
        m.put("leido", b.isLeido());
        m.put("entregado", b.isEntregado());
        m.put("confirmado", b.getConfirmado());
        m.put("fechaCreacion", b.getFechaCreacion() != null ? b.getFechaCreacion().atZone(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        m.put("numeroApartamento", b.getNumeroApartamento());
        m.put("nombreResidente", b.getNombreResidente());
        return m;
    }

}
