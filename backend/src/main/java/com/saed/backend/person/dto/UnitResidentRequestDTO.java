package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UnitResidentRequestDTO(
        @NotNull(message = "El ID de la persona es obligatorio")
        Long personaId,

        @NotBlank(message = "El tipo de residente es obligatorio")
        @Pattern(regexp = "^(PROPIETARIO|ARRENDATARIO|FAMILIAR|OTRO)$", message = "Tipo de residente no válido")
        String tipoResidente
) {
}
