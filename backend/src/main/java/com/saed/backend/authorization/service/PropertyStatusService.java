package com.saed.backend.authorization.service;

import com.saed.backend.authorization.exception.InactivePropertyException;
import com.saed.backend.authorization.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing property active/inactive status with in-memory caching.
 * Ensures operational modifications are blocked when a property is frozen (INACTIVA).
 */
@Service
public class PropertyStatusService {

    private static final Logger log = LoggerFactory.getLogger(PropertyStatusService.class);
    private static final long CACHE_TTL_MS = 30_000L; // 30 seconds TTL

    private record CachedStatus(String status, long timestamp) {}

    private final PropertyRepository propertyRepository;
    private final Map<Long, CachedStatus> cache = new ConcurrentHashMap<>();

    public PropertyStatusService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Checks whether a property is active.
     * If propertyId is null, returns true (not scoped to a specific property).
     */
    public boolean isPropertyActive(Long propertyId) {
        if (propertyId == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        CachedStatus entry = cache.get(propertyId);
        if (entry != null && (now - entry.timestamp() < CACHE_TTL_MS)) {
            return "ACTIVA".equalsIgnoreCase(entry.status());
        }

        // F4-H08 / F04-03: fail closed. An unknown (or RLS-hidden) property or a failed lookup is never
        // treated as writable; only a positively read status is cached.
        Optional<String> statusOpt;
        try {
            statusOpt = propertyRepository.getPropertyStatus(propertyId);
        } catch (RuntimeException e) {
            log.warn("Property status lookup failed for propertyId={} ({}); treating as not active",
                    propertyId, e.getClass().getSimpleName());
            return false;
        }
        if (statusOpt.isEmpty()) {
            return false;
        }
        String status = statusOpt.get();
        cache.put(propertyId, new CachedStatus(status, now));
        return "ACTIVA".equalsIgnoreCase(status);
    }

    /**
     * Throws InactivePropertyException if the property is not ACTIVA.
     */
    public void validatePropertyIsActive(Long propertyId) {
        if (!isPropertyActive(propertyId)) {
            throw new InactivePropertyException("La copropiedad se encuentra inactiva. Las operaciones de modificación están temporalmente deshabilitadas.");
        }
    }

    /**
     * Updates the cached status immediately (e.g. when an admin toggles status).
     */
    public void updateCache(Long propertyId, String newStatus) {
        if (propertyId != null && newStatus != null) {
            cache.put(propertyId, new CachedStatus(newStatus.toUpperCase(), System.currentTimeMillis()));
        }
    }

    /**
     * Invalidates the cache entry for a property.
     */
    public void invalidateCache(Long propertyId) {
        if (propertyId != null) {
            cache.remove(propertyId);
        }
    }
}
