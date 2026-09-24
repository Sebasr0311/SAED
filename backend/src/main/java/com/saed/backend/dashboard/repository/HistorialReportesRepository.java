package com.saed.backend.dashboard.repository;

import com.saed.backend.dashboard.dto.HistorialReporteDTO;

import java.util.List;
import java.util.Optional;

public interface HistorialReportesRepository {

    HistorialReporteDTO save(HistorialReporteDTO dto);

    List<HistorialReporteDTO> findHistorial(Long orgId, Long propertyId, int page, int size);

    Optional<HistorialReporteDTO> findById(Long id);
}
