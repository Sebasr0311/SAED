package com.saed.backend.porteria.intercom;

import java.time.ZonedDateTime;

/** A persisted intercom (citofonia) request from the gate to the residents of a unit (F07-02). */
public record IntercomRequest(
    Long idSolicitud,
    Long idPropiedad,
    Long idUnidad,
    Long idVisita,
    String nombreVisitante,
    String estado,
    ZonedDateTime fechaSolicitud,
    ZonedDateTime fechaExpiracion,
    Long respondidoPor,
    ZonedDateTime fechaRespuesta
) {}
