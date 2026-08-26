package com.saed.backend.person.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UnitOwnerDTO(
        Long id,
        PersonaDTO persona,
        BigDecimal porcentajePropiedad,
        String esPrincipal,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado
) {
}
