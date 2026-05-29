package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.*;
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
                int idApartamento = obtenerIdApartamento(claims);
                List<Buzon> lista = buzonDAO.findPendientesByApartamento(idApartamento);
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/confirmar-pendiente")) {
                int idApartamento = obtenerIdApartamento(claims);
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
                Buzon b = buzonDAO.findByVisitaAndPendiente(idVisita);
                if (b == null) {
                    Buzon existente = buzonDAO.findByVisita(idVisita);
                    if (existente != null)
                        sendJson(exchange, 200, Map.of("confirmado", existente.getConfirmado(), "idMensaje", existente.getIdMensaje()));
                    else
                        sendJson(exchange, 200, Map.of("confirmado", null));
                } else {
                    sendJson(exchange, 200, Map.of("confirmado", null, "idMensaje", b.getIdMensaje()));
                }

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/paquetes-pendientes")) {
                if (!"PORTERO".equals(rol)) throw new Exception("Solo porteros pueden ver paquetes pendientes");
                int count = buzonDAO.countPaquetesPendientes();
                sendJson(exchange, 200, Map.of("count", count));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/paquetes")) {
                if ("PORTERO".equals(rol)) {
                    List<Buzon> lista = buzonDAO.findAllPaquetesPendientes();
                    sendJson(exchange, 200, toMapList(lista));
                } else {
                    List<Buzon> lista = buzonDAO.findAllPaquetes();
                    sendJson(exchange, 200, toMapList(lista));
                }

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/quejas-ruido-pendientes")) {
                if (!"PORTERO".equals(rol)) throw new Exception("Solo porteros pueden ver quejas pendientes");
                List<Buzon> lista = buzonDAO.findQuejasRuidoPendientesHoy();
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/avisos")) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores pueden ver avisos");
                List<Buzon> lista = buzonDAO.findAllAvisos();
                sendJson(exchange, 200, toMapList(lista));

            } else if ("GET".equalsIgnoreCase(method)) {
                String idApartamentoStr = query != null ? JsonUtil.extraerValor(query, "idApartamento") : null;
                int idApartamento;
                if (idApartamentoStr != null) {
                    idApartamento = Integer.parseInt(idApartamentoStr);
                } else if ("RESIDENTE".equals(rol)) {
                    idApartamento = obtenerIdApartamento(claims);
                } else {
                    throw new Exception("idApartamento requerido para este rol");
                }
                List<Buzon> lista = buzonDAO.findByApartamento(idApartamento);
                sendJson(exchange, 200, toMapList(lista));

            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/paquete")) {
                if (!"PORTERO".equals(rol)) throw new Exception("Solo porteros pueden registrar paquetes");
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
                if (!"PORTERO".equals(rol)) throw new Exception("Solo porteros pueden enviar avisos de ruido");
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
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores pueden crear avisos");
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
                if (!"RESIDENTE".equals(rol)) throw new Exception("Solo residentes pueden confirmar visitas");
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                int idMensaje = ((Number) data.get("idMensaje")).intValue();
                int confirmado = ((Number) data.get("confirmado")).intValue();
                buzonDAO.confirmarVisita(idMensaje, confirmado);
                sendJson(exchange, 200, Map.of("mensaje", confirmado == 1 ? "Visita confirmada" : "Visita rechazada"));

            } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/vaciar-multi")) {
                if (!"RESIDENTE".equals(rol)) throw new Exception("Solo residentes pueden vaciar el buzon");
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                @SuppressWarnings("unchecked")
                List<Object> rawIds = (List<Object>) data.get("ids");
                List<Integer> ids = new ArrayList<>();
                for (Object o : rawIds) ids.add(((Number) o).intValue());
                buzonDAO.marcarMultiLeidoYEntregado(ids);
                sendJson(exchange, 200, Map.of("mensaje", "Mensajes eliminados"));

            } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/vaciar")) {
                if (!"RESIDENTE".equals(rol)) throw new Exception("Solo residentes pueden vaciar el buzon");
                int idApartamento = obtenerIdApartamento(claims);
                buzonDAO.marcarTodoLeidoYEntregado(idApartamento);
                sendJson(exchange, 200, Map.of("mensaje", "Buzon vaciado"));

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "leido".equals(parts[4])) {
                buzonDAO.marcarLeido(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Marcado como leido"));

            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "entregado".equals(parts[4])) {
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

    private int obtenerIdApartamento(Map<String, Object> claims) throws Exception {
        int idUsuario = ((Number) claims.get("idUsuario")).intValue();
        String sql = "SELECT a.id_apartamento "
                   + "FROM   USUARIOS u "
                   + "JOIN   RESIDENTES r ON r.id_residente = u.id_residente "
                   + "JOIN   CONTRATO_RESIDENTE cr ON cr.id_residente = r.id_residente "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cr.id_contrato AND c.estado = 'ACTIVO' "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "WHERE  u.id_usuario = ? AND ROWNUM = 1";
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id_apartamento");
            }
        }
        throw new Exception("No se encontro apartamento para el usuario");
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
        m.put("fechaCreacion", b.getFechaCreacion() != null ? b.getFechaCreacion().toString() : null);
        m.put("numeroApartamento", b.getNumeroApartamento());
        m.put("nombreResidente", b.getNombreResidente());
        return m;
    }

}
