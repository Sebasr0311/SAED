package com.saed.backend.contratos.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class PlantillaContratoDTO {

    private Long idPlantilla;
    private Long idOrganizacion;
    private String codigo;
    private String nombre;
    private String tipoContrato;
    private String descripcion;
    private String contenidoHtml;
    private List<String> variablesDisponibles;
    private List<String> camposRequeridos;
    private Integer version;
    private String estado;
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;
    private Long creadoPor;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;

    public PlantillaContratoDTO() {}

    public Long getIdPlantilla() { return idPlantilla; }
    public void setIdPlantilla(Long idPlantilla) { this.idPlantilla = idPlantilla; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipoContrato() { return tipoContrato; }
    public void setTipoContrato(String tipoContrato) { this.tipoContrato = tipoContrato; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getContenidoHtml() { return contenidoHtml; }
    public void setContenidoHtml(String contenidoHtml) { this.contenidoHtml = contenidoHtml; }

    public List<String> getVariablesDisponibles() { return variablesDisponibles; }
    public void setVariablesDisponibles(List<String> variablesDisponibles) { this.variablesDisponibles = variablesDisponibles; }

    public List<String> getCamposRequeridos() { return camposRequeridos; }
    public void setCamposRequeridos(List<String> camposRequeridos) { this.camposRequeridos = camposRequeridos; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getVigenciaDesde() { return vigenciaDesde; }
    public void setVigenciaDesde(LocalDate vigenciaDesde) { this.vigenciaDesde = vigenciaDesde; }

    public LocalDate getVigenciaHasta() { return vigenciaHasta; }
    public void setVigenciaHasta(LocalDate vigenciaHasta) { this.vigenciaHasta = vigenciaHasta; }

    public Long getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Long creadoPor) { this.creadoPor = creadoPor; }

    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public OffsetDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
