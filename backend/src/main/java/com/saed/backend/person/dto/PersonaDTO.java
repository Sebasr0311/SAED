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
        String tipoRelacion,
        Long idUsuario,
        String nombreUsuario,
        String estadoUsuario
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
            String numeroApartamento,
            String tipoRelacion
    ) {
        this(id, tipoDocumentoId, numeroDocumento, tipoPersona, primerNombre, segundoNombre, primerApellido, segundoApellido, email, telefono, estado, idApartamento, numeroApartamento, tipoRelacion, null, null, null);
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
            String estado,
            Long idApartamento,
            String numeroApartamento
    ) {
        this(id, tipoDocumentoId, numeroDocumento, tipoPersona, primerNombre, segundoNombre, primerApellido, segundoApellido, email, telefono, estado, idApartamento, numeroApartamento, null, null, null, null);
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
        this(id, tipoDocumentoId, numeroDocumento, tipoPersona, primerNombre, segundoNombre, primerApellido, segundoApellido, email, telefono, estado, null, null, null, null, null, null);
    }
}

