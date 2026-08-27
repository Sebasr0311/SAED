package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record VisitaDTO(
        Long idVisita,
        Long unidadId,
        Long visitanteId,
        String metodoIngreso,
        String motivo,
        Long autorizadoPor,
        ZonedDateTime fechaProgramada,
        String estado,
        ZonedDateTime fechaCreacion
) {}
