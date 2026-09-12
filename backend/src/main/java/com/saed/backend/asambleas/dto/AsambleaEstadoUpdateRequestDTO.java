package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AsambleaEstadoUpdateRequestDTO {

    @NotBlank(message = "El nuevo estado es obligatorio")
    @Pattern(regexp = "CONVOCADA|EN_CURSO|EN_RECESO|FINALIZADA|CANCELADA", message = "Estado de asamblea inválido")
    private String nuevoEstado;

    public AsambleaEstadoUpdateRequestDTO() {}

    public AsambleaEstadoUpdateRequestDTO(String nuevoEstado) {
        this.nuevoEstado = nuevoEstado;
    }

    public String getNuevoEstado() { return nuevoEstado; }
    public void setNuevoEstado(String nuevoEstado) { this.nuevoEstado = nuevoEstado; }
}
