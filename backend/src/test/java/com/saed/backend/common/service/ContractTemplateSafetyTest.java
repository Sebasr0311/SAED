package com.saed.backend.common.service;

import com.saed.backend.common.service.impl.PdfServiceImpl;
import com.saed.backend.finanzas.dto.CoarrendatarioDTO;
import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-03 / F5-H06: contract template variables are HTML-escaped (only server-built HTML fragments are inserted
 * verbatim), dates and amounts round-trip in the documented format, and the PDF renderer loads no external or
 * local-file resources.
 */
class ContractTemplateSafetyTest {

    private final VariableResolverService resolver = new VariableResolverService();

    @Test
    void variableValuesAreHtmlEscaped() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("residente.nombreCompleto", "<script>alert(1)</script><img src=x onerror=y>");
        String out = resolver.resolverVariables("<p>${residente.nombreCompleto}</p>", vars);
        assertFalse(out.contains("<script>"), out);
        assertFalse(out.contains("<img"), out);
        assertTrue(out.contains("&lt;script&gt;"), out);
    }

    @Test
    void clientSuppliedHtmlKeyIsNotTrusted() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("coarrendatarios.firmas_html", "<iframe src=\"file:///etc/passwd\"></iframe>");
        String out = resolver.resolverVariables("${coarrendatarios.firmas_html}", vars);
        assertFalse(out.contains("<iframe"), out);
    }

    @Test
    void serverBuiltSignatureBlockKeepsStructureButEscapesNames() {
        ContratoDetalleDTO dto = new ContratoDetalleDTO();
        dto.setCoarrendatarios(List.of(new CoarrendatarioDTO(1L, 2L, 3L, "COARRENDATARIO", "N", null, "ACTIVO",
                "<b>Eva</b>", "123", "300", null)));
        Map<String, Object> vars = resolver.construirMapaVariables(dto);
        String out = resolver.resolverVariables("${coarrendatarios.firmas_html}|${coarrendatarios.nombres}", vars);
        assertTrue(out.contains("<table"), out);
        assertFalse(out.contains("<b>Eva</b>"), out);
        assertTrue(out.contains("&lt;b&gt;Eva&lt;/b&gt;"), out);
    }

    @Test
    void datesAndAmountsRoundTripInTheContractFormat() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fechaInicio", LocalDate.of(2026, 10, 15));
        vars.put("valorCanon", new BigDecimal("1850000"));
        String out = resolver.resolverVariables("{{fechaInicio}} ${valorCanon}", vars);
        assertTrue(out.startsWith("15 de octubre de 2026"), out);
        assertTrue(out.contains("1.850.000"), out);
    }

    @Test
    void floorVariableComesFromTheUnit() {
        ContratoDetalleDTO dto = new ContratoDetalleDTO();
        dto.setPiso(12);
        assertEquals(12, resolver.construirMapaVariables(dto).get("piso"));
    }

    @Test
    void pdfRendererLoadsNoExternalOrLocalResources() {
        assertNull(PdfServiceImpl.resolveResource(null, "file:///etc/passwd"));
        assertNull(PdfServiceImpl.resolveResource("file:///", "../secret.css"));
        assertNull(PdfServiceImpl.resolveResource(null, "http://169.254.169.254/latest/meta-data"));
        assertNull(PdfServiceImpl.resolveResource(null, "https://example.com/logo.png"));
        assertEquals("data:image/png;base64,AAAA", PdfServiceImpl.resolveResource(null, "data:image/png;base64,AAAA"));
    }
}
