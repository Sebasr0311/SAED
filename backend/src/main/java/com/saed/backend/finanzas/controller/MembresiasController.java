package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.dto.MembresiaRenovacionRequestDTO;
import com.saed.backend.finanzas.dto.MembresiaUpgradeRequestDTO;
import com.saed.backend.finanzas.service.WompiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.MembershipHistoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
 *   POST   /api/v1/membresias/renovar            — crear intención Wompi de renovación (ADMIN_ORGANIZACION)
 *   POST   /api/v1/membresias/upgrade            — crear intención Wompi de upgrade (ADMIN_ORGANIZACION)
 *   GET    /api/v1/membresias/{id}/historial     — historial de ciclo de vida (GAP-ENT-05)
 */
@Tag(name = "Membresías", description = "Suscripciones de organizaciones a planes SaaS")
@RestController
@RequestMapping("/api/v1/membresias")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
public class MembresiasController {

    private static final Logger log = LoggerFactory.getLogger(MembresiasController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WompiService wompiService;
    private final MembershipHistoryService membershipHistoryService;

    public MembresiasController(NamedParameterJdbcTemplate jdbcTemplate,
                                WompiService wompiService,
                                MembershipHistoryService membershipHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.wompiService = wompiService;
        this.membershipHistoryService = membershipHistoryService;
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
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
    @Auditable(action = "CREATE", resource = "MEMBRESIA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
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

        // ─── HISTORIAL: INICIO (atómico con @Transactional) ─────────────
        Long callerUserId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getUserId() : null;
        membershipHistoryService.recordChange(
                id,
                null,
                idPlan.longValue(),
                "INICIO",
                "Membresía creada con estado " + estado.toUpperCase(),
                callerUserId
        );

        return ApiResponse.success(Map.of(
                "id", id,
                "idOrganizacion", idOrg,
                "idPlan", idPlan,
                "estado", estado.toUpperCase()));
    }

    // ─── CAMBIAR ESTADO ──────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
    @Auditable(action = "UPDATE_STATUS", resource = "MEMBRESIA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<String> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String estado = body.getOrDefault("estado", "").toUpperCase();
        if (!List.of("ACTIVA", "CANCELADA", "SUSPENDIDA", "PRUEBA", "EXPIRADA").contains(estado)) {
            return ApiResponse.error("Estado inválido. Valores: ACTIVA, CANCELADA, SUSPENDIDA, PRUEBA, EXPIRADA");
        }
        // Obtener estado anterior para el historial
        List<Map<String, Object>> rowsCurrent = jdbcTemplate.queryForList(
                "SELECT ID_PLAN, ESTADO FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", id));
        if (rowsCurrent.isEmpty()) {
            return ApiResponse.error("Membresía no encontrada");
        }
        Map<String, Object> membresiaActual = rowsCurrent.get(0);
        String estadoAnterior = (String) membresiaActual.get("ESTADO");
        Long idPlan = ((Number) membresiaActual.get("ID_PLAN")).longValue();

        int rows = jdbcTemplate.update(
                "UPDATE MEMBRESIAS SET ESTADO = :estado WHERE ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", id).addValue("estado", estado));
        if (rows == 0) return ApiResponse.error("Membresía no encontrada");

        // ─── HISTORIAL: CAMBIO DE ESTADO (atómico con @Transactional) ──
        String tipoCambio;
        if ("SUSPENDIDA".equalsIgnoreCase(estado)) {
            tipoCambio = "SUSPENSION";
        } else if ("CANCELADA".equalsIgnoreCase(estado)) {
            tipoCambio = "CANCELACION";
        } else if ("ACTIVA".equalsIgnoreCase(estado) && "SUSPENDIDA".equalsIgnoreCase(estadoAnterior)) {
            tipoCambio = "REACTIVACION";
        } else {
            tipoCambio = "RENOVACION";
        }

        Long callerUserId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getUserId() : null;
        membershipHistoryService.recordChange(
                id,
                idPlan,
                idPlan,
                tipoCambio,
                "Estado cambió de " + estadoAnterior + " a " + estado,
                callerUserId
        );

        return ApiResponse.success("OK");
    }

    // ─── CANCELAR ────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
    @Auditable(action = "CANCEL", resource = "MEMBRESIA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<String> cancelar(@PathVariable Long id) {
        // Obtener datos actuales para el historial
        List<Map<String, Object>> rowsList = jdbcTemplate.queryForList(
                "SELECT ID_PLAN, ESTADO FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", id));
        if (rowsList.isEmpty()) {
            return ApiResponse.error("Membresía no encontrada");
        }
        Map<String, Object> membresiaActual = rowsList.get(0);
        Long idPlan = ((Number) membresiaActual.get("ID_PLAN")).longValue();
        String estadoAnterior = (String) membresiaActual.get("ESTADO");

        int rows = jdbcTemplate.update(
                "UPDATE MEMBRESIAS SET ESTADO = 'CANCELADA', FECHA_FIN = TRUNC(SYSDATE) " +
                "WHERE ID_MEMBRESIA = :id AND ESTADO IN ('ACTIVA','PRUEBA')",
                new MapSqlParameterSource("id", id));
        if (rows == 0) return ApiResponse.error("Membresía no encontrada o ya inactiva");

        // ─── HISTORIAL: CANCELACION (atómico con @Transactional) ────────
        Long callerUserId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getUserId() : null;
        membershipHistoryService.recordChange(
                id,
                idPlan,
                idPlan,
                "CANCELACION",
                "Membresía cancelada. Estado anterior: " + estadoAnterior,
                callerUserId
        );

        return ApiResponse.success("Membresía cancelada");
    }

    // ─── RENOVACIÓN COMERCIAL VIA WOMPI (GAP-ENT-04) ─────────────

    @PostMapping("/renovar")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")
    @Auditable(action = "CREATE_INTENTION", resource = "MEMBRESIA_RENOVACION", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<Map<String, Object>> renovar(@Valid @RequestBody(required = false) MembresiaRenovacionRequestDTO request) throws Exception {
        String ciclo = (request != null && request.getCicloFacturacion() != null) ? request.getCicloFacturacion() : "MENSUAL";
        Map<String, Object> intencion = wompiService.crearIntencionMembresia("RENOVACION", null, ciclo);
        return ApiResponse.success(intencion);
    }

    // ─── UPGRADE COMERCIAL VIA WOMPI (GAP-ENT-04) ────────────────

    @PostMapping("/upgrade")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")
    @Auditable(action = "CREATE_INTENTION", resource = "MEMBRESIA_UPGRADE", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ApiResponse<Map<String, Object>> upgrade(@Valid @RequestBody MembresiaUpgradeRequestDTO request) throws Exception {
        if (request == null || request.getIdPlanNuevo() == null) {
            return ApiResponse.error("El identificador del plan destino (idPlanNuevo) es obligatorio");
        }
        String ciclo = request.getCicloFacturacion() != null ? request.getCicloFacturacion() : "MENSUAL";
        Map<String, Object> intencion = wompiService.crearIntencionMembresia("UPGRADE", request.getIdPlanNuevo(), ciclo);
        return ApiResponse.success(intencion);
    }

    // ─── HISTORIAL DEL CICLO DE VIDA (GAP-ENT-05) ───────────────

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION')")
    public ApiResponse<List<Map<String, Object>>> historial(@PathVariable Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        boolean isSuperAdmin = ctx != null && "SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode());
        if (!isSuperAdmin) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_SUPERADMIN") || a.getAuthority().equals("ROLE_SUPERADMIN"))) {
                isSuperAdmin = true;
            }
        }

        if (!isSuperAdmin) {
            Long userOrgId = (ctx != null) ? ctx.getOrganizationId() : null;
            if (userOrgId == null) {
                return ApiResponse.error("Acceso no autorizado: contexto de organización no disponible");
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ID_ORGANIZACION FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id",
                    new MapSqlParameterSource("id", id));
            if (rows.isEmpty() || !userOrgId.equals(((Number) rows.get(0).get("ID_ORGANIZACION")).longValue())) {
                return ApiResponse.error("Acceso no autorizado a la membresía indicada");
            }
        }
        return ApiResponse.success(membershipHistoryService.getHistoryForMembership(id));
    }
}
