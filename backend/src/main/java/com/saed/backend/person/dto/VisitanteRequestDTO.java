package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotNull;

public record VisitanteRequestDTO(
    @NotNull(message = "El ID de la persona es requerido")
    Long personaId,
    
    String esFrecuente,
    String empresa,
    String fotoUrl,
    String observaciones,
    String estado
) {}
