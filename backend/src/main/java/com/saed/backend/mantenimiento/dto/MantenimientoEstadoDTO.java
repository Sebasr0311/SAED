package com.saed.backend.mantenimiento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public class MantenimientoEstadoDTO {

    @NotBlank(message = "El nuevo estado es obligatorio")
    @Pattern(regexp = "^(?i)(PROGRAMADO|EN_PROCESO|COMPLETADO|CANCELADO|REPROGRAMADO)$", message = "Estado no válido para mantenimiento")
    private String nuevoEstado;

    @DecimalMin(value = "0.0", message = "El costo real no puede ser negativo")
    private BigDecimal costoReal;

    private Instant fechaEjecucion;

    @Size(max = 500, message = "Las notas de cierre no pueden exceder 500 caracteres")
    private String notasCierre;

    @Size(max = 500, message = "La URL del informe técnico no puede exceder 500 caracteres")
    private String informeTecnicoUrl;

    @Size(max = 500, message = "La URL de la evidencia después no puede exceder 500 caracteres")
    private String evidenciaDespuesUrl;

    public MantenimientoEstadoDTO() {
    }

    public String getNuevoEstado() {
        return nuevoEstado;
    }

    public void setNuevoEstado(String nuevoEstado) {
        this.nuevoEstado = nuevoEstado;
    }

    public BigDecimal getCostoReal() {
        return costoReal;
    }

    public void setCostoReal(BigDecimal costoReal) {
        this.costoReal = costoReal;
    }

    public Instant getFechaEjecucion() {
        return fechaEjecucion;
    }

    public void setFechaEjecucion(Instant fechaEjecucion) {
        this.fechaEjecucion = fechaEjecucion;
    }

    public String getNotasCierre() {
        return notasCierre;
    }

    public void setNotasCierre(String notasCierre) {
        this.notasCierre = notasCierre;
    }

    public String getInformeTecnicoUrl() {
        return informeTecnicoUrl;
    }

    public void setInformeTecnicoUrl(String informeTecnicoUrl) {
        this.informeTecnicoUrl = informeTecnicoUrl;
    }

    public String getEvidenciaDespuesUrl() {
        return evidenciaDespuesUrl;
    }

    public void setEvidenciaDespuesUrl(String evidenciaDespuesUrl) {
        this.evidenciaDespuesUrl = evidenciaDespuesUrl;
    }
}
