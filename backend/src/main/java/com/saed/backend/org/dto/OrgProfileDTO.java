package com.saed.backend.org.dto;

import java.time.ZonedDateTime;

public class OrgProfileDTO {
    private Long idOrganizacion;
    private String nombre;
    private String identificacionFiscal;
    private String emailContacto;
    private String telefonoContacto;
    private String direccion;
    private String ciudad;
    private String pais;
    private String estado;
    private ZonedDateTime fechaCreacion;

    public OrgProfileDTO() {}

    public OrgProfileDTO(Long idOrganizacion, String nombre, String identificacionFiscal,
                         String emailContacto, String telefonoContacto, String direccion,
                         String ciudad, String pais, String estado, ZonedDateTime fechaCreacion) {
        this.idOrganizacion = idOrganizacion;
        this.nombre = nombre;
        this.identificacionFiscal = identificacionFiscal;
        this.emailContacto = emailContacto;
        this.telefonoContacto = telefonoContacto;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.pais = pais;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String identificacionFiscal) { this.identificacionFiscal = identificacionFiscal; }

    public String getEmailContacto() { return emailContacto; }
    public void setEmailContacto(String emailContacto) { this.emailContacto = emailContacto; }

    public String getTelefonoContacto() { return telefonoContacto; }
    public void setTelefonoContacto(String telefonoContacto) { this.telefonoContacto = telefonoContacto; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public ZonedDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(ZonedDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
