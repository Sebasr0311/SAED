package com.saed.backend.seguros.dto;

import java.time.LocalDate;

/**
 * Minimized insurance policy view for residents (F10-17): insurer, coverage, number, validity and status only.
 * It deliberately carries no insured value, premium, deductible, broker contact or document reference.
 */
public class PolizaResidenteDTO {
    private Long idPoliza;
    private String companiaAseguradora;
    private String numeroPoliza;
    private String ramoCobertura;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;

    public Long getIdPoliza() { return idPoliza; }
    public void setIdPoliza(Long idPoliza) { this.idPoliza = idPoliza; }

    public String getCompaniaAseguradora() { return companiaAseguradora; }
    public void setCompaniaAseguradora(String companiaAseguradora) { this.companiaAseguradora = companiaAseguradora; }

    public String getNumeroPoliza() { return numeroPoliza; }
    public void setNumeroPoliza(String numeroPoliza) { this.numeroPoliza = numeroPoliza; }

    public String getRamoCobertura() { return ramoCobertura; }
    public void setRamoCobertura(String ramoCobertura) { this.ramoCobertura = ramoCobertura; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
