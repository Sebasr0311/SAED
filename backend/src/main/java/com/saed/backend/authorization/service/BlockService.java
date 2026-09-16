package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import com.saed.backend.authorization.dto.BlockTreeDTO;

import java.util.List;

public interface BlockService {
    List<BlockDTO> findByPropertyId(Long propertyId);
    List<BlockTreeDTO> findTreeByPropertyId(Long propertyId);
    BlockDTO findById(Long propertyId, Long id);
    Long create(Long propertyId, BlockRequestDTO request);
    void update(Long propertyId, Long id, BlockRequestDTO request);
    void updateStatus(Long propertyId, Long id, String estado);
    void delete(Long propertyId, Long id);
}
