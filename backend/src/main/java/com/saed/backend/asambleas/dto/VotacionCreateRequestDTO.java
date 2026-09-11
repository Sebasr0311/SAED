package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotacionCreateRequestDTO {

    @NotNull(message = "El punto del orden del día es obligatorio")
    private Integer puntoOrdenDia;

    @NotBlank(message = "El título de la votación es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @NotBlank(message = "El tipo de mayoría requerida es obligatorio")
    @Pattern(regexp = "SIMPLE_50_MAS_1|CALIFICADA_70_PCT|UNANIMIDAD_100_PCT", message = "Tipo de mayoría inválido")
    private String tipoMayoriaRequerida;
}
