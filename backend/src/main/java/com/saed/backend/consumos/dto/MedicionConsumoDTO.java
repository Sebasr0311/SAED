package com.saed.backend.consumos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MedicionConsumoDTO {
    private Long idMedicion;
    private Long idPropiedad;
    private Long idUnidad;
    private String unidadIdentificador;
    private String tipoServicio;
    private String numeroMedidor;
    private String periodo;
    private BigDecimal lecturaAnterior;
    private BigDecimal lecturaActual;
    private BigDecimal consumoCalculado;
    private String unidadMedida;
    private BigDecimal tarifaUnitaria;
    private BigDecimal costoTotal;
    private String fotoMedidorUrl;
    private String anomaliaDetectada;
    private String observacionAnomalia;
    private Long leidoPor;
    private LocalDate fechaTomaLectura;

    public MedicionConsumoDTO() {}

    public Long getIdMedicion() { return idMedicion; }
    public void setIdMedicion(Long idMedicion) { this.idMedicion = idMedicion; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getUnidadIdentificador() { return unidadIdentificador; }
    public void setUnidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; }

    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }

    public String getNumeroMedidor() { return numeroMedidor; }
    public void setNumeroMedidor(String numeroMedidor) { this.numeroMedidor = numeroMedidor; }

    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }

    public BigDecimal getLecturaAnterior() { return lecturaAnterior; }
    public void setLecturaAnterior(BigDecimal lecturaAnterior) { this.lecturaAnterior = lecturaAnterior; }

    public BigDecimal getLecturaActual() { return lecturaActual; }
    public void setLecturaActual(BigDecimal lecturaActual) { this.lecturaActual = lecturaActual; }

    public BigDecimal getConsumoCalculado() { return consumoCalculado; }
    public void setConsumoCalculado(BigDecimal consumoCalculado) { this.consumoCalculado = consumoCalculado; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public BigDecimal getTarifaUnitaria() { return tarifaUnitaria; }
    public void setTarifaUnitaria(BigDecimal tarifaUnitaria) { this.tarifaUnitaria = tarifaUnitaria; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal costoTotal) { this.costoTotal = costoTotal; }

    public String getFotoMedidorUrl() { return fotoMedidorUrl; }
    public void setFotoMedidorUrl(String fotoMedidorUrl) { this.fotoMedidorUrl = fotoMedidorUrl; }

    public String getAnomaliaDetectada() { return anomaliaDetectada; }
    public void setAnomaliaDetectada(String anomaliaDetectada) { this.anomaliaDetectada = anomaliaDetectada; }

    public String getObservacionAnomalia() { return observacionAnomalia; }
    public void setObservacionAnomalia(String observacionAnomalia) { this.observacionAnomalia = observacionAnomalia; }

    public Long getLeidoPor() { return leidoPor; }
    public void setLeidoPor(Long leidoPor) { this.leidoPor = leidoPor; }

    public LocalDate getFechaTomaLectura() { return fechaTomaLectura; }
    public void setFechaTomaLectura(LocalDate fechaTomaLectura) { this.fechaTomaLectura = fechaTomaLectura; }
}
