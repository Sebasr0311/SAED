package com.saed.backend.asambleas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotacionDTO {
    private Long idVotacion;
    private Long idAsamblea;
    private Integer puntoOrdenDia;
    private String titulo;
    private String descripcion;
    private String tipoMayoriaRequerida;
    private OffsetDateTime horaApertura;
    private OffsetDateTime horaCierre;
    private String estado;
    private BigDecimal votosSi;
    private BigDecimal votosNo;
    private BigDecimal votosBlanco;
    private BigDecimal votosAbstencion;
    private Integer totalVotos;
    private Boolean aprobada;
}
