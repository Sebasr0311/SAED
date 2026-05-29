package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.ResidenteService;
import com.edificio.admin.dao.*;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.model.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

public class ResidenteHandler extends BaseHandler implements HttpHandler {

    private final ResidenteService service = new ResidenteService();
    private final ContratoResidenteDAO contratoResidenteDAO = new ContratoResidenteDAO();
    private final ContratoDAO contratoDAO = new ContratoDAO();
    private final ApartamentoDAO apartamentoDAO = new ApartamentoDAO();
    private final CuotaArriendoDAO cuotaDAO = new CuotaArriendoDAO();
    private final MultaDAO multaDAO = new MultaDAO();
    private final VisitanteFrecuenteDAO frecuenteDAO = new VisitanteFrecuenteDAO();
    private final QRAccesoDAO qrAccesoDAO = new QRAccesoDAO();
    private final TutorDAO tutorDAO = new TutorDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                String query = exchange.getRequestURI().getQuery();
                String idApartamentoStr = query != null ? JsonUtil.extraerValor(query, "idApartamento") : null;
                String conUsuarioStr = query != null ? JsonUtil.extraerValor(query, "conUsuario") : null;
                
                if ("true".equals(conUsuarioStr)) {
                    // Solo residentes con cuenta de usuario (uno por apartamento)
                    List<Residente> list = service.listarConUsuario();
                    sendJson(exchange, 200, list);
                } else if (idApartamentoStr != null) {
                    // Filtrar por apartamento
                    int idApartamento = Integer.parseInt(idApartamentoStr);
                    List<Residente> list = service.listarTodos();
                    List<Residente> filtered = new ArrayList<>();
                    for (Residente r : list) {
                        if (r.getIdApartamento() != null && r.getIdApartamento() == idApartamento) {
                            filtered.add(r);
                        }
                    }
                    sendJson(exchange, 200, filtered);
                } else {
                    // Devolver todos
                    List<Residente> list = service.listarTodos();
                    sendJson(exchange, 200, list);
                }
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4 && !parts[3].equals("dashboard")) {
                int id = Integer.parseInt(parts[3]);
                Residente r = service.buscarPorId(id);
                if (r != null && r.isEsMenorEdad()) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", r.getId());
                    resp.put("idTipoDoc", r.getIdTipoDoc());
                    resp.put("numeroDocumento", r.getNumeroDocumento());
                    resp.put("nombres", r.getNombres());
                    resp.put("apellidos", r.getApellidos());
                    resp.put("telefono", r.getTelefono());
                    resp.put("email", r.getEmail());
                    resp.put("fechaNacimiento", r.getFechaNacimiento() != null ? r.getFechaNacimiento().toString() : null);
                    resp.put("esMenorEdad", true);
                    resp.put("activo", r.isActivo());
                    resp.put("idApartamento", r.getIdApartamento());
                    resp.put("numeroApartamento", r.getNumeroApartamento());
                    Tutor tutor = tutorDAO.findByResidenteMenor(id);
                    resp.put("tutor", tutor != null ? tutor : null);
                    sendJson(exchange, 200, resp);
                } else {
                    sendJson(exchange, 200, r);
                }
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "dashboard".equals(parts[4])) {
                int idRes = Integer.parseInt(parts[3]);
                Map<String, Object> data = new HashMap<>();
                Residente r = service.buscarPorId(idRes);
                data.put("residente", r);
                List<ContratoResidente> crs = contratoResidenteDAO.findByResidente(idRes);
                for (ContratoResidente cr : crs) {
                    Contrato c = contratoDAO.findById(cr.getIdContrato());
                    if (c != null && c.getEstado().name().equals("ACTIVO")) {
                        data.put("contrato", c);
                        Apartamento a = apartamentoDAO.findById(c.getIdApartamento());
                        data.put("apartamento", a);
                        List<CuotaArriendo> cuotas = cuotaDAO.findByContrato(c.getIdContrato());
                        data.put("cuotas", cuotas);
                        List<Multa> multas = multaDAO.findByApartamento(a.getIdApartamento());
                        data.put("multas", multas);
                        break;
                    }
                }
                if (!data.containsKey("cuotas")) data.put("cuotas", Collections.emptyList());
                if (!data.containsKey("multas")) data.put("multas", Collections.emptyList());
                sendJson(exchange, 200, data);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "qr-activos".equals(parts[4])) {
                int idRes = Integer.parseInt(parts[3]);
                List<Map<String, Object>> qrs = qrAccesoDAO.findActivosByResidente(idRes);
                sendJson(exchange, 200, qrs);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "frecuentes".equals(parts[4])) {
                int idRes = Integer.parseInt(parts[3]);
                List<VisitanteFrecuente> frecs = frecuenteDAO.findByResidente(idRes);
                sendJson(exchange, 200, frecs);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Map<String, Object> requestData = JsonUtil.fromJson(body, Map.class);
                
                // Extraer datos del residente (compatibilidad con y sin wrapper)
                Residente r;
                if (requestData.containsKey("residente")) {
                    String residenteJson = JsonUtil.toJson(requestData.get("residente"));
                    r = JsonUtil.fromJson(residenteJson, Residente.class);
                } else {
                    r = JsonUtil.fromJson(body, Residente.class);
                }
                
                Integer id = service.registrar(r);
                
                // Validar tutor para menores de 16-17 años
                int edad = Period.between(r.getFechaNacimiento(), LocalDate.now()).getYears();
                if (edad >= 16 && edad < 18) {
                    Map<String, Object> tutorData = (Map<String, Object>) requestData.get("tutor");
                    if (tutorData == null || tutorData.isEmpty()) {
                        // Eliminar el residente recién creado si no tiene tutor
                        service.desactivar(id);
                        throw new DatosInvalidosException(
                            "El residente menor de edad (" + edad + " a\u00f1os) debe tener un tutor legal registrado."
                        );
                    }
                    String tutorJson = JsonUtil.toJson(tutorData);
                    Tutor t = JsonUtil.fromJson(tutorJson, Tutor.class);
                    t.setIdResidenteMenor(id);
                    tutorDAO.insert(t);
                }
                
                sendJson(exchange, 201, Map.of("id", id));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "perfil".equals(parts[4])) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, String> data = JsonUtil.fromJson(body, Map.class);
                int idRes = Integer.parseInt(parts[3]);
                Residente r = service.buscarPorId(idRes);
                if (data.containsKey("telefono")) r.setTelefono(data.get("telefono"));
                if (data.containsKey("email")) r.setEmail(data.get("email"));
                service.actualizar(r);
                sendJson(exchange, 200, Map.of("mensaje", "Perfil actualizado"));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 4) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Map<String, Object> requestData = JsonUtil.fromJson(body, Map.class);
                
                Residente r;
                if (requestData.containsKey("residente")) {
                    String residenteJson = JsonUtil.toJson(requestData.get("residente"));
                    r = JsonUtil.fromJson(residenteJson, Residente.class);
                } else {
                    r = JsonUtil.fromJson(body, Residente.class);
                }
                r.setId(Integer.parseInt(parts[3]));
                service.actualizar(r);
                
                // Validar tutor para menores de 16-17 años al actualizar
                int edad = Period.between(r.getFechaNacimiento(), LocalDate.now()).getYears();
                if (edad >= 16 && edad < 18) {
                    Tutor tutorExistente = tutorDAO.findByResidenteMenor(r.getId());
                    Map<String, Object> tutorData = (Map<String, Object>) requestData.get("tutor");
                    
                    if (tutorExistente == null && (tutorData == null || tutorData.isEmpty())) {
                        throw new DatosInvalidosException(
                            "El residente menor de edad (" + edad + " a\u00f1os) debe tener un tutor legal registrado."
                        );
                    }
                    
                    if (tutorData != null && !tutorData.isEmpty()) {
                        String tutorJson = JsonUtil.toJson(tutorData);
                        Tutor t = JsonUtil.fromJson(tutorJson, Tutor.class);
                        t.setIdResidenteMenor(r.getId());
                        if (tutorExistente != null) {
                            t.setIdTutor(tutorExistente.getIdTutor());
                            tutorDAO.update(t);
                        } else {
                            tutorDAO.insert(t);
                        }
                    }
                }
                
                sendJson(exchange, 200, Map.of("mensaje", "Residente actualizado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 6 && "frecuentes".equals(parts[4])) {
                int idFrec = Integer.parseInt(parts[5]);
                frecuenteDAO.ocultarFrecuente(idFrec);
                sendJson(exchange, 200, Map.of("mensaje", "Frecuente ocultado"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                service.desactivar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Residente desactivado"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

}
