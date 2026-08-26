package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {
    Long create(OrganizationRequestDTO request, Long creadoPor);
    Optional<OrganizationDTO> findById(Long id);
    List<OrganizationDTO> findAll();
    void update(Long id, OrganizationRequestDTO request);
    void updateStatus(Long id, String status);
}
