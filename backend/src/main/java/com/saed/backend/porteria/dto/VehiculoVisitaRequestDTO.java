package com.saed.backend.porteria.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehiculoVisitaRequestDTO(
        @NotNull Long visitaId,
        Long parqueaderoId,
        @NotNull @Size(max = 15) String placa,
        @NotNull @Size(max = 20) String tipoVehiculo,
        @NotNull @Size(max = 10) String estado
) {}
