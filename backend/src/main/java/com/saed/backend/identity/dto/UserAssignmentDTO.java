package com.saed.backend.identity.dto;

public class UserAssignmentDTO {
    private Long idAsignacion;
    private Long idOrganizacion;
    private Long idPropiedad;
    private Long idUnidad;
    private String roleCode;
    private String scope;
    private String nombreOrganizacion;
    private String nombrePropiedad;
    private String identificadorUnidad;
    
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
    public String getNombreOrganizacion() { return nombreOrganizacion; }
    public void setNombreOrganizacion(String nombreOrganizacion) { this.nombreOrganizacion = nombreOrganizacion; }
    public String getNombrePropiedad() { return nombrePropiedad; }
    public void setNombrePropiedad(String nombrePropiedad) { this.nombrePropiedad = nombrePropiedad; }
    public String getIdentificadorUnidad() { return identificadorUnidad; }
    public void setIdentificadorUnidad(String identificadorUnidad) { this.identificadorUnidad = identificadorUnidad; }
}
