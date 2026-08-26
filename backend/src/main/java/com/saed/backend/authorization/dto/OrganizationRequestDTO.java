package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationRequestDTO {
    @NotBlank @Size(max = 150) private String nombre;
    @NotBlank @Size(max = 30) private String identificacionFiscal;
    @NotBlank @Size(max = 150) private String emailContacto;
    @Size(max = 30) private String telefonoContacto;
    @Size(max = 200) private String direccion;
    @Size(max = 80) private String ciudad;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String v) { this.identificacionFiscal = v; }
    public String getEmailContacto() { return emailContacto; }
    public void setEmailContacto(String v) { this.emailContacto = v; }
    public String getTelefonoContacto() { return telefonoContacto; }
    public void setTelefonoContacto(String v) { this.telefonoContacto = v; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String v) { this.direccion = v; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String v) { this.ciudad = v; }
}
