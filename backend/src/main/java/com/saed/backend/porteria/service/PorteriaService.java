package com.saed.backend.porteria.service;

import com.saed.backend.porteria.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PorteriaService {
    // Admin CRUD
    List<PorteriaDTO> listarPorterias();
    PorteriaDTO getPorteriaById(Long id);
    PorteriaDTO crearPorteria(PorteriaCreateDTO request);
    PorteriaDTO actualizarPorteria(Long id, PorteriaCreateDTO request);
    void eliminarPorteria(Long id);

    // Operaciones portero
    VisitaDTO programarVisita(VisitaRequestDTO request);
    VisitaDTO getVisitaById(Long id);
    List<VisitaDTO> getVisitasByUnidad(Long unidadId);
    VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request);
    void registrarSalidaVisita(Long id);
    List<VisitaListDTO> getVisitasResumen();
    List<VisitaHistorialDTO> getVisitasHistorial(String fechaInicio, String fechaFin);
    VisitaDetalleDTO getVisitaDetalle(Long id);
    
    RegistroAccesoDTO registrarEntrada(RegistroAccesoRequestDTO request);
    RegistroAccesoDTO registrarSalida(RegistroAccesoRequestDTO request);
    List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId);

    QrAccesoDTO generarQrAcceso(QrAccesoRequestDTO request);
    QrAccesoDTO getQrAccesoById(Long id);
    boolean validarQr(String token);
    Map<String, Object> validarQrDetalle(String token);
    Map<String, Object> notificarVisitaQr(String token, String fotoCaptura);
    Map<String, Object> registrarEntradaQr(String token, String medioTransporte, String placa, String descripcion);

    VehiculoVisitaDTO registrarIngresoVehiculo(VehiculoVisitaRequestDTO request);
    void registrarSalidaVehiculo(Long vehiculoVisitaId, BigDecimal costoTotal);
}



