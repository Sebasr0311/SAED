package com.saed.backend.person.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UnitOwnerRequestDTO(
        @NotNull(message = "El ID de la persona es obligatorio")
        Long personaId,

        @NotNull(message = "El porcentaje de propiedad es obligatorio")
        @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede ser mayor a 100")
        BigDecimal porcentajePropiedad,

        @NotNull(message = "Debe indicar si es propietario principal")
        @Pattern(regexp = "^(S|N)$", message = "El indicador principal debe ser S o N")
        String esPrincipal
) {
}
