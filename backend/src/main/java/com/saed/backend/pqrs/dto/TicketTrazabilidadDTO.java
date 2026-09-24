package com.saed.backend.pqrs.dto;

import java.time.ZonedDateTime;

public class TicketTrazabilidadDTO {
    private Long idTrazabilidad;
    private Long idTicket;
    private Long idUsuario;
    private String nombreUsuario;
    private String tipoIntervencion;
    private String comentario;
    private String adjuntoUrl;
    private String estadoAnterior;
    private String estadoNuevo;
    private ZonedDateTime fechaHora;

    public TicketTrazabilidadDTO() {}

    public Long getIdTrazabilidad() { return idTrazabilidad; }
    public void setIdTrazabilidad(Long idTrazabilidad) { this.idTrazabilidad = idTrazabilidad; }

    public Long getIdTicket() { return idTicket; }
    public void setIdTicket(Long idTicket) { this.idTicket = idTicket; }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getTipoIntervencion() { return tipoIntervencion; }
    public void setTipoIntervencion(String tipoIntervencion) { this.tipoIntervencion = tipoIntervencion; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public String getAdjuntoUrl() { return adjuntoUrl; }
    public void setAdjuntoUrl(String adjuntoUrl) { this.adjuntoUrl = adjuntoUrl; }

    public String getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(String estadoAnterior) { this.estadoAnterior = estadoAnterior; }

    public String getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(String estadoNuevo) { this.estadoNuevo = estadoNuevo; }

    public ZonedDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(ZonedDateTime fechaHora) { this.fechaHora = fechaHora; }
}
