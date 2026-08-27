package com.saed.backend.porteria.service.impl;

import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.repository.PorteriaRepository;
import com.saed.backend.porteria.service.PorteriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@Transactional
public class PorteriaServiceImpl implements PorteriaService {

    private final PorteriaRepository porteriaRepository;

    public PorteriaServiceImpl(PorteriaRepository porteriaRepository) {
        this.porteriaRepository = porteriaRepository;
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
        return porteriaRepository.createQrAcceso(request);
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


