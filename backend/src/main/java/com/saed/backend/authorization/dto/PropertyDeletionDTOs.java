package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PropertyDeletionDTOs {

    public record RequestResponse(
            boolean success,
            String challengeId,
            String message,
            int expiresInSeconds,
            String maskedEmail
    ) {}

    public record VerifyRequest(
            @NotBlank(message = "El identificador de desafío es obligatorio")
            String challengeId,

            @NotBlank(message = "El código OTP es obligatorio")
            @Pattern(regexp = "^[0-9]{6}$", message = "El código debe ser de exactamente 6 dígitos numéricos")
            String code
    ) {}

    public record VerifyResponse(
            boolean success,
            boolean verified,
            String message
    ) {}

    public record ConfirmRequest(
            @NotBlank(message = "El identificador de desafío es obligatorio")
            String challengeId,

            boolean confirmacionDefinitiva
    ) {}

    public record ConfirmResponse(
            boolean success,
            String message,
            Long idPropiedadEliminada
    ) {}
}
