package com.saed.backend.asambleas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoderDTO {
    private Long idPoder;
    private Long idAsamblea;
    private Long idUnidad;
    private String unidadIdentificador;
    private Long idPersonaPropietario;
    private String nombrePropietario;
    private String documentoPropietario;
    private Long idPersonaApoderado;
    private String nombreApoderado;
    private String documentoApoderado;
    private String documentoPoderUrl;
    private String estado;
    private Long validadoPor;
    private OffsetDateTime fechaRegistro;
}
