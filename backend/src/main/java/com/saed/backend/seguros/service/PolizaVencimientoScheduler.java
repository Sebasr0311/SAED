package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Daily insurance expiry job (audit F10-08). Every tenant runs in its own property-scoped context
 * (never a global or bootstrap context) through {@link PolizaVencimientoService}; a failure in one
 * tenant does not stop the others, and the context is always cleared afterwards. The job is enabled unless
 * {@code saed.seguros.vencimiento-enabled=false}; running it on several instances is safe because the alert per
 * policy and expiry period is unique in the database (V5.71).
 */
@Component
@ConditionalOnProperty(name = "saed.seguros.vencimiento-enabled", havingValue = "true", matchIfMissing = true)
public class PolizaVencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolizaVencimientoScheduler.class);

    private final PolizaVencimientoService service;
    private final Optional<PolizaTenantSource> tenantSource;

    public PolizaVencimientoScheduler(PolizaVencimientoService service, Optional<PolizaTenantSource> tenantSource) {
        this.service = service;
        this.tenantSource = tenantSource;
    }

    @Scheduled(cron = "${saed.seguros.vencimiento-cron:0 0 3 * * ?}")
    public int verificarYAlertarVencimientos() {
        if (tenantSource.isEmpty()) {
            log.warn("[PolizaScheduler] No PolizaTenantSource configured; expiry detection runs only on demand per property");
            return 0;
        }
        LocalDate hoy = LocalDate.now();
        int total = 0;
        List<PolizaTenantSource.TenantScope> tenants;
        try {
            tenants = tenantSource.get().tenants();
        } catch (RuntimeException e) {
            log.error("[PolizaScheduler] Tenant enumeration failed; no expiry detection this run ({})",
                    e.getClass().getSimpleName());
            return 0;
        }
        for (PolizaTenantSource.TenantScope tenant : tenants) {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(tenant.idUsuarioAdministrador())
                    .organizationId(tenant.idOrganizacion())
                    .propertyId(tenant.idPropiedad())
                    .roleCode("ADMIN_PROPIEDAD")
                    .build());
            try {
                total += service.detectarVencimientos(tenant.idPropiedad(), hoy);
            } catch (RuntimeException e) {
                log.error("[PolizaScheduler] Expiry detection failed for property {} ({})",
                        tenant.idPropiedad(), e.getClass().getSimpleName());
            } finally {
                SaedContextHolder.clearContext();
            }
        }
        return total;
    }
}
