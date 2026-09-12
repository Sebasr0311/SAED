package com.saed.backend.automatizaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AccionRequestDTO {

    @NotBlank(message = "El tipo de acción es obligatorio")
    @Pattern(
        regexp = "ENVIAR_NOTIFICACION|GENERAR_MULTA|CREAR_TICKET_MANTENIMIENTO|REVOCAR_QR|ENVIAR_CORREO_ADMIN|GENERAR_TAREA_SLA|WEBHOOK_EXTERNO",
        message = "Tipo de acción no válido"
    )
    private String tipoAccion;

    @NotBlank(message = "Los parámetros de la acción en formato JSON son obligatorios")
    private String parametrosJson;

    private Integer ordenEjecucion;

    public AccionRequestDTO() {}

    public AccionRequestDTO(String tipoAccion, String parametrosJson, Integer ordenEjecucion) {
        this.tipoAccion = tipoAccion;
        this.parametrosJson = parametrosJson;
        this.ordenEjecucion = ordenEjecucion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tipoAccion;
        private String parametrosJson;
        private Integer ordenEjecucion;

        public Builder tipoAccion(String tipoAccion) { this.tipoAccion = tipoAccion; return this; }
        public Builder parametrosJson(String parametrosJson) { this.parametrosJson = parametrosJson; return this; }
        public Builder ordenEjecucion(Integer ordenEjecucion) { this.ordenEjecucion = ordenEjecucion; return this; }

        public AccionRequestDTO build() {
            return new AccionRequestDTO(tipoAccion, parametrosJson, ordenEjecucion);
        }
    }

    public String getTipoAccion() { return tipoAccion; }
    public void setTipoAccion(String tipoAccion) { this.tipoAccion = tipoAccion; }

    public String getParametrosJson() { return parametrosJson; }
    public void setParametrosJson(String parametrosJson) { this.parametrosJson = parametrosJson; }

    public Integer getOrdenEjecucion() { return ordenEjecucion; }
    public void setOrdenEjecucion(Integer ordenEjecucion) { this.ordenEjecucion = ordenEjecucion; }
}
