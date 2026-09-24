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
        Boolean esFrecuente,
        Long minutosTranscurridos,
        Integer tiempoMaximoMinutos,
        Boolean excedido
) {
    public VisitaDetalleDTO(
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
    ) {
        this(idVisita, nombreVisitante, apellidoVisitante, documentoVisitante, telefonoVisitante, emailVisitante,
             nombreResidente, numeroApartamento, piso, fechaVisita, fechaSalida, estado, notas, tipoVehiculo,
             placaVehiculo, descripcionVehiculo, codigoParqueadero, fotoCaptura, cantidadPersonas, esFrecuente,
             null, null, null);
    }

    public VisitaDetalleDTO withPermanencia(Long minutosTranscurridos, Integer tiempoMaximoMinutos, Boolean excedido) {
        return new VisitaDetalleDTO(idVisita, nombreVisitante, apellidoVisitante, documentoVisitante, telefonoVisitante, emailVisitante,
             nombreResidente, numeroApartamento, piso, fechaVisita, fechaSalida, estado, notas, tipoVehiculo,
             placaVehiculo, descripcionVehiculo, codigoParqueadero, fotoCaptura, cantidadPersonas, esFrecuente,
             minutosTranscurridos, tiempoMaximoMinutos, excedido);
    }
}
