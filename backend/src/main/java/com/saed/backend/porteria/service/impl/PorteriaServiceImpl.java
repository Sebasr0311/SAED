package com.saed.backend.porteria.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.repository.PorteriaRepository;
import com.saed.backend.porteria.service.PorteriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import com.saed.backend.context.SaedContextHolder;

@Service
@Transactional
public class PorteriaServiceImpl implements PorteriaService {

    private static final Logger log = LoggerFactory.getLogger(PorteriaServiceImpl.class);

    private final PorteriaRepository porteriaRepository;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PorteriaServiceImpl(PorteriaRepository porteriaRepository, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.porteriaRepository = porteriaRepository;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- ADMIN CRUD PORTERÍAS ---
    @Override
    @Transactional(readOnly = true)
    public List<PorteriaDTO> listarPorterias() {
        Long orgId = SaedContextHolder.getContext().getOrganizationId();
        Long propId = SaedContextHolder.getContext().getPropertyId();
        return jdbcTemplate.query(
            "SELECT ID_PORTERIA, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO FROM PORTERIAS " +
            "WHERE ID_PROPIEDAD = :propId ORDER BY NOMBRE",
            Map.of("propId", propId),
            (rs, rowNum) -> new PorteriaDTO(
                rs.getLong("ID_PORTERIA"), rs.getString("NOMBRE"),
                rs.getString("UBICACION"), rs.getString("TELEFONO_CONTACTO"),
                rs.getString("ESTADO")
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PorteriaDTO getPorteriaById(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT ID_PORTERIA, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO FROM PORTERIAS WHERE ID_PORTERIA = :id",
            Map.of("id", id),
            (rs, rowNum) -> new PorteriaDTO(
                rs.getLong("ID_PORTERIA"), rs.getString("NOMBRE"),
                rs.getString("UBICACION"), rs.getString("TELEFONO_CONTACTO"),
                rs.getString("ESTADO")
            )
        );
    }

    @Override
    public PorteriaDTO crearPorteria(PorteriaCreateDTO request) {
        Long propId = SaedContextHolder.getContext().getPropertyId();
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, TELEFONO_CONTACTO) VALUES (:propId, :nombre, :ubicacion, :tel)",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("propId", propId)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto()),
            keyHolder
        );
        Number id = keyHolder.getKey();
        return getPorteriaById(id.longValue());
    }

    @Override
    public PorteriaDTO actualizarPorteria(Long id, PorteriaCreateDTO request) {
        jdbcTemplate.update(
            "UPDATE PORTERIAS SET NOMBRE = :nombre, UBICACION = :ubicacion, TELEFONO_CONTACTO = :tel WHERE ID_PORTERIA = :id",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.nombre())
                .addValue("ubicacion", request.ubicacion())
                .addValue("tel", request.telefonoContacto())
        );
        return getPorteriaById(id);
    }

    @Override
    public void eliminarPorteria(Long id) {
        jdbcTemplate.update("DELETE FROM PORTERIAS WHERE ID_PORTERIA = :id", Map.of("id", id));
    }

    // --- OPERACIONES PORTERO ---
    @Override
    public VisitaDTO programarVisita(VisitaRequestDTO request) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(request.unidadId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para programar visitas en otra unidad");
            }
        }
        return porteriaRepository.createVisita(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitaDTO getVisitaById(Long id) {
        return porteriaRepository.getVisitaById(id)
                .orElseThrow(() -> new RuntimeException("Visita no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaDTO> getVisitasByUnidad(Long unidadId) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(unidadId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar visitas de otra unidad");
            }
        }
        return porteriaRepository.getVisitasByUnidad(unidadId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaRepository.getVisitasResumen();
    }

    @Override
    public void registrarSalidaVisita(Long id) {
        porteriaRepository.getVisitaById(id).orElseThrow();
        porteriaRepository.updateVisitaEstado(id, "FINALIZADA");
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaHistorialDTO> getVisitasHistorial(String fechaInicio, String fechaFin) {
        return porteriaRepository.getVisitasHistorial(fechaInicio, fechaFin);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitaDetalleDTO getVisitaDetalle(Long id) {
        return porteriaRepository.getVisitaDetalle(id)
                .orElseThrow(() -> new RuntimeException("Visita no encontrada"));
    }

    @Override
    public VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request) {
        return porteriaRepository.updateVisita(id, request);
    }

    @Override
    @Auditable(action = "CHECKIN", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public RegistroAccesoDTO registrarEntrada(RegistroAccesoRequestDTO request) {
        if (!"ENTRADA".equals(request.tipoMovimiento())) {
            throw new IllegalArgumentException("El movimiento debe ser ENTRADA");
        }
        if (request.visitaId() != null) {
            porteriaRepository.updateVisitaEstado(request.visitaId(), "ACTIVA");
        }
        return porteriaRepository.createRegistroAcceso(request);
    }

    @Override
    @Auditable(action = "CHECKOUT", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public RegistroAccesoDTO registrarSalida(RegistroAccesoRequestDTO request) {
        if (!"SALIDA".equals(request.tipoMovimiento())) {
            throw new IllegalArgumentException("El movimiento debe ser SALIDA");
        }
        if (request.visitaId() != null) {
            porteriaRepository.updateVisitaEstado(request.visitaId(), "FINALIZADA");
        }
        return porteriaRepository.createRegistroAcceso(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId) {
        return porteriaRepository.getRegistrosByPropiedad(propiedadId);
    }

    @Override
    public QrAccesoDTO generarQrAcceso(QrAccesoRequestDTO request) {
        QrAccesoDTO qr = porteriaRepository.createQrAcceso(request);
        try {
            Long unitId = SaedContextHolder.getContext().getUnitId();
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD UH ON UH.ID_PERSONA = P.ID_PERSONA " +
                "WHERE UH.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL", 
                Map.of("u", unitId)
            );
            if (!residentes.isEmpty()) {
                String destinatario = (String) residentes.get(0).get("EMAIL");
                emailService.enviarCorreoQR(destinatario, qr.tokenQr(), qr.fechaExpiracion().toString(), "Visitante");
            }
        } catch(Exception e) { log.error("Error sending QR email", e); }
        return qr;
    }

    @Override
    @Transactional(readOnly = true)
    public QrAccesoDTO getQrAccesoById(Long id) {
        return porteriaRepository.getQrAccesoById(id)
                .orElseThrow(() -> new RuntimeException("QR Acceso no encontrado"));
    }

    @Override
    public boolean validarQr(String token) {
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        if (qr == null) return false;
        if (!"ACTIVO".equals(qr.estado())) return false;
        if (qr.fechaExpiracion().isBefore(ZonedDateTime.now())) return false;
        if (qr.usosConsumidos() >= qr.usosPermitidos()) return false;
        
        porteriaRepository.consumeQrUso(qr.idQr());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validarQrDetalle(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("valido", false, "mensaje", "Token no proporcionado");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token).orElse(null);
        if (qr == null) {
            return Map.of("valido", false, "mensaje", "Código QR no encontrado");
        }
        if (!"ACTIVO".equalsIgnoreCase(qr.estado())) {
            return Map.of("valido", false, "mensaje", "El código QR no se encuentra activo (Estado: " + qr.estado() + ")");
        }
        if (qr.fechaExpiracion() != null && qr.fechaExpiracion().isBefore(ZonedDateTime.now())) {
            return Map.of("valido", false, "mensaje", "El código QR ha expirado");
        }
        if (qr.usosPermitidos() != null && qr.usosConsumidos() != null && qr.usosConsumidos() >= qr.usosPermitidos()) {
            return Map.of("valido", false, "mensaje", "El código QR ya superó el límite de usos permitidos");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valido", true);
        result.put("mensaje", "Código QR válido");
        result.put("codigoQr", qr.tokenQr());
        result.put("idVisita", qr.visitaId());
        result.put("fechaExpiracion", qr.fechaExpiracion() != null ? qr.fechaExpiracion().toString() : null);

        VisitaDetalleDTO detalle = qr.visitaId() != null ? porteriaRepository.getVisitaDetalle(qr.visitaId()).orElse(null) : null;
        if (detalle != null) {
            String nombre = detalle.nombreVisitante() != null ? detalle.nombreVisitante() : "";
            if (detalle.apellidoVisitante() != null && !detalle.apellidoVisitante().isBlank()) {
                nombre = (nombre + " " + detalle.apellidoVisitante()).trim();
            }
            result.put("nombreVisitante", nombre);
            result.put("documentoVisitante", detalle.documentoVisitante() != null ? detalle.documentoVisitante() : "");
            result.put("nombreResidente", detalle.nombreResidente() != null ? detalle.nombreResidente() : "");
            result.put("numeroApartamento", detalle.numeroApartamento() != null ? detalle.numeroApartamento() : "");
            result.put("notas", detalle.notas() != null ? detalle.notas() : "");
        } else {
            result.put("nombreVisitante", "Visitante");
            result.put("documentoVisitante", "");
            result.put("nombreResidente", "");
            result.put("numeroApartamento", "");
            result.put("notas", "");
        }
        return result;
    }

    @Override
    @Auditable(action = "NOTIFICAR_VISITA", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Map<String, Object> notificarVisitaQr(String token, String fotoCaptura) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token QR requerido para notificar");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Código QR no encontrado"));

        Long destinatarioId = null;
        String nombreVisitante = "un visitante";
        if (qr.visitaId() != null) {
            try {
                VisitaDetalleDTO det = porteriaRepository.getVisitaDetalle(qr.visitaId()).orElse(null);
                if (det != null && det.nombreVisitante() != null) {
                    nombreVisitante = det.nombreVisitante();
                }
                List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT u.ID_USUARIO FROM USUARIOS u " +
                    "JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA " +
                    "JOIN RESIDENTES_UNIDAD ru ON ru.ID_PERSONA = p.ID_PERSONA " +
                    "JOIN VISITAS v ON v.ID_UNIDAD = ru.ID_UNIDAD " +
                    "WHERE v.ID_VISITA = :visitaId " +
                    "ORDER BY CASE WHEN ru.TIPO_RESIDENTE = 'TITULAR' THEN 1 ELSE 2 END",
                    new MapSqlParameterSource("visitaId", qr.visitaId()),
                    Long.class
                );
                if (!userIds.isEmpty()) {
                    destinatarioId = userIds.get(0);
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener destinatario para visita {}: {}", qr.visitaId(), e.getMessage());
            }
        }
        if (destinatarioId == null) {
            destinatarioId = SaedContextHolder.getContext().getUserId() != null ? SaedContextHolder.getContext().getUserId() : 1L;
        }

        Long notifId = System.currentTimeMillis();
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("destId", destinatarioId)
                .addValue("titulo", "Visitante en portería")
                .addValue("mensaje", "El visitante " + nombreVisitante + " se encuentra en portería.")
                .addValue("estado", "PENDIENTE");
            jdbcTemplate.update(
                "INSERT INTO NOTIFICACIONES (ID_USUARIO_DESTINATARIO, CANAL, TITULO, MENSAJE, ESTADO_ENVIO) " +
                "VALUES (:destId, 'IN_APP', :titulo, :mensaje, :estado)",
                params, keyHolder, new String[]{"ID_NOTIFICACION"}
            );
            if (keyHolder.getKey() != null) {
                notifId = keyHolder.getKey().longValue();
            }
        } catch (Exception e) {
            log.warn("Error guardando registro en NOTIFICACIONES: {}", e.getMessage());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("idMensaje", notifId);
        resp.put("idVisita", qr.visitaId());
        resp.put("status", "NOTIFICADO");
        resp.put("success", true);
        return resp;
    }

    @Override
    @Auditable(action = "CHECKIN_QR", resource = "ACCESO_PORTERIA", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Map<String, Object> registrarEntradaQr(String token, String medioTransporte, String placa, String descripcion) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token QR requerido");
        }
        QrAccesoDTO qr = porteriaRepository.getQrAccesoByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Código QR no encontrado"));

        if (!"ACTIVO".equalsIgnoreCase(qr.estado())) {
            throw new IllegalStateException("El código QR no se encuentra activo (Estado: " + qr.estado() + ")");
        }
        if (qr.fechaExpiracion() != null && qr.fechaExpiracion().isBefore(ZonedDateTime.now())) {
            throw new IllegalStateException("El código QR ha expirado");
        }
        if (qr.usosPermitidos() != null && qr.usosConsumidos() != null && qr.usosConsumidos() >= qr.usosPermitidos()) {
            throw new IllegalStateException("El código QR ya alcanzó el límite de usos permitidos");
        }

        // 1. Consumir uso
        porteriaRepository.consumeQrUso(qr.idQr());
        int nuevosUsos = (qr.usosConsumidos() != null ? qr.usosConsumidos() : 0) + 1;
        if (qr.usosPermitidos() != null && nuevosUsos >= qr.usosPermitidos()) {
            try {
                jdbcTemplate.update("UPDATE QR_ACCESOS SET ESTADO = 'USADO' WHERE ID_QR = :id", new MapSqlParameterSource("id", qr.idQr()));
            } catch (Exception ignored) {}
        }

        // 2. Actualizar visita a EN_CURSO
        if (qr.visitaId() != null) {
            porteriaRepository.updateVisitaEstado(qr.visitaId(), "EN_CURSO");
        }

        // 3. Metadata para Registro de Acceso
        Long propId = SaedContextHolder.getContext().getPropertyId();
        Long porteroId = SaedContextHolder.getContext().getUserId();
        Long personaId = 1L;
        Long unidadId = null;

        if (qr.visitaId() != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT v.ID_UNIDAD, vis.ID_PERSONA, u.ID_PROPIEDAD " +
                    "FROM VISITAS v " +
                    "JOIN UNIDADES u ON u.ID_UNIDAD = v.ID_UNIDAD " +
                    "JOIN VISITANTES vis ON vis.ID_VISITANTE = v.ID_VISITANTE " +
                    "WHERE v.ID_VISITA = :visitaId",
                    new MapSqlParameterSource("visitaId", qr.visitaId())
                );
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    if (r.get("ID_UNIDAD") != null) unidadId = ((Number) r.get("ID_UNIDAD")).longValue();
                    if (r.get("ID_PERSONA") != null) personaId = ((Number) r.get("ID_PERSONA")).longValue();
                    if (propId == null && r.get("ID_PROPIEDAD") != null) propId = ((Number) r.get("ID_PROPIEDAD")).longValue();
                }
            } catch (Exception e) {
                log.warn("Error consultando metadata de visita {}: {}", qr.visitaId(), e.getMessage());
            }
        }
        if (propId == null) propId = 1L;

        // 4. Identificar portería válida
        Long porteriaId = null;
        try {
            porteriaId = jdbcTemplate.queryForObject(
                "SELECT MIN(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("propId", propId),
                Long.class
            );
        } catch (Exception ignored) {}
        if (porteriaId == null) {
            try {
                porteriaId = jdbcTemplate.queryForObject("SELECT MIN(ID_PORTERIA) FROM PORTERIAS", new MapSqlParameterSource(), Long.class);
            } catch (Exception ignored) {}
        }
        if (porteriaId == null) porteriaId = 1L;

        // 5. Insertar Registro de Acceso
        String obs = (medioTransporte != null ? medioTransporte : "A_PIE") +
                (descripcion != null && !descripcion.isBlank() ? " - " + descripcion : "");
        RegistroAccesoRequestDTO regRequest = new RegistroAccesoRequestDTO(
            propId,
            porteriaId,
            null,
            qr.visitaId(),
            personaId,
            unidadId,
            qr.idQr(),
            "ENTRADA",
            "QR_SCAN",
            porteroId,
            placa,
            obs
        );
        porteriaRepository.createRegistroAcceso(regRequest);

        // 6. Asignar parqueadero y registrar vehículo si aplica
        String parqAsignado = null;
        if (qr.visitaId() != null && ("CARRO".equalsIgnoreCase(medioTransporte) || "MOTO".equalsIgnoreCase(medioTransporte))) {
            try {
                List<Map<String, Object>> parqs = jdbcTemplate.queryForList(
                    "SELECT ID_PARQUEADERO, NUMERO_PARQUEADERO FROM PARQUEADEROS " +
                    "WHERE ID_PROPIEDAD = :propId AND ESTADO = 'DISPONIBLE' " +
                    "ORDER BY ID_PARQUEADERO",
                    new MapSqlParameterSource("propId", propId)
                );
                Long parqId = null;
                if (!parqs.isEmpty()) {
                    parqId = ((Number) parqs.get(0).get("ID_PARQUEADERO")).longValue();
                    parqAsignado = (String) parqs.get(0).get("NUMERO_PARQUEADERO");
                    jdbcTemplate.update("UPDATE PARQUEADEROS SET ESTADO = 'OCUPADO' WHERE ID_PARQUEADERO = :id", new MapSqlParameterSource("id", parqId));
                }
                VehiculoVisitaRequestDTO vehRequest = new VehiculoVisitaRequestDTO(
                    qr.visitaId(),
                    parqId,
                    placa != null ? placa : "",
                    "CARRO".equalsIgnoreCase(medioTransporte) ? "CARRO" : "MOTO",
                    "DENTRO"
                );
                porteriaRepository.createVehiculoVisita(vehRequest);
            } catch (Exception e) {
                log.warn("No se pudo asignar parqueadero o registrar vehículo: {}", e.getMessage());
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("mensaje", "Entrada registrada exitosamente");
        resp.put("idVisita", qr.visitaId());
        resp.put("parqueadero", parqAsignado);
        return resp;
    }

    @Override
    @Auditable(action = "CHECKIN_VEHICULO", resource = "ACCESO_VEHICULO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public VehiculoVisitaDTO registrarIngresoVehiculo(VehiculoVisitaRequestDTO request) {
        return porteriaRepository.createVehiculoVisita(request);
    }

    @Override
    @Auditable(action = "CHECKOUT_VEHICULO", resource = "ACCESO_VEHICULO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void registrarSalidaVehiculo(Long vehiculoVisitaId, BigDecimal costoTotal) {
        porteriaRepository.registerSalidaVehiculo(vehiculoVisitaId, costoTotal);
    }
}






