package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record VisitaListDTO(
        Long idVisita,
        String nombreVisitante,
        String documentoVisitante,
        String numeroApartamento,
        ZonedDateTime fechaIngreso,
        ZonedDateTime fechaSalida,
        String estado
) {}
