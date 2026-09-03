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
}
