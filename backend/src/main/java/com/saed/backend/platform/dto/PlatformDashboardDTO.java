package com.saed.backend.platform.dto;

import java.util.List;
import java.util.Map;

public record PlatformDashboardDTO(
    Map<String, Object> organizaciones,
    Map<String, Object> propiedades,
    Map<String, Object> usuarios,
    Map<String, Object> planesMembresias,
    Map<String, Object> plataforma,
    List<Map<String, Object>> actividadReciente,
    List<Map<String, Object>> alertas,
    Map<String, Object> saludTecnica
) {
    public PlatformDashboardDTO(
        Map<String, Object> organizaciones,
        Map<String, Object> propiedades,
        Map<String, Object> usuarios,
        Map<String, Object> planesMembresias,
        Map<String, Object> plataforma
    ) {
        this(organizaciones, propiedades, usuarios, planesMembresias, plataforma, List.of(), List.of(), Map.of());
    }
}
