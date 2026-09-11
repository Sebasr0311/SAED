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
public class AsistenciaDTO {
    private Long idAsistencia;
    private Long idAsamblea;
    private Long idUnidad;
    private String unidadIdentificador;
    private Long idPersonaAsistente;
    private String nombreAsistente;
    private String documentoAsistente;
    private String esPropietarioDirecto;
    private Long idPoder;
    private BigDecimal coeficientePonderado;
    private OffsetDateTime horaRegistro;
    private OffsetDateTime horaRetiro;
}
