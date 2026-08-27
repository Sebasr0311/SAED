package com.saed.backend.person.dto;

public record VisitanteDTO(
    Long id,
    Long personaId,
    String esFrecuente,
    String empresa,
    String fotoUrl,
    String observaciones,
    String estado
) {}
