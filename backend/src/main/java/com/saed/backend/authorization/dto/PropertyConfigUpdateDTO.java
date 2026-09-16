package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PropertyConfigUpdateDTO {
    @NotBlank(message = "El valor es requerido")
    @Size(max = 2000, message = "El valor no puede superar los 2000 caracteres")
    private String valor;

    @Size(max = 300, message = "La descripción no puede superar los 300 caracteres")
    private String descripcion;

    public PropertyConfigUpdateDTO() {}

    public PropertyConfigUpdateDTO(String valor) {
        this.valor = valor;
    }

    public PropertyConfigUpdateDTO(String valor, String descripcion) {
        this.valor = valor;
        this.descripcion = descripcion;
    }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
