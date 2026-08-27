package com.saed.backend.porteria.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroAccesoRequestDTO(
        @NotNull Long propiedadId,
        @NotNull Long porteriaId,
        Long accesoConfiguradoId,
        Long visitaId,
        @NotNull Long personaId,
        Long unidadId,
        Long qrId,
        @NotNull @Size(max = 10) String tipoMovimiento,
        @NotNull @Size(max = 30) String metodoAutorizacion,
        Long porteroOperadorId,
        @Size(max = 15) String placaVehiculo,
        @Size(max = 300) String observaciones
) {}
