package com.saed.backend.parqueaderos.dto;

public record ParqueaderoDTO(
    Long idParqueadero,
    Long idPropiedad,
    String numeroParqueadero,
    String tipo,
    String estado
) {}
