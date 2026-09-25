package com.saed.backend.org.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class UpdateOrgAdminRequestDTO {

    private Long idPropiedad;

    @Size(max = 60, message = "El primer nombre no puede exceder 60 caracteres")
    private String primerNombre;

    @Size(max = 60, message = "El primer apellido no puede exceder 60 caracteres")
    private String primerApellido;

    @Email(message = "El correo electrónico debe ser válido")
    private String email;

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String telefono;

    private String estado;

    private String password;

    public UpdateOrgAdminRequestDTO() {}

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
