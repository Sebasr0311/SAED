package com.saed.backend.finanzas;

import com.saed.backend.common.exception.GlobalExceptionHandler;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.PagosController;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.repository.impl.FinanzasRepositoryImpl;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.finanzas.service.impl.FinanzasServiceImpl;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.common.service.VariableResolverService;
import com.saed.backend.person.service.ConvivienteQuotaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F06 / C-09: ordinary payment registration must never turn client input into an approved
 * payment. Pure unit tests with mocked persistence; they do not prove Oracle trigger or
 * transaction behavior.
 */
class OrdinaryPaymentRegistrationIntegrityTest {
    private static final long UNIT_ID = 1000L;
    private static final long FEE_ID = 55L;

    private NamedParameterJdbcTemplate jdbc;
    private FinanzasRepository repository;
    private FinanzasServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = mock(FinanzasRepository.class);
        service = new FinanzasServiceImpl(repository, mock(PdfService.class), mock(EmailService.class),
                mock(TemplateRenderService.class), mock(VariableResolverService.class),
                mock(FileStorageService.class), jdbc, mock(ConvivienteQuotaService.class));
        CuotaDTO fee = new CuotaDTO(FEE_ID, UNIT_ID, "101", "Synthetic Resident", 7L, "ADMINISTRACION",
                "2099-01", new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2099, 1, 31), "PENDIENTE");
        doReturn(List.of(fee)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(jdbc.queryForList(contains("FROM CUOTAS"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_CUOTA", FEE_ID, "SALDO_PENDIENTE", new BigDecimal("100.00"),
                        "ESTADO", "PENDIENTE")));
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(10L)
                .propertyId(100L).unitId(UNIT_ID).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static PagoRequestDTO request(String method) {
        return new PagoRequestDTO(FEE_ID, LocalDate.of(2099, 1, 10), new BigDecimal("30.00"), method, "REF-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASARELA_WOMPI", "pasarela_wompi", " PASARELA_WOMPI ", "WOMPI"})
    void residentGatewayMethodIsRejectedBeforeAnyLedgerWrite(String method) {
        assertThrows(IllegalArgumentException.class, () -> service.registrarPago(request(method)));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASARELA_WOMPI", "WOMPI"})
    void manualEvidenceRouteAlsoRejectsGatewayMethod(String method) {
        assertThrows(IllegalArgumentException.class, () -> service.registrarPagoManual(request(method), null));
        verifyNoInteractions(repository);
    }

    @Test
    void manualMethodIsRegisteredPendingWithoutDebit() {
        service.registrarPago(request("TRANSFERENCIA"));

        verify(repository).registrarPago(any(PagoRequestDTO.class), eq(UNIT_ID), eq("PENDIENTE_APROBACION"), isNull(), isNull());
        verify(repository, never()).registrarPago(any(PagoRequestDTO.class), anyLong(), eq("APROBADO"), any(), any());
        verify(repository, never()).recalcularCarteraUnidad(anyLong());
    }

    @Test
    void internalGatewaySettlementApprovesWithoutJavaDebit() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(10L)
                .propertyId(100L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        service.registrarPagoPasarelaVerificado(request("PASARELA_WOMPI"));

        verify(repository).registrarPago(any(PagoRequestDTO.class), eq(UNIT_ID), eq("APROBADO"), isNull(), isNull());
        // The fee debit belongs to the V5.17 trigger (see PaymentSingleDebitOwnerTest).
        verify(repository).recalcularCarteraUnidad(UNIT_ID);
    }

    @Test
    void postPagosWithGatewayMethodReturns400AndWritesNothing() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PagosController(service, mock(WompiService.class)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class)))
                .build();

        mvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCuota\":55,\"valorPagado\":30.00,\"metodoPago\":\"PASARELA_WOMPI\",\"referencia\":\"FORGED\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
    }

    @Test
    void repositoryDefaultsToPendingWhenStateIsAbsentEvenForGatewayMethod() {
        NamedParameterJdbcTemplate repoJdbc = mock(NamedParameterJdbcTemplate.class);
        doAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(2);
            ((GeneratedKeyHolder) holder).getKeyList().add(Map.of("ID_PAGO", 501L));
            return 1;
        }).when(repoJdbc).update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        FinanzasRepositoryImpl repo = new FinanzasRepositoryImpl(repoJdbc);

        repo.registrarPago(request("PASARELA_WOMPI"), UNIT_ID);
        repo.registrarPago(request("PASARELA_WOMPI"), UNIT_ID, "  ", null, null);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(repoJdbc, times(2)).update(contains("INSERT INTO PAGOS"), params.capture(), any(KeyHolder.class), any(String[].class));
        for (SqlParameterSource p : params.getAllValues()) {
            assertEquals("PENDIENTE_APROBACION", ((MapSqlParameterSource) p).getValue("estado"));
        }
    }
}
