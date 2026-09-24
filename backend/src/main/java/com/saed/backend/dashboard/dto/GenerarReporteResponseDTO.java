package com.saed.backend.dashboard.dto;

import java.time.OffsetDateTime;

public class GenerarReporteResponseDTO {

    private Long idHistorialReporte;
    private Long idReporteConfig;
    private String nombreReporte;
    private String formato;
    private String archivoUrl;
    private String archivoSha256;
    private Integer totalRegistros;
    private Long tiempoGeneracionMs;
    private OffsetDateTime fechaEjecucion;

    public GenerarReporteResponseDTO() {}

    public GenerarReporteResponseDTO(Long idHistorialReporte, Long idReporteConfig, String nombreReporte,
                                     String formato, String archivoUrl, String archivoSha256,
                                     Integer totalRegistros, Long tiempoGeneracionMs, OffsetDateTime fechaEjecucion) {
        this.idHistorialReporte = idHistorialReporte;
        this.idReporteConfig = idReporteConfig;
        this.nombreReporte = nombreReporte;
        this.formato = formato;
        this.archivoUrl = archivoUrl;
        this.archivoSha256 = archivoSha256;
        this.totalRegistros = totalRegistros;
        this.tiempoGeneracionMs = tiempoGeneracionMs;
        this.fechaEjecucion = fechaEjecucion;
    }

    public Long getIdHistorialReporte() {
        return idHistorialReporte;
    }

    public void setIdHistorialReporte(Long idHistorialReporte) {
        this.idHistorialReporte = idHistorialReporte;
    }

    public Long getIdReporteConfig() {
        return idReporteConfig;
    }

    public void setIdReporteConfig(Long idReporteConfig) {
        this.idReporteConfig = idReporteConfig;
    }

    public String getNombreReporte() {
        return nombreReporte;
    }

    public void setNombreReporte(String nombreReporte) {
        this.nombreReporte = nombreReporte;
    }

    public String getFormato() {
        return formato;
    }

    public void setFormato(String formato) {
        this.formato = formato;
    }

    public String getArchivoUrl() {
        return archivoUrl;
    }

    public void setArchivoUrl(String archivoUrl) {
        this.archivoUrl = archivoUrl;
    }

    public String getArchivoSha256() {
        return archivoSha256;
    }

    public void setArchivoSha256(String archivoSha256) {
        this.archivoSha256 = archivoSha256;
    }

    public Integer getTotalRegistros() {
        return totalRegistros;
    }

    public void setTotalRegistros(Integer totalRegistros) {
        this.totalRegistros = totalRegistros;
    }

    public Long getTiempoGeneracionMs() {
        return tiempoGeneracionMs;
    }

    public void setTiempoGeneracionMs(Long tiempoGeneracionMs) {
        this.tiempoGeneracionMs = tiempoGeneracionMs;
    }

    public OffsetDateTime getFechaEjecucion() {
        return fechaEjecucion;
    }

    public void setFechaEjecucion(OffsetDateTime fechaEjecucion) {
        this.fechaEjecucion = fechaEjecucion;
    }
}
