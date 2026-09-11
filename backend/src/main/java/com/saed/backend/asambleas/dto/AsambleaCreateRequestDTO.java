package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsambleaCreateRequestDTO {

    private Long idPropiedad;

    @NotBlank(message = "El tipo de asamblea es obligatorio")
    @Pattern(regexp = "ORDINARIA|EXTRAORDINARIA|SEGUNDA_CONVOCATORIA", message = "Tipo de asamblea inválido")
    private String tipo;

    @NotBlank(message = "La modalidad es obligatoria")
    @Pattern(regexp = "PRESENCIAL|VIRTUAL|MIXTA", message = "Modalidad inválida")
    private String modalidad;

    @NotBlank(message = "El título de la asamblea es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String titulo;

    private Integer convocatoriaNumero;

    @NotBlank(message = "La fecha y hora de primera convocatoria es obligatoria")
    private String fechaHoraPrimeraConv;

    private String fechaHoraSegundaConv;

    @NotBlank(message = "El lugar o enlace de la asamblea es obligatorio")
    @Size(max = 300, message = "El lugar o enlace no puede exceder 300 caracteres")
    private String lugarOEnlace;

    @NotBlank(message = "El orden del día es obligatorio")
    private String ordenDelDia;

    private BigDecimal quorumRequeridoPct;
}
