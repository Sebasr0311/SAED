package com.saed.backend.porteria.dto;

import java.time.ZonedDateTime;

public record QrAccesoDTO(
        Long idQr,
        Long visitaId,
        String tokenQr,
        ZonedDateTime fechaGeneracion,
        ZonedDateTime fechaExpiracion,
        Integer usosPermitidos,
        Integer usosConsumidos,
        ZonedDateTime fechaRevocacion,
        String motivoRevocacion,
        Long revocadoPor,
        String estado,
        Long generadoPor
) {}
