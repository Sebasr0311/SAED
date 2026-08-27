package com.saed.backend.person.dto;

public record PersonaDTO(
        Long id,
        Long tipoDocumentoId,
        String numeroDocumento,
        String tipoPersona,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String email,
        String telefono,
        String estado
) {
}
