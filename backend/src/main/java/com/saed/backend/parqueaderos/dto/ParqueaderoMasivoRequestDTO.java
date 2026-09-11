package com.saed.backend.parqueaderos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ParqueaderoMasivoRequestDTO(
    String prefijo,
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    @Max(value = 500, message = "La cantidad máxima por lote es 500")
    Integer cantidad,
    @NotNull(message = "El número inicial es obligatorio")
    @Min(value = 1, message = "El número inicial debe ser mayor o igual a 1")
    Integer numeroInicial,
    @NotBlank(message = "El tipo de parqueadero es obligatorio")
    @Pattern(regexp = "^(PRIVADO|VISITANTES|DISCAPACITADOS|MOTOS|BICICLETAS)$",
             message = "Tipo inválido: PRIVADO, VISITANTES, DISCAPACITADOS, MOTOS o BICICLETAS")
    String tipo,
    @Pattern(regexp = "^(DISPONIBLE|ASIGNADO|MANTENIMIENTO|INACTIVO)$",
             message = "Estado inválido: DISPONIBLE, ASIGNADO, MANTENIMIENTO o INACTIVO")
    String estado
) {
    public ParqueaderoMasivoRequestDTO {
        if (estado == null || estado.isBlank()) {
            estado = "DISPONIBLE";
        }
        if (prefijo == null) {
            prefijo = "";
        }
    }
}
