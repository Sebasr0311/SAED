package com.saed.backend.obras.dto;

/**
 * Gate decision for a worker entering a work (F09-03). Carries only what the porter needs: the decision,
 * the reason and the worker's name and company; no document or contact data.
 */
public record IngresoObraDTO(
        boolean permitido,
        String motivo,
        String nombreTrabajador,
        String empresa
) {
}
