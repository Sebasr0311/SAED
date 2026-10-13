package com.saed.backend.dashboard.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * ReportExportServiceImpl — Motor transversal de exportación para SAED 2.0.
 *
 * Implementa exportación en formatos JSON, CSV (RFC 4180 con BOM UTF-8) y PDF (OpenHTMLtoPDF).
 * Aplica control de volumen (máx. 10.000 registros), auditoría de exportación y desacoplamiento de BD.
 */
@Service
public class ReportExportServiceImpl implements ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportServiceImpl.class);
    private static final int MAX_EXPORT_LIMIT = 10000;
    private static final MediaType MEDIA_TYPE_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final MediaType MEDIA_TYPE_XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PdfService pdfService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AuditService auditService;

    public ReportExportServiceImpl(PdfService pdfService, ObjectMapper objectMapper) {
        this.pdfService = pdfService;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // 1. CARTERA MOROSA
    // =========================================================================

    @Override
    public ExportResult exportCarteraMorosa(List<CarteraMorosaDTO> items, ExportFormat format, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin) {
        enforceVolumeLimit(items);
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        String baseFilename = "cartera_morosa_" + timestamp;

        ExportResult result = switch (format) {
            case CSV, XLSX -> tabular(format, baseFilename, "Cartera Morosa", CARTERA_HEADERS, carteraRows(items));
            case PDF -> new ExportResult(generateCarteraMorosaPdf(items, propertyId, fechaInicio, fechaFin),
                    baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            case JSON -> new ExportResult(serializeToJson(items), baseFilename + ".json", MediaType.APPLICATION_JSON);
        };

        recordAudit(format, "CARTERA_MOROSA", propertyId);
        return result;
    }

    private static final List<String> CARTERA_HEADERS = List.of("Unidad", "Propiedad", "Cuotas Pendientes",
            "Deuda Total", "Primer Vencimiento", "Ultimo Vencimiento", "Dias Mora");

    private static List<List<Object>> carteraRows(List<CarteraMorosaDTO> items) {
        List<List<Object>> rows = new ArrayList<>();
        if (items != null) {
            for (CarteraMorosaDTO item : items) {
                rows.add(Arrays.asList(item.unidad(), item.propiedad(), item.cuotasPendientes(),
                        item.deudaTotal() != null ? item.deudaTotal() : BigDecimal.ZERO,
                        item.primerVencimiento(), item.ultimoVencimiento(), item.diasMora()));
            }
        }
        return rows;
    }

    private static ExportResult tabular(ExportFormat format, String baseFilename, String sheetName,
                                        List<String> headers, List<List<Object>> rows) {
        if (format == ExportFormat.XLSX) {
            return new ExportResult(TabularExport.xlsx(sheetName, headers, rows), baseFilename + ".xlsx", MEDIA_TYPE_XLSX);
        }
        return new ExportResult(TabularExport.csv(headers, rows), baseFilename + ".csv", MEDIA_TYPE_CSV);
    }

    private byte[] generateCarteraMorosaPdf(List<CarteraMorosaDTO> items, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin) {
        StringBuilder html = new StringBuilder();
        appendHtmlHeader(html, "Reporte de Cartera Morosa");

        html.append("<div class=\"header\">");
        html.append("<h1>SAED 2.0 &#8212; Reporte de Cartera Morosa</h1>");
        html.append("<div class=\"meta\">");
        if (propertyId != null) {
            html.append("<span><strong>Propiedad ID:</strong> ").append(propertyId).append("</span> | ");
        }
        if (fechaInicio != null) {
            html.append("<span><strong>Desde:</strong> ").append(fechaInicio.format(DISPLAY_DATE_FORMAT)).append("</span> | ");
        }
        if (fechaFin != null) {
            html.append("<span><strong>Hasta:</strong> ").append(fechaFin.format(DISPLAY_DATE_FORMAT)).append("</span> | ");
        }
        html.append("<span><strong>Generado:</strong> ").append(LocalDateTime.now().format(DISPLAY_DATETIME_FORMAT)).append("</span>");
        html.append("</div></div>");

        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th style=\"width: 12%;\">Unidad</th>");
        html.append("<th style=\"width: 25%;\">Propiedad</th>");
        html.append("<th style=\"width: 12%; text-align: right;\">Cuotas Pend.</th>");
        html.append("<th style=\"width: 17%; text-align: right;\">Deuda Total</th>");
        html.append("<th style=\"width: 12%; text-align: center;\">1er Venc.</th>");
        html.append("<th style=\"width: 12%; text-align: center;\">&#218;lt. Venc.</th>");
        html.append("<th style=\"width: 10%; text-align: right;\">D&#237;as Mora</th>");
        html.append("</tr></thead><tbody>");

        long totalCuotas = 0;
        BigDecimal totalDeuda = BigDecimal.ZERO;

        if (items != null && !items.isEmpty()) {
            for (CarteraMorosaDTO item : items) {
                if (item.cuotasPendientes() != null) totalCuotas += item.cuotasPendientes();
                if (item.deudaTotal() != null) totalDeuda = totalDeuda.add(item.deudaTotal());

                html.append("<tr>");
                html.append("<td><strong>").append(escapeXml(item.unidad())).append("</strong></td>");
                html.append("<td>").append(escapeXml(item.propiedad())).append("</td>");
                html.append("<td style=\"text-align: right;\">").append(item.cuotasPendientes() != null ? item.cuotasPendientes() : 0).append("</td>");
                html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(item.deudaTotal())).append("</td>");
                html.append("<td style=\"text-align: center;\">").append(item.primerVencimiento() != null ? item.primerVencimiento().toString() : "-").append("</td>");
                html.append("<td style=\"text-align: center;\">").append(item.ultimoVencimiento() != null ? item.ultimoVencimiento().toString() : "-").append("</td>");
                html.append("<td style=\"text-align: right; font-weight: bold; color: #DC2626;\">").append(item.diasMora() != null ? item.diasMora() : 0).append("</td>");
                html.append("</tr>");
            }
            html.append("<tr class=\"total-row\">");
            html.append("<td colspan=\"2\">TOTAL GENERAL (").append(items.size()).append(" registros)</td>");
            html.append("<td style=\"text-align: right;\">").append(totalCuotas).append("</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(totalDeuda)).append("</td>");
            html.append("<td colspan=\"3\"></td>");
            html.append("</tr>");
        } else {
            html.append("<tr><td colspan=\"7\" class=\"no-data\">No se encontraron unidades en mora para los criterios seleccionados.</td></tr>");
        }

        html.append("</tbody></table>");
        appendHtmlFooter(html);

        return renderPdf(html.toString(), "CARTERA_MOROSA");
    }

    // =========================================================================
    // 2. EJECUCIÓN DE CUOTAS
    // =========================================================================

    @Override
    public ExportResult exportEjecucionCuotas(List<EjecucionCuotasDTO> items, ExportFormat format, Long propertyId, String periodoInicio, String periodoFin) {
        enforceVolumeLimit(items);
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        String baseFilename = "ejecucion_cuotas_" + timestamp;

        ExportResult result = switch (format) {
            case CSV, XLSX -> tabular(format, baseFilename, "Ejecucion Cuotas", CUOTAS_HEADERS, cuotasRows(items));
            case PDF -> new ExportResult(generateEjecucionCuotasPdf(items, propertyId, periodoInicio, periodoFin),
                    baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            case JSON -> new ExportResult(serializeToJson(items), baseFilename + ".json", MediaType.APPLICATION_JSON);
        };

        recordAudit(format, "EJECUCION_CUOTAS", propertyId);
        return result;
    }

    private static final List<String> CUOTAS_HEADERS = List.of("Periodo", "Total Cuotas", "Pagadas", "Pendientes",
            "Total Facturado", "Total Pendiente", "Total Recaudado", "Porcentaje Recaudado (%)");

    private static List<List<Object>> cuotasRows(List<EjecucionCuotasDTO> items) {
        List<List<Object>> rows = new ArrayList<>();
        if (items != null) {
            for (EjecucionCuotasDTO item : items) {
                rows.add(Arrays.asList(item.periodo(), item.totalCuotas(), item.pagadas(), item.pendientes(),
                        orZero(item.totalFacturado()), orZero(item.totalPendiente()), orZero(item.totalRecaudado()),
                        item.porcentajeRecaudado()));
            }
        }
        return rows;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private byte[] generateEjecucionCuotasPdf(List<EjecucionCuotasDTO> items, Long propertyId, String periodoInicio, String periodoFin) {
        StringBuilder html = new StringBuilder();
        appendHtmlHeader(html, "Reporte de Ejecución de Cuotas");

        html.append("<div class=\"header\">");
        html.append("<h1>SAED 2.0 &#8212; Reporte de Ejecuci&#243;n de Cuotas</h1>");
        html.append("<div class=\"meta\">");
        if (propertyId != null) {
            html.append("<span><strong>Propiedad ID:</strong> ").append(propertyId).append("</span> | ");
        }
        if (periodoInicio != null) {
            html.append("<span><strong>Periodo Desde:</strong> ").append(escapeXml(periodoInicio)).append("</span> | ");
        }
        if (periodoFin != null) {
            html.append("<span><strong>Periodo Hasta:</strong> ").append(escapeXml(periodoFin)).append("</span> | ");
        }
        html.append("<span><strong>Generado:</strong> ").append(LocalDateTime.now().format(DISPLAY_DATETIME_FORMAT)).append("</span>");
        html.append("</div></div>");

        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th style=\"width: 12%;\">Periodo</th>");
        html.append("<th style=\"width: 11%; text-align: right;\">Total Cuotas</th>");
        html.append("<th style=\"width: 10%; text-align: right;\">Pagadas</th>");
        html.append("<th style=\"width: 10%; text-align: right;\">Pendientes</th>");
        html.append("<th style=\"width: 16%; text-align: right;\">Total Facturado</th>");
        html.append("<th style=\"width: 16%; text-align: right;\">Total Pendiente</th>");
        html.append("<th style=\"width: 16%; text-align: right;\">Total Recaudado</th>");
        html.append("<th style=\"width: 9%; text-align: right;\">% Recaudo</th>");
        html.append("</tr></thead><tbody>");

        long sumTotalCuotas = 0;
        long sumPagadas = 0;
        long sumPendientes = 0;
        BigDecimal sumFacturado = BigDecimal.ZERO;
        BigDecimal sumPendiente = BigDecimal.ZERO;
        BigDecimal sumRecaudado = BigDecimal.ZERO;

        if (items != null && !items.isEmpty()) {
            for (EjecucionCuotasDTO item : items) {
                if (item.totalCuotas() != null) sumTotalCuotas += item.totalCuotas();
                if (item.pagadas() != null) sumPagadas += item.pagadas();
                if (item.pendientes() != null) sumPendientes += item.pendientes();
                if (item.totalFacturado() != null) sumFacturado = sumFacturado.add(item.totalFacturado());
                if (item.totalPendiente() != null) sumPendiente = sumPendiente.add(item.totalPendiente());
                if (item.totalRecaudado() != null) sumRecaudado = sumRecaudado.add(item.totalRecaudado());

                html.append("<tr>");
                html.append("<td><strong>").append(escapeXml(item.periodo())).append("</strong></td>");
                html.append("<td style=\"text-align: right;\">").append(item.totalCuotas() != null ? item.totalCuotas() : 0).append("</td>");
                html.append("<td style=\"text-align: right; color: #16A34A;\">").append(item.pagadas() != null ? item.pagadas() : 0).append("</td>");
                html.append("<td style=\"text-align: right; color: #DC2626;\">").append(item.pendientes() != null ? item.pendientes() : 0).append("</td>");
                html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(item.totalFacturado())).append("</td>");
                html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(item.totalPendiente())).append("</td>");
                html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(item.totalRecaudado())).append("</td>");
                html.append("<td style=\"text-align: right; font-weight: bold;\">").append(item.porcentajeRecaudado() != null ? item.porcentajeRecaudado() + "%" : "0.0%").append("</td>");
                html.append("</tr>");
            }

            double overallPct = 0.0;
            if (sumFacturado.compareTo(BigDecimal.ZERO) > 0) {
                overallPct = Math.round(sumRecaudado.doubleValue() / sumFacturado.doubleValue() * 1000.0) / 10.0;
            }

            html.append("<tr class=\"total-row\">");
            html.append("<td>TOTAL</td>");
            html.append("<td style=\"text-align: right;\">").append(sumTotalCuotas).append("</td>");
            html.append("<td style=\"text-align: right;\">").append(sumPagadas).append("</td>");
            html.append("<td style=\"text-align: right;\">").append(sumPendientes).append("</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(sumFacturado)).append("</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(sumPendiente)).append("</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(sumRecaudado)).append("</td>");
            html.append("<td style=\"text-align: right;\">").append(overallPct).append("%</td>");
            html.append("</tr>");
        } else {
            html.append("<tr><td colspan=\"8\" class=\"no-data\">No se registraron cuotas para los periodos consultados.</td></tr>");
        }

        html.append("</tbody></table>");
        appendHtmlFooter(html);

        return renderPdf(html.toString(), "EJECUCION_CUOTAS");
    }

    // =========================================================================
    // 3. PAGOS RECIENTES
    // =========================================================================

    @Override
    public ExportResult exportPagosRecientes(List<PagoRecienteDTO> items, ExportFormat format, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin) {
        enforceVolumeLimit(items);
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        String baseFilename = "pagos_recientes_" + timestamp;

        ExportResult result = switch (format) {
            case CSV, XLSX -> tabular(format, baseFilename, "Pagos Recientes", PAGOS_HEADERS, pagosRows(items));
            case PDF -> new ExportResult(generatePagosRecientesPdf(items, propertyId, fechaInicio, fechaFin),
                    baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            case JSON -> new ExportResult(serializeToJson(items), baseFilename + ".json", MediaType.APPLICATION_JSON);
        };

        recordAudit(format, "PAGOS_RECIENTES", propertyId);
        return result;
    }

    private static final List<String> PAGOS_HEADERS = List.of("ID Pago", "Unidad", "Monto Total", "Metodo Pago",
            "Estado", "Fecha Pago", "Referencia Comprobante");

    private static List<List<Object>> pagosRows(List<PagoRecienteDTO> items) {
        List<List<Object>> rows = new ArrayList<>();
        if (items != null) {
            for (PagoRecienteDTO item : items) {
                rows.add(Arrays.asList(item.idPago(), item.unidad(), orZero(item.montoTotal()), item.metodoPago(),
                        item.estado(), item.fechaPago(), item.referenciaComprobante()));
            }
        }
        return rows;
    }

    private byte[] generatePagosRecientesPdf(List<PagoRecienteDTO> items, Long propertyId, LocalDate fechaInicio, LocalDate fechaFin) {
        StringBuilder html = new StringBuilder();
        appendHtmlHeader(html, "Reporte de Pagos Recientes");

        html.append("<div class=\"header\">");
        html.append("<h1>SAED 2.0 &#8212; Reporte de Pagos Recientes</h1>");
        html.append("<div class=\"meta\">");
        if (propertyId != null) {
            html.append("<span><strong>Propiedad ID:</strong> ").append(propertyId).append("</span> | ");
        }
        if (fechaInicio != null) {
            html.append("<span><strong>Desde:</strong> ").append(fechaInicio.format(DISPLAY_DATE_FORMAT)).append("</span> | ");
        }
        if (fechaFin != null) {
            html.append("<span><strong>Hasta:</strong> ").append(fechaFin.format(DISPLAY_DATE_FORMAT)).append("</span> | ");
        }
        html.append("<span><strong>Generado:</strong> ").append(LocalDateTime.now().format(DISPLAY_DATETIME_FORMAT)).append("</span>");
        html.append("</div></div>");

        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th style=\"width: 10%;\">ID Pago</th>");
        html.append("<th style=\"width: 14%;\">Unidad</th>");
        html.append("<th style=\"width: 18%; text-align: right;\">Monto Total</th>");
        html.append("<th style=\"width: 16%;\">M&#233;todo Pago</th>");
        html.append("<th style=\"width: 12%; text-align: center;\">Estado</th>");
        html.append("<th style=\"width: 16%; text-align: center;\">Fecha Pago</th>");
        html.append("<th style=\"width: 14%;\">Referencia</th>");
        html.append("</tr></thead><tbody>");

        BigDecimal totalRecaudado = BigDecimal.ZERO;

        if (items != null && !items.isEmpty()) {
            for (PagoRecienteDTO item : items) {
                if (item.montoTotal() != null) totalRecaudado = totalRecaudado.add(item.montoTotal());

                String badgeClass = "badge-neutral";
                if ("APROBADO".equalsIgnoreCase(item.estado())) badgeClass = "badge-success";
                else if ("PENDIENTE".equalsIgnoreCase(item.estado())) badgeClass = "badge-warning";
                else if ("RECHAZADO".equalsIgnoreCase(item.estado())) badgeClass = "badge-danger";

                html.append("<tr>");
                html.append("<td>").append(item.idPago() != null ? item.idPago() : "-").append("</td>");
                html.append("<td><strong>").append(escapeXml(item.unidad())).append("</strong></td>");
                html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(item.montoTotal())).append("</td>");
                html.append("<td>").append(escapeXml(item.metodoPago())).append("</td>");
                html.append("<td style=\"text-align: center;\"><span class=\"").append(badgeClass).append("\">")
                    .append(escapeXml(item.estado())).append("</span></td>");
                html.append("<td style=\"text-align: center;\">").append(item.fechaPago() != null ? item.fechaPago().format(DISPLAY_DATETIME_FORMAT) : "-").append("</td>");
                html.append("<td>").append(escapeXml(item.referenciaComprobante())).append("</td>");
                html.append("</tr>");
            }
            html.append("<tr class=\"total-row\">");
            html.append("<td colspan=\"2\">TOTAL GENERAL (").append(items.size()).append(" pagos)</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(totalRecaudado)).append("</td>");
            html.append("<td colspan=\"4\"></td>");
            html.append("</tr>");
        } else {
            html.append("<tr><td colspan=\"7\" class=\"no-data\">No se encontraron pagos recientes para los criterios especificados.</td></tr>");
        }

        html.append("</tbody></table>");
        appendHtmlFooter(html);

        return renderPdf(html.toString(), "PAGOS_RECIENTES");
    }

    // =========================================================================
    // 4. EJECUCIÓN PRESUPUESTAL GLOBAL
    // =========================================================================

    @Override
    public ExportResult exportEjecucionPresupuestal(EjecucionPresupuestalGlobalDTO dto, ExportFormat format, Long propertyId, Integer vigencia) {
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        int effVigencia = vigencia != null ? vigencia : (dto != null && dto.vigenciaAnio() != null ? dto.vigenciaAnio() : LocalDate.now().getYear());
        String baseFilename = "ejecucion_presupuestal_" + effVigencia + "_" + timestamp;

        ExportResult result = switch (format) {
            case CSV, XLSX -> tabular(format, baseFilename, "Ejecucion Presupuestal", PRESUPUESTO_HEADERS,
                    presupuestoRows(dto));
            case PDF -> new ExportResult(generateEjecucionPresupuestalPdf(dto, propertyId, effVigencia),
                    baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            case JSON -> new ExportResult(serializeToJson(dto), baseFilename + ".json", MediaType.APPLICATION_JSON);
        };

        recordAudit(format, "EJECUCION_PRESUPUESTAL", propertyId);
        return result;
    }

    private static final List<String> PRESUPUESTO_HEADERS = List.of("Vigencia", "Ingresos Presupuestados",
            "Ingresos Ejecutados", "% Ejecucion Ingresos", "Egresos Presupuestados", "Egresos Ejecutados",
            "% Ejecucion Egresos", "Superavit Presupuestado", "Superavit Ejecutado", "Estado Financiero",
            "Total Pagos Aprobados", "Total Gastos Pagados");

    private static List<List<Object>> presupuestoRows(EjecucionPresupuestalGlobalDTO dto) {
        List<List<Object>> rows = new ArrayList<>();
        if (dto != null) {
            rows.add(Arrays.asList(dto.vigenciaAnio(), orZero(dto.ingresosPresupuestados()),
                    orZero(dto.ingresosEjecutados()), dto.porcentajeEjecucionIngresos(),
                    orZero(dto.egresosPresupuestados()), orZero(dto.egresosEjecutados()),
                    dto.porcentajeEjecucionEgresos(), orZero(dto.superavitPresupuestado()),
                    orZero(dto.superavitEjecutado()), dto.estadoFinanciero(), dto.totalPagosAprobados(),
                    dto.totalGastosPagados()));
        }
        return rows;
    }

    private byte[] generateEjecucionPresupuestalPdf(EjecucionPresupuestalGlobalDTO dto, Long propertyId, Integer vigencia) {
        StringBuilder html = new StringBuilder();
        appendHtmlHeader(html, "Reporte de Ejecución Presupuestal Global");

        html.append("<div class=\"header\">");
        html.append("<h1>SAED 2.0 &#8212; Ejecuci&#243;n Presupuestal Consolidada</h1>");
        html.append("<div class=\"meta\">");
        if (propertyId != null) {
            html.append("<span><strong>Propiedad ID:</strong> ").append(propertyId).append("</span> | ");
        }
        html.append("<span><strong>Vigencia Fiscal:</strong> ").append(vigencia).append("</span> | ");
        html.append("<span><strong>Generado:</strong> ").append(LocalDateTime.now().format(DISPLAY_DATETIME_FORMAT)).append("</span>");
        html.append("</div></div>");

        if (dto != null) {
            html.append("<table style=\"margin-top: 10px;\">");
            html.append("<thead><tr>");
            html.append("<th style=\"width: 30%;\">Concepto Financiero</th>");
            html.append("<th style=\"width: 25%; text-align: right;\">Presupuestado</th>");
            html.append("<th style=\"width: 25%; text-align: right;\">Ejecutado Efectivo</th>");
            html.append("<th style=\"width: 20%; text-align: right;\">% Cumplimiento</th>");
            html.append("</tr></thead><tbody>");

            html.append("<tr>");
            html.append("<td><strong>INGRESOS TOTALES</strong> (").append(dto.totalPagosAprobados() != null ? dto.totalPagosAprobados() : 0).append(" pagos aprobados)</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(dto.ingresosPresupuestados())).append("</td>");
            html.append("<td style=\"text-align: right; color: #16A34A;\" class=\"num\">").append(formatCurrency(dto.ingresosEjecutados())).append("</td>");
            html.append("<td style=\"text-align: right; font-weight: bold;\">").append(dto.porcentajeEjecucionIngresos() != null ? dto.porcentajeEjecucionIngresos() + "%" : "0.0%").append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td><strong>EGRESOS TOTALES</strong> (").append(dto.totalGastosPagados() != null ? dto.totalGastosPagados() : 0).append(" gastos pagados)</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(dto.egresosPresupuestados())).append("</td>");
            html.append("<td style=\"text-align: right; color: #DC2626;\" class=\"num\">").append(formatCurrency(dto.egresosEjecutados())).append("</td>");
            html.append("<td style=\"text-align: right; font-weight: bold;\">").append(dto.porcentajeEjecucionEgresos() != null ? dto.porcentajeEjecucionEgresos() + "%" : "0.0%").append("</td>");
            html.append("</tr>");

            String colorSuperavit = "#1E293B";
            if ("SUPERAVIT".equalsIgnoreCase(dto.estadoFinanciero())) colorSuperavit = "#16A34A";
            else if ("DEFICIT".equalsIgnoreCase(dto.estadoFinanciero())) colorSuperavit = "#DC2626";

            html.append("<tr class=\"total-row\">");
            html.append("<td>SUPER&#193;VIT / BALANCE (").append(escapeXml(dto.estadoFinanciero())).append(")</td>");
            html.append("<td style=\"text-align: right;\" class=\"num\">").append(formatCurrency(dto.superavitPresupuestado())).append("</td>");
            html.append("<td style=\"text-align: right; color: ").append(colorSuperavit).append(";\" class=\"num\">").append(formatCurrency(dto.superavitEjecutado())).append("</td>");
            html.append("<td style=\"text-align: right;\">&#8212;</td>");
            html.append("</tr>");

            html.append("</tbody></table>");
        } else {
            html.append("<table><tbody><tr><td class=\"no-data\">No hay datos de presupuesto registrados para la vigencia solicitada.</td></tr></tbody></table>");
        }

        appendHtmlFooter(html);
        return renderPdf(html.toString(), "EJECUCION_PRESUPUESTAL");
    }

    // =========================================================================
    // UTILIDADES DE VOLUMEN, AUDITORÍA, CSV, XML Y PDF
    // =========================================================================

    private void enforceVolumeLimit(List<?> items) {
        if (items != null && items.size() > MAX_EXPORT_LIMIT) {
            throw new ExportVolumeLimitExceededException(
                    "El volumen del reporte excede el límite máximo permitido de 10.000 registros para exportación."
            );
        }
    }

    private void recordAudit(ExportFormat format, String tipoReporte, Long propId) {
        if (auditService != null) {
            try {
                SaedContext ctx = SaedContextHolder.getContext();
                Long userId = ctx != null ? ctx.getUserId() : null;
                Long orgId = ctx != null ? ctx.getOrganizationId() : null;
                Long effectivePropId = propId != null ? propId : (ctx != null ? ctx.getPropertyId() : null);

                String estadoNuevo = String.format("{\"formato\":\"%s\",\"reporte\":\"%s\"}", format, tipoReporte);
                auditService.recordSuccess(
                        userId,
                        orgId,
                        effectivePropId,
                        "REPORTE_EXPORTADO",
                        "REPORTES",
                        null,
                        null,
                        null,
                        null,
                        estadoNuevo
                );
            } catch (RuntimeException e) {
                // Non-blocking audit: the export already succeeded; only the failure class is logged.
                log.warn("Fallo no bloqueante al registrar auditoría de exportación ({})", e.getClass().getSimpleName());
            }
        }
    }

    private byte[] serializeToJson(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al generar formato JSON del reporte.", e);
        }
    }

    private byte[] renderPdf(String xhtml, String reportName) {
        try {
            return pdfService.generarPdf(xhtml);
        } catch (Exception e) {
            log.error("Error al renderizar PDF para reporte {} ({})", reportName, e.getClass().getSimpleName());
            throw new IllegalStateException("Error al generar archivo PDF del reporte.", e);
        }
    }

    private String escapeXml(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\u00A0", "&#160;");
    }

    /**
     * Colombian peso amount, e.g. {@code $ 1.234.567} or {@code -$ 1.500}. Built per call with an explicit
     * pattern (DecimalFormat is not thread-safe and the JDK es-CO pattern inserts a non-breaking space).
     */
    static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "$ 0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        String digits = format.format(amount.abs());
        return (amount.signum() < 0 ? "-$ " : "$ ") + digits;
    }

    private void appendHtmlHeader(StringBuilder html, String title) {
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        html.append("<head>\n");
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        html.append("<title>").append(escapeXml(title)).append("</title>\n");
        html.append("<style type=\"text/css\">\n");
        html.append("@page { size: A4 landscape; margin: 10mm; }\n");
        html.append("body { font-family: Helvetica, Arial, sans-serif; font-size: 8pt; color: #1E293B; margin: 0; padding: 0; }\n");
        html.append(".header { border-bottom: 2px solid #2563EB; padding-bottom: 8px; margin-bottom: 12px; }\n");
        html.append("h1 { font-size: 14pt; color: #0F172A; margin: 0 0 4px 0; }\n");
        html.append(".meta { font-size: 8pt; color: #64748B; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 8px; }\n");
        html.append("th { background-color: #0F172A; color: #FFFFFF; font-weight: bold; padding: 6px 8px; border: 1px solid #334155; font-size: 7.5pt; text-align: left; }\n");
        html.append("td { padding: 5px 8px; border: 1px solid #E2E8F0; font-size: 8pt; }\n");
        html.append("tr:nth-child(even) { background-color: #F8FAFC; }\n");
        html.append("tr.total-row { background-color: #F1F5F9; font-weight: bold; border-top: 2px solid #0F172A; }\n");
        html.append(".num { font-family: monospace; }\n");
        html.append(".no-data { text-align: center; color: #64748B; padding: 20px; font-style: italic; }\n");
        html.append(".badge-success { background-color: #DCFCE7; color: #166534; padding: 2px 6px; font-size: 7pt; font-weight: bold; }\n");
        html.append(".badge-warning { background-color: #FEF3C7; color: #92400E; padding: 2px 6px; font-size: 7pt; font-weight: bold; }\n");
        html.append(".badge-danger { background-color: #FEE2E2; color: #991B1B; padding: 2px 6px; font-size: 7pt; font-weight: bold; }\n");
        html.append(".badge-neutral { background-color: #F1F5F9; color: #475569; padding: 2px 6px; font-size: 7pt; font-weight: bold; }\n");
        html.append(".footer { margin-top: 15px; font-size: 7pt; color: #94A3B8; text-align: right; border-top: 1px solid #E2E8F0; padding-top: 4px; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
    }

    private void appendHtmlFooter(StringBuilder html) {
        html.append("<div class=\"footer\">");
        html.append("SAED 2.0 &#8212; Sistema de Administraci&#243;n de Edificios &#8212; Documento confidencial generado autom&#225;ticamente.");
        html.append("</div>");
        html.append("</body>\n");
        html.append("</html>");
    }
}
