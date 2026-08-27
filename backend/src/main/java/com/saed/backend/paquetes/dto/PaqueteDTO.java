package com.saed.backend.paquetes.dto;

import java.time.ZonedDateTime;

public record PaqueteDTO(
    Long idPaquete,
    Long idPropiedad,
    Long idPorteria,
    Long idUnidad,
    String numeroApartamento,
    Long idPersonaDestinatario,
    String nombreDestinatario,
    String empresaMensajeria,
    String numeroGuia,
    String descripcion,
    String tamano,
    String fotoPaqueteUrl,
    String codigoRetiroPin,
    ZonedDateTime fechaRecepcion,
    String recibidoPorPortero,
    ZonedDateTime fechaNotificacion,
    ZonedDateTime fechaEntrega,
    String entregadoAPersona,
    String entregadoPorPortero,
    String firmaUrl,
    String estado
) {}
