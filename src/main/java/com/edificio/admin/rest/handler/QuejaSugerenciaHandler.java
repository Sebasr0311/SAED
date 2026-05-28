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

public class QuejaSugerenciaHandler extends BaseHandler implements HttpHandler {

    private final QuejaSugerenciaDAO quejaDAO = new QuejaSugerenciaDAO();

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

            // POST /quejas - Crear queja/sugerencia/apelación (RESIDENTE)
            if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                if (!"RESIDENTE".equals(rol))
                    throw new Exception("Solo residentes pueden crear quejas/sugerencias");
                
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                
                String tipo = (String) data.get("tipo");
                if (tipo == null || (!"QUEJA".equals(tipo) && !"SUGERENCIA".equals(tipo) && !"APELACION".equals(tipo)))
                    throw new Exception("Tipo invalido. Use QUEJA, SUGERENCIA o APELACION");
                
                int idApartamento = obtenerIdApartamento(claims);
                
                QuejaSugerencia q = new QuejaSugerencia();
                q.setIdApartamento(idApartamento);
                q.setTipo(tipo);
                q.setCategoria((String) data.get("categoria"));
                q.setTitulo((String) data.get("titulo"));
                q.setDescripcion((String) data.get("descripcion"));
                q.setFotoEvidencia((String) data.get("fotoEvidencia"));
                q.setCreadoPor(((Number) claims.get("idUsuario")).intValue());
                
                // Si es APELACION, requiere idMulta
                if ("APELACION".equals(tipo)) {
                    Object multaObj = data.get("idMulta");
                    if (multaObj == null) throw new Exception("idMulta requerido para apelaciones");
                    q.setIdMulta(((Number) multaObj).intValue());
                }
                
                Integer id = quejaDAO.insert(q);
                sendJson(exchange, 201, Map.of("idQueja", id, "mensaje", "Queja/sugerencia creada exitosamente"));

            // GET /quejas - Mis quejas (RESIDENTE)
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                if ("RESIDENTE".equals(rol)) {
                    int idApartamento = obtenerIdApartamento(claims);
                    List<QuejaSugerencia> lista = quejaDAO.findByApartamento(idApartamento);
                    List<Map<String, Object>> resp = new ArrayList<>();
                    for (QuejaSugerencia q : lista) resp.add(toMap(q));
                    sendJson(exchange, 200, resp);
                } else {
                    throw new Exception("No autorizado");
                }

            // GET /quejas/todas - Todas las quejas con JOIN residente (ADMINISTRADOR)
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/todas")) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores");
                List<QuejaSugerencia> lista = quejaDAO.findAll();
                List<Map<String, Object>> resp = new ArrayList<>();
                for (QuejaSugerencia q : lista) resp.add(toMap(q));
                sendJson(exchange, 200, resp);

            // GET /quejas/pendientes - Solo pendientes (ADMINISTRADOR)
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/pendientes")) {
                if (!"ADMINISTRADOR".equals(rol)) throw new Exception("Solo administradores");
                List<QuejaSugerencia> lista = quejaDAO.findPendientes();
                List<Map<String, Object>> resp = new ArrayList<>();
                for (QuejaSugerencia q : lista) resp.add(toMap(q));
                sendJson(exchange, 200, resp);

            // GET /quejas/:id - Detalle de una queja
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4) {
                int idQueja = Integer.parseInt(parts[3]);
                QuejaSugerencia q = quejaDAO.findById(idQueja);
                if (q == null) throw new Exception("Queja no encontrada");
                
                // Validar permisos: residente solo ve sus quejas, admin ve todas
                if ("RESIDENTE".equals(rol)) {
                    int idApartamento = obtenerIdApartamento(claims);
                    if (q.getIdApartamento() != idApartamento)
                        throw new Exception("No autorizado para ver esta queja");
                }
                
                sendJson(exchange, 200, toMap(q));

            // PUT /quejas/:id/responder - Responder y marcar RESUELTA (ADMINISTRADOR)
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "responder".equals(parts[4])) {
                if (!"ADMINISTRADOR".equals(rol))
                    throw new Exception("Solo administradores pueden responder");
                
                int idQueja = Integer.parseInt(parts[3]);
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                
                String respuesta = (String) data.get("respuesta");
                if (respuesta == null || respuesta.trim().isEmpty())
                    throw new Exception("Respuesta requerida");
                
                int respondidoPor = ((Number) claims.get("idUsuario")).intValue();
                quejaDAO.responder(idQueja, respuesta, respondidoPor);
                sendJson(exchange, 200, Map.of("mensaje", "Respuesta enviada y queja marcada como resuelta"));

            // PUT /quejas/:id/estado - Cambiar estado manualmente (ADMINISTRADOR)
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "estado".equals(parts[4])) {
                if (!"ADMINISTRADOR".equals(rol))
                    throw new Exception("Solo administradores pueden cambiar estado");
                
                int idQueja = Integer.parseInt(parts[3]);
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                
                String nuevoEstado = (String) data.get("estado");
                if (nuevoEstado == null || (!nuevoEstado.equals("PENDIENTE") && !nuevoEstado.equals("EN_REVISION") 
                    && !nuevoEstado.equals("RESUELTA") && !nuevoEstado.equals("CERRADA")))
                    throw new Exception("Estado invalido. Use PENDIENTE, EN_REVISION, RESUELTA o CERRADA");
                
                quejaDAO.cambiarEstado(idQueja, nuevoEstado);
                sendJson(exchange, 200, Map.of("mensaje", "Estado actualizado"));

            // PUT /quejas/:id/prioridad - Cambiar prioridad (ADMINISTRADOR)
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "prioridad".equals(parts[4])) {
                if (!"ADMINISTRADOR".equals(rol))
                    throw new Exception("Solo administradores pueden cambiar prioridad");
                
                int idQueja = Integer.parseInt(parts[3]);
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                
                String nuevaPrioridad = (String) data.get("prioridad");
                if (nuevaPrioridad == null || (!nuevaPrioridad.equals("BAJA") && !nuevaPrioridad.equals("MEDIA") 
                    && !nuevaPrioridad.equals("ALTA")))
                    throw new Exception("Prioridad invalida. Use BAJA, MEDIA o ALTA");
                
                quejaDAO.cambiarPrioridad(idQueja, nuevaPrioridad);
                sendJson(exchange, 200, Map.of("mensaje", "Prioridad actualizada"));

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
                if (!rs.next()) throw new Exception("No se encontro apartamento activo para el usuario");
                return rs.getInt("id_apartamento");
            }
        } catch (SQLException e) {
            throw new Exception("Error al obtener apartamento del residente: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(QuejaSugerencia q) {
        Map<String, Object> map = new HashMap<>();
        map.put("idQueja", q.getIdQueja());
        map.put("idApartamento", q.getIdApartamento());
        map.put("idMulta", q.getIdMulta());
        map.put("tipo", q.getTipo());
        map.put("categoria", q.getCategoria());
        map.put("titulo", q.getTitulo());
        map.put("descripcion", q.getDescripcion());
        map.put("fotoEvidencia", q.getFotoEvidencia());
        map.put("estado", q.getEstado());
        map.put("respuestaAdmin", q.getRespuestaAdmin());
        map.put("prioridad", q.getPrioridad());
        map.put("fechaCreacion", q.getFechaCreacion() != null ? q.getFechaCreacion().toString() : null);
        map.put("fechaRespuesta", q.getFechaRespuesta() != null ? q.getFechaRespuesta().toString() : null);
        map.put("creadoPor", q.getCreadoPor());
        map.put("respondidoPor", q.getRespondidoPor());
        map.put("numeroApartamento", q.getNumeroApartamento());
        map.put("nombreResidente", q.getNombreResidente());
        map.put("nombreAdmin", q.getNombreAdmin());
        return map;
    }
}
