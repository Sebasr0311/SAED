package com.saed.backend.person.dto;

import java.time.LocalDate;

public record UnitResidentDTO(
        Long id,
        PersonaDTO persona,
        String tipoResidente,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado
) {
}
