package com.saed.backend.authorization.dto;

import java.math.BigDecimal;

/**
 * Coefficient census of a property (DEC-04). An incomplete sum is allowed and reported as a warning.
 */
public class UnitCoefficientSummaryDTO {
    private Long idPropiedad;
    private long totalUnidades;
    private long unidadesSinCoeficiente;
    private BigDecimal sumaCoeficientes = BigDecimal.ZERO;
    private BigDecimal faltante;
    private boolean completo;
    private String advertencia;

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long v) { this.idPropiedad = v; }
    public long getTotalUnidades() { return totalUnidades; }
    public void setTotalUnidades(long v) { this.totalUnidades = v; }
    public long getUnidadesSinCoeficiente() { return unidadesSinCoeficiente; }
    public void setUnidadesSinCoeficiente(long v) { this.unidadesSinCoeficiente = v; }
    public BigDecimal getSumaCoeficientes() { return sumaCoeficientes; }
    public void setSumaCoeficientes(BigDecimal v) { this.sumaCoeficientes = v; }
    public BigDecimal getFaltante() { return faltante; }
    public void setFaltante(BigDecimal v) { this.faltante = v; }
    public boolean isCompleto() { return completo; }
    public void setCompleto(boolean v) { this.completo = v; }
    public String getAdvertencia() { return advertencia; }
    public void setAdvertencia(String v) { this.advertencia = v; }
}
