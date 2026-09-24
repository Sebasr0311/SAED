package com.saed.backend.proveedores.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO de actualización de proveedor (GAP-F9-01).
 */
public record ProveedorUpdateDTO(
    @Pattern(regexp = "^(NATURAL|JURIDICA)$", message = "El tipo de persona debe ser NATURAL o JURIDICA")
    String tipoPersona,

    @NotBlank(message = "La razón social o nombre del contratista es obligatorio")
    @Size(max = 200, message = "La razón social no puede exceder 200 caracteres")
    String razonSocial,

    @NotBlank(message = "El NIT o documento de identificación es obligatorio")
    @Size(max = 30, message = "El NIT de identificación no puede exceder 30 caracteres")
    String nitIdentificacion,

    @NotBlank(message = "El correo de contacto es obligatorio")
    @Email(message = "Formato de correo electrónico inválido")
    @Size(max = 150, message = "El correo de contacto no puede exceder 150 caracteres")
    String emailContacto,

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    String telefonoContacto,

    @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
    String direccion,

    @Size(max = 80, message = "La ciudad no puede exceder 80 caracteres")
    String ciudad,

    @NotBlank(message = "La categoría de servicio es obligatoria")
    @Size(max = 80, message = "La categoría de servicio no puede exceder 80 caracteres")
    String categoriaServicio,

    BigDecimal calificacionProm
) {}
