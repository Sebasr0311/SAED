package com.saed.backend.incidentes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class IncidenteEscalamientoRequestDTO {

    @NotBlank(message = "El motivo de escalamiento es obligatorio")
    @Size(max = 500)
    private String motivo;

    @Size(max = 250)
    private String sancionSugerida;

    public IncidenteEscalamientoRequestDTO() {}

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getSancionSugerida() { return sancionSugerida; }
    public void setSancionSugerida(String sancionSugerida) { this.sancionSugerida = sancionSugerida; }
}
