package com.saed.backend.dashboard.service;

import com.saed.backend.dashboard.dto.*;
import com.saed.backend.dashboard.export.ExportResult;

import java.util.List;

public interface ReportesConfiguradosService {

    List<ReporteConfiguradoDTO> listarConfiguraciones(Long propertyId);

    ReporteConfiguradoDTO obtenerPorId(Long id);

    ReporteConfiguradoDTO crearConfiguracion(ReporteConfiguradoCreateRequest request);

    ReporteConfiguradoDTO actualizarConfiguracion(Long id, ReporteConfiguradoUpdateRequest request);

    void desactivarConfiguracion(Long id);

    ExportResult generarReporte(Long idConfig, String overrideFormato, String overrideFiltrosJson);

    GenerarReporteResponseDTO generarReporteMetadata(Long idConfig, String overrideFormato, String overrideFiltrosJson);

    List<HistorialReporteDTO> listarHistorial(Long propertyId, int page, int size);

    HistorialReporteDTO obtenerHistorialPorId(Long idHistorial);
}
