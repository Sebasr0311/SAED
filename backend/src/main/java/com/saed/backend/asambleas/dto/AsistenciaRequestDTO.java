package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class AsistenciaRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id de la persona asistente es obligatorio")
    private Long idPersonaAsistente;

    @NotBlank(message = "Debe indicar si es propietario directo (S/N)")
    @Pattern(regexp = "S|N", message = "Valor debe ser 'S' o 'N'")
    private String esPropietarioDirecto;

    private Long idPoder;

    private BigDecimal coeficientePonderado;

    public AsistenciaRequestDTO() {}

    public AsistenciaRequestDTO(Long idUnidad, Long idPersonaAsistente, String esPropietarioDirecto, Long idPoder, BigDecimal coeficientePonderado) {
        this.idUnidad = idUnidad;
        this.idPersonaAsistente = idPersonaAsistente;
        this.esPropietarioDirecto = esPropietarioDirecto;
        this.idPoder = idPoder;
        this.coeficientePonderado = coeficientePonderado;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idUnidad;
        private Long idPersonaAsistente;
        private String esPropietarioDirecto;
        private Long idPoder;
        private BigDecimal coeficientePonderado;

        public Builder idUnidad(Long idUnidad) { this.idUnidad = idUnidad; return this; }
        public Builder idPersonaAsistente(Long idPersonaAsistente) { this.idPersonaAsistente = idPersonaAsistente; return this; }
        public Builder esPropietarioDirecto(String esPropietarioDirecto) { this.esPropietarioDirecto = esPropietarioDirecto; return this; }
        public Builder idPoder(Long idPoder) { this.idPoder = idPoder; return this; }
        public Builder coeficientePonderado(BigDecimal coeficientePonderado) { this.coeficientePonderado = coeficientePonderado; return this; }

        public AsistenciaRequestDTO build() {
            return new AsistenciaRequestDTO(idUnidad, idPersonaAsistente, esPropietarioDirecto, idPoder, coeficientePonderado);
        }
    }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaAsistente() { return idPersonaAsistente; }
    public void setIdPersonaAsistente(Long idPersonaAsistente) { this.idPersonaAsistente = idPersonaAsistente; }

    public String getEsPropietarioDirecto() { return esPropietarioDirecto; }
    public void setEsPropietarioDirecto(String esPropietarioDirecto) { this.esPropietarioDirecto = esPropietarioDirecto; }

    public Long getIdPoder() { return idPoder; }
    public void setIdPoder(Long idPoder) { this.idPoder = idPoder; }

    public BigDecimal getCoeficientePonderado() { return coeficientePonderado; }
    public void setCoeficientePonderado(BigDecimal coeficientePonderado) { this.coeficientePonderado = coeficientePonderado; }
}
