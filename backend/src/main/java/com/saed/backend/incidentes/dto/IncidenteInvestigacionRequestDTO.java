package com.saed.backend.incidentes.dto;

import jakarta.validation.constraints.Size;

public class IncidenteInvestigacionRequestDTO {

    @Size(max = 1000)
    private String hallazgos;

    @Size(max = 500)
    private String conclusiones;

    @Size(max = 250)
    private String sancionSugerida;

    private String estadoDestino;

    public IncidenteInvestigacionRequestDTO() {}

    public String getHallazgos() { return hallazgos; }
    public void setHallazgos(String hallazgos) { this.hallazgos = hallazgos; }

    public String getConclusiones() { return conclusiones; }
    public void setConclusiones(String conclusiones) { this.conclusiones = conclusiones; }

    public String getSancionSugerida() { return sancionSugerida; }
    public void setSancionSugerida(String sancionSugerida) { this.sancionSugerida = sancionSugerida; }

    public String getEstadoDestino() { return estadoDestino; }
    public void setEstadoDestino(String estadoDestino) { this.estadoDestino = estadoDestino; }
}
