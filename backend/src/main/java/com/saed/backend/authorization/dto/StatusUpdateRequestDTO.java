package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;

public class StatusUpdateRequestDTO {
    @NotBlank private String estado;

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
