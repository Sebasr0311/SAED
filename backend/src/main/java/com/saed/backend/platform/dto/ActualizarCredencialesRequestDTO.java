package com.saed.backend.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ActualizarCredencialesRequestDTO {

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long idUsuario;

    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
    private String nuevoUsername;

    @Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres")
    private String nuevaPassword;

    private String nuevoEmail;

    private Boolean reenviarCorreo = true;

    public ActualizarCredencialesRequestDTO() {}

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }

    public String getNuevoUsername() { return nuevoUsername; }
    public void setNuevoUsername(String nuevoUsername) { this.nuevoUsername = nuevoUsername; }

    public String getNuevaPassword() { return nuevaPassword; }
    public void setNuevaPassword(String nuevaPassword) { this.nuevaPassword = nuevaPassword; }

    public String getNuevoEmail() { return nuevoEmail; }
    public void setNuevoEmail(String nuevoEmail) { this.nuevoEmail = nuevoEmail; }

    public Boolean getReenviarCorreo() { return reenviarCorreo; }
    public void setReenviarCorreo(Boolean reenviarCorreo) { this.reenviarCorreo = reenviarCorreo; }
}
