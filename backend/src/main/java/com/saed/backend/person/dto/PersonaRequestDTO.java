package com.saed.backend.person.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PersonaRequestDTO(
        @NotNull(message = "El tipo de documento es obligatorio")
        Long tipoDocumentoId,

        @NotBlank(message = "El número de documento es obligatorio")
        String numeroDocumento,

        @NotBlank(message = "El tipo de persona es obligatorio")
        @Pattern(regexp = "^(NATURAL|JURIDICA)$", message = "El tipo de persona debe ser NATURAL o JURIDICA")
        String tipoPersona,

        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,

        @Email(message = "Debe proporcionar un email válido")
        String email,

        String telefono
) {
}
