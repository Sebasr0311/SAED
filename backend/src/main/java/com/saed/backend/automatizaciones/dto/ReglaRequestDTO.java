package com.saed.backend.automatizaciones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ReglaRequestDTO {

    @NotNull(message = "El evento disparador es obligatorio")
    private Long idEvento;

    private Long idPropiedad;

    @NotBlank(message = "El nombre de la regla es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder los 120 caracteres")
    private String nombre;

    @Size(max = 300, message = "La descripción no puede exceder los 300 caracteres")
    private String descripcion;

    private String condicionJson;

    @Pattern(regexp = "ACTIVA|INACTIVA", message = "El estado debe ser ACTIVA o INACTIVA")
    private String estado;

    @Valid
    private List<AccionRequestDTO> acciones;

    public ReglaRequestDTO() {}

    public ReglaRequestDTO(Long idEvento, Long idPropiedad, String nombre, String descripcion,
                           String condicionJson, String estado, List<AccionRequestDTO> acciones) {
        this.idEvento = idEvento;
        this.idPropiedad = idPropiedad;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.condicionJson = condicionJson;
        this.estado = estado;
        this.acciones = acciones;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idEvento;
        private Long idPropiedad;
        private String nombre;
        private String descripcion;
        private String condicionJson;
        private String estado;
        private List<AccionRequestDTO> acciones;

        public Builder idEvento(Long idEvento) { this.idEvento = idEvento; return this; }
        public Builder idPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder descripcion(String descripcion) { this.descripcion = descripcion; return this; }
        public Builder condicionJson(String condicionJson) { this.condicionJson = condicionJson; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder acciones(List<AccionRequestDTO> acciones) { this.acciones = acciones; return this; }

        public ReglaRequestDTO build() {
            return new ReglaRequestDTO(idEvento, idPropiedad, nombre, descripcion, condicionJson, estado, acciones);
        }
    }

    public Long getIdEvento() { return idEvento; }
    public void setIdEvento(Long idEvento) { this.idEvento = idEvento; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCondicionJson() { return condicionJson; }
    public void setCondicionJson(String condicionJson) { this.condicionJson = condicionJson; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public List<AccionRequestDTO> getAcciones() { return acciones; }
    public void setAcciones(List<AccionRequestDTO> acciones) { this.acciones = acciones; }
}
