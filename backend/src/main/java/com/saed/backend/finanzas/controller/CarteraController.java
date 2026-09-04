package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * CarteraController - gestion de cartera de unidades (saldos y aging).
 *
 * Contrato:
 *   GET    /api/v1/cartera              - lista toda la cartera
 *   GET    /api/v1/cartera/{idUnidad}   - cartera de una unidad
 *   GET    /api/v1/cartera/resumen      - resumen: total, mora, conteo por estado
 *   POST   /api/v1/cartera/recalcular   - recalcular desde CUOTAS + PAGOS
 *   GET    /api/v1/cartera/antiguedad   - reporte de antiguedad de deuda
 */
@Tag(name = "Cartera", description = "Gestion de cartera y saldos de unidades")
@RestController
@RequestMapping("/api/v1/cartera")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class CarteraController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CarteraController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR TODA LA CARTERA ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        Long propId = (com.saed.backend.context.SaedContextHolder.getContext() != null) ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT c.ID_CARTERA, c.ID_UNIDAD, u.IDENTIFICADOR AS NUMERO_APARTAMENTO, " +
                "c.SALDO_CORRIENTE, c.SALDO_MORA_30, c.SALDO_MORA_60, c.SALDO_MORA_90_MAS, " +
                "c.SALDO_TOTAL, c.FECHA_CORTE, c.ESTADO_CARTERA " +
                "FROM CARTERA c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                (propId != null ? "WHERE u.ID_PROPIEDAD = :propId " : "") +
                "ORDER BY c.SALDO_TOTAL DESC, u.IDENTIFICADOR ASC";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (propId != null) params.addValue("propId", propId);
        return ApiResponse.success(jdbcTemplate.queryForList(sql, params));
    }

    // --- CARTERA POR UNIDAD ---

    @GetMapping("/{idUnidad}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> porUnidad(@PathVariable Long idUnidad) {
        Long propId = (com.saed.backend.context.SaedContextHolder.getContext() != null) ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT c.ID_CARTERA, c.ID_UNIDAD, u.IDENTIFICADOR AS NUMERO_APARTAMENTO, " +
                "c.SALDO_CORRIENTE, c.SALDO_MORA_30, c.SALDO_MORA_60, c.SALDO_MORA_90_MAS, " +
                "c.SALDO_TOTAL, c.FECHA_CORTE, c.ESTADO_CARTERA " +
                "FROM CARTERA c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE c.ID_UNIDAD = :idUnidad " +
                (propId != null ? "AND u.ID_PROPIEDAD = :propId " : "");
        MapSqlParameterSource params = new MapSqlParameterSource("idUnidad", idUnidad);
        if (propId != null) params.addValue("propId", propId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No se encontro cartera para la unidad"));
        }
        return ResponseEntity.ok(ApiResponse.success(rows.get(0)));
    }

    // --- RESUMEN DE CARTERA ---

    @GetMapping("/resumen")
    public ApiResponse<Map<String, Object>> resumen() {
        Long propId = (com.saed.backend.context.SaedContextHolder.getContext() != null) ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT " +
                "COUNT(*) AS TOTAL_UNIDADES, " +
                "NVL(SUM(c.SALDO_TOTAL), 0) AS TOTAL_CARTERA, " +
                "NVL(SUM(c.SALDO_MORA_30 + c.SALDO_MORA_60 + c.SALDO_MORA_90_MAS), 0) AS TOTAL_MORA, " +
                "SUM(CASE WHEN c.ESTADO_CARTERA = 'AL_DIA' THEN 1 ELSE 0 END) AS COUNT_AL_DIA, " +
                "SUM(CASE WHEN c.ESTADO_CARTERA = 'MORA_LEVE' THEN 1 ELSE 0 END) AS COUNT_MORA_LEVE, " +
                "SUM(CASE WHEN c.ESTADO_CARTERA = 'MORA_MEDIA' THEN 1 ELSE 0 END) AS COUNT_MORA_MEDIA, " +
                "SUM(CASE WHEN c.ESTADO_CARTERA = 'MORA_GRAVE' THEN 1 ELSE 0 END) AS COUNT_MORA_GRAVE " +
                "FROM CARTERA c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                (propId != null ? "WHERE u.ID_PROPIEDAD = :propId" : "");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (propId != null) params.addValue("propId", propId);
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, params);
        return ApiResponse.success(result);
    }

    // --- RECALCULAR CARTERA DESDE CUOTAS ---

    @PostMapping("/recalcular")
    @Auditable(action = "EXECUTE", resource = "CARTERA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<String> recalcular() {
        Long propId = (com.saed.backend.context.SaedContextHolder.getContext() != null) ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null;
        String sqlRecalc = "MERGE INTO CARTERA dest " +
                "USING ( " +
                "  SELECT u.ID_UNIDAD, " +
                "    NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_CORRIENTE, " +
                "    NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO < TRUNC(SYSDATE) AND TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_30, " +
                "    NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_60, " +
                "    NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO > 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_90_MAS " +
                "  FROM UNIDADES u " +
                "  LEFT JOIN CUOTAS c ON u.ID_UNIDAD = c.ID_UNIDAD AND c.ESTADO IN ('PENDIENTE', 'VENCIDA') " +
                (propId != null ? "  WHERE u.ID_PROPIEDAD = :propId " : " ") +
                "  GROUP BY u.ID_UNIDAD " +
                ") src " +
                "ON (dest.ID_UNIDAD = src.ID_UNIDAD) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "  dest.SALDO_CORRIENTE = src.SALDO_CORRIENTE, " +
                "  dest.SALDO_MORA_30 = src.SALDO_MORA_30, " +
                "  dest.SALDO_MORA_60 = src.SALDO_MORA_60, " +
                "  dest.SALDO_MORA_90_MAS = src.SALDO_MORA_90_MAS, " +
                "  dest.FECHA_CORTE = TRUNC(SYSDATE), " +
                "  dest.ESTADO_CARTERA = CASE " +
                "    WHEN src.SALDO_MORA_90_MAS > 0 THEN 'MORA_GRAVE' " +
                "    WHEN src.SALDO_MORA_60 > 0 THEN 'MORA_MEDIA' " +
                "    WHEN src.SALDO_MORA_30 > 0 THEN 'MORA_LEVE' " +
                "    ELSE 'AL_DIA' " +
                "  END " +
                "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, SALDO_CORRIENTE, SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS, FECHA_CORTE, ESTADO_CARTERA) " +
                "  VALUES (src.ID_UNIDAD, src.SALDO_CORRIENTE, src.SALDO_MORA_30, src.SALDO_MORA_60, src.SALDO_MORA_90_MAS, TRUNC(SYSDATE), " +
                "    CASE " +
                "      WHEN src.SALDO_MORA_90_MAS > 0 THEN 'MORA_GRAVE' " +
                "      WHEN src.SALDO_MORA_60 > 0 THEN 'MORA_MEDIA' " +
                "      WHEN src.SALDO_MORA_30 > 0 THEN 'MORA_LEVE' " +
                "      ELSE 'AL_DIA' " +
                "    END)";

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (propId != null) params.addValue("propId", propId);
        int rows = jdbcTemplate.update(sqlRecalc, params);
        return ApiResponse.success("Cartera recalculada. " + rows + " registros procesados.");
    }

    // --- REPORTE DE ANTIGUEDAD DE DEUDA ---

    @GetMapping("/antiguedad")
    public ApiResponse<List<Map<String, Object>>> antiguedad() {
        Long propId = (com.saed.backend.context.SaedContextHolder.getContext() != null) ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT " +
                "  CASE " +
                "    WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN 'VIGENTE' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN '0-30 dias' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN '31-60 dias' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN '61-90 dias' " +
                "    ELSE '90+ dias' " +
                "  END AS RANGO, " +
                "  COUNT(*) AS CANTIDAD_CUOTAS, " +
                "  NVL(SUM(c.SALDO_PENDIENTE), 0) AS TOTAL_SALDO " +
                "FROM CUOTAS c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE c.ESTADO IN ('PENDIENTE', 'VENCIDA') " +
                (propId != null ? "AND u.ID_PROPIEDAD = :propId " : "") +
                "GROUP BY " +
                "  CASE " +
                "    WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN 'VIGENTE' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN '0-30 dias' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN '31-60 dias' " +
                "    WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN '61-90 dias' " +
                "    ELSE '90+ dias' " +
                "  END " +
                "ORDER BY MIN(c.FECHA_VENCIMIENTO) DESC";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (propId != null) params.addValue("propId", propId);
        return ApiResponse.success(jdbcTemplate.queryForList(sql, params));
    }
}