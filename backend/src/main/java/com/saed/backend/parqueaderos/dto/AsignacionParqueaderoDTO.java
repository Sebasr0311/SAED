package com.saed.backend.parqueaderos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AsignacionParqueaderoDTO(
    Long idAsignacionParqueadero,
    Long idParqueadero,
    String numeroParqueadero,
    Long idUnidad,
    String numeroApartamento,
    Long idVehiculo,
    String placaVehiculo,
    String tipoAsignacion,
    BigDecimal canonMensual,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String estado
) {}
