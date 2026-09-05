package com.saed.backend.parqueaderos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ParqueaderoDTO(
    Long idParqueadero,
    Long idPropiedad,
    String numeroParqueadero,
    String tipo,
    String estado,
    @JsonProperty("codigo") String codigo,
    @JsonProperty("esVisitante") Boolean esVisitante,
    @JsonProperty("placaVehiculo") String placaVehiculo,
    @JsonProperty("numeroApartamento") String numeroApartamento,
    @JsonProperty("idApartamento") Long idApartamento
) {
    public ParqueaderoDTO(Long idParqueadero, Long idPropiedad, String numeroParqueadero, String tipo, String estado) {
        this(
            idParqueadero,
            idPropiedad,
            numeroParqueadero,
            tipo,
            estado,
            numeroParqueadero,
            "VISITANTES".equalsIgnoreCase(tipo),
            null,
            null,
            null
        );
    }
}
