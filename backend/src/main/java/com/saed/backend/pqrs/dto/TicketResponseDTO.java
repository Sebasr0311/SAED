package com.saed.backend.pqrs.dto;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TicketResponseDTO {
    private Long idTicket;
    private Long idPropiedad;
    private Long idUnidad;
    private String identificadorUnidad;
    private Long idPersonaRadica;
    private String nombreRadicador;
    private String numeroRadicado;
    private String tipo;
    private String categoria;
    private String prioridad;
    private String asunto;
    private String descripcion;
    private String adjuntosUrl;
    private String estado;
    private ZonedDateTime fechaRadicacion;
    private ZonedDateTime fechaLimiteSla;
    private Long responsableAsignado;
    private String nombreResponsable;
    private Integer calificacionServicio;
    private String observacionCierre;
    private ZonedDateTime fechaCierre;
    private String ultimaRespuesta;
    private Boolean vencido;
    private Long tiempoRestanteHoras;

    public TicketResponseDTO() {}

    public Long getIdTicket() { return idTicket; }
    public void setIdTicket(Long idTicket) { this.idTicket = idTicket; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getIdentificadorUnidad() { return identificadorUnidad; }
    public void setIdentificadorUnidad(String identificadorUnidad) { this.identificadorUnidad = identificadorUnidad; }

    public Long getIdPersonaRadica() { return idPersonaRadica; }
    public void setIdPersonaRadica(Long idPersonaRadica) { this.idPersonaRadica = idPersonaRadica; }

    public String getNombreRadicador() { return nombreRadicador; }
    public void setNombreRadicador(String nombreRadicador) { this.nombreRadicador = nombreRadicador; }

    public String getNumeroRadicado() { return numeroRadicado; }
    public void setNumeroRadicado(String numeroRadicado) { this.numeroRadicado = numeroRadicado; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getAdjuntosUrl() { return adjuntosUrl; }
    public void setAdjuntosUrl(String adjuntosUrl) { this.adjuntosUrl = adjuntosUrl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public ZonedDateTime getFechaRadicacion() { return fechaRadicacion; }
    public void setFechaRadicacion(ZonedDateTime fechaRadicacion) { this.fechaRadicacion = fechaRadicacion; }

    public ZonedDateTime getFechaLimiteSla() { return fechaLimiteSla; }
    public void setFechaLimiteSla(ZonedDateTime fechaLimiteSla) {
        this.fechaLimiteSla = fechaLimiteSla;
        calcularSlaMetrics();
    }

    public Long getResponsableAsignado() { return responsableAsignado; }
    public void setResponsableAsignado(Long responsableAsignado) { this.responsableAsignado = responsableAsignado; }

    public String getNombreResponsable() { return nombreResponsable; }
    public void setNombreResponsable(String nombreResponsable) { this.nombreResponsable = nombreResponsable; }

    public Integer getCalificacionServicio() { return calificacionServicio; }
    public void setCalificacionServicio(Integer calificacionServicio) { this.calificacionServicio = calificacionServicio; }

    public String getObservacionCierre() { return observacionCierre; }
    public void setObservacionCierre(String observacionCierre) { this.observacionCierre = observacionCierre; }

    public ZonedDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(ZonedDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public String getUltimaRespuesta() { return ultimaRespuesta; }
    public void setUltimaRespuesta(String ultimaRespuesta) { this.ultimaRespuesta = ultimaRespuesta; }

    public Boolean getVencido() { return vencido; }
    public void setVencido(Boolean vencido) { this.vencido = vencido; }

    public Long getTiempoRestanteHoras() { return tiempoRestanteHoras; }
    public void setTiempoRestanteHoras(Long tiempoRestanteHoras) { this.tiempoRestanteHoras = tiempoRestanteHoras; }

    public void calcularSlaMetrics() {
        if (fechaLimiteSla != null) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
            boolean esCerrado = "RESUELTO".equalsIgnoreCase(estado) || "CERRADO".equalsIgnoreCase(estado);
            this.vencido = !esCerrado && now.isAfter(fechaLimiteSla);
            this.tiempoRestanteHoras = Duration.between(now, fechaLimiteSla).toHours();
        }
    }
}
