package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoderDecisionDTO {

    @NotBlank(message = "La decisión es obligatoria")
    @Pattern(regexp = "APROBADO|RECHAZADO", message = "Decisión inválida (debe ser APROBADO o RECHAZADO)")
    private String estado;
}
