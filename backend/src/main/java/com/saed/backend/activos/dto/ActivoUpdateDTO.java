package com.saed.backend.activos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivoUpdateDTO(
    @NotBlank(message = "El código del activo es obligatorio")
    @Size(max = 50, message = "El código del activo no puede exceder 50 caracteres")
    String codigoActivo,

    @NotBlank(message = "El nombre del activo es obligatorio")
    @Size(max = 100, message = "El nombre del activo no puede exceder 100 caracteres")
    String nombre,

    @Size(max = 50, message = "La categoría no puede exceder 50 caracteres")
    String categoria,

    LocalDate fechaAdquisicion,

    @DecimalMin(value = "0.0", inclusive = true, message = "El valor de adquisición no puede ser negativo")
    BigDecimal valorAdquisicion
) {}
