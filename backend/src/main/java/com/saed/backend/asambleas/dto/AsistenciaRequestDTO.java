package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id de la persona asistente es obligatorio")
    private Long idPersonaAsistente;

    @NotBlank(message = "Debe indicar si es propietario directo (S/N)")
    @Pattern(regexp = "S|N", message = "Valor debe ser 'S' o 'N'")
    private String esPropietarioDirecto;

    private Long idPoder;

    private BigDecimal coeficientePonderado;
}
