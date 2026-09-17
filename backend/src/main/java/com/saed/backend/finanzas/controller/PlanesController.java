package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * PlanesController — catálogo público de consulta de planes comerciales del SaaS (GAP-ENT-07).
 *
 * PLANES no tiene RLS (catálogo público del sistema).
 * La administración y mutación (creación, edición, activación) de planes está
 * reservada exclusivamente a PlatformPlansController bajo SCOPE_SUPERADMIN.
 *
 * Contrato de solo lectura:
 *   GET    /api/v1/planes              — lista (solo activos para público/tenant, todos para superadmin)
 *   GET    /api/v1/planes/{id}         — detalle de plan con módulos y cálculo anual
 *   GET    /api/v1/planes/catalogo     — catálogo simplificado de planes activos
 */
@Tag(name = "Planes", description = "Catálogo de consulta de planes comerciales SaaS")
@RestController
@RequestMapping("/api/v1/planes")
public class PlanesController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlanesController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── SELECT (todos o solo activos) ────────────────────────────

    @Operation(summary = "Catálogo público comercial de planes disponibles")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(
            @RequestParam(value = "solo_activos", defaultValue = "false") boolean soloActivos) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> "SCOPE_SUPERADMIN".equals(a.getAuthority()));

        if (!isSuperAdmin) {
            soloActivos = true;
        }

        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (soloActivos) {
            sql = "SELECT ID_PLAN, CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                  "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, " +
                  "LIMITE_ALMACENAMIENTO_GB, ESTADO, FECHA_CREACION " +
                  "FROM PLANES WHERE ESTADO = 'ACTIVO' ORDER BY PRECIO_MENSUAL ASC";
        } else {
            sql = "SELECT ID_PLAN, CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, " +
                  "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, " +
                  "LIMITE_ALMACENAMIENTO_GB, ESTADO, FECHA_CREACION " +
                  "FROM PLANES ORDER BY PRECIO_MENSUAL ASC";
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        List<Map<String, Object>> enriched = rows.stream().map(this::enrichPlan).toList();
        return ApiResponse.success(enriched);
    }

    // ─── CATÁLOGO (solo activos, para selects del frontend) ──────

    @Operation(summary = "Catálogo simplificado de planes activos")
    @GetMapping("/catalogo")
    public ApiResponse<List<Map<String, Object>>> catalogo() {
        String sql = "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL, " +
                     "LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB " +
                     "FROM PLANES WHERE ESTADO = 'ACTIVO' ORDER BY PRECIO_MENSUAL ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        List<Map<String, Object>> enriched = rows.stream().map(this::enrichPlan).toList();
        return ApiResponse.success(enriched);
    }

    // ─── DETALLE ─────────────────────────────────────────────────

    @Operation(summary = "Detalle de un plan por ID")
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

        Map<String, Object> plan = rows.get(0);
        String estado = (String) plan.get("ESTADO");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> "SCOPE_SUPERADMIN".equals(a.getAuthority()));

        if (!isSuperAdmin && !"ACTIVO".equalsIgnoreCase(estado)) {
            return ApiResponse.error("Plan no encontrado");
        }

        return ApiResponse.success(enrichPlan(plan));
    }

    // ─── HELPER: ENRIQUECIMIENTO CON CÁLCULO ANUAL Y MÓDULOS ────

    private Map<String, Object> enrichPlan(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);

        Number idPlanNum = (Number) (row.get("ID_PLAN") != null ? row.get("ID_PLAN") : row.get("idPlan"));
        Long idPlan = idPlanNum != null ? idPlanNum.longValue() : null;

        Number pmNum = (Number) (row.get("PRECIO_MENSUAL") != null ? row.get("PRECIO_MENSUAL") : row.get("precioMensual"));
        long precioMensual = pmNum != null ? pmNum.longValue() : 0L;
        long precioAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularPrecioAnual(precioMensual);
        int descuentoAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.DESCUENTO_ANUAL_PORCENTAJE;

        Number limProp = (Number) (row.get("LIMITE_PROPIEDADES") != null ? row.get("LIMITE_PROPIEDADES") : row.get("limitePropiedades"));
        Number limUni = (Number) (row.get("LIMITE_UNIDADES") != null ? row.get("LIMITE_UNIDADES") : row.get("limiteUnidades"));
        Number limUsr = (Number) (row.get("LIMITE_USUARIOS") != null ? row.get("LIMITE_USUARIOS") : row.get("limiteUsuarios"));
        Number limStorage = (Number) (row.get("LIMITE_ALMACENAMIENTO_GB") != null ? row.get("LIMITE_ALMACENAMIENTO_GB") : row.get("limiteAlmacenamientoGb"));

        String codigo = (String) (row.get("CODIGO") != null ? row.get("CODIGO") : row.get("codigo"));
        String nombre = (String) (row.get("NOMBRE") != null ? row.get("NOMBRE") : row.get("nombre"));
        String descripcion = (String) (row.get("DESCRIPCION") != null ? row.get("DESCRIPCION") : row.get("descripcion"));
        String estado = (String) (row.get("ESTADO") != null ? row.get("ESTADO") : row.get("estado"));

        // Normalización camelCase y uppercase
        enriched.put("idPlan", idPlan);
        enriched.put("codigo", codigo);
        enriched.put("nombre", nombre);
        enriched.put("descripcion", descripcion);
        enriched.put("precioMensual", precioMensual);
        enriched.put("precioAnual", precioAnual);
        enriched.put("descuentoAnual", descuentoAnual);
        enriched.put("limitePropiedades", limProp != null ? limProp.intValue() : 0);
        enriched.put("limiteUnidades", limUni != null ? limUni.intValue() : 0);
        enriched.put("limiteUsuarios", limUsr != null ? limUsr.intValue() : 0);
        enriched.put("limiteAlmacenamientoGb", limStorage != null ? limStorage.intValue() : 0);
        enriched.put("estado", estado);

        enriched.put("PRECIO_ANUAL", precioAnual);
        enriched.put("DESCUENTO_ANUAL", descuentoAnual);

        // Consultar módulos habilitados desde PLAN_MODULOS
        if (idPlan != null) {
            try {
                String modSql = """
                    SELECT m.CODIGO AS "codigo", m.NOMBRE AS "nombre", m.DESCRIPCION AS "descripcion"
                    FROM PLAN_MODULOS pm
                    JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                    WHERE pm.ID_PLAN = :idPlan AND pm.HABILITADO = 'S'
                    ORDER BY m.ID_MODULO ASC
                    """;
                List<Map<String, Object>> modulos = jdbcTemplate.queryForList(modSql, new MapSqlParameterSource("idPlan", idPlan));
                List<String> codigos = modulos.stream().map(m -> (String) m.get("codigo")).toList();
                enriched.put("modulos", modulos);
                enriched.put("modulosCodigos", codigos);
            } catch (Exception e) {
                enriched.put("modulos", Collections.emptyList());
                enriched.put("modulosCodigos", Collections.emptyList());
            }
        } else {
            enriched.put("modulos", Collections.emptyList());
            enriched.put("modulosCodigos", Collections.emptyList());
        }

        // Features canónicas
        List<String> features = buildFeatures(codigo, limProp, limUni, limUsr, limStorage);
        enriched.put("features", features);

        return enriched;
    }

    private List<String> buildFeatures(String codigo, Number limProp, Number limUni, Number limUsr, Number limStorage) {
        List<String> list = new ArrayList<>();
        int prop = limProp != null ? limProp.intValue() : 1;
        int uni = limUni != null ? limUni.intValue() : 10;
        int usr = limUsr != null ? limUsr.intValue() : 5;
        int storage = limStorage != null ? limStorage.intValue() : 1;

        if ("FREE".equalsIgnoreCase(codigo)) {
            list.add(prop + (prop == 1 ? " Copropiedad" : " Copropiedades"));
            list.add("Hasta " + uni + " unidades residenciales");
            list.add("Hasta " + usr + " usuarios");
            list.add(storage + " GB almacenamiento seguro");
            list.add("Directorio de residentes y unidades");
            list.add("Pases de visita con código QR dinámico");
            list.add("Consola web para portería");
            list.add("Aislamiento multi-tenant Oracle RLS");
        } else if ("PRO".equalsIgnoreCase(codigo)) {
            list.add("Hasta " + prop + " copropiedades");
            list.add("Hasta " + uni + " unidades residenciales");
            list.add("Hasta " + usr + " usuarios administrativos");
            list.add(storage + " GB almacenamiento seguro");
            list.add("Recaudo en línea Wompi (PSE y tarjetas)");
            list.add("Custodia de paquetes con PIN de 6 dígitos");
            list.add("Control de bahías de parqueadero y placas");
            list.add("PQRS y convivencia con trazabilidad");
            list.add("Reservas de zonas comunes y amenidades");
            list.add("Gestión de obras, reformas y pólizas");
        } else if ("ENTERPRISE".equalsIgnoreCase(codigo)) {
            list.add("Copropiedades corporativas (hasta " + prop + ")");
            list.add("Hasta " + uni + " unidades residenciales");
            list.add("Hasta " + usr + " usuarios");
            list.add(storage + " GB almacenamiento de alta capacidad");
            list.add("Todo lo incluido en el Plan Profesional");
            list.add("Asambleas y votaciones en tiempo real Ley 675");
            list.add("Supervisión centralizada multi-propiedad");
            list.add("Exportación contable y auditoría avanzada");
            list.add("Acompañamiento y soporte corporativo");
        } else {
            list.add("Hasta " + prop + " copropiedades");
            list.add("Hasta " + uni + " unidades residenciales");
            list.add(storage + " GB almacenamiento seguro");
        }
        return list;
    }
}
