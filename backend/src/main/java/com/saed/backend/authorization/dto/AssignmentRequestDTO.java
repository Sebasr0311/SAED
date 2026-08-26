package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotNull;

public class AssignmentRequestDTO {
    @NotNull private Long idUsuario;
    @NotNull private Long idRol;
    private Long idOrganizacion;
    private Long idPropiedad;
    private Long idUnidad;

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long v) { this.idUsuario = v; }
    public Long getIdRol() { return idRol; }
    public void setIdRol(Long v) { this.idRol = v; }
    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long v) { this.idOrganizacion = v; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long v) { this.idPropiedad = v; }
    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long v) { this.idUnidad = v; }
}
