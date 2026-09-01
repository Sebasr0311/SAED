package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * PlanesController — catálogo de planes comerciales del SaaS.
 *
 * PLANES no tiene RLS (catálogo público del sistema). Solo SUPERADMIN
 * y ADMIN_ORGANIZACION pueden gestionar. Los residentes solo ven
 * los planes ACTIVOS.
 *
 * Contrato:
 *   GET    /api/v1/planes              — lista (ACTIVOS para no-admin, todos para admin)
 *   GET    /api/v1/planes/{id}         — detalle
 *   POST   /api/v1/planes              — crear (SUPERADMIN/ADMIN_ORG)
 *   PUT    /api/v1/planes/{id}         — actualizar (SUPERADMIN/ADMIN_ORG)
 *   PATCH  /api/v1/planes/{id}/status  — activar/desactivar (SUPERADMIN)
 *   GET    /api/v1/planes/catalogo     — solo planes activos (para selects)
 */
@Tag(name = "Planes", description = "Catálogo de planes comerciales SaaS")
@RestController
@RequestMapping("/api/v1/planes")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class PlanesController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlanesController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── SELECT (todos o solo activos) ────────────────────────────

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(
            @RequestParam(value = "solo_activos", defaultValue = "false") boolean soloActivos) {

        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (soloActivos) {
            sql = "SELECT ID_PLAN, CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                  "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, " +
                  "LIMITE_ALMACENAMIENTO_GB, ESTADO, FECHA_CREACION " +
                  "FROM PLANES WHERE ESTADO = 'ACTIVO' ORDER BY PRECIO_MENSUAL";
        } else {
            sql = "SELECT ID_PLAN, CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                  "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, " +
                  "LIMITE_ALMACENAMIENTO_GB, ESTADO, FECHA_CREACION " +
                  "FROM PLANES ORDER BY PRECIO_MENSUAL";
        }

        List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, params);
        return ApiResponse.success(items);
    }

    // ─── CATÁLOGO (solo activos, para selects del frontend) ──────

    @GetMapping("/catalogo")
    public ApiResponse<List<Map<String, Object>>> catalogo() {
        String sql = "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL " +
                     "FROM PLANES WHERE ESTADO = 'ACTIVO' ORDER BY PRECIO_MENSUAL";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    // ─── DETALLE ─────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detalle(@PathVariable Long id) {
        String sql = "SELECT ID_PLAN, CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                     "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, " +
                     "LIMITE_ALMACENAMIENTO_GB, ESTADO, FECHA_CREACION " +
                     "FROM PLANES WHERE ID_PLAN = :id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        if (rows.isEmpty()) {
            return ApiResponse.error("Plan no encontrado");
        }
        return ApiResponse.success(rows.get(0));
    }

    // ─── CREAR ───────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auditable(action = "CREATE", resource = "PLAN", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ApiResponse<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        String codigo    = (String) body.getOrDefault("codigo", "");
        String nombre    = (String) body.getOrDefault("nombre", "");
        String desc      = (String) body.getOrDefault("descripcion", "");
        Number precio    = (Number) body.getOrDefault("precioMensual", 0);
        Number limProp   = (Number) body.getOrDefault("limitePropiedades", null);
        Number limUnid   = (Number) body.getOrDefault("limiteUnidades", null);
        Number limUsr    = (Number) body.getOrDefault("limiteUsuarios", null);
        Number limAlm    = (Number) body.getOrDefault("limiteAlmacenamientoGb", null);

        if (codigo.isBlank() || nombre.isBlank()) {
            return ApiResponse.error("codigo y nombre son obligatorios");
        }

        String sql = "INSERT INTO PLANES (CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                     "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB) " +
                     "VALUES (:codigo, :nombre, :desc, :precio, :limProp, :limUnid, :limUsr, :limAlm)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("nombre", nombre)
                .addValue("desc", desc.isBlank() ? null : desc)
                .addValue("precio", precio)
                .addValue("limProp", limProp)
                .addValue("limUnid", limUnid)
                .addValue("limUsr", limUsr)
                .addValue("limAlm", limAlm);

        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLAN"});
        Long id = keyHolder.getKey().longValue();

        return ApiResponse.success(Map.of("id", id, "codigo", codigo, "nombre", nombre));
    }

    // ─── ACTUALIZAR ──────────────────────────────────────────────

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resource = "PLAN", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ApiResponse<String> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String nombre    = (String) body.getOrDefault("nombre", null);
        String desc      = (String) body.getOrDefault("descripcion", null);
        Number precio    = (Number) body.getOrDefault("precioMensual", null);
        Number limProp   = (Number) body.getOrDefault("limitePropiedades", null);
        Number limUnid   = (Number) body.getOrDefault("limiteUnidades", null);
        Number limUsr    = (Number) body.getOrDefault("limiteUsuarios", null);
        Number limAlm    = (Number) body.getOrDefault("limiteAlmacenamientoGb", null);

        StringBuilder sb = new StringBuilder("UPDATE PLANES SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean first = true;

        if (nombre != null)        { sb.append(first?"":", ").append("NOMBRE = :nombre"); params.addValue("nombre", nombre); first = false; }
        if (desc != null)          { sb.append(first?"":", ").append("DESCRIPCION = :desc"); params.addValue("desc", desc); first = false; }
        if (precio != null)        { sb.append(first?"":", ").append("PRECIO_MENSUAL = :precio"); params.addValue("precio", precio); first = false; }
        if (limProp != null)       { sb.append(first?"":", ").append("LIMITE_PROPIEDADES = :limProp"); params.addValue("limProp", limProp); first = false; }
        if (limUnid != null)       { sb.append(first?"":", ").append("LIMITE_UNIDADES = :limUnid"); params.addValue("limUnid", limUnid); first = false; }
        if (limUsr != null)        { sb.append(first?"":", ").append("LIMITE_USUARIOS = :limUsr"); params.addValue("limUsr", limUsr); first = false; }
        if (limAlm != null)        { sb.append(first?"":", ").append("LIMITE_ALMACENAMIENTO_GB = :limAlm"); params.addValue("limAlm", limAlm); first = false; }

        if (first) return ApiResponse.error("Ningún campo para actualizar");

        sb.append(" WHERE ID_PLAN = :id");
        int rows = jdbcTemplate.update(sb.toString(), params);
        if (rows == 0) return ApiResponse.error("Plan no encontrado");
        return ApiResponse.success("OK");
    }

    // ─── CAMBIAR ESTADO ──────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @Auditable(action = "UPDATE_STATUS", resource = "PLAN", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ApiResponse<String> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.getOrDefault("estado", "").toUpperCase();
        if (!List.of("ACTIVO", "INACTIVO").contains(estado)) {
            return ApiResponse.error("estado debe ser ACTIVO o INACTIVO");
        }
        int rows = jdbcTemplate.update(
                "UPDATE PLANES SET ESTADO = :estado WHERE ID_PLAN = :id",
                new MapSqlParameterSource("id", id).addValue("estado", estado));
        if (rows == 0) return ApiResponse.error("Plan no encontrado");
        return ApiResponse.success("OK");
    }
}
