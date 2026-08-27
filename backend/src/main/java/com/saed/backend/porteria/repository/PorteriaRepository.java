package com.saed.backend.porteria.repository;

import com.saed.backend.porteria.dto.*;

import java.util.List;
import java.util.Optional;

public interface PorteriaRepository {
    // Visitas
    VisitaDTO createVisita(VisitaRequestDTO request);
    Optional<VisitaDTO> getVisitaById(Long id);
    List<VisitaDTO> getVisitasByUnidad(Long unidadId);
    VisitaDTO updateVisita(Long id, VisitaRequestDTO request);
    void updateVisitaEstado(Long id, String estado);
    List<VisitaListDTO> getVisitasResumen();

    // Registro Acceso
    RegistroAccesoDTO createRegistroAcceso(RegistroAccesoRequestDTO request);
    Optional<RegistroAccesoDTO> getRegistroAccesoById(Long id);
    List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId);

    // QR Acceso
    QrAccesoDTO createQrAcceso(QrAccesoRequestDTO request);
    Optional<QrAccesoDTO> getQrAccesoById(Long id);
    Optional<QrAccesoDTO> getQrAccesoByToken(String token);
    void consumeQrUso(Long id);

    // Vehiculo Visita
    VehiculoVisitaDTO createVehiculoVisita(VehiculoVisitaRequestDTO request);
    Optional<VehiculoVisitaDTO> getVehiculoVisitaById(Long id);
    void registerSalidaVehiculo(Long id, java.math.BigDecimal costoTotal);
}

