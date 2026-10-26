package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.dashboard.controller.ReportesConfiguradosController;
import com.saed.backend.dashboard.dto.GenerarReporteResponseDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F11 (review triage #2): generation without an explicit format uses the configuration's default format. */
class ConfiguredReportDefaultFormatTest {

    private ReportesConfiguradosService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportesConfiguradosService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReportesConfiguradosController(service, new ObjectMapper())).build();
    }

    private ReporteConfiguradoDTO config(String defaultFormat) {
        ReporteConfiguradoDTO dto = new ReporteConfiguradoDTO();
        dto.setFormatoSalidaDefecto(defaultFormat);
        return dto;
    }

    private ExportResult csv() {
        return new ExportResult("a,b\n".getBytes(StandardCharsets.UTF_8), "r.csv", MediaType.parseMediaType("text/csv"));
    }

    @Test
    void withoutFormatTheConfiguredDefaultDecidesAndAFileIsReturned() throws Exception {
        when(service.obtenerPorId(7L)).thenReturn(config("CSV"));
        when(service.generarReporte(eq(7L), eq("CSV"), isNull())).thenReturn(csv());

        mvc.perform(post("/api/v1/reportes/configurados/7/generar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("r.csv")))
                .andExpect(content().string("a,b\n"));
        verify(service, never()).generarReporteMetadata(any(), any(), any());
    }

    @Test
    void metadataFlagStillReturnsJsonMetadata() throws Exception {
        when(service.generarReporteMetadata(eq(7L), isNull(), isNull())).thenReturn(mock(GenerarReporteResponseDTO.class));

        mvc.perform(post("/api/v1/reportes/configurados/7/generar").param("metadata", "true"))
                .andExpect(status().isOk());
        verify(service, never()).generarReporte(any(), any(), any());
    }

    @Test
    void explicitJsonFormatStillReturnsMetadata() throws Exception {
        when(service.generarReporteMetadata(eq(7L), eq("JSON"), isNull())).thenReturn(mock(GenerarReporteResponseDTO.class));

        mvc.perform(post("/api/v1/reportes/configurados/7/generar").param("formato", "JSON"))
                .andExpect(status().isOk());
        verify(service, never()).generarReporte(any(), any(), any());
    }

    @Test
    void aDefaultOfJsonReturnsMetadata() throws Exception {
        when(service.obtenerPorId(7L)).thenReturn(config("JSON"));
        when(service.generarReporteMetadata(eq(7L), eq("JSON"), isNull())).thenReturn(mock(GenerarReporteResponseDTO.class));

        mvc.perform(post("/api/v1/reportes/configurados/7/generar")).andExpect(status().isOk());
        verify(service, never()).generarReporte(any(), any(), any());
    }

    @Test
    void anExplicitFormatOverridesTheDefaultWithoutLookingItUp() throws Exception {
        when(service.generarReporte(eq(7L), eq("pdf"), isNull())).thenReturn(csv());

        mvc.perform(post("/api/v1/reportes/configurados/7/generar").param("format", "pdf")).andExpect(status().isOk());
        verify(service, never()).obtenerPorId(any());
    }
}
