package com.saed.backend.platform.service;

import java.util.Set;

/**
 * Service interface for evaluating and enforcing SaaS module entitlements.
 * Connects MEMBRESIAS -> PLANES -> PLAN_MODULOS -> MODULOS to govern tenant capabilities.
 */
public interface ModuleEntitlementService {

    /**
     * Checks if the current tenant in context is entitled to access the specified module.
     * Throws ModuleNotEntitledException if the module is not entitled or membership is inactive.
     *
     * @param moduleCode canonical module code (e.g. "ASAMBLEAS", "OBRAS", "POLIZAS", "RESERVAS")
     */
    void checkModuleAccess(String moduleCode);

    /**
     * Checks if a specific organization is entitled to access the specified module.
     *
     * @param organizationId organization ID
     * @param moduleCode canonical module code
     * @return true if entitled and active, false otherwise
     */
    boolean isModuleEnabled(Long organizationId, String moduleCode);

    /**
     * Returns the set of enabled module codes for the current tenant in context.
     *
     * @return Set of uppercase module codes
     */
    Set<String> getMyEnabledModules();

    /**
     * Returns the set of enabled module codes for the specified organization.
     *
     * @param organizationId organization ID
     * @return Set of uppercase module codes
     */
    Set<String> getEnabledModulesForOrganization(Long organizationId);

    /**
     * Resolves the current organization ID from context (SaedContextHolder, property, user assignment, or request headers).
     *
     * @return organization ID or null if unresolvable
     */
    Long resolveCurrentOrganizationId();
}
