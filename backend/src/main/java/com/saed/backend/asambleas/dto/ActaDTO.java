package com.saed.backend.asambleas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActaDTO {

    private Long idActa;
    private Long idAsamblea;
    private String numeroActa;
    private String contenidoTexto;
    private String documentoFirmadoUrl;
    private Long idDocumento;
    private String estado;
    private Long redactadaPor;
    private String redactorNombre;
    private OffsetDateTime fechaCreacion;

    // Metadata de la asamblea asociada
    private Long idPropiedad;
    private String asambleaTitulo;
    private String asambleaTipo;
    private String asambleaModalidad;
    private String asambleaEstado;
    private Integer convocatoriaNumero;
    private OffsetDateTime fechaHoraPrimeraConv;
    private String lugarOEnlace;
    private String ordenDelDia;
    private BigDecimal quorumRequeridoPct;
    private BigDecimal quorumAlcanzadoPct;
    private Integer totalAsistentes;
    private BigDecimal totalCoeficienteAsistentes;
    private Integer totalPoderesAprobados;
    private Integer totalVotaciones;

    // Resultados consolidados de gobernanza (F10-04)
    private List<VotacionDTO> votaciones;
}
