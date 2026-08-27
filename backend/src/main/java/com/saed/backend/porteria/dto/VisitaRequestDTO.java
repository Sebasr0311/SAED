package com.saed.backend.porteria.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public record VisitaRequestDTO(
        @NotNull Long unidadId,
        @NotNull Long visitanteId,
        @NotNull @Size(max = 25) String metodoIngreso,
        @Size(max = 200) String motivo,
        Long autorizadoPor,
        ZonedDateTime fechaProgramada,
        @NotNull @Size(max = 20) String estado
) {}
