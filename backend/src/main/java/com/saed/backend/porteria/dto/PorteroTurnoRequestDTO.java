package com.saed.backend.porteria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PorteroTurnoRequestDTO(
    @NotNull(message = "El idUsuario del portero es obligatorio")
    Long idUsuario,

    @NotBlank(message = "El código del turno es obligatorio")
    String codigoTurno,

    String diasSemana
) {}
