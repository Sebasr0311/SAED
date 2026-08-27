package com.saed.backend.paquetes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaqueteRequestDTO(
    @NotNull Long idUnidad,
    Long idPersonaDestinatario,
    @NotBlank @Size(max=100) String empresaMensajeria,
    @Size(max=80) String numeroGuia,
    @NotBlank @Size(max=300) String descripcion,
    @NotBlank @Size(max=20) String tamano,
    String fotoPaqueteUrl,
    @NotNull Long idPorteria
) {}
