package com.saed.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarActivacionRequestDTO(
    @NotBlank(message = "El token de activación es requerido")
    String token,

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    String password,

    @NotBlank(message = "La confirmación de contraseña es requerida")
    String confirmPassword
) {}
