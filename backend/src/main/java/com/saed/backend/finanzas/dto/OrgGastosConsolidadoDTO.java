package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrgGastosConsolidadoDTO {

    private BigDecimal totalGastado = BigDecimal.ZERO;
    private long cantidadGastos = 0;
    private long conSoporte = 0;
    private long sinSoporte = 0;
    private Map<String, BigDecimal> desgloseCategorias = new HashMap<>();
    private List<Map<String, Object>> desglosePropiedades = new ArrayList<>();
    private List<GastoResponseDTO> gastos = new ArrayList<>();

    public OrgGastosConsolidadoDTO() {}

    public BigDecimal getTotalGastado() { return totalGastado; }
    public void setTotalGastado(BigDecimal totalGastado) { this.totalGastado = totalGastado; }

    public long getCantidadGastos() { return cantidadGastos; }
    public void setCantidadGastos(long cantidadGastos) { this.cantidadGastos = cantidadGastos; }

    public long getConSoporte() { return conSoporte; }
    public void setConSoporte(long conSoporte) { this.conSoporte = conSoporte; }

    public long getSinSoporte() { return sinSoporte; }
    public void setSinSoporte(long sinSoporte) { this.sinSoporte = sinSoporte; }

    public Map<String, BigDecimal> getDesgloseCategorias() { return desgloseCategorias; }
    public void setDesgloseCategorias(Map<String, BigDecimal> desgloseCategorias) { this.desgloseCategorias = desgloseCategorias; }

    public List<Map<String, Object>> getDesglosePropiedades() { return desglosePropiedades; }
    public void setDesglosePropiedades(List<Map<String, Object>> desglosePropiedades) { this.desglosePropiedades = desglosePropiedades; }

    public List<GastoResponseDTO> getGastos() { return gastos; }
    public void setGastos(List<GastoResponseDTO> gastos) { this.gastos = gastos; }
}
