package com.saed.backend.platform.dto;

import java.util.Map;

public record PlatformDashboardDTO(
    Map<String, Object> organizaciones,
    Map<String, Object> propiedades,
    Map<String, Object> usuarios,
    Map<String, Object> planesMembresias,
    Map<String, Object> plataforma
) {}
