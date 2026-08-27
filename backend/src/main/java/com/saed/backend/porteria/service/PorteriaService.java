package com.saed.backend.porteria.service;

import com.saed.backend.porteria.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface PorteriaService {
    VisitaDTO programarVisita(VisitaRequestDTO request);
    VisitaDTO getVisitaById(Long id);
    List<VisitaDTO> getVisitasByUnidad(Long unidadId);
    VisitaDTO actualizarVisita(Long id, VisitaRequestDTO request);
    List<VisitaListDTO> getVisitasResumen();
    
    RegistroAccesoDTO registrarEntrada(RegistroAccesoRequestDTO request);
    RegistroAccesoDTO registrarSalida(RegistroAccesoRequestDTO request);
    List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId);

    QrAccesoDTO generarQrAcceso(QrAccesoRequestDTO request);
    QrAccesoDTO getQrAccesoById(Long id);
    boolean validarQr(String token);

    VehiculoVisitaDTO registrarIngresoVehiculo(VehiculoVisitaRequestDTO request);
    void registrarSalidaVehiculo(Long vehiculoVisitaId, BigDecimal costoTotal);
}


