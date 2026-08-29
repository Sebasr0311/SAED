package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * MembresiasController — suscripción de una organización a un plan.
 *
 * RLS en MEMBRESIAS: FN_FILTRO_ORGANIZACION filtra automáticamente.
 * UIX_MEMBRESIAS_VIGENTE: solo 1 membresía ACTIVA/PRUEBA por organización.
 *
 * Contrato:
 *   GET    /api/v1/membresias                    — lista (RLS filtra por org)
 *   GET    /api/v1/membresias/{id}               — detalle
 *   GET    /api/v1/membresias/vigente            — membresía activa de la org del contexto
 *   POST   /api/v1/membresias                    — crear (suscripción a plan)
 *   PATCH  /api/v1/membresias/{id}/status        — cambiar estado
 *   DELETE /api/v1/membresias/{id}               — cancelar (cambia a INACTIVA)
 */
@Tag(name = "Membresías", description = "Suscripciones de organizaciones a planes SaaS")
@RestController
@RequestMapping("/api/v1/membresias")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class MembresiasController {

    private static final Logger log = LoggerFactory.getLogger(MembresiasController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MembresiasController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── LISTAR ──────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        String sql = "SELECT m.ID_MEMBRESIA, m.ID_ORGANIZACION, o.NOMBRE AS ORG_NOMBRE, " +
                     "m.ID_PLAN, p.CODIGO AS PLAN_CODIGO, p.NOMBRE AS PLAN_NOMBRE, " +
                     "m.FECHA_INICIO, m.FECHA_FIN, m.FECHA_RENOVACION, " +
                     "m.ESTADO, m.ES_PRUEBA, m.DIAS_PRUEBA, m.FECHA_CREACION " +
                     "FROM MEMBRESIAS m " +
                     "JOIN ORGANIZACIONES o ON m.ID_ORGANIZACION = o.ID_ORGANIZACION " +
                     "JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN " +
                     "ORDER BY m.FECHA_CREACION DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    // ─── VIGENTE (la que tiene ACTIVA o PRUEBA en la org del contexto) ──

    @GetMapping("/vigente")
    public ApiResponse<Map<String, Object>> vigente() {
        String sql = "SELECT m.ID_MEMBRESIA, m.ID_ORGANIZACION, o.NOMBRE AS ORG_NOMBRE, " +
                     "m.ID_PLAN, p.CODIGO AS PLAN_CODIGO, p.NOMBRE AS PLAN_NOMBRE, " +
                     "p.PRECIO_MENSUAL, m.FECHA_INICIO, m.FECHA_FIN, " +
                     "m.FECHA_RENOVACION, m.ESTADO, m.ES_PRUEBA, m.DIAS_PRUEBA " +
                     "FROM MEMBRESIAS m " +
                     "JOIN ORGANIZACIONES o ON m.ID_ORGANIZACION = o.ID_ORGANIZACION " +
                     "JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN " +
                     "WHERE m.ESTADO IN ('ACTIVA','PRUEBA') " +
                     "ORDER BY m.FECHA_INICIO DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        if (rows.isEmpty()) return ApiResponse.error("Sin membresía vigente");
        return ApiResponse.success(rows.get(0));
    }

    // ─── DETALLE ─────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detalle(@PathVariable Long id) {
        String sql = "SELECT m.*, o.NOMBRE AS ORG_NOMBRE, p.CODIGO AS PLAN_CODIGO, " +
                     "p.NOMBRE AS PLAN_NOMBRE, p.PRECIO_MENSUAL " +
                     "FROM MEMBRESIAS m " +
                     "JOIN ORGANIZACIONES o ON m.ID_ORGANIZACION = o.ID_ORGANIZACION " +
                     "JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN " +
                     "WHERE m.ID_MEMBRESIA = :id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) return ApiResponse.error("Membresía no encontrada");
        return ApiResponse.success(rows.get(0));
    }

    // ─── CREAR ───────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        Number idOrg  = (Number) body.get("idOrganizacion");
        Number idPlan = (Number) body.get("idPlan");
        String estado = (String) body.getOrDefault("estado", "PRUEBA");
        Boolean esPrueba = (Boolean) body.getOrDefault("esPrueba", false);
        Number diasPrueba = (Number) body.getOrDefault("diasPrueba", null);

        if (idOrg == null || idPlan == null) {
            return ApiResponse.error("idOrganizacion e idPlan son obligatorios");
        }

        // Verificar que no exista una membresía ACTIVA/PRUEBA para esta org
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MEMBRESIAS " +
                "WHERE ID_ORGANIZACION = :idOrg AND ESTADO IN ('ACTIVA','PRUEBA')",
                new MapSqlParameterSource("idOrg", idOrg.longValue()),
                Long.class);
        if (count != null && count > 0) {
            return ApiResponse.error("La organización ya tiene una membresía activa o en prueba. " +
                    "Suspenda o cancele la membresía existente primero.");
        }

        String sql = "INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, ES_PRUEBA, DIAS_PRUEBA) " +
                     "VALUES (:idOrg, :idPlan, :estado, :esPrueba, :diasPrueba)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrg", idOrg.longValue())
                .addValue("idPlan", idPlan.longValue())
                .addValue("estado", estado.toUpperCase())
                .addValue("esPrueba", esPrueba != null && esPrueba ? "S" : "N")
                .addValue("diasPrueba", diasPrueba);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_MEMBRESIA"});
        Long id = keyHolder.getKey().longValue();

        // ─── HISTORIAL: CREACION ────────────────────────────────
        try {
            String planNombre = jdbcTemplate.queryForObject(
                    "SELECT NOMBRE FROM PLANES WHERE ID_PLAN = :idPlan",
                    new MapSqlParameterSource("idPlan", idPlan.longValue()),
                    String.class);
            jdbcTemplate.update(
                    "INSERT INTO MEMBRESIAS_HISTORIAL " +
                    "(ID_MEMBRESIA, TIPO_CAMBIO, PLAN_NUEVO, REALIZADO_POR, FECHA_CAMBIO, NOTAS) " +
                    "VALUES (:idMembresia, 'CREACION', :planNuevo, 'SISTEMA', SYSDATE, :notas)",
                    new MapSqlParameterSource()
                            .addValue("idMembresia", id)
                            .addValue("planNuevo", planNombre)
                            .addValue("notas", "Membresia creada con estado " + estado.toUpperCase()));
        } catch (Exception e) {
            log.error("Error insertando historial de creacion", e);
        }

        return ApiResponse.success(Map.of(
                "id", id,
                "idOrganizacion", idOrg,
                "idPlan", idPlan,
                "estado", estado.toUpperCase()));
    }

    // ─── CAMBIAR ESTADO ──────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ApiResponse<String> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.getOrDefault("estado", "").toUpperCase();
        if (!List.of("ACTIVA", "INACTIVA", "PENDIENTE", "SUSPENDIDA", "PRUEBA").contains(estado)) {
            return ApiResponse.error("Estado inválido. Valores: ACTIVA, INACTIVA, PENDIENTE, SUSPENDIDA, PRUEBA");
        }
        // Obtener estado anterior para el historial
        Map<String, Object> membresiaActual = jdbcTemplate.queryForMap(
                "SELECT m.ESTADO, p.NOMBRE AS PLAN_NOMBRE " +
                "FROM MEMBRESIAS m JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN " +
                "WHERE m.ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", id));
        String estadoAnterior = (String) membresiaActual.get("ESTADO");
        String planNombre = (String) membresiaActual.get("PLAN_NOMBRE");

        int rows = jdbcTemplate.update(
                "UPDATE MEMBRESIAS SET ESTADO = :estado WHERE ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", id).addValue("estado", estado));
        if (rows == 0) return ApiResponse.error("Membresía no encontrada");

        // ─── HISTORIAL: CAMBIO_ESTADO ──────────────────────────
        try {
            jdbcTemplate.update(
                    "INSERT INTO MEMBRESIAS_HISTORIAL " +
                    "(ID_MEMBRESIA, TIPO_CAMBIO, PLAN_ANTERIOR, PLAN_NUEVO, REALIZADO_POR, FECHA_CAMBIO, NOTAS) " +
                    "VALUES (:idMembresia, 'CAMBIO_ESTADO', :planAnterior, :planNuevo, 'SISTEMA', SYSDATE, :notas)",
                    new MapSqlParameterSource()
                            .addValue("idMembresia", id)
                            .addValue("planAnterior", planNombre)
                            .addValue("planNuevo", planNombre)
                            .addValue("notas", "Estado cambió de " + estadoAnterior + " a " + estado.toUpperCase()));
        } catch (Exception e) {
            log.error("Error insertando historial de cambio de estado", e);
        }

        return ApiResponse.success("OK");
    }

    // ─── CANCELAR ────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ApiResponse<String> cancelar(@PathVariable Long id) {
        // Obtener datos actuales para el historial
        Map<String, Object> membresiaActual;
        try {
            membresiaActual = jdbcTemplate.queryForMap(
                    "SELECT m.ESTADO, p.NOMBRE AS PLAN_NOMBRE " +
                    "FROM MEMBRESIAS m JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN " +
                    "WHERE m.ID_MEMBRESIA = :id",
                    new MapSqlParameterSource("id", id));
        } catch (Exception e) {
            membresiaActual = null;
        }

        int rows = jdbcTemplate.update(
                "UPDATE MEMBRESIAS SET ESTADO = 'INACTIVA', FECHA_FIN = TRUNC(SYSDATE) " +
                "WHERE ID_MEMBRESIA = :id AND ESTADO IN ('ACTIVA','PRUEBA')",
                new MapSqlParameterSource("id", id));
        if (rows == 0) return ApiResponse.error("Membresía no encontrada o ya inactiva");

        // ─── HISTORIAL: CANCELACION ────────────────────────────
        try {
            String planNombre = membresiaActual != null ? (String) membresiaActual.get("PLAN_NOMBRE") : null;
            String estadoAnterior = membresiaActual != null ? (String) membresiaActual.get("ESTADO") : "DESCONOCIDO";
            jdbcTemplate.update(
                    "INSERT INTO MEMBRESIAS_HISTORIAL " +
                    "(ID_MEMBRESIA, TIPO_CAMBIO, PLAN_ANTERIOR, REALIZADO_POR, FECHA_CAMBIO, NOTAS) " +
                    "VALUES (:idMembresia, 'CANCELACION', :planAnterior, 'SISTEMA', SYSDATE, :notas)",
                    new MapSqlParameterSource()
                            .addValue("idMembresia", id)
                            .addValue("planAnterior", planNombre)
                            .addValue("notas", "Membresía cancelada. Estado anterior: " + estadoAnterior));
        } catch (Exception e) {
            log.error("Error insertando historial de cancelación", e);
        }

        return ApiResponse.success("Membresía cancelada");
    }
}
