package com.saed.backend.parqueaderos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AsignacionParqueaderoRequestDTO(
    @NotNull Long idParqueadero,
    @NotNull Long idUnidad,
    Long idVehiculo,
    @NotBlank String tipoAsignacion,
    @NotNull BigDecimal canonMensual
) {}
