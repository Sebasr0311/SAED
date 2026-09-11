package com.saed.backend.sanciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DescargoRequestDTO {
    @NotBlank(message = "Los argumentos de defensa son obligatorios")
    @Size(min = 10, message = "Los descargos deben contener al menos 10 caracteres")
    private String descargos;

    private String pruebasAdjuntasUrl;

    public DescargoRequestDTO() {}

    public String getDescargos() { return descargos; }
    public void setDescargos(String descargos) { this.descargos = descargos; }

    public String getPruebasAdjuntasUrl() { return pruebasAdjuntasUrl; }
    public void setPruebasAdjuntasUrl(String pruebasAdjuntasUrl) { this.pruebasAdjuntasUrl = pruebasAdjuntasUrl; }
}
