package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VotacionCreateRequestDTO {

    @NotNull(message = "El punto del orden del día es obligatorio")
    private Integer puntoOrdenDia;

    @NotBlank(message = "El título de la votación es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @NotBlank(message = "El tipo de mayoría requerida es obligatorio")
    @Pattern(regexp = "SIMPLE_50_MAS_1|CALIFICADA_70_PCT|UNANIMIDAD_100_PCT", message = "Tipo de mayoría inválido")
    private String tipoMayoriaRequerida;

    public VotacionCreateRequestDTO() {}

    public VotacionCreateRequestDTO(Integer puntoOrdenDia, String titulo, String descripcion, String tipoMayoriaRequerida) {
        this.puntoOrdenDia = puntoOrdenDia;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tipoMayoriaRequerida = tipoMayoriaRequerida;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer puntoOrdenDia;
        private String titulo;
        private String descripcion;
        private String tipoMayoriaRequerida;

        public Builder puntoOrdenDia(Integer puntoOrdenDia) { this.puntoOrdenDia = puntoOrdenDia; return this; }
        public Builder titulo(String titulo) { this.titulo = titulo; return this; }
        public Builder descripcion(String descripcion) { this.descripcion = descripcion; return this; }
        public Builder tipoMayoriaRequerida(String tipoMayoriaRequerida) { this.tipoMayoriaRequerida = tipoMayoriaRequerida; return this; }

        public VotacionCreateRequestDTO build() {
            return new VotacionCreateRequestDTO(puntoOrdenDia, titulo, descripcion, tipoMayoriaRequerida);
        }
    }

    public Integer getPuntoOrdenDia() { return puntoOrdenDia; }
    public void setPuntoOrdenDia(Integer puntoOrdenDia) { this.puntoOrdenDia = puntoOrdenDia; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipoMayoriaRequerida() { return tipoMayoriaRequerida; }
    public void setTipoMayoriaRequerida(String tipoMayoriaRequerida) { this.tipoMayoriaRequerida = tipoMayoriaRequerida; }
}
