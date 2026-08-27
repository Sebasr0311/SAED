package com.saed.backend.porteria.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record VehiculoVisitaDTO(
        Long idVehiculoVisita,
        Long visitaId,
        Long parqueaderoId,
        String placa,
        String tipoVehiculo,
        ZonedDateTime fechaIngreso,
        ZonedDateTime fechaSalida,
        BigDecimal costoTotal,
        String estado
) {}
