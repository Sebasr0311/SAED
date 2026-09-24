package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record VisitaListDTO(
        Long idVisita,
        String nombreVisitante,
        String documentoVisitante,
        String numeroApartamento,
        ZonedDateTime fechaIngreso,
        ZonedDateTime fechaSalida,
        String estado,
        Long minutosTranscurridos,
        Integer tiempoMaximoMinutos,
        Boolean excedido
) {
    public VisitaListDTO(
        Long idVisita,
        String nombreVisitante,
        String documentoVisitante,
        String numeroApartamento,
        ZonedDateTime fechaIngreso,
        ZonedDateTime fechaSalida,
        String estado
    ) {
        this(idVisita, nombreVisitante, documentoVisitante, numeroApartamento, fechaIngreso, fechaSalida, estado, null, null, null);
    }

    public VisitaListDTO withPermanencia(Long minutosTranscurridos, Integer tiempoMaximoMinutos, Boolean excedido) {
        return new VisitaListDTO(idVisita, nombreVisitante, documentoVisitante, numeroApartamento, fechaIngreso, fechaSalida, estado, minutosTranscurridos, tiempoMaximoMinutos, excedido);
    }
}
