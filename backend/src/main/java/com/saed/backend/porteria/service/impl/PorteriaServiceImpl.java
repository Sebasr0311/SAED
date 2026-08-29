package com.saed.backend.porteria.service.impl;

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
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Map;
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
        return porteriaRepository.getVisitasByUnidad(unidadId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaRepository.getVisitasResumen();
    }

    @Override
    public VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request) {
        return porteriaRepository.updateVisita(id, request);
    }

    @Override
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
                "JOIN UNIDAD_HABITANTES UH ON UH.ID_PERSONA = P.ID_PERSONA " +
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
    public VehiculoVisitaDTO registrarIngresoVehiculo(VehiculoVisitaRequestDTO request) {
        return porteriaRepository.createVehiculoVisita(request);
    }

    @Override
    public void registrarSalidaVehiculo(Long vehiculoVisitaId, BigDecimal costoTotal) {
        porteriaRepository.registerSalidaVehiculo(vehiculoVisitaId, costoTotal);
    }
}




