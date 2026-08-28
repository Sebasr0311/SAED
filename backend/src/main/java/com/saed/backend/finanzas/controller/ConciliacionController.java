package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * ConciliacionController - conciliacion bancaria.
 *
 * Contrato:
 *   GET    /api/v1/conciliaciones              - listar todas
 *   POST   /api/v1/conciliaciones              - crear
 *   PATCH  /api/v1/conciliaciones/{id}/estado  - cambiar estado
 *   GET    /api/v1/conciliaciones/resumen      - resumen por estado
 */
@Tag(name = "Conciliaciones", description = "Conciliacion bancaria")
@RestController
@RequestMapping("/api/v1/conciliaciones")
public class ConciliacionController {

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

    // --- CREAR ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        String bancoCuenta = (String) body.getOrDefault("bancoCuenta", "");
        String periodo = (String) body.getOrDefault("periodo", "");
        Number saldoBanco = (Number) body.getOrDefault("saldoBanco", 0);
        Number saldoLibros = (Number) body.getOrDefault("saldoLibros", 0);
        String documentoExtractoUrl = (String) body.getOrDefault("documentoExtractoUrl", null);

        if (bancoCuenta.isBlank() || periodo.isBlank()) {
            return ApiResponse.error("bancoCuenta y periodo son obligatorios");
        }

        String sql = "INSERT INTO CONCILIACIONES (ID_PROPIEDAD, BANCO_CUENTA, PERIODO, " +
                "SALDO_BANCO, SALDO_LIBROS, DOCUMENTO_EXTRACTO_URL, CONCILIADO_POR) " +
                "VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :bancoCuenta, :periodo, " +
                ":saldoBanco, :saldoLibros, :documentoUrl, SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bancoCuenta", bancoCuenta)
                .addValue("periodo", periodo)
                .addValue("saldoBanco", saldoBanco)
                .addValue("saldoLibros", saldoLibros)
                .addValue("documentoUrl", documentoExtractoUrl);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONCILIACION"});
        Long id = keyHolder.getKey().longValue();

        return ApiResponse.success(Map.of(
                "id", id,
                "bancoCuenta", bancoCuenta,
                "periodo", periodo,
                "saldoBanco", saldoBanco,
                "saldoLibros", saldoLibros
        ));
    }

    // --- CAMBIAR ESTADO ---

    @PatchMapping("/{id}/estado")
    public ApiResponse<String> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.getOrDefault("estado", "").toUpperCase();
        if (!List.of("EN_PROCESO", "CONCILIADA", "DISCREPANCIA").contains(estado)) {
            return ApiResponse.error("estado debe ser EN_PROCESO, CONCILIADA o DISCREPANCIA");
        }

        MapSqlParameterSource params = new MapSqlParameterSource("id", id)
                .addValue("estado", estado);

        int rows;
        if ("CONCILIADA".equals(estado)) {
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

        if (rows == 0) return ApiResponse.error("Conciliacion no encontrada");
        return ApiResponse.success("OK");
    }

    // --- RESUMEN ---

    @GetMapping("/resumen")
    public ApiResponse<Map<String, Object>> resumen() {
        String sql = "SELECT " +
                "COUNT(*) AS TOTAL_REGISTROS, " +
                "SUM(CASE WHEN ESTADO = 'CONCILIADA' THEN 1 ELSE 0 END) AS CONCILIADAS, " +
                "SUM(CASE WHEN ESTADO = 'EN_PROCESO' THEN 1 ELSE 0 END) AS EN_PROCESO, " +
                "SUM(CASE WHEN ESTADO = 'DISCREPANCIA' THEN 1 ELSE 0 END) AS CON_DISCREPANCIAS, " +
                "NVL(SUM(CASE WHEN ESTADO = 'DISCREPANCIA' THEN ABS(DIFERENCIA) ELSE 0 END), 0) " +
                "AS TOTAL_DISCREPANCIAS " +
                "FROM CONCILIACIONES";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, new MapSqlParameterSource());
        return ApiResponse.success(result);
    }
}