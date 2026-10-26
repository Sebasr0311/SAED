package com.saed.backend.dashboard.kpi;

/** Server-derived scope of a KPI query: exactly one of organization, property or unit drives the filter. */
public record KpiScope(Long organizationId, Long propertyId, Long unitId) {
}
