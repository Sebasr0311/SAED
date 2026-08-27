package com.saed.backend.porteria.service.impl;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.repository.PorteriaRepository;
import com.saed.backend.porteria.service.PorteriaService;
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

    private final PorteriaRepository porteriaRepository;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PorteriaServiceImpl(PorteriaRepository porteriaRepository, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.porteriaRepository = porteriaRepository;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

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
        } catch(Exception e) { e.printStackTrace(); }
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




