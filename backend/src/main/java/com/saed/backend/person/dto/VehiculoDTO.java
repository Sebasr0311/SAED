package com.saed.backend.person.dto;

import java.time.ZonedDateTime;

public record VehiculoDTO(
    Long id,
    Long personaId,
    Long unidadId,
    String placa,
    String tipoVehiculo,
    String marca,
    String modelo,
    String color,
    String tagRfid,
    String estado,
    ZonedDateTime fechaCreacion
) {}
