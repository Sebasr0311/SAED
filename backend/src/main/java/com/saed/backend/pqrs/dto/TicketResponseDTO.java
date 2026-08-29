package com.saed.backend.pqrs.dto;

import java.time.ZonedDateTime;

public class TicketResponseDTO {
    private Long idTicket;
    private String numeroRadicado;
    private String tipo;
    private String categoria;
    private String prioridad;
    private String asunto;
    private String descripcion;
    private String estado;
    private ZonedDateTime fechaRadicacion;
    private ZonedDateTime fechaLimiteSla;
    private Long responsableAsignado;
    
    // Getters and Setters
    public Long getIdTicket() { return idTicket; }
    public void setIdTicket(Long idTicket) { this.idTicket = idTicket; }
    
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
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    public ZonedDateTime getFechaRadicacion() { return fechaRadicacion; }
    public void setFechaRadicacion(ZonedDateTime fechaRadicacion) { this.fechaRadicacion = fechaRadicacion; }
    
    public ZonedDateTime getFechaLimiteSla() { return fechaLimiteSla; }
    public void setFechaLimiteSla(ZonedDateTime fechaLimiteSla) { this.fechaLimiteSla = fechaLimiteSla; }
    
    public Long getResponsableAsignado() { return responsableAsignado; }
    public void setResponsableAsignado(Long responsableAsignado) { this.responsableAsignado = responsableAsignado; }
}
