package com.saed.backend.org.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class OrgProfileUpdateRequestDTO {

    @Email(message = "El email debe ser válido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String emailContacto;

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String telefonoContacto;

    @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
    private String direccion;

    @Size(max = 80, message = "La ciudad no puede exceder 80 caracteres")
    private String ciudad;

    @Size(max = 60, message = "El país no puede exceder 60 caracteres")
    private String pais;

    public OrgProfileUpdateRequestDTO() {}

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
}
