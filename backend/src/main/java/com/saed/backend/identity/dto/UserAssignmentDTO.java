package com.saed.backend.identity.dto;

public class UserAssignmentDTO {
    private Long idAsignacion;
    private Long idOrganizacion;
    private Long idPropiedad;
    private Long idUnidad;
    private String roleCode;
    private String scope;
    
    // Getters and Setters
    public Long getIdAsignacion() { return idAsignacion; }
    public void setIdAsignacion(Long idAsignacion) { this.idAsignacion = idAsignacion; }
    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
