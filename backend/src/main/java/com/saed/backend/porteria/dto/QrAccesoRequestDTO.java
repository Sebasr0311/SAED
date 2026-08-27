package com.saed.backend.porteria.dto;

import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;

public record QrAccesoRequestDTO(
        @NotNull Long visitaId,
        @NotNull String tokenQr,
        @NotNull ZonedDateTime fechaExpiracion,
        @NotNull Integer usosPermitidos,
        @NotNull String estado,
        Long generadoPor
) {}
