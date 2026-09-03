package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PropertyRequestDTO {
    private Long idOrganizacion;
    @NotNull private Long idTipoPropiedad;
    @NotBlank @Size(max = 150) private String nombre;
    @NotBlank @Size(max = 200) private String direccion;
    @NotBlank @Size(max = 80) private String ciudad;
    @Size(max = 20) private String tipoOcupacionPredominante = "MIXTA";

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long v) { this.idOrganizacion = v; }
    public Long getIdTipoPropiedad() { return idTipoPropiedad; }
    public void setIdTipoPropiedad(Long v) { this.idTipoPropiedad = v; }
    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String v) { this.direccion = v; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String v) { this.ciudad = v; }
    public String getTipoOcupacionPredominante() { return tipoOcupacionPredominante; }
    public void setTipoOcupacionPredominante(String v) { this.tipoOcupacionPredominante = v; }
}
