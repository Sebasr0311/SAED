package com.saed.backend.mantenimiento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class MantenimientoDTO {

    private Long idMantenimiento;
    private Long idActivo;
    private String codigoActivo;
    private String nombreActivo;
    private Long idPropiedad;
    private String tipoMantenimiento;
    private String prioridad;
    private String titulo;
    private String descripcionTrabajo;
    private LocalDate fechaProgramada;
    private Instant fechaEjecucion;
    private BigDecimal costoEstimado;
    private BigDecimal costoReal;
    private String tecnicoResponsable;
    private Long idProveedorServicio;
    private String razonSocialProveedor;
    private String nitProveedor;
    private String evidenciaAntesUrl;
    private String evidenciaDespuesUrl;
    private String informeTecnicoUrl;
    private String notasCierre;
    private String estado;
    private Long solicitadoPor;
    private Instant fechaCreacion;

    // Campos integrados de bloqueo de zona común (si aplica)
    private Long idBloqueoZona;
    private Long idZonaBloqueada;
    private String nombreZonaBloqueada;
    private Instant fechaInicioBloqueo;
    private Instant fechaFinBloqueo;
    private String motivoBloqueo;

    public MantenimientoDTO() {
    }

    public Long getIdMantenimiento() {
        return idMantenimiento;
    }

    public void setIdMantenimiento(Long idMantenimiento) {
        this.idMantenimiento = idMantenimiento;
    }

    public Long getIdActivo() {
        return idActivo;
    }

    public void setIdActivo(Long idActivo) {
        this.idActivo = idActivo;
    }

    public String getCodigoActivo() {
        return codigoActivo;
    }

    public void setCodigoActivo(String codigoActivo) {
        this.codigoActivo = codigoActivo;
    }

    public String getNombreActivo() {
        return nombreActivo;
    }

    public void setNombreActivo(String nombreActivo) {
        this.nombreActivo = nombreActivo;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
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

    public Instant getFechaEjecucion() {
        return fechaEjecucion;
    }

    public void setFechaEjecucion(Instant fechaEjecucion) {
        this.fechaEjecucion = fechaEjecucion;
    }

    public BigDecimal getCostoEstimado() {
        return costoEstimado;
    }

    public void setCostoEstimado(BigDecimal costoEstimado) {
        this.costoEstimado = costoEstimado;
    }

    public BigDecimal getCostoReal() {
        return costoReal;
    }

    public void setCostoReal(BigDecimal costoReal) {
        this.costoReal = costoReal;
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

    public String getRazonSocialProveedor() {
        return razonSocialProveedor;
    }

    public void setRazonSocialProveedor(String razonSocialProveedor) {
        this.razonSocialProveedor = razonSocialProveedor;
    }

    public String getNitProveedor() {
        return nitProveedor;
    }

    public void setNitProveedor(String nitProveedor) {
        this.nitProveedor = nitProveedor;
    }

    public String getEvidenciaAntesUrl() {
        return evidenciaAntesUrl;
    }

    public void setEvidenciaAntesUrl(String evidenciaAntesUrl) {
        this.evidenciaAntesUrl = evidenciaAntesUrl;
    }

    public String getEvidenciaDespuesUrl() {
        return evidenciaDespuesUrl;
    }

    public void setEvidenciaDespuesUrl(String evidenciaDespuesUrl) {
        this.evidenciaDespuesUrl = evidenciaDespuesUrl;
    }

    public String getInformeTecnicoUrl() {
        return informeTecnicoUrl;
    }

    public void setInformeTecnicoUrl(String informeTecnicoUrl) {
        this.informeTecnicoUrl = informeTecnicoUrl;
    }

    public String getNotasCierre() {
        return notasCierre;
    }

    public void setNotasCierre(String notasCierre) {
        this.notasCierre = notasCierre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getSolicitadoPor() {
        return solicitadoPor;
    }

    public void setSolicitadoPor(Long solicitadoPor) {
        this.solicitadoPor = solicitadoPor;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Long getIdBloqueoZona() {
        return idBloqueoZona;
    }

    public void setIdBloqueoZona(Long idBloqueoZona) {
        this.idBloqueoZona = idBloqueoZona;
    }

    public Long getIdZonaBloqueada() {
        return idZonaBloqueada;
    }

    public void setIdZonaBloqueada(Long idZonaBloqueada) {
        this.idZonaBloqueada = idZonaBloqueada;
    }

    public String getNombreZonaBloqueada() {
        return nombreZonaBloqueada;
    }

    public void setNombreZonaBloqueada(String nombreZonaBloqueada) {
        this.nombreZonaBloqueada = nombreZonaBloqueada;
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
