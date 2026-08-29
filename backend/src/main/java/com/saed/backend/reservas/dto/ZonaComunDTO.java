package com.saed.backend.reservas.dto;

import java.math.BigDecimal;

public class ZonaComunDTO {
    private Long idZona;
    private String nombre;
    private String tipo;
    private Integer aforoMaximo;
    private String requiereReserva;
    private BigDecimal costoReserva;
    private String estado;

    // Getters and Setters
    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Integer getAforoMaximo() { return aforoMaximo; }
    public void setAforoMaximo(Integer aforoMaximo) { this.aforoMaximo = aforoMaximo; }

    public String getRequiereReserva() { return requiereReserva; }
    public void setRequiereReserva(String requiereReserva) { this.requiereReserva = requiereReserva; }

    public BigDecimal getCostoReserva() { return costoReserva; }
    public void setCostoReserva(BigDecimal costoReserva) { this.costoReserva = costoReserva; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
