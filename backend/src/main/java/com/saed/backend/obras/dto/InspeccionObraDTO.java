package com.saed.backend.obras.dto;

/** Final inspection recorded when a work is completed (F09-03). resultado: APROBADA, CON_OBSERVACIONES or RECHAZADA. */
public record InspeccionObraDTO(
        String resultado,
        String observaciones
) {
}
