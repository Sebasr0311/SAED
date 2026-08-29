package com.saed.backend.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ReservaDTO {
    private Long idReserva;
    private Long idZona;
    private String nombreZona;
    private Long idUnidad;
    private Long idPersonaSolicita;
    private LocalDate fechaReserva;
    private String horaInicio;
    private String horaFin;
    private Integer cantidadAsistentes;
    private BigDecimal costoTotal;
    private String observaciones;
    private String estado;
    private ZonedDateTime fechaSolicitud;
    
    // Getters and Setters
    public Long getIdReserva() { return idReserva; }
    public void setIdReserva(Long idReserva) { this.idReserva = idReserva; }

    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }
    
    public String getNombreZona() { return nombreZona; }
    public void setNombreZona(String nombreZona) { this.nombreZona = nombreZona; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaSolicita() { return idPersonaSolicita; }
    public void setIdPersonaSolicita(Long idPersonaSolicita) { this.idPersonaSolicita = idPersonaSolicita; }

    public LocalDate getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDate fechaReserva) { this.fechaReserva = fechaReserva; }

    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }

    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }

    public Integer getCantidadAsistentes() { return cantidadAsistentes; }
    public void setCantidadAsistentes(Integer cantidadAsistentes) { this.cantidadAsistentes = cantidadAsistentes; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal costoTotal) { this.costoTotal = costoTotal; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public ZonedDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(ZonedDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
}
