package com.saed.backend.proveedores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para cambio de estado de proveedor (GAP-F9-01).
 * Restringido a los estados definidos en CK_PROV_ESTADO: ACTIVO, INACTIVO, BLOQUEADO.
 */
public record ProveedorEstadoDTO(
    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "^(ACTIVO|INACTIVO|BLOQUEADO)$", message = "El estado debe ser ACTIVO, INACTIVO o BLOQUEADO")
    String estado
) {}
