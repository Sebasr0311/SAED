package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
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
import java.util.UUID;

/**
 * PazYSalvoController - emision y verificacion de paz y salvo.
 *
 * Contrato:
 *   GET    /api/v1/paz-y-salvos                  - listar todas
 *   POST   /api/v1/paz-y-salvos                  - generar (valida saldo = 0)
 *   GET    /api/v1/paz-y-salvos/{id}             - detalle
 *   GET    /api/v1/paz-y-salvos/verificar/{codigo} - verificar por codigo
 */
@Tag(name = "Paz y Salvos", description = "Emision y verificacion de paz y salvo")
@RestController
@RequestMapping("/api/v1/paz-y-salvos")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class PazYSalvoController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PazYSalvoController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- LISTAR ---

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        String sql = "SELECT p.ID_PAZ_SALVO, p.ID_UNIDAD, p.ID_PERSONA_SOLICITANTE, " +
                "p.CODIGO_VERIFICACION, p.FECHA_EMISION, p.FECHA_VENCIMIENTO, " +
                "p.SALDO_A_LA_FECHA, p.MOTIVO, p.DOCUMENTO_PDF_URL, " +
                "p.EMITIDO_POR, p.ESTADO " +
                "FROM PAZ_Y_SALVOS p ORDER BY p.FECHA_EMISION DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    // --- GENERAR ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> generar(@RequestBody Map<String, Object> body) {
        Number idUnidad = (Number) body.get("idUnidad");
        String motivo = (String) body.getOrDefault("motivo", "");

        if (idUnidad == null) {
            return ApiResponse.error("idUnidad es obligatorio");
        }

        // Get persona from JWT context (never trust client)
        Long idUsuario = Long.parseLong(
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getName());
        Number idPersonaSolicitante = jdbcTemplate.queryForObject(
                "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :id",
                new MapSqlParameterSource("id", idUsuario),
                Number.class);

        if (idPersonaSolicitante == null) {
            return ApiResponse.error("Usuario no valido");
        }

        // 1. Check unit's SALDO_TOTAL from CARTERA
        List<Map<String, Object>> carteraRows = jdbcTemplate.queryForList(
                "SELECT SALDO_TOTAL FROM CARTERA WHERE ID_UNIDAD = :idUnidad",
                new MapSqlParameterSource("idUnidad", idUnidad));

        if (!carteraRows.isEmpty()) {
            Number saldoTotal = (Number) carteraRows.get(0).get("SALDO_TOTAL");
            if (saldoTotal != null && saldoTotal.doubleValue() > 0) {
                return ApiResponse.error("Unidad tiene saldo pendiente");
            }
        }

        // 2. Generate verification code (UUID)
        String codigoVerificacion = UUID.randomUUID().toString();

        // 3. Insert PAZ_Y_SALVOS
        Number saldo = (!carteraRows.isEmpty())
                ? (Number) carteraRows.get(0).get("SALDO_TOTAL")
                : 0;

        String sql = "INSERT INTO PAZ_Y_SALVOS (ID_UNIDAD, ID_PERSONA_SOLICITANTE, " +
                "CODIGO_VERIFICACION, FECHA_VENCIMIENTO, SALDO_A_LA_FECHA, " +
                "MOTIVO, EMITIDO_POR) " +
                "VALUES (:idUnidad, :idPersonaSolicitante, :codigo, " +
                "TRUNC(SYSDATE) + 30, :saldo, :motivo, " +
                "SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idUnidad", idUnidad)
                .addValue("idPersonaSolicitante", idPersonaSolicitante)
                .addValue("codigo", codigoVerificacion)
                .addValue("saldo", saldo)
                .addValue("motivo", motivo.isBlank() ? null : motivo);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PAZ_SALVO"});
        Long id = keyHolder.getKey().longValue();

        return ApiResponse.success(Map.of(
                "id", id,
                "codigoVerificacion", codigoVerificacion,
                "estado", "VALIDO"
        ));
    }

    // --- DETALLE ---

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detalle(@PathVariable Long id) {
        String sql = "SELECT ID_PAZ_SALVO, ID_UNIDAD, ID_PERSONA_SOLICITANTE, " +
                "CODIGO_VERIFICACION, FECHA_EMISION, FECHA_VENCIMIENTO, " +
                "SALDO_A_LA_FECHA, MOTIVO, DOCUMENTO_PDF_URL, EMITIDO_POR, ESTADO " +
                "FROM PAZ_Y_SALVOS WHERE ID_PAZ_SALVO = :id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return ApiResponse.error("Paz y salvos no encontrado");
        }
        return ApiResponse.success(rows.get(0));
    }

    // --- VERIFICAR POR CODIGO ---

    @GetMapping("/verificar/{codigo}")
    public ApiResponse<Map<String, Object>> verificar(@PathVariable String codigo) {
        String sql = "SELECT ID_PAZ_SALVO, ID_UNIDAD, CODIGO_VERIFICACION, " +
                "FECHA_EMISION, FECHA_VENCIMIENTO, SALDO_A_LA_FECHA, " +
                "MOTIVO, ESTADO " +
                "FROM PAZ_Y_SALVOS WHERE CODIGO_VERIFICACION = :codigo";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("codigo", codigo));

        if (rows.isEmpty()) {
            return ApiResponse.error("Codigo de verificacion no valido");
        }

        Map<String, Object> pazSalvo = rows.get(0);
        String estado = (String) pazSalvo.get("ESTADO");

        // Check if expired
        if ("VENCIDO".equals(estado)) {
            return ApiResponse.error("Paz y salvos vencido");
        }

        return ApiResponse.success(pazSalvo);
    }
}