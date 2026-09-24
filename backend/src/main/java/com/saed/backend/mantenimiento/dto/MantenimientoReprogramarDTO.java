package com.saed.backend.mantenimiento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public class MantenimientoReprogramarDTO {

    @NotNull(message = "La nueva fecha programada es obligatoria")
    private LocalDate nuevaFechaProgramada;

    private Instant nuevaFechaInicioBloqueo;
    private Instant nuevaFechaFinBloqueo;

    @Size(max = 500, message = "El motivo de reprogramación no puede exceder 500 caracteres")
    private String motivo;

    public MantenimientoReprogramarDTO() {
    }

    public LocalDate getNuevaFechaProgramada() {
        return nuevaFechaProgramada;
    }

    public void setNuevaFechaProgramada(LocalDate nuevaFechaProgramada) {
        this.nuevaFechaProgramada = nuevaFechaProgramada;
    }

    public Instant getNuevaFechaInicioBloqueo() {
        return nuevaFechaInicioBloqueo;
    }

    public void setNuevaFechaInicioBloqueo(Instant nuevaFechaInicioBloqueo) {
        this.nuevaFechaInicioBloqueo = nuevaFechaInicioBloqueo;
    }

    public Instant getNuevaFechaFinBloqueo() {
        return nuevaFechaFinBloqueo;
    }

    public void setNuevaFechaFinBloqueo(Instant nuevaFechaFinBloqueo) {
        this.nuevaFechaFinBloqueo = nuevaFechaFinBloqueo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
