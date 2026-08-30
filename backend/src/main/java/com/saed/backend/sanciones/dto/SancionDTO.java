package com.saed.backend.sanciones.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class SancionDTO {
    private Long idSancion;
    private Long idPropiedad;
    private Long idUnidad;
    private Long idPersonaImputada;
    private Long idIncidenteOrigen;
    private String numeroExpediente;
    private String tipoFalta;
    private String gravedad; // LEVE, MODERADA, GRAVE
    private String descripcionHechos;
    private String articuloReglamentoViolado;
    private String evidenciasUrls;
    private String tipoSancionPropuesta;
    private ZonedDateTime fechaAperturaPliego;
    private LocalDate fechaLimiteDescargos;
    private String resolucionFinal;
    private ZonedDateTime fechaResolucion;
    private String estado; // NOTIFICADA, EN_DESCARGOS, ARCHIVADA, SANCION_APLICADA
    private Long creadoPor;

    // Getters and Setters
    public Long getIdSancion() { return idSancion; }
    public void setIdSancion(Long idSancion) { this.idSancion = idSancion; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaImputada() { return idPersonaImputada; }
    public void setIdPersonaImputada(Long idPersonaImputada) { this.idPersonaImputada = idPersonaImputada; }

    public Long getIdIncidenteOrigen() { return idIncidenteOrigen; }
    public void setIdIncidenteOrigen(Long idIncidenteOrigen) { this.idIncidenteOrigen = idIncidenteOrigen; }

    public String getNumeroExpediente() { return numeroExpediente; }
    public void setNumeroExpediente(String numeroExpediente) { this.numeroExpediente = numeroExpediente; }

    public String getTipoFalta() { return tipoFalta; }
    public void setTipoFalta(String tipoFalta) { this.tipoFalta = tipoFalta; }

    public String getGravedad() { return gravedad; }
    public void setGravedad(String gravedad) { this.gravedad = gravedad; }

    public String getDescripcionHechos() { return descripcionHechos; }
    public void setDescripcionHechos(String descripcionHechos) { this.descripcionHechos = descripcionHechos; }

    public String getArticuloReglamentoViolado() { return articuloReglamentoViolado; }
    public void setArticuloReglamentoViolado(String articuloReglamentoViolado) { this.articuloReglamentoViolado = articuloReglamentoViolado; }

    public String getEvidenciasUrls() { return evidenciasUrls; }
    public void setEvidenciasUrls(String evidenciasUrls) { this.evidenciasUrls = evidenciasUrls; }

    public String getTipoSancionPropuesta() { return tipoSancionPropuesta; }
    public void setTipoSancionPropuesta(String tipoSancionPropuesta) { this.tipoSancionPropuesta = tipoSancionPropuesta; }

    public ZonedDateTime getFechaAperturaPliego() { return fechaAperturaPliego; }
    public void setFechaAperturaPliego(ZonedDateTime fechaAperturaPliego) { this.fechaAperturaPliego = fechaAperturaPliego; }

    public LocalDate getFechaLimiteDescargos() { return fechaLimiteDescargos; }
    public void setFechaLimiteDescargos(LocalDate fechaLimiteDescargos) { this.fechaLimiteDescargos = fechaLimiteDescargos; }

    public String getResolucionFinal() { return resolucionFinal; }
    public void setResolucionFinal(String resolucionFinal) { this.resolucionFinal = resolucionFinal; }

    public ZonedDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(ZonedDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Long creadoPor) { this.creadoPor = creadoPor; }
}
