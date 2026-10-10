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
    List<VisitaListDTO> getVisitasResumen(Long propId);
    List<VisitaHistorialDTO> getVisitasHistorial(Long propId, String fechaInicio, String fechaFin);
    Optional<VisitaDetalleDTO> getVisitaDetalle(Long id);

    // Registro Acceso
    RegistroAccesoDTO createRegistroAcceso(RegistroAccesoRequestDTO request);
    Optional<RegistroAccesoDTO> getRegistroAccesoById(Long id);
    List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId);

    // QR Acceso
    QrAccesoDTO createQrAcceso(QrAccesoRequestDTO request);
    Optional<QrAccesoDTO> getQrAccesoById(Long id);
    Optional<QrAccesoDTO> getQrAccesoByToken(String token);
    void consumeQrUso(Long id);
    QrConsumoResultadoDTO validarYConsumirQrSp(String tokenQr, Long idPorteria, Long idUsuarioPortero);

    // Vehiculo Visita
    VehiculoVisitaDTO createVehiculoVisita(VehiculoVisitaRequestDTO request);
    Optional<VehiculoVisitaDTO> getVehiculoVisitaById(Long id);
    void registerSalidaVehiculo(Long id, java.math.BigDecimal costoTotal);

    // Lifecycle (F07-01 / F07-03)
    Optional<Long> findVisitaPropertyId(Long visitaId);
    Optional<Long> findFirstPorteriaId(Long propId);
    /** Moves the visit to {@code nuevoEstado} only when its current state is one of {@code desde}; returns rows updated. */
    int transitionVisitaEstado(Long visitaId, String nuevoEstado, List<String> desde);
    int revokeActiveQrs(Long visitaId, Long revocadoPor, String motivo);
    int revokeQr(Long idQr, Long revocadoPor, String motivo);
    /** Claims one DISPONIBLE spot of the given type with a conditional update; never double-assigns. */
    Optional<ParkingSpotClaim> claimAvailableSpot(Long propId, String tipoParqueadero);
    List<VehiculoVisitaDTO> findVehiculosDentro(Long visitaId);
    /** Closes the vehicle only if it is still DENTRO; returns false when it was already closed. */
    boolean closeVehiculoVisita(Long id, java.math.BigDecimal costoTotal);
    /** Frees a spot unless another vehicle is still parked in it. */
    void releaseParqueadero(Long parqueaderoId);
    void insertSalidaVisita(Long visitaId, Long porteriaId, Long porteroId);
    /** Adds plate and observations to the latest ENTRADA record of the visit. */
    void complementEntrada(Long visitaId, String placa, String observaciones);
    List<Long> findActiveUnitIdsForUser(Long userId);
}

