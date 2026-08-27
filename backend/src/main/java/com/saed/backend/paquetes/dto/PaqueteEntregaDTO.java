package com.saed.backend.paquetes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaqueteEntregaDTO(
    @NotBlank String codigoRetiroPin,
    @NotNull Long idPersonaRecibe,
    @NotNull Long idPorteria,
    String firmaUrl
) {}
