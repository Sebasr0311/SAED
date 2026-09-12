package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PoderDecisionDTO {

    @NotBlank(message = "La decisión es obligatoria")
    @Pattern(regexp = "APROBADO|RECHAZADO", message = "Decisión inválida (debe ser APROBADO o RECHAZADO)")
    private String estado;

    public PoderDecisionDTO() {}

    public PoderDecisionDTO(String estado) {
        this.estado = estado;
    }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
