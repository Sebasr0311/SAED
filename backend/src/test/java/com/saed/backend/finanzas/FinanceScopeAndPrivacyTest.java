package com.saed.backend.finanzas;

import com.saed.backend.common.exception.GlobalExceptionHandler;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.common.service.VariableResolverService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.ResidentesFinanzasController;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.dto.ResidenteDashboardDTO;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.repository.impl.FinanzasRepositoryImpl;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.impl.FinanzasServiceImpl;
import com.saed.backend.finanzas.service.impl.PazYSalvoServiceImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F06-06 / DEC-02 / resident dashboard: a resident outside the caller's scope is a 404 (not a 200 echoing the
 * id), RESIDENTE_CONVIVENCIA receives no monetary detail at the service layer, SUPERADMIN reads no operational
 * finance data, and failed reads are not hidden behind empty results. Mocked persistence; no database.
 */
class FinanceScopeAndPrivacyTest {
    private static final long PERSON = 50L;
    private static final long OTHER_PERSON = 51L;
    private static final long UNIT = 1000L;

    private NamedParameterJdbcTemplate jdbc;
    private FinanzasRepository repository;
    private FinanzasServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = mock(FinanzasRepository.class);
        service = new FinanzasServiceImpl(repository, mock(PdfService.class), mock(EmailService.class),
                mock(TemplateRenderService.class), mock(VariableResolverService.class),
                mock(FileStorageService.class), jdbc, mock(ConvivienteQuotaService.class));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(PERSON);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(
                List.of(Map.of("ID_UNIDAD", UNIT, "IDENTIFICADOR", "101")));
        when(repository.getCuotasByResidente(PERSON)).thenReturn(List.of(new CuotaDTO(1L, UNIT, "101", "Resident",
                null, "Administracion", "2026-10", new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("70"), LocalDate.of(2026, 10, 10), "PAGADA_PARCIAL")));
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void as(String role, String scope, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).organizationId(10L).propertyId(100L)
                .unitId(unit).roleCode(role).roleScope(scope).build());
    }

    // ---- resident dashboard

    @Test
    void aResidentSeesOnlyTheirOwnDashboard() {
        as("RESIDENTE", "UNIDAD", UNIT);

        ResidenteDashboardDTO dashboard = service.getDashboardResidente(PERSON);

        assertEquals(1, dashboard.cuotas().size());
        assertEquals(UNIT, dashboard.idUnidad());
    }

    @Test
    void aResidentAskingForAnotherPersonGetsNotFoundNotAnEcho() {
        as("RESIDENTE", "UNIDAD", UNIT);

        assertThrows(NoSuchElementException.class, () -> service.getDashboardResidente(OTHER_PERSON));

        verify(repository, never()).getCuotasByResidente(anyLong());
    }

    @Test
    void anOwnerAskingForAnotherPersonGetsNotFound() {
        as("PROPIETARIO", "UNIDAD", UNIT);

        assertThrows(NoSuchElementException.class, () -> service.getDashboardResidente(OTHER_PERSON));
    }

    @Test
    void anAdministratorGetsNotFoundForAPersonWithoutAUnitInTheirProperty() {
        as("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        assertThrows(NoSuchElementException.class, () -> service.getDashboardResidente(999L));

        verify(repository, never()).getCuotasByResidente(anyLong());
    }

    @Test
    void anAdministratorSeesAResidentOfTheirProperty() {
        as("ADMIN_PROPIEDAD", "PROPIEDAD", null);

        ResidenteDashboardDTO dashboard = service.getDashboardResidente(PERSON);

        assertEquals(UNIT, dashboard.idUnidad());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(contains("RESIDENTES_UNIDAD"), params.capture());
        assertEquals(100L, params.getValue().getValue("propId"));
    }

    @Test
    void databaseFailuresAreNotHiddenBehindAnEmptyDashboard() {
        as("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class, () -> service.getDashboardResidente(PERSON));
    }

    @Test
    void theDashboardRouteAnswers404ForAnOutOfScopeResident() throws Exception {
        FinanzasService mocked = mock(FinanzasService.class);
        when(mocked.getDashboardResidente(OTHER_PERSON)).thenThrow(new NoSuchElementException("Residente no encontrado"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResidentesFinanzasController(mocked))
                .setControllerAdvice(new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class))).build();

        mvc.perform(get("/api/v1/residentes/" + OTHER_PERSON + "/dashboard"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"idResidente\""))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void feesOfAPersonCoverTheirUnitsNotOnlyTheirRentalContracts() {
        NamedParameterJdbcTemplate repoJdbc = mock(NamedParameterJdbcTemplate.class);
        when(repoJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        as("ADMIN_PROPIEDAD", "PROPIEDAD", null);

        new FinanzasRepositoryImpl(repoJdbc).getCuotasByResidente(PERSON);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(repoJdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("RESIDENTES_UNIDAD"), sql.getValue());
        assertTrue(sql.getValue().contains("PROPIETARIOS_UNIDAD"), sql.getValue());
        assertTrue(sql.getValue().contains("VALOR_DESCUENTO") && sql.getValue().contains("VALOR_INTERES_MORA"),
                "the total must be computed, not read from the constant virtual column: " + sql.getValue());
    }

    // ---- RESIDENTE_CONVIVENCIA: no monetary detail

    @Test
    void convivenciaCannotReadOrRegisterPaymentsOrFees() {
        as("RESIDENTE_CONVIVENCIA", "UNIDAD", UNIT);

        assertThrows(AccessDeniedException.class, () -> service.getPagos(null, UNIT, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.getPagoDetalle(1L));
        assertThrows(AccessDeniedException.class, () -> service.getDashboardResidente(PERSON));
        assertThrows(AccessDeniedException.class, () -> service.getCuotasPendientes());
        assertThrows(AccessDeniedException.class, () -> service.registrarPago(
                new PagoRequestDTO(1L, LocalDate.of(2026, 10, 1), new BigDecimal("10"), "TRANSFERENCIA", "R")));
        assertThrows(AccessDeniedException.class, () -> service.getContratos());
        assertThrows(AccessDeniedException.class, () -> service.getContratoDetalle(1L));

        verifyNoInteractions(repository);
    }

    // ---- DEC-02: SUPERADMIN reads no operational finance data

    @Test
    void superadminCannotReadContractsPaymentsOrDashboards() {
        as("SUPERADMIN", "GLOBAL", null);

        assertThrows(AccessDeniedException.class, () -> service.getContratoDetalle(1L));
        assertThrows(AccessDeniedException.class, () -> service.getPagos(null, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.getPagoDetalle(1L));
        assertThrows(AccessDeniedException.class, () -> service.getDashboardResidente(PERSON));

        verifyNoInteractions(repository);
    }

    @Test
    void superadminCannotReadAUnitFinancialStatement() {
        as("SUPERADMIN", "GLOBAL", null);
        PazYSalvoServiceImpl paz = new PazYSalvoServiceImpl(jdbc, mock(FileStorageService.class), mock(PdfService.class));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map.of(
                "ID_UNIDAD", UNIT, "IDENTIFICADOR", "101", "ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)));

        assertThrows(AccessDeniedException.class, () -> paz.verificarEstadoFinanciero(UNIT));
    }

    @Test
    void aContractOfAnotherPropertyIsNotFoundForAnAdministrator() {
        as("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        com.saed.backend.finanzas.dto.ContratoDetalleDTO detail = new com.saed.backend.finanzas.dto.ContratoDetalleDTO();
        detail.setIdPropiedad(999L);
        when(repository.getContratoDetalle(1L)).thenReturn(detail);

        assertThrows(NoSuchElementException.class, () -> service.getContratoDetalle(1L));
    }
}
