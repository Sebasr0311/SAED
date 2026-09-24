package com.saed.backend.dashboard.dto;

import java.time.OffsetDateTime;

public class HistorialReporteDTO {

    private Long idHistorialReporte;
    private Long idReporteConfig;
    private String nombreReporte;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String nombrePropiedad;
    private Long idUsuarioEjecuto;
    private String emailUsuario;
    private String formatoGenerado;
    private String parametrosFiltroJson;
    private String archivoGeneradoUrl;
    private String archivoSha256;
    private Long tiempoGeneracionMs;
    private Integer registrosProcesados;
    private OffsetDateTime fechaEjecucion;

    public HistorialReporteDTO() {}

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

    public Long getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Long idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getNombrePropiedad() {
        return nombrePropiedad;
    }

    public void setNombrePropiedad(String nombrePropiedad) {
        this.nombrePropiedad = nombrePropiedad;
    }

    public Long getIdUsuarioEjecuto() {
        return idUsuarioEjecuto;
    }

    public void setIdUsuarioEjecuto(Long idUsuarioEjecuto) {
        this.idUsuarioEjecuto = idUsuarioEjecuto;
    }

    public String getEmailUsuario() {
        return emailUsuario;
    }

    public void setEmailUsuario(String emailUsuario) {
        this.emailUsuario = emailUsuario;
    }

    public String getFormatoGenerado() {
        return formatoGenerado;
    }

    public void setFormatoGenerado(String formatoGenerado) {
        this.formatoGenerado = formatoGenerado;
    }

    public String getParametrosFiltroJson() {
        return parametrosFiltroJson;
    }

    public void setParametrosFiltroJson(String parametrosFiltroJson) {
        this.parametrosFiltroJson = parametrosFiltroJson;
    }

    public String getArchivoGeneradoUrl() {
        return archivoGeneradoUrl;
    }

    public void setArchivoGeneradoUrl(String archivoGeneradoUrl) {
        this.archivoGeneradoUrl = archivoGeneradoUrl;
    }

    public String getArchivoSha256() {
        return archivoSha256;
    }

    public void setArchivoSha256(String archivoSha256) {
        this.archivoSha256 = archivoSha256;
    }

    public Long getTiempoGeneracionMs() {
        return tiempoGeneracionMs;
    }

    public void setTiempoGeneracionMs(Long tiempoGeneracionMs) {
        this.tiempoGeneracionMs = tiempoGeneracionMs;
    }

    public Integer getRegistrosProcesados() {
        return registrosProcesados;
    }

    public void setRegistrosProcesados(Integer registrosProcesados) {
        this.registrosProcesados = registrosProcesados;
    }

    public OffsetDateTime getFechaEjecucion() {
        return fechaEjecucion;
    }

    public void setFechaEjecucion(OffsetDateTime fechaEjecucion) {
        this.fechaEjecucion = fechaEjecucion;
    }
}
