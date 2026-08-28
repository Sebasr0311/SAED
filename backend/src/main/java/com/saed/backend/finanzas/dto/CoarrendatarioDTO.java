package com.saed.backend.finanzas.dto;

public record CoarrendatarioDTO(
    Long idContratoResidente,
    Long idContrato,
    Long idPersona,
    String tipoVinculo,
    String esResponsablePago,
    String fechaVinculacion,
    String estado
) {}
