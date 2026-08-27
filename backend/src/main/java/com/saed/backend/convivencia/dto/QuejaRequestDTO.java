package com.saed.backend.convivencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class QuejaRequestDTO {
    @NotBlank private String tipo;
    @NotBlank private String categoria;
    @NotBlank private String titulo;
    @NotBlank private String descripcion;
    private Long idMulta;

    public String getTipo() { return this.tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getCategoria() { return this.categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getTitulo() { return this.titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return this.descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Long getIdMulta() { return this.idMulta; }
    public void setIdMulta(Long idMulta) { this.idMulta = idMulta; }
}
