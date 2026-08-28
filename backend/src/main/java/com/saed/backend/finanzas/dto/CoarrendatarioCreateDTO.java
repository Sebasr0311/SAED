package com.saed.backend.finanzas.dto;

public record CoarrendatarioCreateDTO(
    Long idContrato,
    Long idPersona,
    String tipoVinculo,
    String esResponsablePago
) {}
