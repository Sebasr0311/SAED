package com.saed.backend.emergencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContactoEmergenciaRequestDTO {

    @NotBlank(message = "La entidad u organismo es obligatoria")
    @Size(max = 100, message = "La entidad no puede exceder 100 caracteres")
    private String entidad;

    @NotBlank(message = "El tipo de servicio es obligatorio")
    @Size(max = 40, message = "El tipo de servicio no puede exceder 40 caracteres")
    private String tipoServicio;

    @NotBlank(message = "El teléfono principal es obligatorio")
    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String telefonoPrincipal;

    @Size(max = 30, message = "El teléfono alterno no puede exceder 30 caracteres")
    private String telefonoAlterno;

    @Size(max = 150, message = "La dirección no puede exceder 150 caracteres")
    private String direccion;

    @Pattern(regexp = "^[SN]$", message = "esPrioritarioMinuta debe ser 'S' o 'N'")
    private String esPrioritarioMinuta;

    private Integer ordenVisualizacion;

    public ContactoEmergenciaRequestDTO() {}

    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }

    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }

    public String getTelefonoPrincipal() { return telefonoPrincipal; }
    public void setTelefonoPrincipal(String telefonoPrincipal) { this.telefonoPrincipal = telefonoPrincipal; }

    public String getTelefonoAlterno() { return telefonoAlterno; }
    public void setTelefonoAlterno(String telefonoAlterno) { this.telefonoAlterno = telefonoAlterno; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getEsPrioritarioMinuta() { return esPrioritarioMinuta; }
    public void setEsPrioritarioMinuta(String esPrioritarioMinuta) { this.esPrioritarioMinuta = esPrioritarioMinuta; }

    public Integer getOrdenVisualizacion() { return ordenVisualizacion; }
    public void setOrdenVisualizacion(Integer ordenVisualizacion) { this.ordenVisualizacion = ordenVisualizacion; }
}
