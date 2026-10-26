package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.HistorialReporteDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoUpdateRequest;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.registry.ReportDefinition;
import com.saed.backend.dashboard.registry.ReportDefinitionKey;
import com.saed.backend.dashboard.registry.ReportDefinitionRegistry;
import com.saed.backend.dashboard.registry.ReportExecutionParams;
import com.saed.backend.dashboard.registry.ReportExecutionResult;
import com.saed.backend.dashboard.repository.HistorialReportesRepository;
import com.saed.backend.dashboard.repository.ReporteConfiguradoRepository;
import com.saed.backend.dashboard.service.ReportesService;
import com.saed.backend.dashboard.service.impl.ReportesConfiguradosServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-01: configured reports are scoped by server context; malformed input is rejected with 400. */
class ConfiguredReportScopeTest {

    private static final long ORG = 1L;
    private static final long OTHER_ORG = 2L;
    private static final long PROP = 10L;
    private static final long FOREIGN_PROP = 99L;
    private static final long CONFIG_ID = 500L;

    private ReporteConfiguradoRepository repo;
    private HistorialReportesRepository historial;
    private ReportDefinition definition;
    private ReportesConfiguradosServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(ReporteConfiguradoRepository.class);
        historial = mock(HistorialReportesRepository.class);
        definition = mock(ReportDefinition.class);
        when(definition.getKey()).thenReturn(ReportDefinitionKey.CARTERA_MOROSA);
        ReportDefinitionRegistry registry = new ReportDefinitionRegistry(List.of(definition));
        service = new ReportesConfiguradosServiceImpl(repo, historial, registry,
            mock(ReportesService.class), mock(ReportExportService.class), new ObjectMapper());
        when(repo.propertyBelongsToOrganization(PROP, ORG)).thenReturn(true);
        when(repo.propertyBelongsToOrganization(FOREIGN_PROP, ORG)).thenReturn(false);
        when(definition.execute(any(), any(), any(), any())).thenReturn(new ReportExecutionResult(0,
            new ExportResult(new byte[] {1}, "r.pdf", MediaType.APPLICATION_PDF), List.of()));
        when(historial.save(any())).thenAnswer(inv -> {
            HistorialReporteDTO h = inv.getArgument(0);
            h.setIdHistorialReporte(77L);
            return h;
        });
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void ctx(String role, Long org, Long prop, Long user) {
        SaedContextHolder.setContext(SaedContext.builder().userId(user).organizationId(org).propertyId(prop)
            .roleCode(role).roleScope(role).build());
    }

    private static ReporteConfiguradoDTO config(Long org, Long prop) {
        ReporteConfiguradoDTO dto = new ReporteConfiguradoDTO();
        dto.setIdReporteConfig(CONFIG_ID);
        dto.setNombre("Cartera");
        dto.setConsultaOrigenClave("CARTERA_MOROSA");
        dto.setFormatoSalidaDefecto("PDF");
        dto.setEstado("ACTIVO");
        dto.setIdOrganizacion(org);
        dto.setIdPropiedad(prop);
        dto.setEsPlantillaSistema(org == null);
        return dto;
    }

    private static ReporteConfiguradoCreateRequest createRequest() {
        ReporteConfiguradoCreateRequest r = new ReporteConfiguradoCreateRequest();
        r.setNombre("Mi cartera");
        r.setConsultaOrigenClave("CARTERA_MOROSA");
        r.setFormatoSalidaDefecto("CSV");
        return r;
    }

    // ---- DEC-02 and absent context -------------------------------------------------------

    @Test
    void superadminCannotReadConfiguredReports() {
        ctx("SUPERADMIN", 1L, 1L, 1L);
        assertThrows(AccessDeniedException.class, () -> service.listarConfiguraciones(null));
        verify(repo, never()).findAllVisible(any(), any());
    }

    @Test
    void propertyAdminWithoutPropertyContextIsDeniedInsteadOfSeeingTheOrganization() {
        ctx("ADMIN_PROPIEDAD", ORG, null, 5L);
        assertThrows(AccessDeniedException.class, () -> service.listarConfiguraciones(null));
        verify(repo, never()).findAllVisible(any(), any());
    }

    @Test
    void organizationAdminWithoutOrganizationContextIsDenied() {
        ctx("ADMIN_ORGANIZACION", null, null, 5L);
        assertThrows(AccessDeniedException.class, () -> service.listarConfiguraciones(null));
        verify(repo, never()).findAllVisible(any(), any());
    }

    @Test
    void missingUserContextIsDeniedInsteadOfFallingBackToUserOne() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, null);
        assertThrows(AccessDeniedException.class, () -> service.crearConfiguracion(createRequest()));
        verify(repo, never()).create(any(), any(), any(), any());
    }

    @Test
    void organizationAdminCannotListAForeignProperty() {
        ctx("ADMIN_ORGANIZACION", ORG, null, 5L);
        assertThrows(NoSuchElementException.class, () -> service.listarConfiguraciones(FOREIGN_PROP));
        verify(repo, never()).findAllVisible(any(), any());
    }

    @Test
    void propertyAdminListingIsBoundToTheContextProperty() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        service.listarConfiguraciones(FOREIGN_PROP);
        verify(repo).findAllVisible(ORG, PROP);
    }

    @Test
    void organizationAdminCannotCreateForAForeignProperty() {
        ctx("ADMIN_ORGANIZACION", ORG, null, 5L);
        ReporteConfiguradoCreateRequest r = createRequest();
        r.setIdPropiedad(FOREIGN_PROP);
        assertThrows(NoSuchElementException.class, () -> service.crearConfiguracion(r));
        verify(repo, never()).create(any(), any(), any(), any());
    }

    @Test
    void foreignConfigurationIsIndistinguishableFromMissing() {
        ctx("ADMIN_ORGANIZACION", ORG, null, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(OTHER_ORG, null)));
        assertThrows(NoSuchElementException.class, () -> service.obtenerPorId(CONFIG_ID));
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.obtenerPorId(CONFIG_ID));
    }

    @Test
    void foreignHistoryEntryIsIndistinguishableFromMissing() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        HistorialReporteDTO h = new HistorialReporteDTO();
        h.setIdHistorialReporte(3L);
        h.setIdOrganizacion(ORG);
        h.setIdPropiedad(FOREIGN_PROP);
        when(historial.findById(3L)).thenReturn(Optional.of(h));
        assertThrows(NoSuchElementException.class, () -> service.obtenerHistorialPorId(3L));
    }

    @Test
    void propertyAdminHistoryIsBoundToTheContextProperty() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        service.listarHistorial(FOREIGN_PROP, 0, 50);
        verify(historial).findHistorial(ORG, PROP, 0, 50);
    }

    // ---- malformed input -------------------------------------------------------------------

    @Test
    void createWithUnknownFormatIsRejected() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        ReporteConfiguradoCreateRequest r = createRequest();
        r.setFormatoSalidaDefecto("XML");
        assertThrows(IllegalArgumentException.class, () -> service.crearConfiguracion(r));
        verify(repo, never()).create(any(), any(), any(), any());
    }

    @Test
    void createWithMalformedFilterJsonIsRejected() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        ReporteConfiguradoCreateRequest r = createRequest();
        r.setParametrosFiltroJson("{fechaInicio: ");
        assertThrows(IllegalArgumentException.class, () -> service.crearConfiguracion(r));
        verify(repo, never()).create(any(), any(), any(), any());
    }

    @Test
    void updateWithUnknownStateIsRejected() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, PROP)));
        ReporteConfiguradoUpdateRequest r = new ReporteConfiguradoUpdateRequest();
        r.setEstado("BORRADO");
        assertThrows(IllegalArgumentException.class, () -> service.actualizarConfiguracion(CONFIG_ID, r));
        verify(repo, never()).update(anyLong(), any(), any());
    }

    @Test
    void generationRejectsMalformedDate() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, PROP)));
        assertThrows(IllegalArgumentException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"fechaInicio\":\"2026-13-45\"}"));
        verify(definition, never()).execute(any(), any(), any(), any());
    }

    @Test
    void generationRejectsMalformedJsonAndUnknownFilters() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, PROP)));
        assertThrows(IllegalArgumentException.class, () -> service.generarReporte(CONFIG_ID, "PDF", "[1,"));
        assertThrows(IllegalArgumentException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"organizationId\":2}"));
        assertThrows(IllegalArgumentException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"periodoInicio\":\"2026-13\"}"));
        assertThrows(IllegalArgumentException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"fechaInicio\":\"2026-05-01\",\"fechaFin\":\"2026-04-01\"}"));
        verify(definition, never()).execute(any(), any(), any(), any());
    }

    @Test
    void generationRejectsUnknownFormat() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, PROP)));
        assertThrows(IllegalArgumentException.class, () -> service.generarReporte(CONFIG_ID, "DOCX", null));
        verify(definition, never()).execute(any(), any(), any(), any());
    }

    @Test
    void propertyAdminCannotTargetAnotherPropertyThroughFilters() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, PROP)));
        assertThrows(AccessDeniedException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"propertyId\":" + FOREIGN_PROP + "}"));
        verify(definition, never()).execute(any(), any(), any(), any());
    }

    @Test
    void organizationAdminCannotTargetAForeignPropertyThroughFilters() {
        ctx("ADMIN_ORGANIZACION", ORG, null, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(ORG, null)));
        assertThrows(NoSuchElementException.class,
            () -> service.generarReporte(CONFIG_ID, "PDF", "{\"propertyId\":" + FOREIGN_PROP + "}"));
        verify(definition, never()).execute(any(), any(), any(), any());
    }

    @Test
    void generationRecordsHistoryWithTheContextTenantAndUser() {
        ctx("ADMIN_PROPIEDAD", ORG, PROP, 5L);
        when(repo.findById(CONFIG_ID)).thenReturn(Optional.of(config(null, null)));
        service.generarReporte(CONFIG_ID, "xlsx",
            "{\"fechaInicio\":\"2026-01-01\",\"fechaFin\":\"2026-03-31\"}");
        ArgumentCaptor<ReportExecutionParams> params = ArgumentCaptor.forClass(ReportExecutionParams.class);
        verify(definition).execute(any(), any(), params.capture(), eq(ExportFormat.XLSX));
        assertEquals(PROP, params.getValue().propertyId());
        ArgumentCaptor<HistorialReporteDTO> hist = ArgumentCaptor.forClass(HistorialReporteDTO.class);
        verify(historial).save(hist.capture());
        assertEquals(ORG, hist.getValue().getIdOrganizacion());
        assertEquals(PROP, hist.getValue().getIdPropiedad());
        assertEquals(5L, hist.getValue().getIdUsuarioEjecuto());
    }

    @Test
    void exportFormatParsingIsStrict() {
        assertEquals(ExportFormat.JSON, ExportFormat.fromString(null));
        assertEquals(ExportFormat.JSON, ExportFormat.fromString(" "));
        assertEquals(ExportFormat.XLSX, ExportFormat.fromString("xlsx"));
        assertThrows(IllegalArgumentException.class, () -> ExportFormat.fromString("xml"));
        verify(historial, never()).findHistorial(any(), any(), anyInt(), anyInt());
    }
}
