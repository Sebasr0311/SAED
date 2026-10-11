package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UnitRepository {
    Long create(UnitRequestDTO request);
    Optional<UnitDTO> findById(Long id);
    List<UnitDTO> findAll();
    List<UnitDTO> findAll(Long propertyId);
    void update(Long id, UnitRequestDTO request);

    /** Whether another unit of the property uses the identifier in the same block (null block is its own group). */
    boolean existsIdentifier(Long propertyId, Long blockId, String identifier, Long excludeUnitId);

    /**
     * Locks the property row (serializing concurrent coefficient changes of one property) and returns the
     * sum of the coefficients of its non-inactive units, excluding {@code excludeUnitId}.
     */
    BigDecimal lockPropertyAndSumCoefficients(Long propertyId, Long excludeUnitId);

    UnitCoefficientSummaryDTO coefficientSummary(Long propertyId);
}
