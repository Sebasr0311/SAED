package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * GastosController - gestion de gastos operativos de una propiedad.
 *
 * Contrato:
 *   GET    /api/v1/gastos              - listar (filtro opcional por idPresupuesto)
 *   POST   /api/v1/gastos              - crear (actualiza MONTO_EJECUTADO del presupuesto)
 *   PUT    /api/v1/gastos/{id}         - actualizar
 *   DELETE /api/v1/gastos/{id}         - eliminar
 */
@Tag(name = "Gastos", description = "Gestion de gastos operativos")
@RestController
@RequestMapping("/api/v1/gastos")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class GastosController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GastosController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(
            @RequestParam(value = "idPresupuesto", required = false) Long idPresupuesto) {

        StringBuilder sb = new StringBuilder(
                "SELECT g.ID_GASTO, g.ID_PROPIEDAD, g.ID_PRESUPUESTO, g.CATEGORIA, " +
                "g.BENEFICIARIO, g.MONTO, g.FECHA_GASTO, g.FACTURA_SOPORTE_URL, " +
                "g.METODO_PAGO, g.ESTADO, g.REGISTRADO_POR " +
                "FROM GASTOS g WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (idPresupuesto != null) {
            sb.append(" AND g.ID_PRESUPUESTO = :idPresupuesto");
            params.addValue("idPresupuesto", idPresupuesto);
        }
        sb.append(" ORDER BY g.FECHA_GASTO DESC");

        return ApiResponse.success(jdbcTemplate.queryForList(sb.toString(), params));
    }

    // --- CREAR ---

    @PostMapping
    @Auditable(action = "CREATE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(@RequestBody Map<String, Object> body) {
        Number idPresupuesto = (Number) body.get("idPresupuesto");
        String categoria = (String) body.getOrDefault("categoria", "");
        String beneficiario = (String) body.getOrDefault("beneficiario", "");
        Number monto = (Number) body.getOrDefault("monto", 0);
        String fechaGasto = (String) body.getOrDefault("fechaGasto", null);
        String metodoPago = body.get("metodoPago") != null ? ((String) body.get("metodoPago")).toUpperCase() : "";
        String facturaSoporteUrl = (String) body.getOrDefault("facturaSoporteUrl", null);

        if (categoria.isBlank() || beneficiario.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("categoria y beneficiario son obligatorios"));
        }

        String sql;
        MapSqlParameterSource params;

        if (fechaGasto != null && !fechaGasto.isBlank()) {
            sql = "INSERT INTO GASTOS (ID_PROPIEDAD, ID_PRESUPUESTO, CATEGORIA, BENEFICIARIO, " +
                    "MONTO, FECHA_GASTO, FACTURA_SOPORTE_URL, METODO_PAGO, REGISTRADO_POR) " +
                    "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :idPresupuesto, :categoria, " +
                    ":beneficiario, :monto, TO_DATE(:fechaGasto, 'YYYY-MM-DD'), :facturaUrl, " +
                    ":metodoPago, SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))";
            params = new MapSqlParameterSource()
                    .addValue("idPresupuesto", idPresupuesto)
                    .addValue("categoria", categoria)
                    .addValue("beneficiario", beneficiario)
                    .addValue("monto", monto)
                    .addValue("fechaGasto", fechaGasto)
                    .addValue("facturaUrl", facturaSoporteUrl)
                    .addValue("metodoPago", metodoPago);
        } else {
            sql = "INSERT INTO GASTOS (ID_PROPIEDAD, ID_PRESUPUESTO, CATEGORIA, BENEFICIARIO, " +
                    "MONTO, FACTURA_SOPORTE_URL, METODO_PAGO, REGISTRADO_POR) " +
                    "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :idPresupuesto, :categoria, " +
                    ":beneficiario, :monto, :facturaUrl, :metodoPago, " +
                    "SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))";
            params = new MapSqlParameterSource()
                    .addValue("idPresupuesto", idPresupuesto)
                    .addValue("categoria", categoria)
                    .addValue("beneficiario", beneficiario)
                    .addValue("monto", monto)
                    .addValue("facturaUrl", facturaSoporteUrl)
                    .addValue("metodoPago", metodoPago);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_GASTO"});
        Long id = keyHolder.getKey().longValue();

        // Update MONTO_EJECUTADO in PRESUPUESTOS if linked
        if (idPresupuesto != null) {
            jdbcTemplate.update(
                    "UPDATE PRESUPUESTOS SET MONTO_EJECUTADO = MONTO_EJECUTADO + :monto " +
                    "WHERE ID_PRESUPUESTO = :idPresupuesto",
                    new MapSqlParameterSource("idPresupuesto", idPresupuesto)
                            .addValue("monto", monto));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", id,
                "categoria", categoria,
                "beneficiario", beneficiario,
                "monto", monto
        )));
    }

    // --- ACTUALIZAR ---

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String categoria = (String) body.getOrDefault("categoria", null);
        String beneficiario = (String) body.getOrDefault("beneficiario", null);
        Number monto = (Number) body.getOrDefault("monto", null);
        String metodoPago = body.get("metodoPago") != null ? ((String) body.get("metodoPago")).toUpperCase() : null;
        String estado = body.get("estado") != null ? ((String) body.get("estado")).toUpperCase() : null;
        String facturaSoporteUrl = (String) body.getOrDefault("facturaSoporteUrl", null);

        StringBuilder sb = new StringBuilder("UPDATE GASTOS SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean first = true;

        if (categoria != null) {
            sb.append(first ? "" : ", ").append("CATEGORIA = :categoria");
            params.addValue("categoria", categoria);
            first = false;
        }
        if (beneficiario != null) {
            sb.append(first ? "" : ", ").append("BENEFICIARIO = :beneficiario");
            params.addValue("beneficiario", beneficiario);
            first = false;
        }
        if (monto != null) {
            sb.append(first ? "" : ", ").append("MONTO = :monto");
            params.addValue("monto", monto);
            first = false;
        }
        if (metodoPago != null) {
            sb.append(first ? "" : ", ").append("METODO_PAGO = :metodoPago");
            params.addValue("metodoPago", metodoPago);
            first = false;
        }
        if (estado != null) {
            sb.append(first ? "" : ", ").append("ESTADO = :estado");
            params.addValue("estado", estado);
            first = false;
        }
        if (facturaSoporteUrl != null) {
            sb.append(first ? "" : ", ").append("FACTURA_SOPORTE_URL = :facturaUrl");
            params.addValue("facturaUrl", facturaSoporteUrl);
            first = false;
        }

        if (first) return ResponseEntity.badRequest().body(ApiResponse.error("Ningun campo para actualizar"));

        sb.append(" WHERE ID_GASTO = :id");
        int rows = jdbcTemplate.update(sb.toString(), params);
        if (rows == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    // --- ELIMINAR ---

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> eliminar(@PathVariable Long id) {
        // Revert MONTO_EJECUTADO in PRESUPUESTOS before deleting
        List<Map<String, Object>> gastos = jdbcTemplate.queryForList(
                "SELECT ID_PRESUPUESTO, MONTO FROM GASTOS WHERE ID_GASTO = :id",
                new MapSqlParameterSource("id", id));

        if (gastos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));
        }

        Map<String, Object> gasto = gastos.get(0);
        Number idPresupuesto = (Number) gasto.get("ID_PRESUPUESTO");
        Number monto = (Number) gasto.get("MONTO");

        jdbcTemplate.update("DELETE FROM GASTOS WHERE ID_GASTO = :id",
                new MapSqlParameterSource("id", id));

        if (idPresupuesto != null) {
            jdbcTemplate.update(
                    "UPDATE PRESUPUESTOS SET MONTO_EJECUTADO = MONTO_EJECUTADO - :monto " +
                    "WHERE ID_PRESUPUESTO = :idPresupuesto",
                    new MapSqlParameterSource("idPresupuesto", idPresupuesto)
                            .addValue("monto", monto));
        }

        return ResponseEntity.ok(ApiResponse.success("Eliminado"));
    }
}
