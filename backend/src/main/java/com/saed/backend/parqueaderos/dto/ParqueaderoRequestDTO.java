package com.saed.backend.parqueaderos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ParqueaderoRequestDTO(
    @NotBlank String numeroParqueadero,
    @NotBlank @Pattern(regexp = "^(PRIVADO|VISITANTES|DISCAPACITADOS|MOTOS|BICICLETAS)$") String tipo,
    @NotBlank @Pattern(regexp = "^(DISPONIBLE|ASIGNADO|MANTENIMIENTO|INACTIVO)$") String estado
) {}
