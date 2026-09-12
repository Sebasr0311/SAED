package com.saed.backend.consumos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MedicionConsumoRequestDTO {

    private Long idUnidad;

    @NotBlank(message = "El tipo de servicio es obligatorio")
    @Pattern(regexp = "^(AGUA|ENERGIA|GAS|SOLAR|OTRO)$", message = "Tipo de servicio inválido (AGUA, ENERGIA, GAS, SOLAR, OTRO)")
    private String tipoServicio;

    @NotBlank(message = "El número de medidor es obligatorio")
    @Size(max = 50, message = "El número de medidor no puede exceder 50 caracteres")
    private String numeroMedidor;

    @NotBlank(message = "El período es obligatorio")
    @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$", message = "El período debe tener formato YYYY-MM (ej. 2026-09)")
    private String periodo;

    @NotNull(message = "La lectura anterior es obligatoria")
    private BigDecimal lecturaAnterior;

    @NotNull(message = "La lectura actual es obligatoria")
    private BigDecimal lecturaActual;

    @Size(max = 10, message = "La unidad de medida no puede exceder 10 caracteres")
    private String unidadMedida;

    private BigDecimal tarifaUnitaria;

    @Size(max = 500, message = "La URL de la foto no puede exceder 500 caracteres")
    private String fotoMedidorUrl;

    @Pattern(regexp = "^[SN]$", message = "anomaliaDetectada debe ser 'S' o 'N'")
    private String anomaliaDetectada;

    @Size(max = 300, message = "La observación no puede exceder 300 caracteres")
    private String observacionAnomalia;

    private LocalDate fechaTomaLectura;

    public MedicionConsumoRequestDTO() {}

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

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

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public BigDecimal getTarifaUnitaria() { return tarifaUnitaria; }
    public void setTarifaUnitaria(BigDecimal tarifaUnitaria) { this.tarifaUnitaria = tarifaUnitaria; }

    public String getFotoMedidorUrl() { return fotoMedidorUrl; }
    public void setFotoMedidorUrl(String fotoMedidorUrl) { this.fotoMedidorUrl = fotoMedidorUrl; }

    public String getAnomaliaDetectada() { return anomaliaDetectada; }
    public void setAnomaliaDetectada(String anomaliaDetectada) { this.anomaliaDetectada = anomaliaDetectada; }

    public String getObservacionAnomalia() { return observacionAnomalia; }
    public void setObservacionAnomalia(String observacionAnomalia) { this.observacionAnomalia = observacionAnomalia; }

    public LocalDate getFechaTomaLectura() { return fechaTomaLectura; }
    public void setFechaTomaLectura(LocalDate fechaTomaLectura) { this.fechaTomaLectura = fechaTomaLectura; }
}
