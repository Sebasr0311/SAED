package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.service.VisitaService;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.TipoVehiculo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

public class VisitaHandler extends BaseHandler implements HttpHandler {

    private final VisitaService visitaService = new VisitaService();
    private final VisitanteDAO visitanteDAO = new VisitanteDAO();
    private final ContratoResidenteDAO contratoResidenteDAO = new ContratoResidenteDAO();
    private final VisitanteFrecuenteDAO frecuenteDAO = new VisitanteFrecuenteDAO();
    private final QRAccesoDAO qrDAO = new QRAccesoDAO();
    private final RegistroAccesoDAO registroAccesoDAO = new RegistroAccesoDAO();
    private final VehiculoVisitaDAO vehiculoVisitaDAO = new VehiculoVisitaDAO();
    private final ParqueaderoDAO parqueaderoDAO = new ParqueaderoDAO();
    private final ResidenteDAO residenteDAO = new ResidenteDAO();
    private final PersonaVisitaDAO personaVisitaDAO = new PersonaVisitaDAO();
    private final BuzonDAO buzonDAO = new BuzonDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 4 && "buscar".equals(parts[3])) {
                // GET /api/visitas/buscar?documento=XXX — busca visitante por cédula
                String query = exchange.getRequestURI().getQuery();
                String doc = query != null ? JsonUtil.extraerValor(query, "documento") : null;
                if (doc != null && !doc.isBlank()) {
                    Visitante vt = visitanteDAO.findByNumeroDocumento(doc);
                    if (vt != null) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", vt.getId());
                        m.put("nombres", vt.getNombres());
                        m.put("apellidos", vt.getApellidos());
                        m.put("numeroDocumento", vt.getNumeroDocumento());
                        m.put("telefono", vt.getTelefono());
                        m.put("email", vt.getEmail());
                        sendJson(exchange, 200, m);
                        return;
                    }
                }
                sendJson(exchange, 200, Map.of());
                return;
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4 && "hoy".equals(parts[3])) {
                // GET /api/visitas/hoy — obtiene todas las visitas de hoy (incluyendo finalizadas)
                VisitaDAO visitaDAO = new VisitaDAO();
                List<Visita> visitasHoy = visitaDAO.findVisitasHoy();
                sendJson(exchange, 200, visitasHoy);
                return;
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 4 && "historial".equals(parts[3])) {
                // GET /api/visitas/historial?fechaInicio=YYYY-MM-DD&fechaFin=YYYY-MM-DD
                String query = exchange.getRequestURI().getQuery();
                String fechaInicio = query != null ? JsonUtil.extraerValor(query, "fechaInicio") : null;
                String fechaFin = query != null ? JsonUtil.extraerValor(query, "fechaFin") : null;
                if (fechaInicio == null || fechaFin == null) {
                    throw new Exception("Los parámetros fechaInicio y fechaFin son obligatorios");
                }
                VisitaDAO visitaDAO = new VisitaDAO();
                List<Visita> visitas = visitaDAO.findByRangoFechas(fechaInicio, fechaFin);
                sendJson(exchange, 200, visitas);
                return;
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 5 && "detalle".equals(parts[4])) {
                // GET /api/visitas/{id}/detalle — devuelve información completa de la visita
                int idVisita = Integer.parseInt(parts[3]);
                Map<String, Object> detalle = obtenerDetalleCompleto(idVisita);
                sendJson(exchange, 200, detalle);
            } else if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                List<Visita> list = visitaService.listarPendientesYActivas();
                sendJson(exchange, 200, list);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> visitanteData = (Map<String, Object>) data.get("visitante");
                int idResidente = ((Number) data.get("idResidente")).intValue();

                Integer idVisitante = findOrCreateVisitante(visitanteData);
                int idContratoRes = obtenerIdContratoResActivo(idResidente);

                Visita v = new Visita();
                v.setIdContratoRes(idContratoRes);
                v.setIdResidente(idResidente);
                v.setTiempoValidezMin(((Number) data.getOrDefault("tiempoValidezMin", 30)).intValue());
                v.setCantidadPersonas(((Number) data.getOrDefault("cantidadPersonas", 1)).intValue());
                v.setNotas((String) data.get("notas"));
                v.setEstado(com.edificio.admin.model.enums.EstadoVisita.PENDIENTE);

                VehiculoVisita vehiculo = null;
                if (data.containsKey("vehiculo")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vehData = (Map<String, Object>) data.get("vehiculo");
                    vehiculo = new VehiculoVisita();
                    vehiculo.setPlaca((String) vehData.get("placa"));
                    if (vehData.get("tipo") != null)
                        vehiculo.setTipo(TipoVehiculo.valueOf((String) vehData.get("tipo")));
                }

                String codigoQR = visitaService.crearVisita(v, idVisitante, vehiculo);
                sendJson(exchange, 201, Map.of("codigoQr", codigoQR, "idVisita", v.getIdVisita(), "idVisitante", idVisitante));

            } else if ("POST".equalsIgnoreCase(method) && parts.length == 4 && "rapida".equals(parts[3])) {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = JsonUtil.fromJson(body, Map.class);
                int idFrecuente = ((Number) data.get("idFrecuente")).intValue();
                int idVisitante = ((Number) data.get("idVisitante")).intValue();
                int idResidente = ((Number) data.get("idResidente")).intValue();
                int idContratoRes = obtenerIdContratoResActivo(idResidente);
                int cantPersonas = data.containsKey("cantidadPersonas") ? ((Number) data.get("cantidadPersonas")).intValue() : 1;
                int tiempoValidez = data.containsKey("tiempoValidezMin") ? ((Number) data.get("tiempoValidezMin")).intValue() : 30;
                // No permitir generar QR si el mismo residente ya tiene uno activo para este visitante
                if (qrDAO.tieneQRActivo(idVisitante, idResidente))
                    throw new Exception("Ya gener\u00f3 un c\u00f3digo QR para esta persona. \u00daselo o espere a que expire.");
                String tipoVehiculo = (String) data.get("tipoVehiculo");
                String placa = (String) data.get("placa");
                String notas = (String) data.get("notas");
                VisitanteFrecuenteDAO.LiberarVisitaResult res = frecuenteDAO.liberarVisita(
                    idVisitante, idContratoRes, idResidente,
                    cantPersonas, tiempoValidez, tipoVehiculo, placa, null, notas);
                sendJson(exchange, 201, Map.of("codigoQr", res.codigoQR, "idVisita", res.idVisita));
            } else if ("PUT".equalsIgnoreCase(method) && parts.length == 5 && "salida".equals(parts[4])) {
                // PUT /api/visitas/{id}/salida — registra la salida de una visita
                int idVisita = Integer.parseInt(parts[3]);
                registroAccesoDAO.registrarSalida(idVisita);
                // Liberar parqueadero asignado
                liberarParqueaderoVisita(idVisita);
                sendJson(exchange, 200, Map.of("mensaje", "Salida registrada exitosamente"));
            } else if ("DELETE".equalsIgnoreCase(method) && parts.length == 4) {
                visitaService.cancelar(Integer.parseInt(parts[3]));
                sendJson(exchange, 200, Map.of("mensaje", "Visita cancelada"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
        }
    }

    private void liberarParqueaderoVisita(int idVisita) {
        try {
            List<VehiculoVisita> vehiculos = vehiculoVisitaDAO.findByVisita(idVisita);
            for (VehiculoVisita vv : vehiculos) {
                if (vv.getIdParqueadero() != null) {
                    Parqueadero parq = parqueaderoDAO.findById(vv.getIdParqueadero());
                    if (parq != null && !parq.isEsVisitante() && "OCUPADO".equals(parq.getEstado().name())) {
                        continue; // no liberar parqueaderos de residentes automaticamente
                    }
                    if (parq != null && "OCUPADO".equals(parq.getEstado().name())) {
                        parq.setEstado(com.edificio.admin.model.enums.EstadoParqueadero.DISPONIBLE);
                        parqueaderoDAO.update(parq);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error liberando parqueadero para visita " + idVisita + ": " + e.getMessage());
        }
    }

    private Integer findOrCreateVisitante(Map<String, Object> data) throws Exception {
        String numDoc = (String) data.get("numeroDocumento");
        if (numDoc == null || numDoc.isBlank())
            throw new Exception("El n\u00famero de documento del visitante es obligatorio");

        Object tipoDocObj = data.get("idTipoDoc");
        if (tipoDocObj != null) {
            int idTipoDoc = ((Number) tipoDocObj).intValue();
            Visitante existente = visitanteDAO.findByDocumento(idTipoDoc, numDoc);
            if (existente != null) return existente.getId();
        } else {
            Visitante existente = visitanteDAO.findByNumeroDocumento(numDoc);
            if (existente != null) return existente.getId();
            throw new Exception("El tipo de documento (idTipoDoc) del visitante es obligatorio para registrar uno nuevo.");
        }

        String nombres   = (String) data.get("nombres");
        String apellidos = (String) data.get("apellidos");
        if (nombres   == null || nombres.isBlank())   throw new Exception("El nombre del visitante es obligatorio.");
        if (apellidos == null || apellidos.isBlank())  throw new Exception("Los apellidos del visitante son obligatorios.");

        Visitante vis = new Visitante();
        vis.setIdTipoDoc(((Number) tipoDocObj).intValue());
        vis.setNumeroDocumento(numDoc);
        vis.setNombres(nombres);
        vis.setApellidos(apellidos);
        vis.setTelefono((String) data.get("telefono"));
        vis.setEmail((String) data.get("email"));
        vis.setActivo(true);
        return visitanteDAO.insert(vis);
    }

    private int obtenerIdContratoResActivo(int idResidente) throws Exception {
        List<ContratoResidente> list = contratoResidenteDAO.findByResidente(idResidente);
        if (list.isEmpty()) throw new Exception("El residente no tiene contratos activos");
        return list.get(0).getIdContratoRes();
    }

    /**
     * Obtiene el detalle completo de una visita incluyendo:
     * - Datos del residente que generó el código
     * - Datos del visitante
     * - Foto capturada (del buzón)
     * - Parqueadero asignado
     * - Hora de ingreso
     * - Apartamento
     */
    private Map<String, Object> obtenerDetalleCompleto(int idVisita) throws Exception {
        VisitaDAO visitaDAO = new VisitaDAO();
        
        // Obtener datos básicos de la visita
        Visita visita = visitaDAO.findById(idVisita);
        if (visita == null) throw new Exception("Visita no encontrada");
        
        Map<String, Object> detalle = new HashMap<>();
        
        // Datos de la visita
        detalle.put("idVisita", visita.getIdVisita());
        detalle.put("fechaRegistro", visita.getFechaRegistro());
        detalle.put("cantidadPersonas", visita.getCantidadPersonas());
        detalle.put("estado", visita.getEstado() != null ? visita.getEstado().name() : null);
        detalle.put("notas", visita.getNotas());
        detalle.put("motivoVisita", visita.getNotas());
        detalle.put("esFrecuente", false);
        
        // Datos del residente
        if (visita.getIdResidente() != null) {
            Residente residente = residenteDAO.findById(visita.getIdResidente());
            if (residente != null) {
                detalle.put("nombreResidente", residente.getNombres() + " " + residente.getApellidos());
            }
        }
        
        // Datos del apartamento
        if (visita.getIdContratoRes() != null) {
            ContratoResidente cr = contratoResidenteDAO.findById(visita.getIdContratoRes());
            if (cr != null) {
                ContratoDAO contratoDAO = new ContratoDAO();
                Contrato contrato = contratoDAO.findById(cr.getIdContrato());
                if (contrato != null && contrato.getIdApartamento() != null) {
                    ApartamentoDAO apartamentoDAO = new ApartamentoDAO();
                    Apartamento apt = apartamentoDAO.findById(contrato.getIdApartamento());
                    if (apt != null) {
                        detalle.put("numeroApartamento", apt.getNumero());
                        detalle.put("piso", apt.getPiso());
                    }
                }
            }
        }
        
        // Datos del visitante (titular) - plano
        List<PersonaVisita> personas = personaVisitaDAO.findByVisita(idVisita);
        for (PersonaVisita pv : personas) {
            if (pv.isEsTitular()) {
                Visitante visitante = visitanteDAO.findById(pv.getIdVisitante());
                if (visitante != null) {
                    detalle.put("nombreVisitante", visitante.getNombres());
                    detalle.put("apellidoVisitante", visitante.getApellidos());
                    detalle.put("documentoVisitante", visitante.getNumeroDocumento());
                    detalle.put("telefonoVisitante", visitante.getTelefono());
                    detalle.put("emailVisitante", visitante.getEmail());
                }
                break;
            }
        }
        
        // Registro de acceso (hora de entrada y parqueadero)
        RegistroAcceso registro = registroAccesoDAO.findByVisita(idVisita);
        if (registro != null) {
            detalle.put("fechaVisita", registro.getHoraEntrada());
            detalle.put("fechaSalida", registro.getHoraSalida());
            
            // Obtener vehículo y parqueadero asignado
            VehiculoVisitaDAO vehiculoDAO = new VehiculoVisitaDAO();
            List<VehiculoVisita> vehiculos = vehiculoDAO.findByVisita(idVisita);
            if (!vehiculos.isEmpty()) {
                VehiculoVisita vehiculo = vehiculos.get(0);
                detalle.put("placaVehiculo", vehiculo.getPlaca());
                detalle.put("tipoVehiculo", vehiculo.getTipo());
                detalle.put("descripcionVehiculo", vehiculo.getDescripcionTipo());
                
                if (vehiculo.getIdParqueadero() != null) {
                    ParqueaderoDAO parqueaderoDAO = new ParqueaderoDAO();
                    Parqueadero parq = parqueaderoDAO.findById(vehiculo.getIdParqueadero());
                    if (parq != null) {
                        detalle.put("codigoParqueadero", parq.getCodigo());
                    }
                }
            }
        }
        
        // Foto capturada (del buzón - mensaje tipo CONFIRMAR_VISITA)
        List<Buzon> mensajes = buzonDAO.findAllByVisita(idVisita);
        for (Buzon msg : mensajes) {
            if (msg.getFotoCaptura() != null) {
                detalle.put("fotoCaptura", msg.getFotoCaptura());
                break;
            }
        }
        
        // Verificar si es visitante frecuente
        if (visita.getIdResidente() != null && detalle.containsKey("documentoVisitante")) {
            try {
                List<VisitanteFrecuente> frecs = frecuenteDAO.findByResidente(visita.getIdResidente());
                String docVisitante = (String) detalle.get("documentoVisitante");
                for (VisitanteFrecuente vf : frecs) {
                    if (docVisitante != null && docVisitante.equals(vf.getDocumento())) {
                        detalle.put("esFrecuente", true);
                        break;
                    }
                }
            } catch (Exception e) {
                // ignorar error al verificar frecuente
            }
        }
        
        return detalle;
    }

}
