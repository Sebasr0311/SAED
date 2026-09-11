package com.saed.backend.sanciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SancionCreateRequestDTO {
    @NotNull(message = "idUnidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "idPersonaImputada es obligatorio")
    private Long idPersonaImputada;

    private Long idIncidenteOrigen;

    @NotBlank(message = "tipoFalta es obligatorio")
    private String tipoFalta;

    private String gravedad = "LEVE";

    @NotBlank(message = "descripcionHechos es obligatoria")
    private String descripcionHechos;

    private String articuloReglamentoViolado;

    private String tipoSancionPropuesta = "AMONESTACION_ESCRITA";

    private String evidenciasUrls;

    private Integer diasParaDescargos = 10;

    public SancionCreateRequestDTO() {}

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaImputada() { return idPersonaImputada; }
    public void setIdPersonaImputada(Long idPersonaImputada) { this.idPersonaImputada = idPersonaImputada; }

    public Long getIdIncidenteOrigen() { return idIncidenteOrigen; }
    public void setIdIncidenteOrigen(Long idIncidenteOrigen) { this.idIncidenteOrigen = idIncidenteOrigen; }

    public String getTipoFalta() { return tipoFalta; }
    public void setTipoFalta(String tipoFalta) { this.tipoFalta = tipoFalta; }

    public String getGravedad() { return gravedad; }
    public void setGravedad(String gravedad) { this.gravedad = gravedad; }

    public String getDescripcionHechos() { return descripcionHechos; }
    public void setDescripcionHechos(String descripcionHechos) { this.descripcionHechos = descripcionHechos; }

    public String getArticuloReglamentoViolado() { return articuloReglamentoViolado; }
    public void setArticuloReglamentoViolado(String articuloReglamentoViolado) { this.articuloReglamentoViolado = articuloReglamentoViolado; }

    public String getTipoSancionPropuesta() { return tipoSancionPropuesta; }
    public void setTipoSancionPropuesta(String tipoSancionPropuesta) { this.tipoSancionPropuesta = tipoSancionPropuesta; }

    public String getEvidenciasUrls() { return evidenciasUrls; }
    public void setEvidenciasUrls(String evidenciasUrls) { this.evidenciasUrls = evidenciasUrls; }

    public Integer getDiasParaDescargos() { return diasParaDescargos; }
    public void setDiasParaDescargos(Integer diasParaDescargos) { this.diasParaDescargos = diasParaDescargos; }
}
