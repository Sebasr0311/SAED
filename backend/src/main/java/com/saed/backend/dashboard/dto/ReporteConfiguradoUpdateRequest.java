package com.saed.backend.dashboard.dto;

import jakarta.validation.constraints.Size;

public class ReporteConfiguradoUpdateRequest {

    @Size(max = 150)
    private String nombre;

    @Size(max = 500)
    private String descripcion;

    @Size(max = 30)
    private String formatoSalidaDefecto;

    @Size(max = 100)
    private String consultaOrigenClave;

    private String parametrosFiltroJson;

    @Size(max = 30)
    private String estado;

    public ReporteConfiguradoUpdateRequest() {}

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getFormatoSalidaDefecto() {
        return formatoSalidaDefecto;
    }

    public void setFormatoSalidaDefecto(String formatoSalidaDefecto) {
        this.formatoSalidaDefecto = formatoSalidaDefecto;
    }

    public String getConsultaOrigenClave() {
        return consultaOrigenClave;
    }

    public void setConsultaOrigenClave(String consultaOrigenClave) {
        this.consultaOrigenClave = consultaOrigenClave;
    }

    public String getParametrosFiltroJson() {
        return parametrosFiltroJson;
    }

    public void setParametrosFiltroJson(String parametrosFiltroJson) {
        this.parametrosFiltroJson = parametrosFiltroJson;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
