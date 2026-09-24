package com.saed.backend.platform.dto;

import java.util.List;
import java.util.Map;

public record PlatformAnalyticsDTO(
    Double tasaRetencionOrganizacionesPct,
    List<Map<String, Object>> distribucionPropiedadesPorCiudad,
    List<Map<String, Object>> crecimientoOrganizacionesMensual
) {}
