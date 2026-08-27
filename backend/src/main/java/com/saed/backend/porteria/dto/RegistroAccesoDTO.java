package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record RegistroAccesoDTO(
        Long idRegistroAcceso,
        Long propiedadId,
        Long porteriaId,
        Long accesoConfiguradoId,
        Long visitaId,
        Long personaId,
        Long unidadId,
        Long qrId,
        String tipoMovimiento,
        String metodoAutorizacion,
        ZonedDateTime fechaHora,
        Long porteroOperadorId,
        String placaVehiculo,
        String observaciones
) {}
