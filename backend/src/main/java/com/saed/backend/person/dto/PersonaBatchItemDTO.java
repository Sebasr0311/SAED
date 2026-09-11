package com.saed.backend.person.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaBatchItemDTO(
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        String email,
        String telefono,
        String apartamento,
        String tipoRelacion
) {
    public PersonaBatchItemDTO {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            tipoDocumento = "CC";
        }
        if (tipoRelacion == null || tipoRelacion.isBlank()) {
            tipoRelacion = "ARRENDATARIO";
        }
    }
}
