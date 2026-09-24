package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActaCreateRequestDTO {

    @NotNull(message = "El ID de asamblea es obligatorio")
    private Long idAsamblea;

    @NotBlank(message = "El número de acta es obligatorio")
    @Size(max = 50, message = "El número de acta no puede superar 50 caracteres")
    private String numeroActa;

    @Size(max = 100000, message = "El contenido del acta excede el límite permitido")
    private String contenidoTexto;

    private String documentoFirmadoUrl;
    private Long idDocumento;
}
