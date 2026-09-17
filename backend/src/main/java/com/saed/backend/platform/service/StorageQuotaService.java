package com.saed.backend.platform.service;

import com.saed.backend.platform.dto.StorageQuotaDTO;

/**
 * Canonical service for enforcing organizational storage quotas in SAED 2.0.
 * Derives limits from active SaaS membership plans (PLANES.LIMITE_ALMACENAMIENTO_GB),
 * computes usage from Oracle database metadata (VERSIONES_DOCUMENTO, GASTOS),
 * and prevents race conditions across multi-instance clusters via Oracle row-level locking.
 */
public interface StorageQuotaService {

    /**
     * Binary gigabyte conversion constant (1 GB = 1024^3 bytes).
     */
    long BYTES_PER_GB = 1024L * 1024L * 1024L;

    /**
     * Maximum allowed size for a single file upload across the system (10 MB).
     */
    long MAX_SINGLE_FILE_BYTES = 10L * 1024L * 1024L;

    /**
     * Retrieves the storage limit in bytes for an organization based on its active plan.
     *
     * @param organizationId organization ID
     * @return storage limit in bytes (0 if plan has no storage allocation)
     */
    long getStorageLimitBytes(Long organizationId);

    /**
     * Computes the total storage used in bytes by an organization from valid persistent metadata.
     *
     * @param organizationId organization ID
     * @return current used storage in bytes
     */
    long getStorageUsedBytes(Long organizationId);

    /**
     * Computes the available storage in bytes for an organization.
     *
     * @param organizationId organization ID
     * @return remaining bytes available
     */
    long getStorageAvailableBytes(Long organizationId);

    /**
     * Retrieves a full snapshot of storage quota, usage, and metrics for an organization.
     *
     * @param organizationId organization ID
     * @return DTO with storage metrics
     */
    StorageQuotaDTO getQuota(Long organizationId);

    /**
     * Acquires a pessimistic lock on the organization's active membership and validates
     * that uploading a new file of size {@code newFileBytes} will not exceed the contracted quota.
     * <p>
     * Exact limit usage (used + new == limit) is allowed.
     * Strictly exceeding (used + new > limit) throws {@link com.saed.backend.common.exception.StorageQuotaExceededException}.
     *
     * @param organizationId organization ID
     * @param newFileBytes   size of the new file in bytes
     */
    void validateUpload(Long organizationId, long newFileBytes);

    /**
     * Acquires a pessimistic lock on the organization's active membership and validates
     * that replacing an existing file of {@code oldFileBytes} with a new file of {@code newFileBytes}
     * will not exceed the contracted quota: (used - oldFileBytes + newFileBytes <= limit).
     *
     * @param organizationId organization ID
     * @param oldFileBytes   size of the existing file being replaced
     * @param newFileBytes   size of the replacement file
     */
    void validateReplacement(Long organizationId, long oldFileBytes, long newFileBytes);
}
