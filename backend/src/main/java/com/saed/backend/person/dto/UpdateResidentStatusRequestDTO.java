package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateResidentStatusRequestDTO(
        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "Estado no válido (debe ser ACTIVO o INACTIVO)")
        String estado
) {
}
