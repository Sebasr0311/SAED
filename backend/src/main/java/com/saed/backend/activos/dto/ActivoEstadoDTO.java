package com.saed.backend.activos.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivoEstadoDTO(
    @NotBlank(message = "El estado es obligatorio")
    String estado,
    String motivo
) {}
