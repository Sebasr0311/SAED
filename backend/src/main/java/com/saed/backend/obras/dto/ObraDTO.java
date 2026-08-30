package com.saed.backend.obras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ObraDTO {
    private Long idObra;
    private Long idUnidad;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaFinEstimada;
    private String responsableObra;
    private String telefonoResponsable;
    private BigDecimal depositoGarantia;
    private String licenciaUrbanisticaUrl;
    private String estado;
    private Long solicitadoPor;
    private Long aprobadoPor;
    private ZonedDateTime fechaAprobacion;

    // Getters and Setters
    public Long getIdObra() { return idObra; }
    public void setIdObra(Long idObra) { this.idObra = idObra; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFinEstimada() { return fechaFinEstimada; }
    public void setFechaFinEstimada(LocalDate fechaFinEstimada) { this.fechaFinEstimada = fechaFinEstimada; }

    public String getResponsableObra() { return responsableObra; }
    public void setResponsableObra(String responsableObra) { this.responsableObra = responsableObra; }

    public String getTelefonoResponsable() { return telefonoResponsable; }
    public void setTelefonoResponsable(String telefonoResponsable) { this.telefonoResponsable = telefonoResponsable; }

    public BigDecimal getDepositoGarantia() { return depositoGarantia; }
    public void setDepositoGarantia(BigDecimal depositoGarantia) { this.depositoGarantia = depositoGarantia; }

    public String getLicenciaUrbanisticaUrl() { return licenciaUrbanisticaUrl; }
    public void setLicenciaUrbanisticaUrl(String licenciaUrbanisticaUrl) { this.licenciaUrbanisticaUrl = licenciaUrbanisticaUrl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getSolicitadoPor() { return solicitadoPor; }
    public void setSolicitadoPor(Long solicitadoPor) { this.solicitadoPor = solicitadoPor; }

    public Long getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(Long aprobadoPor) { this.aprobadoPor = aprobadoPor; }

    public ZonedDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(ZonedDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }
}
