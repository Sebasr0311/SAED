package com.saed.backend.finanzas.controller;

import com.saed.backend.platform.annotation.RequireModule;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ConciliacionController - conciliacion bancaria.
 *
 * Contrato:
 *   GET    /api/v1/conciliaciones              - listar todas
 *   POST   /api/v1/conciliaciones              - crear
 *   PUT    /api/v1/conciliaciones/{id}         - actualizar
 *   PATCH  /api/v1/conciliaciones/{id}/estado  - cambiar estado
 *   GET    /api/v1/conciliaciones/resumen      - resumen por estado
 *
 * F06-05: the bank account is accepted as {@code bancoCuenta} or {@code cuentaBanco} (the name the page
 * sends), the selected status is persisted, CONCILIADO needs equal bank and book balances, and one
 * account can be reconciled once per period.
 */
@Tag(name = "Conciliaciones", description = "Conciliacion bancaria")
@RequireModule("FINANZAS")
@RestController
@RequestMapping("/api/v1/conciliaciones")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class ConciliacionController {

    private static final List<String> ESTADOS = List.of("EN_PROCESO", "CONCILIADO", "CON_DIFERENCIAS");
    private static final Pattern PERIODO = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConciliacionController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        String sql = "SELECT ID_CONCILIACION, ID_PROPIEDAD, BANCO_CUENTA, PERIODO, " +
                "SALDO_BANCO, SALDO_LIBROS, DIFERENCIA, DOCUMENTO_EXTRACTO_URL, " +
                "ESTADO, CONCILIADO_POR, FECHA_CONCILIACION " +
                "FROM CONCILIACIONES ORDER BY PERIODO DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    // --- ACTUALIZAR ---

    @PutMapping("/{id}")
    @Transactional
    @Auditable(action = "UPDATE", resource = "CONCILIACION", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String bancoCuenta = firstText(body, "bancoCuenta", "cuentaBanco");
        String periodo = text(body.get("periodo"));
        String estado = text(body.get("estado")) != null ? text(body.get("estado")).toUpperCase() : null;
        BigDecimal saldoBanco;
        BigDecimal saldoLibros;
        try {
            saldoBanco = money(body.get("saldoBanco"));
            saldoLibros = money(body.get("saldoLibros"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        if (periodo != null && !PERIODO.matcher(periodo).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("periodo debe tener el formato AAAA-MM"));
        }
        if (estado != null && !ESTADOS.contains(estado)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("estado debe ser EN_PROCESO, CONCILIADO o CON_DIFERENCIAS"));
        }

        Map<String, Object> current = findRow(id);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Conciliacion no encontrada"));
        }
        BigDecimal effectiveBank = saldoBanco != null ? saldoBanco : (BigDecimal) current.get("SALDO_BANCO");
        BigDecimal effectiveBooks = saldoLibros != null ? saldoLibros : (BigDecimal) current.get("SALDO_LIBROS");
        String effectiveState = estado != null ? estado : (String) current.get("ESTADO");
        if ("CONCILIADO".equals(effectiveState) && !balanced(effectiveBank, effectiveBooks)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "No se puede marcar como CONCILIADO mientras el saldo del banco y el de libros difieran"));
        }

        String effectiveAccount = bancoCuenta != null ? bancoCuenta : (String) current.get("BANCO_CUENTA");
        String effectivePeriod = periodo != null ? periodo : (String) current.get("PERIODO");
        if ((bancoCuenta != null || periodo != null) && effectiveAccount != null && effectivePeriod != null
                && countOthers(effectiveAccount, effectivePeriod, id) > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "Ya existe una conciliacion para esa cuenta y periodo"));
        }

        StringBuilder sb = new StringBuilder("UPDATE CONCILIACIONES SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean first = true;

        if (bancoCuenta != null) {
            sb.append(first ? "" : ", ").append("BANCO_CUENTA = :bancoCuenta");
            params.addValue("bancoCuenta", bancoCuenta);
            first = false;
        }
        if (periodo != null) {
            sb.append(first ? "" : ", ").append("PERIODO = :periodo");
            params.addValue("periodo", periodo);
            first = false;
        }
        if (saldoBanco != null) {
            sb.append(first ? "" : ", ").append("SALDO_BANCO = :saldoBanco");
            params.addValue("saldoBanco", saldoBanco);
            first = false;
        }
        if (saldoLibros != null) {
            sb.append(first ? "" : ", ").append("SALDO_LIBROS = :saldoLibros");
            params.addValue("saldoLibros", saldoLibros);
            first = false;
        }
        if (estado != null) {
            sb.append(first ? "" : ", ").append("ESTADO = :estado");
            params.addValue("estado", estado);
            first = false;
        }

        if (first) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Ningun campo para actualizar"));
        }

        sb.append(" WHERE ID_CONCILIACION = :id");
        int rows = jdbcTemplate.update(sb.toString(), params);
        if (rows == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Conciliacion no encontrada"));
        }
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    // --- CREAR ---

    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "CONCILIACION", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(@RequestBody Map<String, Object> body) {
        String bancoCuenta = firstText(body, "bancoCuenta", "cuentaBanco");
        String periodo = text(body.get("periodo"));
        String documentoExtractoUrl = text(body.get("documentoExtractoUrl"));
        String estado = text(body.get("estado")) != null ? text(body.get("estado")).toUpperCase() : "EN_PROCESO";

        if (bancoCuenta == null || periodo == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("bancoCuenta y periodo son obligatorios"));
        }
        if (!PERIODO.matcher(periodo).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("periodo debe tener el formato AAAA-MM"));
        }
        if (!ESTADOS.contains(estado)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("estado debe ser EN_PROCESO, CONCILIADO o CON_DIFERENCIAS"));
        }
        BigDecimal saldoBanco;
        BigDecimal saldoLibros;
        try {
            saldoBanco = money(body.get("saldoBanco"));
            saldoLibros = money(body.get("saldoLibros"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        saldoBanco = saldoBanco != null ? saldoBanco : BigDecimal.ZERO;
        saldoLibros = saldoLibros != null ? saldoLibros : BigDecimal.ZERO;
        if ("CONCILIADO".equals(estado) && !balanced(saldoBanco, saldoLibros)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "No se puede marcar como CONCILIADO mientras el saldo del banco y el de libros difieran"));
        }
        if (countOthers(bancoCuenta, periodo, null) > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "Ya existe una conciliacion para esa cuenta y periodo"));
        }

        String sql = "INSERT INTO CONCILIACIONES (ID_PROPIEDAD, BANCO_CUENTA, PERIODO, " +
                "SALDO_BANCO, SALDO_LIBROS, DOCUMENTO_EXTRACTO_URL, ESTADO, CONCILIADO_POR) " +
                "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :bancoCuenta, :periodo, " +
                ":saldoBanco, :saldoLibros, :documentoUrl, :estado, SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bancoCuenta", bancoCuenta)
                .addValue("periodo", periodo)
                .addValue("saldoBanco", saldoBanco)
                .addValue("saldoLibros", saldoLibros)
                .addValue("documentoUrl", documentoExtractoUrl)
                .addValue("estado", estado);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONCILIACION"});
        Long id = keyHolder.getKey().longValue();

        Map<String, Object> created = new HashMap<>();
        created.put("id", id);
        created.put("bancoCuenta", bancoCuenta);
        created.put("periodo", periodo);
        created.put("saldoBanco", saldoBanco);
        created.put("saldoLibros", saldoLibros);
        created.put("estado", estado);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // --- CAMBIAR ESTADO ---

    @PatchMapping("/{id}/estado")
    @Transactional
    @Auditable(action = "UPDATE", resource = "CONCILIACION", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.getOrDefault("estado", "").toUpperCase();
        if (!ESTADOS.contains(estado)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("estado debe ser EN_PROCESO, CONCILIADO o CON_DIFERENCIAS"));
        }

        Map<String, Object> current = findRow(id);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Conciliacion no encontrada"));
        }
        if (estado.equals(current.get("ESTADO"))) {
            // Repeating the same status is a no-op: the reconciliation stamp is not re-written.
            return ResponseEntity.ok(ApiResponse.success("OK"));
        }
        if ("CONCILIADO".equals(estado)
                && !balanced((BigDecimal) current.get("SALDO_BANCO"), (BigDecimal) current.get("SALDO_LIBROS"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "No se puede marcar como CONCILIADO mientras el saldo del banco y el de libros difieran"));
        }

        MapSqlParameterSource params = new MapSqlParameterSource("id", id)
                .addValue("estado", estado);

        int rows;
        if ("CONCILIADO".equals(estado)) {
            rows = jdbcTemplate.update(
                    "UPDATE CONCILIACIONES SET ESTADO = :estado, " +
                    "FECHA_CONCILIACION = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                    "WHERE ID_CONCILIACION = :id",
                    params);
        } else {
            rows = jdbcTemplate.update(
                    "UPDATE CONCILIACIONES SET ESTADO = :estado WHERE ID_CONCILIACION = :id",
                    params);
        }

        if (rows == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Conciliacion no encontrada"));
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    // --- RESUMEN ---

    @GetMapping("/resumen")
    public ApiResponse<Map<String, Object>> resumen() {
        String sql = "SELECT " +
                "COUNT(*) AS TOTAL_REGISTROS, " +
                "SUM(CASE WHEN ESTADO = 'CONCILIADO' THEN 1 ELSE 0 END) AS CONCILIADOS, " +
                "SUM(CASE WHEN ESTADO = 'EN_PROCESO' THEN 1 ELSE 0 END) AS EN_PROCESO, " +
                "SUM(CASE WHEN ESTADO = 'CON_DIFERENCIAS' THEN 1 ELSE 0 END) AS CON_DIFERENCIAS, " +
                "NVL(SUM(CASE WHEN ESTADO = 'CON_DIFERENCIAS' THEN ABS(DIFERENCIA) ELSE 0 END), 0) " +
                "AS TOTAL_DIFERENCIAS " +
                "FROM CONCILIACIONES";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, new MapSqlParameterSource());
        return ApiResponse.success(result);
    }

    // --- helpers ---

    private Map<String, Object> findRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT BANCO_CUENTA, PERIODO, SALDO_BANCO, SALDO_LIBROS, ESTADO FROM CONCILIACIONES WHERE ID_CONCILIACION = :id",
                new MapSqlParameterSource("id", id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countOthers(String bancoCuenta, String periodo, Long exceptId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cuenta", bancoCuenta)
                .addValue("periodo", periodo);
        String sql = "SELECT COUNT(1) FROM CONCILIACIONES WHERE UPPER(BANCO_CUENTA) = UPPER(:cuenta) AND PERIODO = :periodo";
        if (exceptId != null) {
            sql += " AND ID_CONCILIACION <> :id";
            params.addValue("id", exceptId);
        }
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count == null ? 0 : count;
    }

    private static boolean balanced(BigDecimal bank, BigDecimal books) {
        return bank != null && books != null && bank.compareTo(books) == 0;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String v = text(body.get(key));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal money(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Los saldos deben ser numericos");
        }
    }
}
