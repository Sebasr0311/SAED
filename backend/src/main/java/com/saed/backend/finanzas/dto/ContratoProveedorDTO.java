package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoProveedorDTO(
    Long idContratoProveedor,
    Long idProveedor,
    Long idPropiedad,
    String numeroContrato,
    String objetoContrato,
    BigDecimal valorTotal,
    String periodicidadPago,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    Integer diasAlertaVenc,
    String estado,
    String nombreProveedor
) {
    public ContratoProveedorDTO(
        Long idContratoProveedor,
        Long idProveedor,
        Long idPropiedad,
        String numeroContrato,
        String objetoContrato,
        BigDecimal valorTotal,
        String periodicidadPago,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer diasAlertaVenc,
        String estado
    ) {
        this(idContratoProveedor, idProveedor, idPropiedad, numeroContrato, objetoContrato,
             valorTotal, periodicidadPago, fechaInicio, fechaFin, diasAlertaVenc, estado, null);
    }
}
