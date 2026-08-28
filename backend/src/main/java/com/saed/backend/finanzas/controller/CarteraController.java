package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
public class CarteraController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CarteraController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR TODA LA CARTERA ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        String sql = "SELECT c.ID_CARTERA, c.ID_UNIDAD, c.SALDO_CORRIENTE, " +
                "c.SALDO_MORA_30, c.SALDO_MORA_60, c.SALDO_MORA_90_MAS, " +
                "c.SALDO_TOTAL, c.FECHA_CORTE, c.ESTADO_CARTERA " +
                "FROM CARTERA c ORDER BY c.SALDO_TOTAL DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    // --- CARTERA POR UNIDAD ---

    @GetMapping("/{idUnidad}")
    public ApiResponse<Map<String, Object>> porUnidad(@PathVariable Long idUnidad) {
        String sql = "SELECT ID_CARTERA, ID_UNIDAD, SALDO_CORRIENTE, " +
                "SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS, " +
                "SALDO_TOTAL, FECHA_CORTE, ESTADO_CARTERA " +
                "FROM CARTERA WHERE ID_UNIDAD = :idUnidad";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("idUnidad", idUnidad));
        if (rows.isEmpty()) {
            return ApiResponse.error("No se encontro cartera para la unidad");
        }
        return ApiResponse.success(rows.get(0));
    }

    // --- RESUMEN DE CARTERA ---

    @GetMapping("/resumen")
    public ApiResponse<Map<String, Object>> resumen() {
        String sql = "SELECT " +
                "COUNT(*) AS TOTAL_UNIDADES, " +
                "NVL(SUM(SALDO_TOTAL), 0) AS TOTAL_CARTERA, " +
                "NVL(SUM(SALDO_MORA_30 + SALDO_MORA_60 + SALDO_MORA_90_MAS), 0) AS TOTAL_MORA, " +
                "SUM(CASE WHEN ESTADO_CARTERA = 'AL_DIA' THEN 1 ELSE 0 END) AS COUNT_AL_DIA, " +
                "SUM(CASE WHEN ESTADO_CARTERA = 'MORA_LEVE' THEN 1 ELSE 0 END) AS COUNT_MORA_LEVE, " +
                "SUM(CASE WHEN ESTADO_CARTERA = 'MORA_MEDIA' THEN 1 ELSE 0 END) AS COUNT_MORA_MEDIA, " +
                "SUM(CASE WHEN ESTADO_CARTERA = 'MORA_GRAVE' THEN 1 ELSE 0 END) AS COUNT_MORA_GRAVE " +
                "FROM CARTERA";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, new MapSqlParameterSource());
        return ApiResponse.success(result);
    }

    // --- RECALCULAR CARTERA DESDE CUOTAS ---

    @PostMapping("/recalcular")
    public ApiResponse<String> recalcular() {
        // 1. Compute aging buckets per unit from CUOTAS with pending/past-due status
        String sqlRecalc = "MERGE INTO CARTERA dest " +
                "USING ( " +
                "  SELECT c.ID_UNIDAD, " +
                "    SUM(CASE WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN c.SALDO_PENDIENTE ELSE 0 END) AS SALDO_CORRIENTE, " +
                "    SUM(CASE WHEN c.FECHA_VENCIMIENTO < TRUNC(SYSDATE) AND TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN c.SALDO_PENDIENTE ELSE 0 END) AS SALDO_MORA_30, " +
                "    SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN c.SALDO_PENDIENTE ELSE 0 END) AS SALDO_MORA_60, " +
                "    SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO > 60 THEN c.SALDO_PENDIENTE ELSE 0 END) AS SALDO_MORA_90_MAS " +
                "  FROM CUOTAS c " +
                "  WHERE c.ESTADO IN ('PENDIENTE', 'VENCIDA') " +
                "  GROUP BY c.ID_UNIDAD " +
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

        int rows = jdbcTemplate.update(sqlRecalc, new MapSqlParameterSource());

        // 2. Clear units that no longer have pending cuotas
        String sqlClear = "DELETE FROM CARTERA WHERE ID_UNIDAD NOT IN ( " +
                "  SELECT DISTINCT ID_UNIDAD FROM CUOTAS WHERE ESTADO IN ('PENDIENTE', 'VENCIDA') " +
                ")";
        jdbcTemplate.update(sqlClear, new MapSqlParameterSource());

        return ApiResponse.success("Cartera recalculada. " + rows + " registros procesados.");
    }

    // --- REPORTE DE ANTIGUEDAD DE DEUDA ---

    @GetMapping("/antiguedad")
    public ApiResponse<List<Map<String, Object>>> antiguedad() {
        String sql = "SELECT " +
                "  CASE " +
                "    WHEN FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN 'VIGENTE' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO <= 30 THEN '0-30 dias' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN '31-60 dias' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN '61-90 dias' " +
                "    ELSE '90+ dias' " +
                "  END AS RANGO, " +
                "  COUNT(*) AS CANTIDAD_CUOTAS, " +
                "  NVL(SUM(SALDO_PENDIENTE), 0) AS TOTAL_SALDO " +
                "FROM CUOTAS " +
                "WHERE ESTADO IN ('PENDIENTE', 'VENCIDA') " +
                "GROUP BY " +
                "  CASE " +
                "    WHEN FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN 'VIGENTE' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO <= 30 THEN '0-30 dias' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN '31-60 dias' " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN '61-90 dias' " +
                "    ELSE '90+ dias' " +
                "  END " +
                "ORDER BY " +
                "  CASE " +
                "    WHEN FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN 0 " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO <= 30 THEN 1 " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN 2 " +
                "    WHEN TRUNC(SYSDATE) - FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN 3 " +
                "    ELSE 4 " +
                "  END";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }
}