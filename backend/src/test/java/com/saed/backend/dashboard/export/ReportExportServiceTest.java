package com.saed.backend.dashboard.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportExportServiceTest {

    @Mock
    private PdfService pdfService;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private ReportExportServiceImpl exportService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        exportService = new ReportExportServiceImpl(pdfService, objectMapper);
        // Inject optional audit service
        org.springframework.test.util.ReflectionTestUtils.setField(exportService, "auditService", auditService);
    }

    // =========================================================================
    // 1. ExportFormat Enum Tests
    // =========================================================================

    @Test
    @DisplayName("ExportFormat.fromString parses formats case-insensitively and defaults to JSON")
    void testExportFormatParsing() {
        assertEquals(ExportFormat.JSON, ExportFormat.fromString("json"));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString("JSON"));
        assertEquals(ExportFormat.PDF, ExportFormat.fromString("pdf"));
        assertEquals(ExportFormat.PDF, ExportFormat.fromString("PDF"));
        assertEquals(ExportFormat.CSV, ExportFormat.fromString("csv"));
        assertEquals(ExportFormat.CSV, ExportFormat.fromString("CSV"));

        // Defaults to JSON
        assertEquals(ExportFormat.JSON, ExportFormat.fromString(null));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString(""));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString("   "));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString("unknown"));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString("xml"));
    }

    // =========================================================================
    // 2. ExportResult & ResponseEntity Tests
    // =========================================================================

    @Test
    @DisplayName("ExportResult builds RFC 6266 / RFC 5987 UTF-8 attachment response")
    void testExportResultToResponseEntity() {
        byte[] sample = "sample content".getBytes(StandardCharsets.UTF_8);
        ExportResult result = new ExportResult(sample, "reporte_año_2026.csv", MediaType.parseMediaType("text/csv;charset=UTF-8"));

        ResponseEntity<Resource> response = result.toResponseEntity();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().containsKey(HttpHeaders.CONTENT_DISPOSITION));

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"));
        assertTrue(disposition.contains("filename"));
    }

    // =========================================================================
    // 3. Volume Limit Check (>10,000 items)
    // =========================================================================

    @Test
    @DisplayName("Volume limit check throws ExportVolumeLimitExceededException when exceeding 10,000 items")
    void testVolumeLimitExceeded() {
        List<CarteraMorosaDTO> largeList = new ArrayList<>(10001);
        for (int i = 0; i < 10001; i++) {
            largeList.add(new CarteraMorosaDTO("U-" + i, "Torre 1", 1L, BigDecimal.TEN, LocalDate.now(), LocalDate.now(), 5L));
        }

        assertThrows(ExportVolumeLimitExceededException.class, () ->
                exportService.exportCarteraMorosa(largeList, ExportFormat.CSV, 1L, null, null));
    }

    // =========================================================================
    // 4. RFC 4180 CSV Generation (BOM, Escaping, CRLF)
    // =========================================================================

    @Test
    @DisplayName("CSV export adheres to RFC 4180 with UTF-8 BOM and correct escaping")
    void testCsvExportRfc4180() {
        CarteraMorosaDTO itemWithSpecialChars = new CarteraMorosaDTO(
                "Apt \"101\", Edificio Central",
                "Torre A, Etapa 1\nSector Norte",
                2L,
                new BigDecimal("350000.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                30L
        );

        ExportResult result = exportService.exportCarteraMorosa(List.of(itemWithSpecialChars), ExportFormat.CSV, 1L, null, null);
        assertNotNull(result);
        assertEquals("text/csv;charset=UTF-8", result.mediaType().toString());

        String csvText = new String(result.content(), StandardCharsets.UTF_8);

        // UTF-8 BOM check
        assertTrue(csvText.startsWith("\uFEFF"), "CSV must start with UTF-8 BOM");

        // Header check
        assertTrue(csvText.contains("Unidad,Propiedad,Cuotas Pendientes,Deuda Total,Primer Vencimiento,Ultimo Vencimiento,Dias Mora"));

        // Escaping check: double quotes inside field doubled (""101"") and enclosed in quotes
        assertTrue(csvText.contains("\"Apt \"\"101\"\", Edificio Central\""));
        // Newline enclosed in quotes
        assertTrue(csvText.contains("\"Torre A, Etapa 1\nSector Norte\""));
        // CRLF check
        assertTrue(csvText.contains("\r\n"));

        // Audit check
        verify(auditService, times(1)).recordSuccess(
                any(), any(), eq(1L), eq("REPORTE_EXPORTADO"), eq("REPORTES"),
                isNull(), isNull(), isNull(), isNull(), contains("CARTERA_MOROSA")
        );
    }

    // =========================================================================
    // 5. OpenHTMLtoPDF XHTML PDF Generation
    // =========================================================================

    @Test
    @DisplayName("PDF export invokes PdfService with valid XHTML content")
    void testPdfExport() throws Exception {
        when(pdfService.generarPdf(anyString())).thenReturn(new byte[]{1, 2, 3});

        PagoRecienteDTO pago = new PagoRecienteDTO(
                101L,
                "Apt & Casa 101",
                new BigDecimal("200000"),
                "TRANSFERENCIA",
                "APROBADO",
                LocalDateTime.of(2026, 3, 1, 10, 30),
                "REF-001 <BANCO>"
        );

        ExportResult result = exportService.exportPagosRecientes(List.of(pago), ExportFormat.PDF, 2L, null, null);
        assertNotNull(result);
        assertEquals(MediaType.APPLICATION_PDF, result.mediaType());
        assertArrayEquals(new byte[]{1, 2, 3}, result.content());

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(pdfService).generarPdf(htmlCaptor.capture());

        String generatedXhtml = htmlCaptor.getValue();
        // Strict XHTML check: starts with XML declaration and DOCTYPE
        assertTrue(generatedXhtml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(generatedXhtml.contains("<!DOCTYPE html"));
        // XML escaping check: & should be escaped to &amp;, < should be &lt;
        assertTrue(generatedXhtml.contains("Apt &amp; Casa 101"));
        assertTrue(generatedXhtml.contains("REF-001 &lt;BANCO&gt;"));
        // Ensure no unescaped &nbsp;
        assertFalse(generatedXhtml.contains("&nbsp;"));
    }

    // =========================================================================
    // 6. JSON Export
    // =========================================================================

    @Test
    @DisplayName("JSON export generates formatted JSON bytes")
    void testJsonExport() {
        EjecucionCuotasDTO cuota = new EjecucionCuotasDTO(
                "2026-03", 50L, 45L, 5L,
                new BigDecimal("10000000"), new BigDecimal("1000000"), new BigDecimal("9000000"), 90.0
        );

        ExportResult result = exportService.exportEjecucionCuotas(List.of(cuota), ExportFormat.JSON, 1L, "2026-01", "2026-03");
        assertNotNull(result);
        assertEquals(MediaType.APPLICATION_JSON, result.mediaType());
        assertTrue(result.filename().endsWith(".json"));

        String jsonText = new String(result.content(), StandardCharsets.UTF_8);
        assertTrue(jsonText.contains("\"periodo\" : \"2026-03\""));
        assertTrue(jsonText.contains("\"totalCuotas\" : 50"));
    }

    // =========================================================================
    // 7. Ejecución Presupuestal Global Export
    // =========================================================================

    @Test
    @DisplayName("Ejecución presupuestal global export produces valid CSV and PDF")
    void testEjecucionPresupuestalGlobalExport() throws Exception {
        when(pdfService.generarPdf(anyString())).thenReturn(new byte[]{4, 5, 6});

        EjecucionPresupuestalGlobalDTO dto = new EjecucionPresupuestalGlobalDTO(
                2026,
                new BigDecimal("120000000"),
                new BigDecimal("115000000"),
                95.8,
                new BigDecimal("110000000"),
                new BigDecimal("105000000"),
                95.5,
                new BigDecimal("10000000"),
                new BigDecimal("10000000"),
                "SUPERAVIT",
                120L,
                45L
        );

        // CSV
        ExportResult csvResult = exportService.exportEjecucionPresupuestal(dto, ExportFormat.CSV, 1L, 2026);
        String csv = new String(csvResult.content(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Vigencia,Ingresos Presupuestados,Ingresos Ejecutados"));
        assertTrue(csv.contains("2026,120000000,115000000,95.8"));

        // PDF
        ExportResult pdfResult = exportService.exportEjecucionPresupuestal(dto, ExportFormat.PDF, 1L, 2026);
        assertNotNull(pdfResult);
        verify(pdfService).generarPdf(contains("SAED 2.0"));
    }
}
