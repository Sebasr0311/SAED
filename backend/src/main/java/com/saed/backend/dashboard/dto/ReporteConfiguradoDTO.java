package com.saed.backend.dashboard.dto;

import java.time.OffsetDateTime;

public class ReporteConfiguradoDTO {

    private Long idReporteConfig;
    private String codigo;
    private String nombre;
    private String modulo;
    private String descripcion;
    private String formatoSalidaDefecto;
    private String consultaOrigenClave;
    private String rolMinimoEjecucion;
    private String estado;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String parametrosFiltroJson;
    private Long idUsuarioCreo;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;
    private boolean esPlantillaSistema;

    public ReporteConfiguradoDTO() {}

    public Long getIdReporteConfig() {
        return idReporteConfig;
    }

    public void setIdReporteConfig(Long idReporteConfig) {
        this.idReporteConfig = idReporteConfig;
    }

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

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
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

    public String getRolMinimoEjecucion() {
        return rolMinimoEjecucion;
    }

    public void setRolMinimoEjecucion(String rolMinimoEjecucion) {
        this.rolMinimoEjecucion = rolMinimoEjecucion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public String getParametrosFiltroJson() {
        return parametrosFiltroJson;
    }

    public void setParametrosFiltroJson(String parametrosFiltroJson) {
        this.parametrosFiltroJson = parametrosFiltroJson;
    }

    public Long getIdUsuarioCreo() {
        return idUsuarioCreo;
    }

    public void setIdUsuarioCreo(Long idUsuarioCreo) {
        this.idUsuarioCreo = idUsuarioCreo;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public boolean isEsPlantillaSistema() {
        return esPlantillaSistema;
    }

    public void setEsPlantillaSistema(boolean esPlantillaSistema) {
        this.esPlantillaSistema = esPlantillaSistema;
    }
}
