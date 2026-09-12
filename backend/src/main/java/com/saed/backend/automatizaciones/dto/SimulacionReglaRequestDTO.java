package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulacionReglaRequestDTO {
    private Long idEntidadOrigen;
    private String tipoEntidadOrigen;
    private String payloadJson;
}
