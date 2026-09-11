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
        String estado,
        Long idApartamento,
        String numeroApartamento,
        String tipoRelacion
) {
    public PersonaDTO(
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
            String estado,
            Long idApartamento,
            String numeroApartamento
    ) {
        this(id, tipoDocumentoId, numeroDocumento, tipoPersona, primerNombre, segundoNombre, primerApellido, segundoApellido, email, telefono, estado, idApartamento, numeroApartamento, null);
    }

    public PersonaDTO(
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
        this(id, tipoDocumentoId, numeroDocumento, tipoPersona, primerNombre, segundoNombre, primerApellido, segundoApellido, email, telefono, estado, null, null, null);
    }
}
