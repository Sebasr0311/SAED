package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import java.util.List;
import java.util.Optional;

public interface UnitRepository {
    Long create(UnitRequestDTO request);
    Optional<UnitDTO> findById(Long id);
    List<UnitDTO> findAll();
    void update(Long id, UnitRequestDTO request);
}
