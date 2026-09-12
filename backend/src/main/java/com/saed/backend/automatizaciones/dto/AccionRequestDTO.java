package com.saed.backend.automatizaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccionRequestDTO {

    @NotBlank(message = "El tipo de acción es obligatorio")
    @Pattern(
        regexp = "ENVIAR_NOTIFICACION|GENERAR_MULTA|CREAR_TICKET_MANTENIMIENTO|REVOCAR_QR|ENVIAR_CORREO_ADMIN|GENERAR_TAREA_SLA|WEBHOOK_EXTERNO",
        message = "Tipo de acción no válido"
    )
    private String tipoAccion;

    @NotBlank(message = "Los parámetros de la acción en formato JSON son obligatorios")
    private String parametrosJson;

    private Integer ordenEjecucion;
}
