package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TutorRequestDTO(
    @NotNull(message = "El ID de la persona menor es requerido")
    Long personaMenorId,
    
    @NotNull(message = "El ID del tutor es requerido")
    Long personaTutorId,
    
    @NotBlank(message = "El parentesco es requerido")
    String parentesco,
    
    String documentoSoporteUrl,
    String estado
) {}
