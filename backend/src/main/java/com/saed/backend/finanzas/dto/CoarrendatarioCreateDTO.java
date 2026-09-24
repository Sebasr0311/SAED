package com.saed.backend.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoarrendatarioCreateDTO(
    Long idContrato,
    Long idPersona,
    String tipoVinculo,
    String esResponsablePago,
    Boolean sincronizarHabitabilidad
) {
    public CoarrendatarioCreateDTO(Long idContrato, Long idPersona, String tipoVinculo, String esResponsablePago) {
        this(idContrato, idPersona, tipoVinculo, esResponsablePago, false);
    }
}
