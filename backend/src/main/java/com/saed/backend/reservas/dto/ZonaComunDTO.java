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
    // F08-01 booking policy (V5.61). Null means "use the default".
    private String horaApertura;
    private String horaCierre;
    private Integer anticipacionMinHoras;
    private Integer anticipacionMaxDias;
    private Integer maxReservasActivasUnidad;
    private BigDecimal valorDeposito;
    private Integer horasLimiteCancelacion;

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

    public String getHoraApertura() { return horaApertura; }
    public void setHoraApertura(String horaApertura) { this.horaApertura = horaApertura; }

    public String getHoraCierre() { return horaCierre; }
    public void setHoraCierre(String horaCierre) { this.horaCierre = horaCierre; }

    public Integer getAnticipacionMinHoras() { return anticipacionMinHoras; }
    public void setAnticipacionMinHoras(Integer anticipacionMinHoras) { this.anticipacionMinHoras = anticipacionMinHoras; }

    public Integer getAnticipacionMaxDias() { return anticipacionMaxDias; }
    public void setAnticipacionMaxDias(Integer anticipacionMaxDias) { this.anticipacionMaxDias = anticipacionMaxDias; }

    public Integer getMaxReservasActivasUnidad() { return maxReservasActivasUnidad; }
    public void setMaxReservasActivasUnidad(Integer maxReservasActivasUnidad) { this.maxReservasActivasUnidad = maxReservasActivasUnidad; }

    public BigDecimal getValorDeposito() { return valorDeposito; }
    public void setValorDeposito(BigDecimal valorDeposito) { this.valorDeposito = valorDeposito; }

    public Integer getHorasLimiteCancelacion() { return horasLimiteCancelacion; }
    public void setHorasLimiteCancelacion(Integer horasLimiteCancelacion) { this.horasLimiteCancelacion = horasLimiteCancelacion; }
}
