package com.saed.backend.org.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateOrgAdminRequestDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
    private String nombreUsuario;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico debe ser válido")
    private String email;

    @Size(max = 100, message = "La contraseña no puede exceder 100 caracteres")
    private String password;

    @NotBlank(message = "El primer nombre es obligatorio")
    @Size(max = 60, message = "El primer nombre no puede exceder 60 caracteres")
    private String primerNombre;

    @NotBlank(message = "El primer apellido es obligatorio")
    @Size(max = 60, message = "El primer apellido no puede exceder 60 caracteres")
    private String primerApellido;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 30, message = "El número de documento no puede exceder 30 caracteres")
    private String numeroDocumento;

    private Long idTipoDocumento = 1L; // Cédula por defecto

    private String telefono;

    @NotNull(message = "El rol es obligatorio")
    private Long idRol = 3L; // 3 = ADMIN_PROPIEDAD por defecto

    private Long idPropiedad; // Requerido si el rol es ADMIN_PROPIEDAD (retrocompatibilidad)
    private java.util.List<Long> idPropiedades; // Asignación de múltiples propiedades

    public CreateOrgAdminRequestDTO() {}

    public java.util.List<Long> getResolvedPropiedades() {
        if (idPropiedades != null && !idPropiedades.isEmpty()) {
            return idPropiedades.stream().filter(java.util.Objects::nonNull).distinct().toList();
        }
        if (idPropiedad != null) {
            return java.util.List.of(idPropiedad);
        }
        return java.util.List.of();
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public Long getIdTipoDocumento() { return idTipoDocumento; }
    public void setIdTipoDocumento(Long idTipoDocumento) { this.idTipoDocumento = idTipoDocumento; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public Long getIdRol() { return idRol; }
    public void setIdRol(Long idRol) { this.idRol = idRol; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public java.util.List<Long> getIdPropiedades() { return idPropiedades; }
    public void setIdPropiedades(java.util.List<Long> idPropiedades) { this.idPropiedades = idPropiedades; }
}

