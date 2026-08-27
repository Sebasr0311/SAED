package com.saed.backend.identity.dto;

public class AuthUserDTO {
    private Long idUsuario;
    private String nombreUsuario;
    private String email;
    private String rol;
    private String alcance;
    private Long idOrganizacion;
    private Long idPropiedad;
    private Long idUnidad;

    public AuthUserDTO() {
    }

    public AuthUserDTO(Long idUsuario, String nombreUsuario, String email, String rol,
                       String alcance, Long idOrganizacion, Long idPropiedad, Long idUnidad) {
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.rol = rol;
        this.alcance = alcance;
        this.idOrganizacion = idOrganizacion;
        this.idPropiedad = idPropiedad;
        this.idUnidad = idUnidad;
    }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getAlcance() { return alcance; }
    public void setAlcance(String alcance) { this.alcance = alcance; }
    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }
}