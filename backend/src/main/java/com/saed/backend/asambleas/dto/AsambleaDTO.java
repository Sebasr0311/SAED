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
public class AsambleaDTO {
    private Long idAsamblea;
    private Long idPropiedad;
    private String tipo;
    private String modalidad;
    private String titulo;
    private Integer convocatoriaNumero;
    private OffsetDateTime fechaHoraPrimeraConv;
    private OffsetDateTime fechaHoraSegundaConv;
    private String lugarOEnlace;
    private String ordenDelDia;
    private BigDecimal quorumRequeridoPct;
    private BigDecimal quorumAlcanzadoPct;
    private String estado;
    private Long convocadaPor;
    private OffsetDateTime fechaCreacion;
    private Integer totalAsistentes;
    private Integer totalVotaciones;
}
