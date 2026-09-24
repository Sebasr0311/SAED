package com.saed.backend.person.dto;

import java.time.ZonedDateTime;

public record TutorDTO(
    Long id,
    Long personaMenorId,
    Long personaTutorId,
    String parentesco,
    String documentoSoporteUrl,
    String estado,
    ZonedDateTime fechaRegistro,
    String nombreTutor,
    String numeroDocumentoTutor,
    String telefonoTutor,
    String emailTutor
) {
    public TutorDTO(
        Long id,
        Long personaMenorId,
        Long personaTutorId,
        String parentesco,
        String documentoSoporteUrl,
        String estado,
        ZonedDateTime fechaRegistro
    ) {
        this(id, personaMenorId, personaTutorId, parentesco, documentoSoporteUrl, estado, fechaRegistro, null, null, null, null);
    }
}
