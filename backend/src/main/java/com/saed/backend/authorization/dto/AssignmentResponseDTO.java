package com.saed.backend.authorization.dto;

public class AssignmentResponseDTO {
    private Long idAsignacion;
    private RoleDTO rol;
    private OrganizationDTO organizacion;
    private PropertyDTO propiedad;
    private UnitDTO unidad;

    public Long getIdAsignacion() { return idAsignacion; }
    public void setIdAsignacion(Long idAsignacion) { this.idAsignacion = idAsignacion; }

    public RoleDTO getRol() { return rol; }
    public void setRol(RoleDTO rol) { this.rol = rol; }

    public OrganizationDTO getOrganizacion() { return organizacion; }
    public void setOrganizacion(OrganizationDTO organizacion) { this.organizacion = organizacion; }

    public PropertyDTO getPropiedad() { return propiedad; }
    public void setPropiedad(PropertyDTO propiedad) { this.propiedad = propiedad; }

    public UnitDTO getUnidad() { return unidad; }
    public void setUnidad(UnitDTO unidad) { this.unidad = unidad; }
}
