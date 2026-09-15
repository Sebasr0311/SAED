package com.saed.backend.platform.service.impl;

import com.saed.backend.common.exception.PlanLimitExceededException;
import com.saed.backend.platform.exception.InactiveMembershipException;
import com.saed.backend.platform.service.PlanLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class PlanLimitServiceImpl implements PlanLimitService {

    private static final Logger log = LoggerFactory.getLogger(PlanLimitServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlanLimitServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void validateAndLockPropertyLimit(Long organizationId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para validar límites de propiedades");
        }

        ActivePlanLimits limits = lockAndGetActivePlanLimits(organizationId, "crear propiedades");
        Long maxLimit = limits.getLimitePropiedades();

        // 0 or NULL means unlimited
        if (maxLimit != null && maxLimit > 0) {
            String countSql = "SELECT COUNT(*) FROM PROPIEDADES WHERE ID_ORGANIZACION = :orgId AND ESTADO = 'ACTIVA'";
            Number countNum = jdbcTemplate.queryForObject(
                    countSql,
                    new MapSqlParameterSource("orgId", organizationId),
                    Number.class
            );
            long currentCount = countNum != null ? countNum.longValue() : 0L;

            if (currentCount >= maxLimit) {
                log.warn("Límite de propiedades alcanzado para organización {}: {}/{}", organizationId, currentCount, maxLimit);
                throw new PlanLimitExceededException("PROPIEDADES", currentCount, maxLimit);
            }
        }
    }

    @Override
    @Transactional
    public void validateAndLockUnitLimit(Long organizationId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para validar límites de unidades");
        }

        ActivePlanLimits limits = lockAndGetActivePlanLimits(organizationId, "crear unidades");
        Long maxLimit = limits.getLimiteUnidades();

        // 0 or NULL means unlimited
        if (maxLimit != null && maxLimit > 0) {
            String countSql = """
                SELECT COUNT(u.ID_UNIDAD)
                FROM UNIDADES u
                JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD
                WHERE pr.ID_ORGANIZACION = :orgId
                  AND (u.ESTADO IS NULL OR u.ESTADO IN ('ACTIVA', 'ACTIVO'))
                """;
            Number countNum = jdbcTemplate.queryForObject(
                    countSql,
                    new MapSqlParameterSource("orgId", organizationId),
                    Number.class
            );
            long currentCount = countNum != null ? countNum.longValue() : 0L;

            if (currentCount >= maxLimit) {
                log.warn("Límite de unidades alcanzado para organización {}: {}/{}", organizationId, currentCount, maxLimit);
                throw new PlanLimitExceededException("UNIDADES", currentCount, maxLimit);
            }
        }
    }

    @Override
    @Transactional
    public void validateAndLockUserLimit(Long organizationId, Long userId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para validar límites de usuarios");
        }

        ActivePlanLimits limits = lockAndGetActivePlanLimits(organizationId, "asignar usuarios");
        Long maxLimit = limits.getLimiteUsuarios();

        // 0 or NULL means unlimited
        if (maxLimit != null && maxLimit > 0) {
            // Check if this specific user already has an active assignment in this organization
            if (userId != null) {
                String checkExistingSql = """
                    SELECT COUNT(*)
                    FROM USUARIO_ASIGNACIONES ua
                    WHERE ua.ID_ORGANIZACION = :orgId
                      AND ua.ID_USUARIO = :userId
                      AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                    """;
                Number existingNum = jdbcTemplate.queryForObject(
                        checkExistingSql,
                        new MapSqlParameterSource()
                                .addValue("orgId", organizationId)
                                .addValue("userId", userId),
                        Number.class
                );
                if (existingNum != null && existingNum.longValue() > 0) {
                    // User is already active in this org, does not consume an extra slot
                    return;
                }
            }

            String countSql = """
                SELECT COUNT(DISTINCT ua.ID_USUARIO)
                FROM USUARIO_ASIGNACIONES ua
                WHERE ua.ID_ORGANIZACION = :orgId
                  AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                """;
            Number countNum = jdbcTemplate.queryForObject(
                    countSql,
                    new MapSqlParameterSource("orgId", organizationId),
                    Number.class
            );
            long currentCount = countNum != null ? countNum.longValue() : 0L;

            if (currentCount >= maxLimit) {
                log.warn("Límite de usuarios alcanzado para organización {}: {}/{}", organizationId, currentCount, maxLimit);
                throw new PlanLimitExceededException("USUARIOS", currentCount, maxLimit);
            }
        }
    }

    @Override
    public Long getOrganizationIdForProperty(Long propertyId) {
        if (propertyId == null) {
            throw new IllegalArgumentException("ID de propiedad requerido");
        }
        String sql = "SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId";
        List<Long> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("propId", propertyId),
                (rs, rowNum) -> rs.getLong("ID_ORGANIZACION")
        );
        if (results.isEmpty()) {
            throw new NoSuchElementException("Propiedad no encontrada: " + propertyId);
        }
        return results.get(0);
    }

    private ActivePlanLimits lockAndGetActivePlanLimits(Long organizationId, String action) {
        // Pessimistic lock on the active/trial membership row
        String lockSql = """
            SELECT m.ID_MEMBRESIA, m.ID_PLAN, m.ESTADO,
                   p.LIMITE_PROPIEDADES, p.LIMITE_UNIDADES, p.LIMITE_USUARIOS
            FROM MEMBRESIAS m
            JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
            WHERE m.ID_ORGANIZACION = :orgId
              AND m.ESTADO IN ('ACTIVA', 'PRUEBA')
              AND (m.FECHA_FIN IS NULL OR m.FECHA_FIN >= TRUNC(SYSDATE))
            FOR UPDATE OF m.ID_MEMBRESIA
            """;

        List<ActivePlanLimits> rows = jdbcTemplate.query(
                lockSql,
                new MapSqlParameterSource("orgId", organizationId),
                (rs, rowNum) -> new ActivePlanLimits(
                        rs.getLong("ID_MEMBRESIA"),
                        rs.getLong("ID_PLAN"),
                        rs.getString("ESTADO"),
                        rs.getObject("LIMITE_PROPIEDADES") != null ? rs.getLong("LIMITE_PROPIEDADES") : null,
                        rs.getObject("LIMITE_UNIDADES") != null ? rs.getLong("LIMITE_UNIDADES") : null,
                        rs.getObject("LIMITE_USUARIOS") != null ? rs.getLong("LIMITE_USUARIOS") : null
                )
        );

        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        // If no active row found, inspect any existing membership to provide precise diagnostic
        String inspectSql = """
            SELECT m.ESTADO, m.FECHA_FIN
            FROM MEMBRESIAS m
            WHERE m.ID_ORGANIZACION = :orgId
            ORDER BY m.FECHA_INICIO DESC
            """;
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                inspectSql,
                new MapSqlParameterSource("orgId", organizationId)
        );

        if (!existing.isEmpty()) {
            String estado = (String) existing.get(0).get("ESTADO");
            throw new InactiveMembershipException(
                    String.format("La membresía de la organización no está vigente (estado: %s). No se puede %s.",
                            estado != null ? estado : "INACTIVA", action)
            );
        }

        throw new InactiveMembershipException(
                String.format("La organización no cuenta con una suscripción a ningún plan SaaS. No se puede %s.", action)
        );
    }

    private static class ActivePlanLimits {
        private final Long idMembresia;
        private final Long idPlan;
        private final String estado;
        private final Long limitePropiedades;
        private final Long limiteUnidades;
        private final Long limiteUsuarios;

        ActivePlanLimits(Long idMembresia, Long idPlan, String estado,
                         Long limitePropiedades, Long limiteUnidades, Long limiteUsuarios) {
            this.idMembresia = idMembresia;
            this.idPlan = idPlan;
            this.estado = estado;
            this.limitePropiedades = limitePropiedades;
            this.limiteUnidades = limiteUnidades;
            this.limiteUsuarios = limiteUsuarios;
        }

        public Long getIdMembresia() {
            return idMembresia;
        }

        public Long getIdPlan() {
            return idPlan;
        }

        public String getEstado() {
            return estado;
        }

        public Long getLimitePropiedades() {
            return limitePropiedades;
        }

        public Long getLimiteUnidades() {
            return limiteUnidades;
        }

        public Long getLimiteUsuarios() {
            return limiteUsuarios;
        }
    }
}
