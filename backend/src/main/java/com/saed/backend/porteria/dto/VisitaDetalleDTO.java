package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record VisitaDetalleDTO(
        Long idVisita,
        String nombreVisitante,
        String apellidoVisitante,
        String documentoVisitante,
        String telefonoVisitante,
        String emailVisitante,
        String nombreResidente,
        String numeroApartamento,
        Integer piso,
        ZonedDateTime fechaVisita,
        ZonedDateTime fechaSalida,
        String estado,
        String notas,
        String tipoVehiculo,
        String placaVehiculo,
        String descripcionVehiculo,
        String codigoParqueadero,
        String fotoCaptura,
        Integer cantidadPersonas,
        Boolean esFrecuente
) {}
