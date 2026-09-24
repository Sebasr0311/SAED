package com.saed.backend.porteria.dto;

import java.time.Instant;

public record PorteroTurnoDTO(
    Long idAsignacionTurno,
    Long idPorteria,
    Long idUsuario,
    String nombreUsuario,
    String nombreCompleto,
    String codigoTurno,
    String nombreTurno,
    String horaInicio,
    String horaFin,
    String diasSemana,
    String estado,
    Instant fechaAsignacion
) {}
