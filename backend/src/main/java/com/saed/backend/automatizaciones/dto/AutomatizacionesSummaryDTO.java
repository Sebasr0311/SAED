package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomatizacionesSummaryDTO {
    private Long totalReglas;
    private Long reglasActivas;
    private Long reglasInactivas;
    private Long totalEventos;
    private Long totalEjecucionesMes;
    private Long ejecucionesExitosas;
    private Long ejecucionesFallidas;
    private Double tasaExito;
}
