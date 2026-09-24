package com.saed.backend.domicilios.dto;

import java.time.ZonedDateTime;

public record DomicilioDTO(
    Long idDomicilio,
    Long idOrganizacion,
    Long idPropiedad,
    Long idUnidad,
    String numeroUnidad,
    Long idPorteria,
    Long idPersonaDestinatario,
    String nombreDestinatario,
    String empresa,
    String nombreDomiciliario,
    String documentoDomiciliario,
    String telefonoDomiciliario,
    String tipoDomicilio,
    String numeroGuia,
    String medioTransporte,
    String placaVehiculo,
    String observaciones,
    String estado,
    ZonedDateTime fechaEntrada,
    ZonedDateTime fechaSalida,
    Long registradoPor,
    String nombreRegistrador,
    Long finalizadoPor,
    String nombreFinalizador,
    ZonedDateTime fechaCreacion,
    Long minutosTranscurridos,
    Integer tiempoMaximoMinutos,
    Boolean excedido
) {
    public DomicilioDTO(
        Long idDomicilio,
        Long idOrganizacion,
        Long idPropiedad,
        Long idUnidad,
        String numeroUnidad,
        Long idPorteria,
        Long idPersonaDestinatario,
        String nombreDestinatario,
        String empresa,
        String nombreDomiciliario,
        String documentoDomiciliario,
        String telefonoDomiciliario,
        String tipoDomicilio,
        String numeroGuia,
        String medioTransporte,
        String placaVehiculo,
        String observaciones,
        String estado,
        ZonedDateTime fechaEntrada,
        ZonedDateTime fechaSalida,
        Long registradoPor,
        String nombreRegistrador,
        Long finalizadoPor,
        String nombreFinalizador,
        ZonedDateTime fechaCreacion
    ) {
        this(
            idDomicilio, idOrganizacion, idPropiedad, idUnidad, numeroUnidad,
            idPorteria, idPersonaDestinatario, nombreDestinatario,
            empresa, nombreDomiciliario, documentoDomiciliario, telefonoDomiciliario,
            tipoDomicilio, numeroGuia, medioTransporte, placaVehiculo,
            observaciones, estado, fechaEntrada, fechaSalida,
            registradoPor, nombreRegistrador, finalizadoPor, nombreFinalizador,
            fechaCreacion, null, null, null
        );
    }

    public DomicilioDTO withPermanencia(Long minutosTranscurridos, Integer tiempoMaximoMinutos, Boolean excedido) {
        return new DomicilioDTO(
            idDomicilio, idOrganizacion, idPropiedad, idUnidad, numeroUnidad,
            idPorteria, idPersonaDestinatario, nombreDestinatario,
            empresa, nombreDomiciliario, documentoDomiciliario, telefonoDomiciliario,
            tipoDomicilio, numeroGuia, medioTransporte, placaVehiculo,
            observaciones, estado, fechaEntrada, fechaSalida,
            registradoPor, nombreRegistrador, finalizadoPor, nombreFinalizador,
            fechaCreacion, minutosTranscurridos, tiempoMaximoMinutos, excedido
        );
    }
}
