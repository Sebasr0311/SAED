package com.saed.backend.incidentes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncidenteInvolucradoDTO {
    private Long idIncidenteInvolucrado;
    private Long idIncidente;
    private Long idPersona;
    private Long idVehiculo;

    @Size(max = 150)
    private String nombreIdentificacionExterna;

    @NotNull
    @Size(max = 25)
    private String rolEnIncidente; // 'AFECTADO', 'PRESUNTO_INFRACTOR', 'TESTIGO', 'INFORMADOR', 'OTRO'

    @Size(max = 500)
    private String declaracionTestimonio;

    // Projection helpers
    private String nombrePersona;
    private String documentoPersona;
    private String placaVehiculo;

    public IncidenteInvolucradoDTO() {}

    public Long getIdIncidenteInvolucrado() { return idIncidenteInvolucrado; }
    public void setIdIncidenteInvolucrado(Long idIncidenteInvolucrado) { this.idIncidenteInvolucrado = idIncidenteInvolucrado; }

    public Long getIdIncidente() { return idIncidente; }
    public void setIdIncidente(Long idIncidente) { this.idIncidente = idIncidente; }

    public Long getIdPersona() { return idPersona; }
    public void setIdPersona(Long idPersona) { this.idPersona = idPersona; }

    public Long getIdVehiculo() { return idVehiculo; }
    public void setIdVehiculo(Long idVehiculo) { this.idVehiculo = idVehiculo; }

    public String getNombreIdentificacionExterna() { return nombreIdentificacionExterna; }
    public void setNombreIdentificacionExterna(String nombreIdentificacionExterna) { this.nombreIdentificacionExterna = nombreIdentificacionExterna; }

    public String getRolEnIncidente() { return rolEnIncidente; }
    public void setRolEnIncidente(String rolEnIncidente) { this.rolEnIncidente = rolEnIncidente; }

    public String getDeclaracionTestimonio() { return declaracionTestimonio; }
    public void setDeclaracionTestimonio(String declaracionTestimonio) { this.declaracionTestimonio = declaracionTestimonio; }

    public String getNombrePersona() { return nombrePersona; }
    public void setNombrePersona(String nombrePersona) { this.nombrePersona = nombrePersona; }

    public String getDocumentoPersona() { return documentoPersona; }
    public void setDocumentoPersona(String documentoPersona) { this.documentoPersona = documentoPersona; }

    public String getPlacaVehiculo() { return placaVehiculo; }
    public void setPlacaVehiculo(String placaVehiculo) { this.placaVehiculo = placaVehiculo; }
}
