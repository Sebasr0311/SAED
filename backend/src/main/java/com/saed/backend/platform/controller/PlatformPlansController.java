package com.saed.backend.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Platform Plans", description = "Administración de Planes SaaS de SAED para SUPERADMIN (Persistencia Oracle)")
@RestController
@RequestMapping("/api/v1/platform/plans")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class PlatformPlansController {

    private static final Logger log = LoggerFactory.getLogger(PlatformPlansController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformPlansController(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/catalog-modules")
    public ApiResponse<List<Map<String, Object>>> getCatalogModules() {
        String sql = """
            SELECT ID_MODULO AS "id", CODIGO AS "codigo", NOMBRE AS "nombre", DESCRIPCION AS "descripcion"
            FROM MODULOS
            ORDER BY ID_MODULO ASC
            """;
        List<Map<String, Object>> modules = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ApiResponse.success(modules);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getPlans() {
        String sql = """
            SELECT p.ID_PLAN AS "id",
                   p.CODIGO AS "codigo",
                   p.NOMBRE AS "nombre",
                   p.DESCRIPCION AS "descripcion",
                   p.PRECIO_MENSUAL AS "precioMensual",
                   p.LIMITE_PROPIEDADES AS "maxPropiedades",
                   p.LIMITE_UNIDADES AS "maxUnidades",
                   p.LIMITE_USUARIOS AS "maxUsuarios",
                   p.LIMITE_ALMACENAMIENTO_GB AS "maxAlmacenamientoGb",
                   p.CONFIGURACION_AVANZADA AS "configuracionAvanzada",
                   p.ESTADO AS "estado",
                   (SELECT COUNT(1) FROM MEMBRESIAS m WHERE m.ID_PLAN = p.ID_PLAN AND m.ESTADO IN ('ACTIVA', 'PRUEBA')) AS "organizacionesActivas",
                   (SELECT COUNT(1) FROM MEMBRESIAS m WHERE m.ID_PLAN = p.ID_PLAN) AS "totalOrganizaciones"
            FROM PLANES p
            ORDER BY p.PRECIO_MENSUAL ASC
            """;
        List<Map<String, Object>> plans = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());

        enrichPlansWithModules(plans);

        return ApiResponse.success(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlanById(@PathVariable Long id) {
        String sql = """
            SELECT p.ID_PLAN AS "id",
                   p.CODIGO AS "codigo",
                   p.NOMBRE AS "nombre",
                   p.DESCRIPCION AS "descripcion",
                   p.PRECIO_MENSUAL AS "precioMensual",
                   p.LIMITE_PROPIEDADES AS "maxPropiedades",
                   p.LIMITE_UNIDADES AS "maxUnidades",
                   p.LIMITE_USUARIOS AS "maxUsuarios",
                   p.LIMITE_ALMACENAMIENTO_GB AS "maxAlmacenamientoGb",
                   p.CONFIGURACION_AVANZADA AS "configuracionAvanzada",
                   p.ESTADO AS "estado",
                   (SELECT COUNT(1) FROM MEMBRESIAS m WHERE m.ID_PLAN = p.ID_PLAN AND m.ESTADO IN ('ACTIVA', 'PRUEBA')) AS "organizacionesActivas",
                   (SELECT COUNT(1) FROM MEMBRESIAS m WHERE m.ID_PLAN = p.ID_PLAN) AS "totalOrganizaciones"
            FROM PLANES p
            WHERE p.ID_PLAN = :id
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        enrichPlansWithModules(rows);

        return ResponseEntity.ok(ApiResponse.success(rows.get(0)));
    }

    @PostMapping
    @Transactional
    @Auditable(action = "CREATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPlan(@RequestBody Map<String, Object> payload) {
        String nombre = (String) payload.getOrDefault("nombre", "");
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del plan es obligatorio");
        }

        String codigo = (String) payload.getOrDefault("codigo", "");
        if (codigo == null || codigo.isBlank()) {
            codigo = nombre.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
        } else {
            codigo = codigo.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        }

        // Check if code already exists
        Number exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM PLANES WHERE UPPER(CODIGO) = :cod",
            new MapSqlParameterSource("cod", codigo),
            Number.class
        );
        if (exists != null && exists.intValue() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código '" + codigo + "' ya está registrado para otro plan.");
        }

        String descripcion = (String) payload.getOrDefault("descripcion", "");
        Number precioMensual = (Number) payload.getOrDefault("precioMensual", 0);
        Number maxPropiedades = (Number) payload.getOrDefault("maxPropiedades", 1);
        Number maxUnidades = (Number) payload.getOrDefault("maxUnidades", 50);
        Number maxUsuarios = (Number) payload.getOrDefault("maxUsuarios", 100);
        Number maxAlmacenamientoGb = (Number) payload.getOrDefault("maxAlmacenamientoGb", 5);
        String estado = (String) payload.getOrDefault("estado", "ACTIVO");
        String configJson = extractConfigJson(payload.get("configuracionAvanzada"));

        String sql = """
            INSERT INTO PLANES (
                CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL,
                LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS,
                LIMITE_ALMACENAMIENTO_GB, CONFIGURACION_AVANZADA, ESTADO
            ) VALUES (
                :codigo, :nombre, :descripcion, :precioMensual,
                :maxPropiedades, :maxUnidades, :maxUsuarios,
                :maxAlmacenamientoGb, :configJson, :estado
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("precioMensual", precioMensual)
                .addValue("maxPropiedades", maxPropiedades)
                .addValue("maxUnidades", maxUnidades)
                .addValue("maxUsuarios", maxUsuarios)
                .addValue("maxAlmacenamientoGb", maxAlmacenamientoGb)
                .addValue("configJson", configJson)
                .addValue("estado", estado != null ? estado.toUpperCase() : "ACTIVO");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLAN"});
        Number newId = keyHolder.getKey();
        Long idPlan = newId != null ? newId.longValue() : 0L;

        // Synchronize selected modules
        if (payload.containsKey("modulos")) {
            @SuppressWarnings("unchecked")
            List<String> modulos = (List<String>) payload.get("modulos");
            syncPlanModulos(idPlan, modulos);
        } else {
            // Default: enable basic modules if none provided
            syncPlanModulos(idPlan, List.of("FINANZAS", "PQRS", "PAQUETES", "PARQUEADEROS"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", idPlan,
                "codigo", codigo,
                "nombre", nombre,
                "estado", estado != null ? estado.toUpperCase() : "ACTIVO",
                "message", "Plan SaaS creado exitosamente con sus capacidades y módulos."
        )));
    }

    @PutMapping("/{id}")
    @Transactional
    @Auditable(action = "UPDATE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePlan(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        String nombre = (String) payload.get("nombre");
        String descripcion = (String) payload.get("descripcion");
        Number precioMensual = (Number) payload.get("precioMensual");
        Number maxPropiedades = (Number) payload.get("maxPropiedades");
        Number maxUnidades = (Number) payload.get("maxUnidades");
        Number maxUsuarios = (Number) payload.get("maxUsuarios");
        Number maxAlmacenamientoGb = (Number) payload.get("maxAlmacenamientoGb");
        String estado = (String) payload.get("estado");
        String configJson = extractConfigJson(payload.get("configuracionAvanzada"));

        String sql = """
            UPDATE PLANES
            SET NOMBRE = NVL(:nombre, NOMBRE),
                DESCRIPCION = NVL(:descripcion, DESCRIPCION),
                PRECIO_MENSUAL = NVL(:precioMensual, PRECIO_MENSUAL),
                LIMITE_PROPIEDADES = NVL(:maxPropiedades, LIMITE_PROPIEDADES),
                LIMITE_UNIDADES = NVL(:maxUnidades, LIMITE_UNIDADES),
                LIMITE_USUARIOS = NVL(:maxUsuarios, LIMITE_USUARIOS),
                LIMITE_ALMACENAMIENTO_GB = NVL(:maxAlmacenamientoGb, LIMITE_ALMACENAMIENTO_GB),
                CONFIGURACION_AVANZADA = CASE WHEN :configJson IS NOT NULL THEN :configJson ELSE CONFIGURACION_AVANZADA END,
                ESTADO = NVL(:estado, ESTADO)
            WHERE ID_PLAN = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", nombre)
                .addValue("descripcion", descripcion)
                .addValue("precioMensual", precioMensual)
                .addValue("maxPropiedades", maxPropiedades)
                .addValue("maxUnidades", maxUnidades)
                .addValue("maxUsuarios", maxUsuarios)
                .addValue("maxAlmacenamientoGb", maxAlmacenamientoGb)
                .addValue("configJson", configJson)
                .addValue("estado", estado != null ? estado.toUpperCase() : null);

        int rows = jdbcTemplate.update(sql, params);
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        if (payload.containsKey("modulos")) {
            @SuppressWarnings("unchecked")
            List<String> modulos = (List<String>) payload.get("modulos");
            syncPlanModulos(id, modulos);
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "message", "Plan SaaS actualizado exitosamente")));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Auditable(action = "DELETE", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> deletePlan(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT NOMBRE FROM PLANES WHERE ID_PLAN = :id",
            new MapSqlParameterSource("id", id)
        );
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String planNombre = (String) rows.get(0).get("NOMBRE");

        // 1. Validar si existen membresías asociadas
        Number memCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM MEMBRESIAS WHERE ID_PLAN = :id",
            new MapSqlParameterSource("id", id), Number.class
        );
        long mCount = memCount != null ? memCount.longValue() : 0L;
        if (mCount > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se puede eliminar el plan '" + planNombre + "' porque tiene " + mCount +
                " organización(es) suscrita(s). Para suspender su comercialización, cambie su estado a INACTIVO."
            );
        }

        // 2. Validar historial de membresías
        Number histCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM MEMBRESIAS_HISTORIAL WHERE ID_PLAN_ANTERIOR = :id OR ID_PLAN_NUEVO = :id",
            new MapSqlParameterSource("id", id), Number.class
        );
        long hCount = histCount != null ? histCount.longValue() : 0L;
        if (hCount > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se puede eliminar el plan '" + planNombre + "' porque registra " + hCount +
                " registro(s) en el historial de suscripciones. Le recomendamos desactivarlo."
            );
        }

        // 3. Validar intenciones de registro pendientes
        Number onbCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM ONBOARDING_INTENCIONES WHERE ID_PLAN = :id AND ESTADO = 'PENDIENTE'",
            new MapSqlParameterSource("id", id), Number.class
        );
        long oCount = onbCount != null ? onbCount.longValue() : 0L;
        if (oCount > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se puede eliminar el plan '" + planNombre + "' porque existen intenciones de registro pendientes vinculadas."
            );
        }

        // Eliminar dependencias de módulos y luego el plan
        jdbcTemplate.update("DELETE FROM PLAN_MODULOS WHERE ID_PLAN = :id", new MapSqlParameterSource("id", id));
        int deleted = jdbcTemplate.update("DELETE FROM PLANES WHERE ID_PLAN = :id", new MapSqlParameterSource("id", id));
        if (deleted == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "id", id,
            "nombre", planNombre,
            "message", "Plan SaaS '" + planNombre + "' eliminado exitosamente."
        )));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    @Auditable(action = "UPDATE_STATUS", resource = "PLAN_SAAS", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> patchPlanStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Object estadoRaw = payload.get("estado");
        if (estadoRaw == null && payload.get("activo") != null) {
            boolean activo = Boolean.parseBoolean(String.valueOf(payload.get("activo")));
            estadoRaw = activo ? "ACTIVO" : "INACTIVO";
        }
        if (estadoRaw == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El estado es requerido"));
        }
        String estadoNorm = String.valueOf(estadoRaw).toUpperCase();
        if (!List.of("ACTIVO", "INACTIVO").contains(estadoNorm)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Estado inválido. Valores permitidos: ACTIVO, INACTIVO"));
        }
        int rows = jdbcTemplate.update(
                "UPDATE PLANES SET ESTADO = :estado WHERE ID_PLAN = :id",
                new MapSqlParameterSource("id", id).addValue("estado", estadoNorm)
        );
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", id,
                "estado", estadoNorm,
                "message", "Estado de plan actualizado exitosamente"
        )));
    }

    private void enrichPlansWithModules(List<Map<String, Object>> plans) {
        if (plans == null || plans.isEmpty()) return;

        try {
            String modSql = """
                SELECT pm.ID_PLAN AS "idPlan", m.CODIGO AS "codigo", m.NOMBRE AS "nombre",
                       m.DESCRIPCION AS "descripcion", pm.HABILITADO AS "habilitado"
                FROM PLAN_MODULOS pm
                JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                """;
            List<Map<String, Object>> modRows = jdbcTemplate.queryForList(modSql, new MapSqlParameterSource());

            Map<Long, List<String>> planToEnabledCodes = new HashMap<>();
            Map<Long, List<Map<String, Object>>> planToModulosDetalle = new HashMap<>();

            for (Map<String, Object> r : modRows) {
                Long idPlan = ((Number) r.get("idPlan")).longValue();
                String code = (String) r.get("codigo");
                String hab = (String) r.get("habilitado");
                boolean isHab = "S".equalsIgnoreCase(hab) || "1".equals(hab);

                planToModulosDetalle.computeIfAbsent(idPlan, k -> new ArrayList<>()).add(Map.of(
                    "codigo", code,
                    "nombre", r.get("nombre") != null ? r.get("nombre") : code,
                    "descripcion", r.get("descripcion") != null ? r.get("descripcion") : "",
                    "habilitado", isHab
                ));

                if (isHab) {
                    planToEnabledCodes.computeIfAbsent(idPlan, k -> new ArrayList<>()).add(code);
                }
            }

            for (Map<String, Object> p : plans) {
                Long id = ((Number) p.get("id")).longValue();
                p.put("modulos", planToEnabledCodes.getOrDefault(id, Collections.emptyList()));
                p.put("modulosDetalle", planToModulosDetalle.getOrDefault(id, Collections.emptyList()));
            }
        } catch (Exception e) {
            log.warn("[PlatformPlans] Error al enriquecer módulos para planes: {}", e.getMessage());
            for (Map<String, Object> p : plans) {
                p.putIfAbsent("modulos", Collections.emptyList());
                p.putIfAbsent("modulosDetalle", Collections.emptyList());
            }
        }
    }

    private void syncPlanModulos(Long idPlan, List<String> modulosCodigos) {
        if (idPlan == null) return;
        try {
            List<Map<String, Object>> allModules = jdbcTemplate.queryForList(
                "SELECT ID_MODULO, CODIGO FROM MODULOS ORDER BY ID_MODULO",
                new MapSqlParameterSource()
            );

            Set<String> selected = (modulosCodigos != null)
                ? modulosCodigos.stream().map(String::toUpperCase).collect(Collectors.toSet())
                : Collections.emptySet();

            for (Map<String, Object> mod : allModules) {
                Number modId = (Number) mod.get("ID_MODULO");
                String modCode = ((String) mod.get("CODIGO")).toUpperCase();
                String habilitado = selected.contains(modCode) ? "S" : "N";

                String mergeSql = """
                    MERGE INTO PLAN_MODULOS pm
                    USING (SELECT :idPlan AS ID_PLAN, :idModulo AS ID_MODULO, :habilitado AS HABILITADO FROM DUAL) src
                    ON (pm.ID_PLAN = src.ID_PLAN AND pm.ID_MODULO = src.ID_MODULO)
                    WHEN MATCHED THEN
                        UPDATE SET pm.HABILITADO = src.HABILITADO
                    WHEN NOT MATCHED THEN
                        INSERT (ID_PLAN, ID_MODULO, HABILITADO)
                        VALUES (src.ID_PLAN, src.ID_MODULO, src.HABILITADO)
                    """;
                jdbcTemplate.update(mergeSql, new MapSqlParameterSource()
                    .addValue("idPlan", idPlan)
                    .addValue("idModulo", modId.longValue())
                    .addValue("habilitado", habilitado)
                );
            }
        } catch (Exception e) {
            log.warn("[PlatformPlans] Error sincronizando PLAN_MODULOS para plan {}: {}", idPlan, e.getMessage());
        }
    }

    private String extractConfigJson(Object rawConfig) {
        if (rawConfig == null) return null;
        if (rawConfig instanceof String str) {
            return str.isBlank() ? null : str;
        }
        try {
            return objectMapper.writeValueAsString(rawConfig);
        } catch (Exception e) {
            return null;
        }
    }
}
