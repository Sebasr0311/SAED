package com.saed.backend.trabajadores.dto;

import java.time.ZonedDateTime;

public record ObraTrabajadorDTO(
        Long idObraTrabajador,
        Long idObra,
        Long idTrabajador,
        String autorizado,
        ZonedDateTime fechaAutorizacion,
        ZonedDateTime fechaRevocacion,
        Long autorizadoPor,
        TrabajadorDTO trabajador
) {}
