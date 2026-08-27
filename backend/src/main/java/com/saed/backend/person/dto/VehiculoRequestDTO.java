package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehiculoRequestDTO(
    @NotNull(message = "El ID de la persona es requerido")
    Long personaId,
    
    @NotNull(message = "El ID de la unidad es requerido para visibilidad RLS")
    Long unidadId,
    
    @NotBlank(message = "La placa es requerida")
    String placa,
    
    @NotBlank(message = "El tipo de vehiculo es requerido")
    String tipoVehiculo,
    
    String marca,
    String modelo,
    String color,
    String tagRfid,
    String estado
) {}
