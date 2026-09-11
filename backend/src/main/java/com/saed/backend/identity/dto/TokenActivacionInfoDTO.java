package com.saed.backend.identity.dto;

import java.time.Instant;

public record TokenActivacionInfoDTO(
    boolean valido,
    String mensaje,
    Long idUsuario,
    String nombreUsuario,
    String email,
    String primerNombre,
    Instant fechaExpiracion
) {
    public static TokenActivacionInfoDTO invalido(String mensaje) {
        return new TokenActivacionInfoDTO(false, mensaje, null, null, null, null, null);
    }

    public static TokenActivacionInfoDTO valido(Long idUsuario, String nombreUsuario, String email, String primerNombre, Instant expira) {
        return new TokenActivacionInfoDTO(true, "Token válido", idUsuario, nombreUsuario, email, primerNombre, expira);
    }
}
