package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record VisitaHistorialDTO(
        Long idVisita,
        String nombreVisitante,
        String apellidoVisitante,
        String documentoVisitante,
        String nombreResidente,
        String numeroApartamento,
        ZonedDateTime fechaVisita,
        ZonedDateTime fechaSalida,
        String estado,
        String tipoVehiculo,
        String placaVehiculo,
        String codigoParqueadero
) {}
