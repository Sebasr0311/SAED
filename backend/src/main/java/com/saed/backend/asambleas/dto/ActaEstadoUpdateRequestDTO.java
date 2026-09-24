package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActaEstadoUpdateRequestDTO {

    @NotBlank(message = "El nuevo estado es obligatorio")
    private String nuevoEstado;

    private Long idDocumento;
    private String documentoFirmadoUrl;
    private String observaciones;
}
