package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoderRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id del propietario es obligatorio")
    private Long idPersonaPropietario;

    @NotNull(message = "El id del apoderado es obligatorio")
    private Long idPersonaApoderado;

    private String documentoPoderUrl;
}
