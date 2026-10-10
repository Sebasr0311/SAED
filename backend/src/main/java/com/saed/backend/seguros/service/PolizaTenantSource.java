package com.saed.backend.seguros.service;

import java.util.List;

/**
 * Supplies the tenants (property plus the administrator account the job acts as) for which the
 * insurance expiry job runs (audit F10-08). Enumerating tenants needs a platform-owned, audited
 * cross-tenant listing, so the implementation belongs to the platform module; without one the job
 * does nothing rather than reading policies without a tenant scope.
 */
@FunctionalInterface
public interface PolizaTenantSource {

    List<TenantScope> tenants();

    record TenantScope(Long idOrganizacion, Long idPropiedad, Long idUsuarioAdministrador) {
    }
}
