package com.saed.backend.consumos.dto;

import java.math.BigDecimal;

public class ConsumoTendenciaDTO {
    private String periodo;
    private String tipoServicio;
    private BigDecimal consumoTotal;
    private BigDecimal costoTotal;
    private int cantidadMediciones;

    public ConsumoTendenciaDTO() {
        this.consumoTotal = BigDecimal.ZERO;
        this.costoTotal = BigDecimal.ZERO;
    }

    public ConsumoTendenciaDTO(String periodo, String tipoServicio, BigDecimal consumoTotal, BigDecimal costoTotal, int cantidadMediciones) {
        this.periodo = periodo;
        this.tipoServicio = tipoServicio;
        this.consumoTotal = consumoTotal != null ? consumoTotal : BigDecimal.ZERO;
        this.costoTotal = costoTotal != null ? costoTotal : BigDecimal.ZERO;
        this.cantidadMediciones = cantidadMediciones;
    }

    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }

    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }

    public BigDecimal getConsumoTotal() { return consumoTotal; }
    public void setConsumoTotal(BigDecimal consumoTotal) { this.consumoTotal = consumoTotal; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal costoTotal) { this.costoTotal = costoTotal; }

    public int getCantidadMediciones() { return cantidadMediciones; }
    public void setCantidadMediciones(int cantidadMediciones) { this.cantidadMediciones = cantidadMediciones; }
}
