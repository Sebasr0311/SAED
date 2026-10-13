package com.saed.backend.dashboard.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.saed.backend.common.service.impl.PdfServiceImpl;
import com.saed.backend.dashboard.controller.ReportesController;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;
import com.saed.backend.dashboard.service.ReportesService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-04: CSV/XLSX/PDF exports open correctly, keep Unicode/dates/totals and neutralize spreadsheet formulas. */
class ReportExportFormatsTest {

    private static final String INJECTION = "=HYPERLINK(\"http://evil.example\",\"x\")";

    private ReportExportServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        service = new ReportExportServiceImpl(new PdfServiceImpl(), mapper);
    }

    private static List<PagoRecienteDTO> hostilePayments() {
        return List.of(
            new PagoRecienteDTO(1L, "+57 Peñalosa", new BigDecimal("200000.50"), "@SUM(A1)", "-2+3",
                LocalDateTime.of(2026, 3, 1, 10, 30), INJECTION),
            new PagoRecienteDTO(2L, "Torre Ñandú 東京", new BigDecimal("-1500"), "\tcmd", "\rAPROBADO",
                LocalDateTime.of(2026, 3, 2, 8, 5), "REF-002"));
    }

    // ---- CSV ---------------------------------------------------------------------------------

    @Test
    void csvNeutralizesEveryFormulaTriggerInTextCellsButKeepsNumbersNumeric() {
        String csv = new String(service.exportPagosRecientes(hostilePayments(), ExportFormat.CSV, 1L, null, null)
            .content(), StandardCharsets.UTF_8);
        String[] lines = csv.substring(1).split("\r\n");
        assertEquals("1,'+57 Peñalosa,200000.50,'@SUM(A1),'-2+3,2026-03-01 10:30:00,"
            + "\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"x\"\")\"", lines[1]);
        assertTrue(lines[2].startsWith("2,Torre Ñandú 東京,-1500,'\tcmd,\"'\rAPROBADO\""), lines[2]);
        for (String cell : new String[] {",=", ",+", ",@", ",\"="}) {
            assertFalse(csv.contains(cell), "unneutralized formula trigger " + cell);
        }
    }

    // ---- XLSX --------------------------------------------------------------------------------

    @Test
    void xlsxIsAWellFormedWorkbookWithTypedCellsAndNoFormulas() throws Exception {
        ExportResult result = service.exportPagosRecientes(hostilePayments(), ExportFormat.XLSX, 1L, null, null);
        assertTrue(result.filename().endsWith(".xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result.mediaType().toString());

        Map<String, byte[]> zip = unzip(result.content());
        for (String part : List.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml")) {
            assertNotNull(zip.get(part), "missing part " + part);
            parse(zip.get(part));
        }
        Document sheet = parse(zip.get("xl/worksheets/sheet1.xml"));
        assertEquals(0, sheet.getElementsByTagName("f").getLength(), "no formula cells");
        NodeList rows = sheet.getElementsByTagName("row");
        assertEquals(3, rows.getLength(), "header + 2 data rows");

        List<String> texts = new ArrayList<>();
        NodeList ts = sheet.getElementsByTagName("t");
        for (int i = 0; i < ts.getLength(); i++) {
            texts.add(ts.item(i).getTextContent());
        }
        assertTrue(texts.contains("Torre Ñandú 東京"), texts.toString());
        assertTrue(texts.contains("2026-03-01 10:30:00"), "dates are ISO text");
        assertTrue(texts.stream().noneMatch(t -> t.startsWith("=") || t.startsWith("+") || t.startsWith("@")),
            "text cells starting with a formula trigger are neutralized: " + texts);

        Element amount = cell(sheet, "C2");
        assertFalse(amount.hasAttribute("t"), "amounts are numeric cells");
        assertEquals("200000.50", amount.getElementsByTagName("v").item(0).getTextContent());
        assertEquals("inlineStr", cell(sheet, "G2").getAttribute("t"));
    }

    @Test
    void xlsxStripsXmlIllegalControlCharacters() throws Exception {
        CarteraMorosaDTO row = new CarteraMorosaDTO("A\u0001B", "Torre\u0000 1", 2L, new BigDecimal("10"),
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), 30L);
        ExportResult result = service.exportCarteraMorosa(List.of(row), ExportFormat.XLSX, 1L, null, null);
        Document sheet = parse(unzip(result.content()).get("xl/worksheets/sheet1.xml"));
        assertEquals("AB", cell(sheet, "A2").getTextContent());
        assertEquals("2026-01-15", cell(sheet, "E2").getTextContent());
    }

    @Test
    void xlsxRespectsTheVolumeLimit() {
        List<CarteraMorosaDTO> rows = new ArrayList<>();
        for (int i = 0; i < 10001; i++) {
            rows.add(new CarteraMorosaDTO("U" + i, "T", 1L, BigDecimal.ONE, null, null, 0L));
        }
        assertThrows(ExportVolumeLimitExceededException.class,
            () -> service.exportCarteraMorosa(rows, ExportFormat.XLSX, 1L, null, null));
    }

    // ---- PDF ---------------------------------------------------------------------------------

    @Test
    void pdfOpensAndCarriesUnicodeDatesAndTotalsOfTheExportedRows() throws Exception {
        List<CarteraMorosaDTO> rows = List.of(
            new CarteraMorosaDTO("101", "Peñalosa & Hijos <S.A.>", 2L, new BigDecimal("350000"),
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), 30L),
            new CarteraMorosaDTO("102", "Edificio Ñandú", 1L, new BigDecimal("150000"),
                LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 15), 10L));
        ExportResult result = service.exportCarteraMorosa(rows, ExportFormat.PDF, 10L,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        try (PDDocument doc = PDDocument.load(result.content())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Peñalosa & Hijos <S.A.>"), text);
            assertTrue(text.contains("Edificio Ñandú"), text);
            assertTrue(text.contains("2026-01-01") && text.contains("2026-03-31"), "filter range printed");
            assertTrue(text.contains("TOTAL GENERAL (2 registros)"), text);
            assertTrue(text.contains("$ 500.000"), "total equals the sum of exported rows: " + text);
        }
    }

    // ---- scope -------------------------------------------------------------------------------

    @Test
    void exportUsesTheServerResolvedScopeAndRejectsUnknownFormatsBeforeQuerying() {
        ReportesService reportes = mock(ReportesService.class);
        ReportExportService exporter = mock(ReportExportService.class);
        ReportesController controller = new ReportesController(reportes, exporter);
        List<CarteraMorosaDTO> items = List.of();
        when(reportes.getCarteraMorosa(isNull(), any(), any(), anyInt(), anyInt())).thenReturn(items);
        when(reportes.resolveScopePropertyId(null)).thenReturn(10L);
        when(exporter.exportCarteraMorosa(any(), any(), any(), any(), any())).thenReturn(
            new ExportResult(new byte[0], "x.csv", org.springframework.http.MediaType.TEXT_PLAIN));

        controller.carteraMorosa(null, null, null, 0, 50, "csv", null);
        verify(exporter).exportCarteraMorosa(eq(items), eq(ExportFormat.CSV), eq(10L), isNull(), isNull());

        assertThrows(IllegalArgumentException.class,
            () -> controller.pagosRecientes(null, null, null, 0, 50, "xml", null));
        verify(reportes, never()).getPagosRecientes(any(), any(), any(), anyInt(), anyInt());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zin.transferTo(out);
                parts.put(entry.getName(), out.toByteArray());
            }
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static Element cell(Document sheet, String ref) {
        NodeList cells = sheet.getElementsByTagName("c");
        for (int i = 0; i < cells.getLength(); i++) {
            Element c = (Element) cells.item(i);
            if (ref.equals(c.getAttribute("r"))) {
                return c;
            }
        }
        throw new AssertionError("cell " + ref + " not found");
    }
}
