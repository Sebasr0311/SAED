package com.saed.backend.trabajadores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TrabajadorEstadoDTO(
        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "El estado debe ser ACTIVO o INACTIVO")
        String estado
) {}
