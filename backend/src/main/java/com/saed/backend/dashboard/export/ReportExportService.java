package com.saed.backend.dashboard.export;

import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de servicio para la exportación de reportes operativos y financieros en SAED 2.0.
 */
public interface ReportExportService {

    ExportResult exportCarteraMorosa(List<CarteraMorosaDTO> items, ExportFormat format, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin);

    ExportResult exportEjecucionCuotas(List<EjecucionCuotasDTO> items, ExportFormat format, Long propertyId, String periodoInicio, String periodoFin);

    ExportResult exportPagosRecientes(List<PagoRecienteDTO> items, ExportFormat format, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin);

    ExportResult exportEjecucionPresupuestal(EjecucionPresupuestalGlobalDTO dto, ExportFormat format, Long propertyId, Integer vigencia);
}
