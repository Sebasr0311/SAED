package com.saed.backend.contratos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public class PlantillaContratoRequestDTO {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 50, message = "El código no debe superar 50 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no debe superar 150 caracteres")
    private String nombre;

    @NotBlank(message = "El tipo de contrato es obligatorio")
    private String tipoContrato;

    @Size(max = 500, message = "La descripción no debe superar 500 caracteres")
    private String descripcion;

    @NotBlank(message = "El contenido HTML es obligatorio")
    private String contenidoHtml;

    private List<String> variablesDisponibles;
    private List<String> camposRequeridos;
    private Integer version;
    private String estado;
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;

    public PlantillaContratoRequestDTO() {}

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
}
