package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class VotoRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id de la persona votante es obligatorio")
    private Long idPersonaVotante;

    @NotBlank(message = "La opción de voto es obligatoria")
    @Pattern(regexp = "SI|NO|BLANCO|ABSTENCION", message = "Opción de voto inválida (SI, NO, BLANCO, ABSTENCION)")
    private String opcionVoto;

    private BigDecimal coeficienteVoto;

    public VotoRequestDTO() {}

    public VotoRequestDTO(Long idUnidad, Long idPersonaVotante, String opcionVoto, BigDecimal coeficienteVoto) {
        this.idUnidad = idUnidad;
        this.idPersonaVotante = idPersonaVotante;
        this.opcionVoto = opcionVoto;
        this.coeficienteVoto = coeficienteVoto;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idUnidad;
        private Long idPersonaVotante;
        private String opcionVoto;
        private BigDecimal coeficienteVoto;

        public Builder idUnidad(Long idUnidad) { this.idUnidad = idUnidad; return this; }
        public Builder idPersonaVotante(Long idPersonaVotante) { this.idPersonaVotante = idPersonaVotante; return this; }
        public Builder opcionVoto(String opcionVoto) { this.opcionVoto = opcionVoto; return this; }
        public Builder coeficienteVoto(BigDecimal coeficienteVoto) { this.coeficienteVoto = coeficienteVoto; return this; }

        public VotoRequestDTO build() {
            return new VotoRequestDTO(idUnidad, idPersonaVotante, opcionVoto, coeficienteVoto);
        }
    }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaVotante() { return idPersonaVotante; }
    public void setIdPersonaVotante(Long idPersonaVotante) { this.idPersonaVotante = idPersonaVotante; }

    public String getOpcionVoto() { return opcionVoto; }
    public void setOpcionVoto(String opcionVoto) { this.opcionVoto = opcionVoto; }

    public BigDecimal getCoeficienteVoto() { return coeficienteVoto; }
    public void setCoeficienteVoto(BigDecimal coeficienteVoto) { this.coeficienteVoto = coeficienteVoto; }
}
