package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;

import java.util.List;
import java.util.Optional;

public interface BlockRepository {
    Long create(BlockRequestDTO request);
    Optional<BlockDTO> findById(Long id);
    List<BlockDTO> findByPropertyId(Long propertyId);
    void update(Long id, BlockRequestDTO request);
    void updateStatus(Long id, String estado);
    void delete(Long id);
    int countChildren(Long id);
    int countUnits(Long id);
    boolean existsByIdAndPropertyId(Long id, Long propertyId);
}
