package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.*;
import com.edificio.admin.rest.dto.ErrorResponse;
import com.edificio.admin.dao.RegistroAccesoDAO;
import com.edificio.admin.dao.VehiculoVisitaDAO;
import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.model.RegistroAcceso;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoParqueadero;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

public class RegistroAccesoHandler extends BaseHandler implements HttpHandler {

    private final RegistroAccesoDAO dao = new RegistroAccesoDAO();
    private final VehiculoVisitaDAO vehiculoVisitaDAO = new VehiculoVisitaDAO();
    private final ParqueaderoDAO parqueaderoDAO = new ParqueaderoDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> claims = AuthMiddleware.authenticate(exchange);
            if (claims == null) return;

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("GET".equalsIgnoreCase(method) && parts.length == 4 && "activos".equals(parts[3])) {
                List<RegistroAcceso> list = dao.findActivos();
                sendJson(exchange, 200, list);
            } else if ("POST".equalsIgnoreCase(method) && parts.length == 5 && "salida".equals(parts[4])) {
                int idAcceso = Integer.parseInt(parts[3]);
                RegistroAcceso ra = dao.findById(idAcceso);
                if (ra == null) throw new Exception("Registro de acceso no encontrado");
                dao.registrarSalida(ra.getIdVisita());
                liberarParqueaderoVisita(ra.getIdVisita());
                sendJson(exchange, 200, Map.of("mensaje", "Salida registrada"));
            } else {
                sendJson(exchange, 405, new ErrorResponse("Metodo no permitido"));
            }
        } catch (Exception e) {
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
                        continue;
                    }
                    if (parq != null && "OCUPADO".equals(parq.getEstado().name())) {
                        parq.setEstado(EstadoParqueadero.DISPONIBLE);
                        parqueaderoDAO.update(parq);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error liberando parqueadero para visita " + idVisita + ": " + e.getMessage());
        }
    }

}
