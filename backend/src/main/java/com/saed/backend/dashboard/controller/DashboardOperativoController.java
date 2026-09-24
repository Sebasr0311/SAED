package com.saed.backend.dashboard.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.dashboard.dto.DashboardPorteriaDTO;
import com.saed.backend.dashboard.dto.DashboardPropiedadDTO;
import com.saed.backend.dashboard.dto.PropertyAnalyticsDTO;
import com.saed.backend.dashboard.service.AnalyticsService;
import com.saed.backend.dashboard.service.DashboardOperativoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard Operativo", description = "Endpoints consolidados para dashboards de administracion de propiedad y porteria")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardOperativoController {

    private final DashboardOperativoService dashboardService;
    private final AnalyticsService analyticsService;

    public DashboardOperativoController(DashboardOperativoService dashboardService, AnalyticsService analyticsService) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "KPIs operativos consolidados para Administrador de Propiedad")
    @GetMapping("/propiedad")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<DashboardPropiedadDTO>> getDashboardPropiedad() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardPropiedad()));
    }

    @Operation(summary = "Analítica y tendencias operativas/financieras para Administrador de Propiedad")
    @GetMapping("/propiedad/analitica")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PropertyAnalyticsDTO>> getAnaliticaPropiedad(
            @RequestParam(required = false) Integer meses) {
        PropertyAnalyticsDTO dto = analyticsService.getPropertyAnalytics(meses);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "KPIs operativos consolidados para Porteria")
    @GetMapping("/porteria")
    @PreAuthorize("hasAnyAuthority('SCOPE_PORTERO', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<DashboardPorteriaDTO>> getDashboardPorteria() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardPorteria()));
    }
}
