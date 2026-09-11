package com.saed.backend.asambleas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuorumLiveDTO {
    private Long idAsamblea;
    private BigDecimal quorumRequeridoPct;
    private BigDecimal quorumAlcanzadoPct;
    private Boolean tieneQuorum;
    private Integer totalUnidadesRegistradas;
    private BigDecimal totalCoeficienteRegistrado;
    private String estadoAsamblea;
}
