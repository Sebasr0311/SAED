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
public class VotoRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id de la persona votante es obligatorio")
    private Long idPersonaVotante;

    @NotBlank(message = "La opción de voto es obligatoria")
    @Pattern(regexp = "SI|NO|BLANCO|ABSTENCION", message = "Opción de voto inválida (SI, NO, BLANCO, ABSTENCION)")
    private String opcionVoto;

    private BigDecimal coeficienteVoto;
}
