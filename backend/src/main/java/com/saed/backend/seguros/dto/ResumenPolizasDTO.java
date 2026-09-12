package com.saed.backend.seguros.dto;

import java.math.BigDecimal;

public class ResumenPolizasDTO {
    private int totalPolizas;
    private int vigentes;
    private int porVencer;
    private int vencidas;
    private int canceladas;
    private BigDecimal valorAseguradoTotal;
    private BigDecimal primaAnualTotal;

    public ResumenPolizasDTO() {
        this.valorAseguradoTotal = BigDecimal.ZERO;
        this.primaAnualTotal = BigDecimal.ZERO;
    }

    public ResumenPolizasDTO(int totalPolizas, int vigentes, int porVencer, int vencidas, int canceladas, BigDecimal valorAseguradoTotal, BigDecimal primaAnualTotal) {
        this.totalPolizas = totalPolizas;
        this.vigentes = vigentes;
        this.porVencer = porVencer;
        this.vencidas = vencidas;
        this.canceladas = canceladas;
        this.valorAseguradoTotal = valorAseguradoTotal != null ? valorAseguradoTotal : BigDecimal.ZERO;
        this.primaAnualTotal = primaAnualTotal != null ? primaAnualTotal : BigDecimal.ZERO;
    }

    public int getTotalPolizas() { return totalPolizas; }
    public void setTotalPolizas(int totalPolizas) { this.totalPolizas = totalPolizas; }

    public int getVigentes() { return vigentes; }
    public void setVigentes(int vigentes) { this.vigentes = vigentes; }

    public int getPorVencer() { return porVencer; }
    public void setPorVencer(int porVencer) { this.porVencer = porVencer; }

    public int getVencidas() { return vencidas; }
    public void setVencidas(int vencidas) { this.vencidas = vencidas; }

    public int getCanceladas() { return canceladas; }
    public void setCanceladas(int canceladas) { this.canceladas = canceladas; }

    public BigDecimal getValorAseguradoTotal() { return valorAseguradoTotal; }
    public void setValorAseguradoTotal(BigDecimal valorAseguradoTotal) { this.valorAseguradoTotal = valorAseguradoTotal; }

    public BigDecimal getPrimaAnualTotal() { return primaAnualTotal; }
    public void setPrimaAnualTotal(BigDecimal primaAnualTotal) { this.primaAnualTotal = primaAnualTotal; }
}
