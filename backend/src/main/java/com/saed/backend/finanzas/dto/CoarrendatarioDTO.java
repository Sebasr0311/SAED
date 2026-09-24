package com.saed.backend.finanzas.dto;

public record CoarrendatarioDTO(
    Long idContratoResidente,
    Long idContrato,
    Long idPersona,
    String tipoVinculo,
    String esResponsablePago,
    String fechaVinculacion,
    String estado,
    String nombrePersona,
    String numeroDocumento,
    String telefono,
    String email
) {
    public CoarrendatarioDTO(
        Long idContratoResidente,
        Long idContrato,
        Long idPersona,
        String tipoVinculo,
        String esResponsablePago,
        String fechaVinculacion,
        String estado
    ) {
        this(idContratoResidente, idContrato, idPersona, tipoVinculo, esResponsablePago, fechaVinculacion, estado, null, null, null, null);
    }
}
