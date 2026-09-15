package com.saed.backend.platform.service;

/**
 * Service for enforcing SaaS membership plan limits (properties, units, users)
 * and preventing concurrency race conditions via pessimistic database locking.
 */
public interface PlanLimitService {

    /**
     * Acquires a pessimistic lock on the organization's active membership and validates
     * that creating a new property will not exceed the plan's LIMITE_PROPIEDADES.
     *
     * @param organizationId organization ID
     */
    void validateAndLockPropertyLimit(Long organizationId);

    /**
     * Acquires a pessimistic lock on the organization's active membership and validates
     * that creating a new unit will not exceed the plan's LIMITE_UNIDADES.
     *
     * @param organizationId organization ID
     */
    void validateAndLockUnitLimit(Long organizationId);

    /**
     * Acquires a pessimistic lock on the organization's active membership and validates
     * that assigning a user will not exceed the plan's LIMITE_USUARIOS.
     * If the user already possesses an active assignment in the same organization,
     * no additional quota is consumed.
     *
     * @param organizationId organization ID
     * @param userId user ID (or null if creating a new user)
     */
    void validateAndLockUserLimit(Long organizationId, Long userId);

    /**
     * Resolves the owning organization ID for a given property ID.
     *
     * @param propertyId property ID
     * @return owning organization ID
     */
    Long getOrganizationIdForProperty(Long propertyId);
}
