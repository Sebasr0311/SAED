package com.saed.backend.seguros.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PolizaSeguroDTO {
    private Long idPoliza;
    private Long idPropiedad;
    private String companiaAseguradora;
    private String numeroPoliza;
    private String ramoCobertura;
    private BigDecimal valorAsegurado;
    private BigDecimal valorPrimaAnual;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasAlertaVencimiento;
    private String nombreCorredorAgente;
    private String telefonoContactoAgente;
    private String documentoCaratulaUrl;
    private String estado;

    // Getters and Setters
    public Long getIdPoliza() { return idPoliza; }
    public void setIdPoliza(Long idPoliza) { this.idPoliza = idPoliza; }
    
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    
    public String getCompaniaAseguradora() { return companiaAseguradora; }
    public void setCompaniaAseguradora(String companiaAseguradora) { this.companiaAseguradora = companiaAseguradora; }
    
    public String getNumeroPoliza() { return numeroPoliza; }
    public void setNumeroPoliza(String numeroPoliza) { this.numeroPoliza = numeroPoliza; }
    
    public String getRamoCobertura() { return ramoCobertura; }
    public void setRamoCobertura(String ramoCobertura) { this.ramoCobertura = ramoCobertura; }
    
    public BigDecimal getValorAsegurado() { return valorAsegurado; }
    public void setValorAsegurado(BigDecimal valorAsegurado) { this.valorAsegurado = valorAsegurado; }
    
    public BigDecimal getValorPrimaAnual() { return valorPrimaAnual; }
    public void setValorPrimaAnual(BigDecimal valorPrimaAnual) { this.valorPrimaAnual = valorPrimaAnual; }
    
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    
    public Integer getDiasAlertaVencimiento() { return diasAlertaVencimiento; }
    public void setDiasAlertaVencimiento(Integer diasAlertaVencimiento) { this.diasAlertaVencimiento = diasAlertaVencimiento; }
    
    public String getNombreCorredorAgente() { return nombreCorredorAgente; }
    public void setNombreCorredorAgente(String nombreCorredorAgente) { this.nombreCorredorAgente = nombreCorredorAgente; }
    
    public String getTelefonoContactoAgente() { return telefonoContactoAgente; }
    public void setTelefonoContactoAgente(String telefonoContactoAgente) { this.telefonoContactoAgente = telefonoContactoAgente; }
    
    public String getDocumentoCaratulaUrl() { return documentoCaratulaUrl; }
    public void setDocumentoCaratulaUrl(String documentoCaratulaUrl) { this.documentoCaratulaUrl = documentoCaratulaUrl; }
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
