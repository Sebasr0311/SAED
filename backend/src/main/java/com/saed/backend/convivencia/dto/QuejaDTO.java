package com.saed.backend.convivencia.dto;

import java.time.LocalDateTime;

public class QuejaDTO {
    private Long idQueja;
    private String radicado;
    private String tipo;
    private String categoria;
    private String prioridad;
    private String titulo;
    private String descripcion;
    private String estado;
    private String respuesta;
    private String autor;
    private String apartamento;
    private LocalDateTime fecha;

    public Long getIdQueja() { return this.idQueja; }
    public void setIdQueja(Long idQueja) { this.idQueja = idQueja; }
    public String getRadicado() { return this.radicado; }
    public void setRadicado(String radicado) { this.radicado = radicado; }
    public String getTipo() { return this.tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getCategoria() { return this.categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getPrioridad() { return this.prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getTitulo() { return this.titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return this.descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getEstado() { return this.estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getRespuesta() { return this.respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public String getAutor() { return this.autor; }
    public void setAutor(String autor) { this.autor = autor; }
    public String getApartamento() { return this.apartamento; }
    public void setApartamento(String apartamento) { this.apartamento = apartamento; }
    public LocalDateTime getFecha() { return this.fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
