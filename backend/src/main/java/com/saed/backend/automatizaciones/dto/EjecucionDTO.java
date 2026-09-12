package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjecucionDTO {
    private Long idEjecucion;
    private Long idRegla;
    private String nombreRegla;
    private String codigoEvento;
    private Long idEntidadOrigen;
    private String tipoEntidadOrigen;
    private String resultado;
    private String logDetalle;
    private Integer tiempoMs;
    private OffsetDateTime fechaEjecucion;
}
