package com.saed.backend.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PagoRechazoRequestDTO(
    @NotBlank(message = "El motivo del rechazo es obligatorio")
    @Size(min = 4, max = 500, message = "El motivo debe tener entre 4 y 500 caracteres")
    String motivoRechazo
) {}
