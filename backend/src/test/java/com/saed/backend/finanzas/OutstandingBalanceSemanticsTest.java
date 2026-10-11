package com.saed.backend.finanzas;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.CarteraController;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import com.saed.backend.finanzas.repository.impl.FinanzasRepositoryImpl;
import com.saed.backend.finanzas.service.impl.PazYSalvoServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-03 / F6-06 / F6-07 / F6-08: PAGADA_PARCIAL is outstanding in every finance read (paz y salvo,
 * portfolio, cash flow), the portfolio recalculation only writes states allowed by CK_CARTERA_ESTADO,
 * and finance reads no longer hide database failures behind zeros.
 *
 * <p>Pure unit tests over the SQL the code issues (mocked JDBC); they do not execute against Oracle.
 */
class OutstandingBalanceSemanticsTest {
    private static final List<String> FORBIDDEN_PORTFOLIO_STATES = List.of("MORA_LEVE", "MORA_MEDIA", "MORA_GRAVE");
    private static final List<String> ALLOWED_PORTFOLIO_STATES = List.of("AL_DIA", "MORA_30", "MORA_60", "MORA_CRITICA");

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(10L).propertyId(100L)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void assertOutstanding(String sql) {
        assertTrue(sql.contains("'PAGADA_PARCIAL'"), "PAGADA_PARCIAL must count as outstanding: " + sql);
        assertFalse(sql.contains("EN_MORA"), "EN_MORA is not a fee state: " + sql);
    }

    @Test
    void pazYSalvoCountsPartiallyPaidFeesAsOutstanding() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(
                List.of(Map.of("ID_UNIDAD", 1000L, "IDENTIFICADOR", "101", "ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L)));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("70.00"));
        PazYSalvoServiceImpl service = new PazYSalvoServiceImpl(jdbc, mock(FileStorageService.class), mock(PdfService.class));

        PazYSalvoEstadoFinancieroDTO estado = service.verificarEstadoFinanciero(1000L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForObject(sql.capture(), any(SqlParameterSource.class), eq(BigDecimal.class));
        String feeSql = sql.getAllValues().stream().filter(s -> s.contains("FROM CUOTAS")).findFirst().orElseThrow();
        assertOutstanding(feeSql);
        assertTrue(feeSql.contains("SALDO_PENDIENTE"), feeSql);
        assertFalse(estado.pazYSalvo(), "a unit with a partial balance must not get a clean certificate");
    }

    @Test
    void feeStatementsOfUnknownUnitsAreNotFound() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        PazYSalvoServiceImpl service = new PazYSalvoServiceImpl(jdbc, mock(FileStorageService.class), mock(PdfService.class));

        assertThrows(java.util.NoSuchElementException.class, () -> service.verificarEstadoFinanciero(999L));
    }

    @Test
    void portfolioRecalculationOnlyWritesStatesAllowedByTheCheckConstraint() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(3);

        new CarteraController(jdbc).recalcular();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        String all = String.join("\n", sql.getAllValues());
        assertOutstanding(all);
        for (String bad : FORBIDDEN_PORTFOLIO_STATES) {
            assertFalse(all.contains(bad), bad + " violates CK_CARTERA_ESTADO");
        }
        for (String good : ALLOWED_PORTFOLIO_STATES) {
            assertTrue(all.contains("'" + good + "'"), good);
        }
        assertFalse(all.contains("MERGE INTO CARTERA"), "MERGE into a policy-protected table raises ORA-28132");
    }

    @Test
    void portfolioRecalculationIsScopedToTheSessionProperty() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        new CarteraController(jdbc).recalcular();

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, atLeastOnce()).update(anyString(), params.capture());
        for (SqlParameterSource p : params.getAllValues()) {
            assertEquals(100L, p.getValue("propId"));
        }
    }

    @Test
    void portfolioRecalculationWithoutPropertyIsDenied() {
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(10L)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> new CarteraController(jdbc).recalcular());
        verifyNoInteractions(jdbc);
    }

    @Test
    void portfolioSummaryCountsTheRealStates() {
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of());

        new CarteraController(jdbc).resumen();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForMap(sql.capture(), any(SqlParameterSource.class));
        for (String bad : FORBIDDEN_PORTFOLIO_STATES) {
            assertFalse(sql.getValue().contains(bad), bad);
        }
        for (String good : List.of("MORA_30", "MORA_60", "MORA_CRITICA", "COBRO_JURIDICO")) {
            assertTrue(sql.getValue().contains("'" + good + "'"), good);
        }
    }

    @Test
    void agingReportCountsPartiallyPaidFees() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        new CarteraController(jdbc).antiguedad();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(SqlParameterSource.class));
        assertOutstanding(sql.getValue());
    }

    @Test
    void repositoryRecalculationSharesTheSameVocabulary() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        new FinanzasRepositoryImpl(jdbc).recalcularCarteraUnidad(1000L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class));
        String all = String.join("\n", sql.getAllValues());
        assertOutstanding(all);
        assertFalse(all.contains("MERGE INTO CARTERA"));
        for (String bad : FORBIDDEN_PORTFOLIO_STATES) {
            assertFalse(all.contains(bad), bad);
        }
    }

    @Test
    void pendingFeesListIncludesPartiallyPaidAndOverdueFees() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        new FinanzasRepositoryImpl(jdbc).getCuotasPendientes();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertOutstanding(sql.getValue());
        assertTrue(sql.getValue().contains("'VENCIDA'"), sql.getValue());
    }

    @Test
    void cashFlowExpectedIncomeAndProjectionCountPartiallyPaidFees() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(BigDecimal.class))).thenReturn(BigDecimal.TEN);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        FlujoCajaRepository repo = new FlujoCajaRepository(jdbc);

        repo.getIngresosEsperados();
        repo.getProyeccionMensual();

        ArgumentCaptor<String> expected = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(expected.capture(), anyMap(), eq(BigDecimal.class));
        assertOutstanding(expected.getValue());
        ArgumentCaptor<String> projection = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(projection.capture(), anyMap(), any(RowMapper.class));
        assertOutstanding(projection.getValue());
    }

    @Test
    void cashFlowDoesNotHideDatabaseFailuresAsZero() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(BigDecimal.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        FlujoCajaRepository repo = new FlujoCajaRepository(jdbc);

        assertThrows(DataAccessResourceFailureException.class, repo::getTotalIngresos);
        assertThrows(DataAccessResourceFailureException.class, repo::getTotalEgresos);
        assertThrows(DataAccessResourceFailureException.class, repo::getIngresosEsperados);
        assertThrows(DataAccessResourceFailureException.class, repo::getGastosProgramados);
        assertThrows(DataAccessResourceFailureException.class, () -> repo.getMovimientosRecientes(5));
        assertThrows(DataAccessResourceFailureException.class, repo::getProyeccionMensual);
    }
}
