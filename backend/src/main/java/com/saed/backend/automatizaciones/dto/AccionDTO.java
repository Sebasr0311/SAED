package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccionDTO {
    private Long idAccion;
    private Long idRegla;
    private String tipoAccion;
    private String parametrosJson;
    private Integer ordenEjecucion;
}
