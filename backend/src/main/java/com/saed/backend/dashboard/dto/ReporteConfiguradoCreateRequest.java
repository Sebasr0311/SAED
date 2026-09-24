package com.saed.backend.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReporteConfiguradoCreateRequest {

    @Size(max = 60)
    private String codigo;

    @NotBlank(message = "El nombre del reporte es obligatorio")
    @Size(max = 150)
    private String nombre;

    @Size(max = 500)
    private String descripcion;

    @Size(max = 60)
    private String modulo;

    @NotBlank(message = "La clave de consulta origen es obligatoria")
    @Size(max = 100)
    private String consultaOrigenClave;

    @Size(max = 30)
    private String formatoSalidaDefecto;

    private String parametrosFiltroJson;

    private Long idPropiedad;

    @Size(max = 50)
    private String rolMinimoEjecucion;

    public ReporteConfiguradoCreateRequest() {}

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

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

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public String getConsultaOrigenClave() {
        return consultaOrigenClave;
    }

    public void setConsultaOrigenClave(String consultaOrigenClave) {
        this.consultaOrigenClave = consultaOrigenClave;
    }

    public String getFormatoSalidaDefecto() {
        return formatoSalidaDefecto;
    }

    public void setFormatoSalidaDefecto(String formatoSalidaDefecto) {
        this.formatoSalidaDefecto = formatoSalidaDefecto;
    }

    public String getParametrosFiltroJson() {
        return parametrosFiltroJson;
    }

    public void setParametrosFiltroJson(String parametrosFiltroJson) {
        this.parametrosFiltroJson = parametrosFiltroJson;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getRolMinimoEjecucion() {
        return rolMinimoEjecucion;
    }

    public void setRolMinimoEjecucion(String rolMinimoEjecucion) {
        this.rolMinimoEjecucion = rolMinimoEjecucion;
    }
}
