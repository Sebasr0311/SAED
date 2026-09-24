package com.saed.backend.mantenimiento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class MantenimientoCreateDTO {

    private Long idActivo;

    @NotBlank(message = "El tipo de mantenimiento es obligatorio")
    @Pattern(regexp = "^(?i)(PREVENTIVO|CORRECTIVO|PREDICTIVO|EMERGENCIA|LOCATIVO)$", message = "El tipo de mantenimiento debe ser PREVENTIVO, CORRECTIVO, PREDICTIVO, EMERGENCIA o LOCATIVO")
    private String tipoMantenimiento;

    @Pattern(regexp = "^(?i)(BAJA|MEDIA|ALTA|URGENTE)$", message = "La prioridad debe ser BAJA, MEDIA, ALTA o URGENTE")
    private String prioridad = "MEDIA";

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 150, message = "El título no puede exceder los 150 caracteres")
    private String titulo;

    @NotBlank(message = "La descripción del trabajo es obligatoria")
    private String descripcionTrabajo;

    @NotNull(message = "La fecha programada es obligatoria")
    private LocalDate fechaProgramada;

    @DecimalMin(value = "0.0", message = "El costo estimado no puede ser negativo")
    private BigDecimal costoEstimado = BigDecimal.ZERO;

    @Size(max = 150, message = "El nombre del técnico no puede exceder los 150 caracteres")
    private String tecnicoResponsable;

    private Long idProveedorServicio;

    @Size(max = 500, message = "La URL de evidencia antes no puede exceder 500 caracteres")
    private String evidenciaAntesUrl;

    // Campos para bloqueo temporal de zona común asociado
    private Long idZonaBloqueo;
    private Instant fechaInicioBloqueo;
    private Instant fechaFinBloqueo;

    @Size(max = 250, message = "El motivo del bloqueo no puede exceder 250 caracteres")
    private String motivoBloqueo;

    public MantenimientoCreateDTO() {
    }

    public Long getIdActivo() {
        return idActivo;
    }

    public void setIdActivo(Long idActivo) {
        this.idActivo = idActivo;
    }

    public String getTipoMantenimiento() {
        return tipoMantenimiento;
    }

    public void setTipoMantenimiento(String tipoMantenimiento) {
        this.tipoMantenimiento = tipoMantenimiento;
    }

    public String getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(String prioridad) {
        this.prioridad = prioridad;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcionTrabajo() {
        return descripcionTrabajo;
    }

    public void setDescripcionTrabajo(String descripcionTrabajo) {
        this.descripcionTrabajo = descripcionTrabajo;
    }

    public LocalDate getFechaProgramada() {
        return fechaProgramada;
    }

    public void setFechaProgramada(LocalDate fechaProgramada) {
        this.fechaProgramada = fechaProgramada;
    }

    public BigDecimal getCostoEstimado() {
        return costoEstimado;
    }

    public void setCostoEstimado(BigDecimal costoEstimado) {
        this.costoEstimado = costoEstimado;
    }

    public String getTecnicoResponsable() {
        return tecnicoResponsable;
    }

    public void setTecnicoResponsable(String tecnicoResponsable) {
        this.tecnicoResponsable = tecnicoResponsable;
    }

    public Long getIdProveedorServicio() {
        return idProveedorServicio;
    }

    public void setIdProveedorServicio(Long idProveedorServicio) {
        this.idProveedorServicio = idProveedorServicio;
    }

    public String getEvidenciaAntesUrl() {
        return evidenciaAntesUrl;
    }

    public void setEvidenciaAntesUrl(String evidenciaAntesUrl) {
        this.evidenciaAntesUrl = evidenciaAntesUrl;
    }

    public Long getIdZonaBloqueo() {
        return idZonaBloqueo;
    }

    public void setIdZonaBloqueo(Long idZonaBloqueo) {
        this.idZonaBloqueo = idZonaBloqueo;
    }

    public Instant getFechaInicioBloqueo() {
        return fechaInicioBloqueo;
    }

    public void setFechaInicioBloqueo(Instant fechaInicioBloqueo) {
        this.fechaInicioBloqueo = fechaInicioBloqueo;
    }

    public Instant getFechaFinBloqueo() {
        return fechaFinBloqueo;
    }

    public void setFechaFinBloqueo(Instant fechaFinBloqueo) {
        this.fechaFinBloqueo = fechaFinBloqueo;
    }

    public String getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public void setMotivoBloqueo(String motivoBloqueo) {
        this.motivoBloqueo = motivoBloqueo;
    }
}
