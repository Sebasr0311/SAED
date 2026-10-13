package com.saed.backend.platform.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.exception.ModuleNotEntitledException;
import com.saed.backend.platform.service.ModuleEntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class ModuleEntitlementServiceImpl implements ModuleEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(ModuleEntitlementServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ModuleEntitlementServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void checkModuleAccess(String moduleCode) {
        if (!StringUtils.hasText(moduleCode)) {
            throw new IllegalArgumentException("Código de módulo requerido");
        }
        String normalizedCode = moduleCode.trim().toUpperCase();

        Long organizationId = resolveCurrentOrganizationId();
        if (organizationId == null) {
            log.warn("[Entitlement] Intento de acceso a módulo '{}' sin contexto de organización", normalizedCode);
            throw new ModuleNotEntitledException(
                    normalizedCode,
                    "Se requiere contexto de organización para acceder al módulo '" + normalizedCode + "'."
            );
        }

        // Evaluated under the caller's own context; no SUPERADMIN/bootstrap elevation.
        SaedContext callerCtx = SaedContextHolder.getContext();
        if (callerCtx == null || callerCtx.getUserId() == null) {
            throw new ModuleNotEntitledException(
                    normalizedCode,
                    "Se requiere contexto de organización para acceder al módulo '" + normalizedCode + "'."
            );
        }

        {
            // 1. Obtener membresía activa o de prueba para la organización
            String memSql = """
                SELECT m.ID_MEMBRESIA, m.ID_PLAN, m.ESTADO
                FROM MEMBRESIAS m
                WHERE m.ID_ORGANIZACION = :orgId
                  AND m.ESTADO IN ('ACTIVA', 'PRUEBA')
                  AND (m.FECHA_FIN IS NULL OR m.FECHA_FIN >= TRUNC(SYSDATE))
                ORDER BY m.ID_MEMBRESIA DESC
                """;

            List<Map<String, Object>> activeMemberships = jdbcTemplate.queryForList(
                    memSql,
                    new MapSqlParameterSource("orgId", organizationId)
            );

            if (activeMemberships.isEmpty()) {
                // Diagnosticar si existe membresía en otro estado
                String checkStateSql = """
                    SELECT m.ESTADO
                    FROM MEMBRESIAS m
                    WHERE m.ID_ORGANIZACION = :orgId
                    ORDER BY m.ID_MEMBRESIA DESC
                    """;
                List<Map<String, Object>> otherMemberships = jdbcTemplate.queryForList(
                        checkStateSql,
                        new MapSqlParameterSource("orgId", organizationId)
                );

                if (!otherMemberships.isEmpty()) {
                    String estado = (String) otherMemberships.get(0).get("ESTADO");
                    log.warn("[Entitlement] Módulo '{}' denegado para org {}: membresía en estado {}", normalizedCode, organizationId, estado);
                    throw new ModuleNotEntitledException(
                            normalizedCode,
                            "El módulo '" + normalizedCode + "' no está disponible porque la membresía de la organización se encuentra en estado " + estado + "."
                    );
                }

                log.warn("[Entitlement] Módulo '{}' denegado para org {}: sin membresía registrada", normalizedCode, organizationId);
                throw new ModuleNotEntitledException(
                        normalizedCode,
                        "La organización no posee una membresía activa para acceder al módulo '" + normalizedCode + "'."
                );
            }

            Long idPlan = ((Number) activeMemberships.get(0).get("ID_PLAN")).longValue();

            // 2. Validar si el módulo existe y está habilitado en PLAN_MODULOS para el plan de la membresía
            String planModSql = """
                SELECT pm.HABILITADO
                FROM PLAN_MODULOS pm
                JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                WHERE pm.ID_PLAN = :idPlan
                  AND UPPER(m.CODIGO) = :moduleCode
                """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    planModSql,
                    new MapSqlParameterSource()
                            .addValue("idPlan", idPlan)
                            .addValue("moduleCode", normalizedCode)
            );

            if (rows.isEmpty()) {
                // Verificar si el módulo existe en el catálogo general
                Number modCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM MODULOS WHERE UPPER(CODIGO) = :code",
                        new MapSqlParameterSource("code", normalizedCode),
                        Number.class
                );
                if (modCount == null || modCount.intValue() == 0) {
                    log.warn("[Entitlement] Módulo desconocido '{}'", normalizedCode);
                    throw new ModuleNotEntitledException(
                            normalizedCode,
                            "El módulo '" + normalizedCode + "' no existe en el catálogo del sistema."
                    );
                }

                log.warn("[Entitlement] Módulo '{}' no configurado para el plan {}", normalizedCode, idPlan);
                throw new ModuleNotEntitledException(
                        normalizedCode,
                        "El módulo '" + normalizedCode + "' no está habilitado para el plan contratado."
                );
            }

            String habilitado = (String) rows.get(0).get("HABILITADO");
            if (!"S".equalsIgnoreCase(habilitado)) {
                log.info("[Entitlement] Módulo '{}' no incluido en el plan {} de la organización {}", normalizedCode, idPlan, organizationId);
                throw new ModuleNotEntitledException(
                        normalizedCode,
                        "El módulo '" + normalizedCode + "' no está incluido en el plan contratado por la copropiedad."
                );
            }

            log.debug("[Entitlement] Módulo '{}' concedido exitosamente para organización {}", normalizedCode, organizationId);
        }
    }

    @Override
    public boolean isModuleEnabled(Long organizationId, String moduleCode) {
        if (organizationId == null || !StringUtils.hasText(moduleCode)) {
            return false;
        }
        SaedContext prevCtx = SaedContextHolder.getContext();
        boolean elevated = false;
        if (prevCtx == null || prevCtx.getUserId() == null) {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L)
                    .organizationId(organizationId)
                    .propertyId(1L)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build());
            elevated = true;
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, 1, 'SUPERADMIN'); END;", organizationId)
                );
            } catch (Exception ignored) {}
        }
        try {
            String sql = """
                SELECT pm.HABILITADO
                FROM MEMBRESIAS mem
                JOIN PLAN_MODULOS pm ON mem.ID_PLAN = pm.ID_PLAN
                JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                WHERE mem.ID_ORGANIZACION = :orgId
                  AND mem.ESTADO IN ('ACTIVA', 'PRUEBA')
                  AND (mem.FECHA_FIN IS NULL OR mem.FECHA_FIN >= TRUNC(SYSDATE))
                  AND UPPER(m.CODIGO) = :moduleCode
                """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("orgId", organizationId)
                            .addValue("moduleCode", moduleCode.trim().toUpperCase())
            );
            if (rows.isEmpty()) {
                return false;
            }
            return "S".equalsIgnoreCase((String) rows.get(0).get("HABILITADO"));
        } catch (Exception e) {
            log.debug("Error verificando isModuleEnabled: {}", e.getMessage());
            return false;
        } finally {
            if (elevated) {
                SaedContextHolder.setContext(prevCtx);
                try {
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public Set<String> getMyEnabledModules() {
        Long orgId = resolveCurrentOrganizationId();
        if (orgId == null) {
            return Collections.emptySet();
        }
        return getEnabledModulesForOrganization(orgId);
    }

    @Override
    public Set<String> getEnabledModulesForOrganization(Long organizationId) {
        if (organizationId == null) {
            return Collections.emptySet();
        }
        SaedContext prevCtx = SaedContextHolder.getContext();
        boolean elevated = false;
        if (prevCtx == null || prevCtx.getUserId() == null) {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L)
                    .organizationId(organizationId)
                    .propertyId(1L)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build());
            elevated = true;
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, 1, 'SUPERADMIN'); END;", organizationId)
                );
            } catch (Exception ignored) {}
        }
        try {
            String sql = """
                SELECT DISTINCT UPPER(m.CODIGO) AS CODIGO
                FROM MEMBRESIAS mem
                JOIN PLAN_MODULOS pm ON mem.ID_PLAN = pm.ID_PLAN
                JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                WHERE mem.ID_ORGANIZACION = :orgId
                  AND mem.ESTADO IN ('ACTIVA', 'PRUEBA')
                  AND (mem.FECHA_FIN IS NULL OR mem.FECHA_FIN >= TRUNC(SYSDATE))
                  AND pm.HABILITADO = 'S'
                """;
            List<String> list = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("orgId", organizationId),
                    (rs, rowNum) -> rs.getString("CODIGO")
            );
            return new HashSet<>(list);
        } finally {
            if (elevated) {
                SaedContextHolder.setContext(prevCtx);
                try {
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public Long resolveCurrentOrganizationId() {
        // 1. SaedContextHolder (fuente primaria de tenant context)
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            if (ctx.getOrganizationId() != null) {
                return ctx.getOrganizationId();
            }
            if (ctx.getPropertyId() != null) {
                Long orgFromProp = getOrgFromProperty(ctx.getPropertyId());
                if (orgFromProp != null) {
                    return orgFromProp;
                }
            }
            if (ctx.getUserId() != null) {
                Long orgFromUsr = getOrgFromUser(ctx.getUserId());
                if (orgFromUsr != null) {
                    return orgFromUsr;
                }
            }
        }

        // F3-07 / F3-13: no client-supplied organization (headers, request parameters) and no
        // "first active organization" fallback. Without a server-side context there is no tenant,
        // so module access is denied.
        return null;
    }

    private Long getOrgFromProperty(Long propertyId) {
        try {
            String sql = "SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId";
            List<Long> list = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("propId", propertyId),
                    (rs, rowNum) -> rs.getLong("ID_ORGANIZACION")
            );
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.debug("Error resolviendo organizacion desde propiedad {}: {}", propertyId, e.getMessage());
            return null;
        }
    }

    private Long getOrgFromUser(Long userId) {
        try {
            String sql = """
                SELECT ID_ORGANIZACION
                FROM USUARIO_ASIGNACIONES
                WHERE ID_USUARIO = :usrId
                  AND ESTADO IN ('ACTIVA', 'ACTIVO')
                  AND ID_ORGANIZACION IS NOT NULL
                ORDER BY ID_ASIGNACION ASC
                """;
            List<Long> list = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("usrId", userId),
                    (rs, rowNum) -> rs.getLong("ID_ORGANIZACION")
            );
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.debug("Error resolviendo organizacion desde usuario {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
