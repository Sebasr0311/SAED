package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * PresupuestoController - gestion de presupuestos por propiedad.
 *
 * Contrato:
 *   GET    /api/v1/presupuestos              - listar todos (RLS filtra por propiedad)
 *   GET    /api/v1/presupuestos/{id}         - detalle
 *   POST   /api/v1/presupuestos              - crear
 *   PUT    /api/v1/presupuestos/{id}         - actualizar
 *   DELETE /api/v1/presupuestos/{id}         - eliminar
 *   GET    /api/v1/presupuestos/ejecucion    - reporte: presupuestado vs ejecutado por rubro
 *   GET    /api/v1/presupuestos/resumen      - resumen: totales, saldo, % ejecucion
 */
@Tag(name = "Presupuestos", description = "Gestion de presupuestos por propiedad")
@RestController
@RequestMapping("/api/v1/presupuestos")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class PresupuestoController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PresupuestoController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(
            @RequestParam(value = "vigencia", required = false) Integer vigenciaAnio) {

        StringBuilder sb = new StringBuilder(
                "SELECT ID_PRESUPUESTO, ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, " +
                "MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO " +
                "FROM PRESUPUESTOS WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (vigenciaAnio != null) {
            sb.append(" AND VIGENCIA_ANIO = :vigencia");
            params.addValue("vigencia", vigenciaAnio);
        }
        sb.append(" ORDER BY RUBRO");

        return ApiResponse.success(jdbcTemplate.queryForList(sb.toString(), params));
    }

    // --- DETALLE ---

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detalle(@PathVariable Long id) {
        String sql = "SELECT ID_PRESUPUESTO, ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, " +
                "MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO " +
                "FROM PRESUPUESTOS WHERE ID_PRESUPUESTO = :id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return ApiResponse.error("Presupuesto no encontrado");
        }
        return ApiResponse.success(rows.get(0));
    }

    // --- CREAR ---

    @PostMapping
    @Auditable(action = "CREATE", resource = "PRESUPUESTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(@RequestBody Map<String, Object> body) {
        String rubro = (String) body.getOrDefault("rubro", "");
        String tipo = body.get("tipo") != null ? ((String) body.get("tipo")).toUpperCase() : "";
        Number montoPresupuestado = (Number) body.getOrDefault("montoPresupuestado", 0);
        Number vigenciaAnio = (Number) body.getOrDefault("vigenciaAnio", 0);

        if (rubro.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("rubro es obligatorio"));
        }
        if (!List.of("INGRESO", "EGRESO").contains(tipo)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("tipo debe ser INGRESO o EGRESO"));
        }

        String sql = "INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO) " +
                "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :vigencia, :rubro, :tipo, :monto)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vigencia", vigenciaAnio)
                .addValue("rubro", rubro)
                .addValue("tipo", tipo)
                .addValue("monto", montoPresupuestado);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PRESUPUESTO"});
        Long id = keyHolder.getKey().longValue();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", id,
                "rubro", rubro,
                "tipo", tipo,
                "montoPresupuestado", montoPresupuestado,
                "vigenciaAnio", vigenciaAnio
        )));
    }

    // --- ACTUALIZAR ---

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PRESUPUESTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<String> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String rubro = (String) body.getOrDefault("rubro", null);
        String tipo = body.get("tipo") != null ? ((String) body.get("tipo")).toUpperCase() : null;
        Number montoPresupuestado = (Number) body.getOrDefault("montoPresupuestado", null);
        String estado = body.get("estado") != null ? ((String) body.get("estado")).toUpperCase() : null;

        StringBuilder sb = new StringBuilder("UPDATE PRESUPUESTOS SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean first = true;

        if (rubro != null) {
            sb.append(first ? "" : ", ").append("RUBRO = :rubro");
            params.addValue("rubro", rubro);
            first = false;
        }
        if (tipo != null) {
            if (!List.of("INGRESO", "EGRESO").contains(tipo)) {
                return ApiResponse.error("tipo debe ser INGRESO o EGRESO");
            }
            sb.append(first ? "" : ", ").append("TIPO = :tipo");
            params.addValue("tipo", tipo);
            first = false;
        }
        if (montoPresupuestado != null) {
            sb.append(first ? "" : ", ").append("MONTO_PRESUPUESTADO = :monto");
            params.addValue("monto", montoPresupuestado);
            first = false;
        }
        if (estado != null) {
            sb.append(first ? "" : ", ").append("ESTADO = :estado");
            params.addValue("estado", estado);
            first = false;
        }

        if (first) return ApiResponse.error("Ningun campo para actualizar");

        sb.append(" WHERE ID_PRESUPUESTO = :id");
        int rows = jdbcTemplate.update(sb.toString(), params);
        if (rows == 0) return ApiResponse.error("Presupuesto no encontrado");
        return ApiResponse.success("OK");
    }

    // --- ELIMINAR ---

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", resource = "PRESUPUESTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<String> eliminar(@PathVariable Long id) {
        int rows = jdbcTemplate.update("DELETE FROM PRESUPUESTOS WHERE ID_PRESUPUESTO = :id",
                new MapSqlParameterSource("id", id));
        if (rows == 0) return ApiResponse.error("Presupuesto no encontrado");
        return ApiResponse.success("Eliminado");
    }

    // --- REPORTE DE EJECUCION ---

    @GetMapping("/ejecucion")
    public ApiResponse<List<Map<String, Object>>> ejecucion(
            @RequestParam(value = "vigencia", required = false) Integer vigenciaAnio) {

        StringBuilder sb = new StringBuilder(
                "SELECT p.ID_PRESUPUESTO, p.RUBRO, p.TIPO, p.MONTO_PRESUPUESTADO, " +
                "p.MONTO_EJECUTADO, " +
                "NVL(p.MONTO_PRESUPUESTADO - p.MONTO_EJECUTADO, p.MONTO_PRESUPUESTADO) AS SALDO, " +
                "CASE WHEN p.MONTO_PRESUPUESTADO > 0 " +
                "  THEN ROUND((p.MONTO_EJECUTADO / p.MONTO_PRESUPUESTADO) * 100, 2) " +
                "  ELSE 0 END AS PORCENTAJE_EJECUCION, " +
                "p.ESTADO " +
                "FROM PRESUPUESTOS p WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (vigenciaAnio != null) {
            sb.append(" AND p.VIGENCIA_ANIO = :vigencia");
            params.addValue("vigencia", vigenciaAnio);
        }
        sb.append(" ORDER BY p.RUBRO");

        return ApiResponse.success(jdbcTemplate.queryForList(sb.toString(), params));
    }

    // --- RESUMEN ---

    @GetMapping("/resumen")
    public ApiResponse<Map<String, Object>> resumen(
            @RequestParam(value = "vigencia", required = false) Integer vigenciaAnio) {

        StringBuilder sb = new StringBuilder(
                "SELECT " +
                "  NVL(SUM(CASE WHEN TIPO = 'INGRESO' THEN MONTO_PRESUPUESTADO ELSE 0 END), 0) AS TOTAL_INGRESOS, " +
                "  NVL(SUM(CASE WHEN TIPO = 'EGRESO' THEN MONTO_PRESUPUESTADO ELSE 0 END), 0) AS TOTAL_EGRESOS, " +
                "  NVL(SUM(CASE WHEN TIPO = 'INGRESO' THEN MONTO_PRESUPUESTADO ELSE 0 END), 0) - " +
                "  NVL(SUM(CASE WHEN TIPO = 'EGRESO' THEN MONTO_PRESUPUESTADO ELSE 0 END), 0) AS SALDO, " +
                "  NVL(SUM(MONTO_EJECUTADO), 0) AS TOTAL_EJECUTADO, " +
                "  CASE WHEN SUM(MONTO_PRESUPUESTADO) > 0 " +
                "    THEN ROUND((SUM(MONTO_EJECUTADO) / SUM(MONTO_PRESUPUESTADO)) * 100, 2) " +
                "    ELSE 0 END AS PORCENTAJE_EJECUCION " +
                "FROM PRESUPUESTOS WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (vigenciaAnio != null) {
            sb.append(" AND VIGENCIA_ANIO = :vigencia");
            params.addValue("vigencia", vigenciaAnio);
        }

        Map<String, Object> result = jdbcTemplate.queryForMap(sb.toString(), params);
        return ApiResponse.success(result);
    }
}