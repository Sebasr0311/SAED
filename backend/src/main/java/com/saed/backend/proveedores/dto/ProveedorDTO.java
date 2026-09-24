package com.saed.backend.proveedores.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO que representa un Proveedor en el Catálogo Maestro de SAED 2.0 (GAP-F9-01).
 * Mapea fielmente las columnas de la tabla Oracle PROVEEDORES.
 */
public record ProveedorDTO(
    Long idProveedor,
    Long idOrganizacion,
    String tipoPersona,
    String razonSocial,
    String nitIdentificacion,
    String emailContacto,
    String telefonoContacto,
    String direccion,
    String ciudad,
    String categoriaServicio,
    BigDecimal calificacionProm,
    String estado,
    OffsetDateTime fechaCreacion
) {}
