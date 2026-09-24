package com.saed.backend.incidentes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class IncidenteEstadoRequestDTO {

    @NotBlank(message = "El estado destino es obligatorio")
    @Size(max = 25)
    private String estado;

    @Size(max = 500)
    private String conclusiones;

    @Size(max = 500)
    private String motivo;

    public IncidenteEstadoRequestDTO() {}

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getConclusiones() { return conclusiones; }
    public void setConclusiones(String conclusiones) { this.conclusiones = conclusiones; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
