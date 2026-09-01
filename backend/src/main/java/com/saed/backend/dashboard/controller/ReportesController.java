package com.saed.backend.dashboard.controller;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * ReportesController — reportes de negocio del sistema.
 *
 * Endpoints de solo lectura que consultan tablas de CUOTAS, PAGOS, UNIDADES y PROPIEDADES.
 * No hay escritura desde este controller.
 *
 * Contrato:
 *   GET /api/v1/reportes/cartera-morosa     — unidades con saldo vencido
 *   GET /api/v1/reportes/ejecucion-cuotas   — resumen de ejecución por periodo
 *   GET /api/v1/reportes/pagos-recientes     — últimos 50 pagos
 */
@Tag(name = "Reportes", description = "Reportes de negocio del sistema")
@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class ReportesController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportesController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/cartera-morosa")
    public ApiResponse<List<Map<String, Object>>> carteraMorosa() {
        String sql = """
            SELECT u.IDENTIFICADOR AS UNIDAD, p.NOMBRE AS PROPIEDAD,
                   COUNT(c.ID_CUOTA) AS CUOTAS_PENDIENTES,
                   SUM(c.SALDO_PENDIENTE) AS DEUDA_TOTAL,
                   MIN(c.FECHA_VENCIMIENTO) AS PRIMER_VENCIMIENTO,
                   MAX(c.FECHA_VENCIMIENTO) AS ULTIMO_VENCIMIENTO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE c.ESTADO IN ('PENDIENTE', 'VENCIDA')
            GROUP BY u.IDENTIFICADOR, p.NOMBRE
            ORDER BY DEUDA_TOTAL DESC
            """;
        List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(items);
    }

    @GetMapping("/ejecucion-cuotas")
    public ApiResponse<List<Map<String, Object>>> ejecucionCuotas() {
        String sql = """
            SELECT c.PERIODO,
                   COUNT(*) AS TOTAL_CUOTAS,
                   SUM(CASE WHEN c.ESTADO = 'PAGADA' THEN 1 ELSE 0 END) AS PAGADAS,
                   SUM(CASE WHEN c.ESTADO IN ('PENDIENTE','VENCIDA') THEN 1 ELSE 0 END) AS PENDIENTES,
                   SUM(c.VALOR_BASE) AS TOTAL_FACTURADO,
                   SUM(c.VALOR_BASE - c.SALDO_PENDIENTE) AS TOTAL_RECAUDADO
            FROM CUOTAS c
            GROUP BY c.PERIODO
            ORDER BY c.PERIODO DESC
            """;
        List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(items);
    }

    @GetMapping("/pagos-recientes")
    public ApiResponse<List<Map<String, Object>>> pagosRecientes() {
        String sql = """
            SELECT p.ID_PAGO, u.IDENTIFICADOR AS UNIDAD, p.MONTO_TOTAL, p.METODO_PAGO,
                   p.ESTADO, p.FECHA_PAGO, p.REFERENCIA_COMPROBANTE
            FROM PAGOS p
            JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
            ORDER BY p.FECHA_PAGO DESC
            FETCH FIRST 50 ROWS ONLY
            """;
        List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(items);
    }
}
