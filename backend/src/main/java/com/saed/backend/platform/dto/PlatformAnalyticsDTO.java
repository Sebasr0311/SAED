package com.saed.backend.platform.dto;

import java.util.List;
import java.util.Map;

public record PlatformAnalyticsDTO(
    Double tasaRetencionOrganizacionesPct,
    List<Map<String, Object>> distribucionPropiedadesPorCiudad,
    List<Map<String, Object>> crecimientoOrganizacionesMensual,
    Map<String, Object> resumenGlobal,
    List<Map<String, Object>> distribucionPropiedadesPorTipo,
    List<Map<String, Object>> distribucionUnidadesPorTipo,
    List<Map<String, Object>> distribucionRoles,
    List<Map<String, Object>> distribucionPlanes,
    List<Map<String, Object>> membresiasPorEstado,
    List<Map<String, Object>> entitlementsModulos,
    Map<String, Object> actividadOperativa,
    Map<String, Object> transaccionesSandbox,
    Map<String, Object> metricasSeguridad
) {
    public PlatformAnalyticsDTO(
        Double tasaRetencionOrganizacionesPct,
        List<Map<String, Object>> distribucionPropiedadesPorCiudad,
        List<Map<String, Object>> crecimientoOrganizacionesMensual
    ) {
        this(tasaRetencionOrganizacionesPct, distribucionPropiedadesPorCiudad, crecimientoOrganizacionesMensual,
             Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
    }
}
