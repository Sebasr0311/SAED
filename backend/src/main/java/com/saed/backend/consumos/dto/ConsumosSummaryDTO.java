package com.saed.backend.consumos.dto;

import java.math.BigDecimal;

public class ConsumosSummaryDTO {
    private int totalMediciones;
    private BigDecimal consumoTotalAgua;
    private BigDecimal consumoTotalEnergia;
    private BigDecimal consumoTotalGas;
    private BigDecimal costoTotalPeriodo;
    private int anomaliasDetectadas;

    public ConsumosSummaryDTO() {
        this.consumoTotalAgua = BigDecimal.ZERO;
        this.consumoTotalEnergia = BigDecimal.ZERO;
        this.consumoTotalGas = BigDecimal.ZERO;
        this.costoTotalPeriodo = BigDecimal.ZERO;
    }

    public int getTotalMediciones() { return totalMediciones; }
    public void setTotalMediciones(int totalMediciones) { this.totalMediciones = totalMediciones; }

    public BigDecimal getConsumoTotalAgua() { return consumoTotalAgua; }
    public void setConsumoTotalAgua(BigDecimal consumoTotalAgua) { this.consumoTotalAgua = consumoTotalAgua; }

    public BigDecimal getConsumoTotalEnergia() { return consumoTotalEnergia; }
    public void setConsumoTotalEnergia(BigDecimal consumoTotalEnergia) { this.consumoTotalEnergia = consumoTotalEnergia; }

    public BigDecimal getConsumoTotalGas() { return consumoTotalGas; }
    public void setConsumoTotalGas(BigDecimal consumoTotalGas) { this.consumoTotalGas = consumoTotalGas; }

    public BigDecimal getCostoTotalPeriodo() { return costoTotalPeriodo; }
    public void setCostoTotalPeriodo(BigDecimal costoTotalPeriodo) { this.costoTotalPeriodo = costoTotalPeriodo; }

    public int getAnomaliasDetectadas() { return anomaliasDetectadas; }
    public void setAnomaliasDetectadas(int anomaliasDetectadas) { this.anomaliasDetectadas = anomaliasDetectadas; }
}
