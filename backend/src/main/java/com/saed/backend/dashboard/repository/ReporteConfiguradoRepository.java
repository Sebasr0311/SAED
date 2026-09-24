package com.saed.backend.dashboard.repository;

import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoUpdateRequest;

import java.util.List;
import java.util.Optional;

public interface ReporteConfiguradoRepository {

    Optional<ReporteConfiguradoDTO> findById(Long id);

    Optional<ReporteConfiguradoDTO> findByCodigo(String codigo);

    List<ReporteConfiguradoDTO> findAllVisible(Long orgId, Long propertyId);

    ReporteConfiguradoDTO create(ReporteConfiguradoCreateRequest request, Long orgId, Long propId, Long userId);

    ReporteConfiguradoDTO update(Long id, ReporteConfiguradoUpdateRequest request, Long userId);

    boolean deactivate(Long id);
}
