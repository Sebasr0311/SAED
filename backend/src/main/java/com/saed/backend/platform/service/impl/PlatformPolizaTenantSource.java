package com.saed.backend.platform.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.service.PolizaTenantSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Platform-owned tenant enumeration for the insurance expiry job (audit F10-08).
 *
 * <p>Listing the properties that hold active policies is inherently cross-tenant, so this single read runs in the
 * platform provisioning context (the same system context the onboarding paths use) and returns identifiers only:
 * organization, property and the active property administrator the job acts as. No policy content, e-mail or
 * personal data is read. The caller's previous context is restored (or cleared) in all cases; the policy
 * detection itself then runs per tenant in a property-scoped administrator context, never in this one.
 */
@Component
public class PlatformPolizaTenantSource implements PolizaTenantSource {

    private static final Logger log = LoggerFactory.getLogger(PlatformPolizaTenantSource.class);

    private static final String TENANTS_SQL = """
            SELECT pr.ID_ORGANIZACION, pr.ID_PROPIEDAD,
                   (SELECT MIN(ua.ID_USUARIO)
                      FROM USUARIO_ASIGNACIONES ua
                      JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
                      JOIN USUARIOS u ON u.ID_USUARIO = ua.ID_USUARIO
                     WHERE ua.ID_PROPIEDAD = pr.ID_PROPIEDAD
                       AND r.CODIGO = 'ADMIN_PROPIEDAD'
                       AND ua.ESTADO = 'ACTIVA'
                       AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
                       AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
                       AND u.ESTADO = 'ACTIVO') AS ID_ADMIN
              FROM PROPIEDADES pr
             WHERE pr.ESTADO = 'ACTIVA'
               AND EXISTS (SELECT 1 FROM POLIZAS_SEGURO ps
                            WHERE ps.ID_PROPIEDAD = pr.ID_PROPIEDAD AND ps.ESTADO <> 'CANCELADA')
             ORDER BY pr.ID_PROPIEDAD
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlatformPolizaTenantSource(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TenantScope> tenants() {
        SaedContext previous = SaedContextHolder.getContext();
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());
        try {
            List<TenantScope> tenants = jdbcTemplate.query(TENANTS_SQL, (rs, rowNum) -> {
                long admin = rs.getLong("ID_ADMIN");
                if (rs.wasNull()) {
                    return null;
                }
                return new TenantScope(rs.getLong("ID_ORGANIZACION"), rs.getLong("ID_PROPIEDAD"), admin);
            });
            List<TenantScope> actionable = tenants.stream().filter(Objects::nonNull).toList();
            if (actionable.size() != tenants.size()) {
                log.warn("[PolizaTenantSource] {} propiedad(es) con pólizas activas sin administrador activo; omitidas",
                        tenants.size() - actionable.size());
            }
            return actionable;
        } finally {
            if (previous != null) {
                SaedContextHolder.setContext(previous);
            } else {
                SaedContextHolder.clearContext();
            }
        }
    }
}
