package com.saed.backend.dashboard.export;

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
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final String CSV_CRLF = "\r\n";
    private static final String UTF8_BOM = "\uFEFF";
    private static final MediaType MEDIA_TYPE_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

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

        ExportResult result;
        switch (format) {
            case CSV -> {
                byte[] csvBytes = generateCarteraMorosaCsv(items);
                result = new ExportResult(csvBytes, baseFilename + ".csv", MEDIA_TYPE_CSV);
            }
            case PDF -> {
                byte[] pdfBytes = generateCarteraMorosaPdf(items, propertyId, fechaInicio, fechaFin);
                result = new ExportResult(pdfBytes, baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            }
            default -> {
                byte[] jsonBytes = serializeToJson(items);
                result = new ExportResult(jsonBytes, baseFilename + ".json", MediaType.APPLICATION_JSON);
            }
        }

        recordAudit(format, "CARTERA_MOROSA", propertyId);
        return result;
    }

    private byte[] generateCarteraMorosaCsv(List<CarteraMorosaDTO> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        sb.append("Unidad,Propiedad,Cuotas Pendientes,Deuda Total,Primer Vencimiento,Ultimo Vencimiento,Dias Mora").append(CSV_CRLF);

        if (items != null) {
            for (CarteraMorosaDTO item : items) {
                sb.append(escapeCsv(item.unidad())).append(",")
                  .append(escapeCsv(item.propiedad())).append(",")
                  .append(escapeCsv(item.cuotasPendientes())).append(",")
                  .append(escapeCsv(item.deudaTotal() != null ? item.deudaTotal().toPlainString() : "0")).append(",")
                  .append(escapeCsv(item.primerVencimiento() != null ? item.primerVencimiento().toString() : "")).append(",")
                  .append(escapeCsv(item.ultimoVencimiento() != null ? item.ultimoVencimiento().toString() : "")).append(",")
                  .append(escapeCsv(item.diasMora())).append(CSV_CRLF);
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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

        ExportResult result;
        switch (format) {
            case CSV -> {
                byte[] csvBytes = generateEjecucionCuotasCsv(items);
                result = new ExportResult(csvBytes, baseFilename + ".csv", MEDIA_TYPE_CSV);
            }
            case PDF -> {
                byte[] pdfBytes = generateEjecucionCuotasPdf(items, propertyId, periodoInicio, periodoFin);
                result = new ExportResult(pdfBytes, baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            }
            default -> {
                byte[] jsonBytes = serializeToJson(items);
                result = new ExportResult(jsonBytes, baseFilename + ".json", MediaType.APPLICATION_JSON);
            }
        }

        recordAudit(format, "EJECUCION_CUOTAS", propertyId);
        return result;
    }

    private byte[] generateEjecucionCuotasCsv(List<EjecucionCuotasDTO> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        sb.append("Periodo,Total Cuotas,Pagadas,Pendientes,Total Facturado,Total Pendiente,Total Recaudado,Porcentaje Recaudado (%)").append(CSV_CRLF);

        if (items != null) {
            for (EjecucionCuotasDTO item : items) {
                sb.append(escapeCsv(item.periodo())).append(",")
                  .append(escapeCsv(item.totalCuotas())).append(",")
                  .append(escapeCsv(item.pagadas())).append(",")
                  .append(escapeCsv(item.pendientes())).append(",")
                  .append(escapeCsv(item.totalFacturado() != null ? item.totalFacturado().toPlainString() : "0")).append(",")
                  .append(escapeCsv(item.totalPendiente() != null ? item.totalPendiente().toPlainString() : "0")).append(",")
                  .append(escapeCsv(item.totalRecaudado() != null ? item.totalRecaudado().toPlainString() : "0")).append(",")
                  .append(escapeCsv(item.porcentajeRecaudado())).append(CSV_CRLF);
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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

        ExportResult result;
        switch (format) {
            case CSV -> {
                byte[] csvBytes = generatePagosRecientesCsv(items);
                result = new ExportResult(csvBytes, baseFilename + ".csv", MEDIA_TYPE_CSV);
            }
            case PDF -> {
                byte[] pdfBytes = generatePagosRecientesPdf(items, propertyId, fechaInicio, fechaFin);
                result = new ExportResult(pdfBytes, baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            }
            default -> {
                byte[] jsonBytes = serializeToJson(items);
                result = new ExportResult(jsonBytes, baseFilename + ".json", MediaType.APPLICATION_JSON);
            }
        }

        recordAudit(format, "PAGOS_RECIENTES", propertyId);
        return result;
    }

    private byte[] generatePagosRecientesCsv(List<PagoRecienteDTO> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        sb.append("ID Pago,Unidad,Monto Total,Metodo Pago,Estado,Fecha Pago,Referencia Comprobante").append(CSV_CRLF);

        if (items != null) {
            for (PagoRecienteDTO item : items) {
                sb.append(escapeCsv(item.idPago())).append(",")
                  .append(escapeCsv(item.unidad())).append(",")
                  .append(escapeCsv(item.montoTotal() != null ? item.montoTotal().toPlainString() : "0")).append(",")
                  .append(escapeCsv(item.metodoPago())).append(",")
                  .append(escapeCsv(item.estado())).append(",")
                  .append(escapeCsv(item.fechaPago() != null ? item.fechaPago().format(DISPLAY_DATETIME_FORMAT) : "")).append(",")
                  .append(escapeCsv(item.referenciaComprobante())).append(CSV_CRLF);
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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

        ExportResult result;
        switch (format) {
            case CSV -> {
                byte[] csvBytes = generateEjecucionPresupuestalCsv(dto);
                result = new ExportResult(csvBytes, baseFilename + ".csv", MEDIA_TYPE_CSV);
            }
            case PDF -> {
                byte[] pdfBytes = generateEjecucionPresupuestalPdf(dto, propertyId, effVigencia);
                result = new ExportResult(pdfBytes, baseFilename + ".pdf", MediaType.APPLICATION_PDF);
            }
            default -> {
                byte[] jsonBytes = serializeToJson(dto);
                result = new ExportResult(jsonBytes, baseFilename + ".json", MediaType.APPLICATION_JSON);
            }
        }

        recordAudit(format, "EJECUCION_PRESUPUESTAL", propertyId);
        return result;
    }

    private byte[] generateEjecucionPresupuestalCsv(EjecucionPresupuestalGlobalDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        sb.append("Vigencia,Ingresos Presupuestados,Ingresos Ejecutados,% Ejecucion Ingresos,Egresos Presupuestados,Egresos Ejecutados,% Ejecucion Egresos,Superavit Presupuestado,Superavit Ejecutado,Estado Financiero,Total Pagos Aprobados,Total Gastos Pagados").append(CSV_CRLF);

        if (dto != null) {
            sb.append(escapeCsv(dto.vigenciaAnio())).append(",")
              .append(escapeCsv(dto.ingresosPresupuestados() != null ? dto.ingresosPresupuestados().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.ingresosEjecutados() != null ? dto.ingresosEjecutados().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.porcentajeEjecucionIngresos())).append(",")
              .append(escapeCsv(dto.egresosPresupuestados() != null ? dto.egresosPresupuestados().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.egresosEjecutados() != null ? dto.egresosEjecutados().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.porcentajeEjecucionEgresos())).append(",")
              .append(escapeCsv(dto.superavitPresupuestado() != null ? dto.superavitPresupuestado().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.superavitEjecutado() != null ? dto.superavitEjecutado().toPlainString() : "0")).append(",")
              .append(escapeCsv(dto.estadoFinanciero())).append(",")
              .append(escapeCsv(dto.totalPagosAprobados())).append(",")
              .append(escapeCsv(dto.totalGastosPagados())).append(CSV_CRLF);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
            } catch (Exception e) {
                log.warn("Fallo no bloqueante al registrar auditoría de exportación: {}", e.getMessage());
            }
        }
    }

    private byte[] serializeToJson(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(object);
        } catch (Exception e) {
            log.error("Error al serializar reporte a JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar formato JSON del reporte: " + e.getMessage(), e);
        }
    }

    private byte[] renderPdf(String xhtml, String reportName) {
        try {
            return pdfService.generarPdf(xhtml);
        } catch (Exception e) {
            log.error("Error al renderizar PDF para reporte {}: {}", reportName, e.getMessage(), e);
            throw new RuntimeException("Error al generar archivo PDF del reporte: " + e.getMessage(), e);
        }
    }

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String str = String.valueOf(val);
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
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

    private static final DecimalFormat CURRENCY_FORMAT = (DecimalFormat) NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
    static {
        DecimalFormatSymbols symbols = CURRENCY_FORMAT.getDecimalFormatSymbols();
        symbols.setCurrencySymbol("$ ");
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        CURRENCY_FORMAT.setDecimalFormatSymbols(symbols);
        CURRENCY_FORMAT.setMaximumFractionDigits(0);
        CURRENCY_FORMAT.setMinimumFractionDigits(0);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "$ 0";
        return CURRENCY_FORMAT.format(amount);
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
