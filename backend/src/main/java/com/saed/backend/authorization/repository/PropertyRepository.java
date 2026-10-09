package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import java.util.List;
import java.util.Optional;

public interface PropertyRepository {
    Long create(PropertyRequestDTO request);
    Optional<PropertyDTO> findById(Long id);
    List<PropertyDTO> findAll();
    void update(Long id, PropertyRequestDTO request);
    void updateStatus(Long id, String estado);
    long countByOrganization(Long orgId);
    Optional<Long> getPropertyLimit(Long orgId);
    Optional<String> getPropertyStatus(Long id);
    boolean deletePropertyCascade(Long propertyId, Long organizationId);

    /**
     * Returns the names of the protected tables (financial, legal or audit history) that still
     * hold rows for the property. An empty list means the property may be hard-deleted.
     */
    List<String> findProtectedHistory(Long propertyId);
}
