package com.saed.backend.proveedores.contratos;

import java.time.LocalDate;

/** A current supplier contract approaching its end date (F09-02). */
public record ContratoPorVencerDTO(
        Long idContratoProveedor,
        Long idPropiedad,
        String numeroContrato,
        String proveedor,
        LocalDate fechaFin,
        Integer diasAlerta
) {
}
