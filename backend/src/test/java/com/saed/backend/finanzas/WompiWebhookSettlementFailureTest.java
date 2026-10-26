package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.PagosController;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.OnboardingService;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F06: a Wompi webhook whose settlement fails must not be acknowledged. Pure unit tests with
 * mocked persistence; rollback itself is only asserted through the transactional contract and
 * still needs proof against real Spring transactions on Oracle.
 */
class WompiWebhookSettlementFailureTest {
    private static final String EVENTS_SECRET = "synthetic-events-secret";
    private static final String REFERENCE = "SAED-CUOTA-55-1700000000000";
    private static final long AMOUNT_CENTS = 3_000_000L;
    private static final String DB_LEAK = "ORA-00001: unique constraint (SAED_OWNER.UK_PAGOS) violated";

    private NamedParameterJdbcTemplate jdbc;
    private EmailService email;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        email = mock(EmailService.class);
        JdbcTemplate plain = mock(JdbcTemplate.class);
        when(plain.getDataSource()).thenReturn(null);
        when(jdbc.getJdbcTemplate()).thenReturn(plain);
        when(jdbc.getJdbcOperations()).thenReturn(mock(JdbcOperations.class));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM TRANSACCIONES_PAGO")) {
                Map<String, Object> intent = new HashMap<>();
                intent.put("ID_TRANSACCION", 9L);
                intent.put("ID_UNIDAD", 1000L);
                intent.put("MONTO_CENTAVOS", AMOUNT_CENTS);
                intent.put("MONEDA", "COP");
                return List.of(intent);
            }
            if (sql.contains("FROM CUOTAS")) {
                return List.of(Map.of("ID_UNIDAD", 1000L));
            }
            if (sql.contains("FROM UNIDADES u")) {
                return List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L));
            }
            if (sql.contains("FROM PERSONAS P")) {
                return List.of(Map.of("EMAIL", "resident@example.invalid"));
            }
            return List.of();
        });
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private WompiServiceImpl service(FinanzasService finanzas) {
        WompiServiceImpl svc = new WompiServiceImpl(jdbc, finanzas, mapper, email,
                mock(TokenActivacionService.class), mock(PasswordEncoder.class),
                mock(OnboardingService.class), mock(MembershipHistoryService.class));
        ReflectionTestUtils.setField(svc, "wompiEventsSecret", EVENTS_SECRET);
        ReflectionTestUtils.setField(svc, "wompiEnvironment", "sandbox");
        return svc;
    }

    private static FinanzasService failingSettlement() {
        return mock(FinanzasService.class, invocation -> {
            throw new DataIntegrityViolationException(DB_LEAK);
        });
    }

    private void pendingTransitionReturns(int rows) {
        when(jdbc.update(contains("UPDATE TRANSACCIONES_PAGO"), any(SqlParameterSource.class))).thenReturn(rows);
    }

    private String signedApprovedEvent(String checksumOverride) throws Exception {
        long timestamp = 1_700_000_000L;
        String id = "wompi-tx-1";
        String statusValue = "APPROVED";
        String checksum = checksumOverride != null ? checksumOverride
                : sha256(id + statusValue + AMOUNT_CENTS + timestamp + EVENTS_SECRET);
        Map<String, Object> tx = Map.of("id", id, "status", statusValue, "reference", REFERENCE,
                "amount_in_cents", AMOUNT_CENTS, "currency", "COP");
        Map<String, Object> event = Map.of(
                "event", "transaction.updated",
                "data", Map.of("transaction", tx),
                "signature", Map.of("properties",
                        List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"),
                        "checksum", checksum),
                "timestamp", timestamp,
                "environment", "test");
        return mapper.writeValueAsString(event);
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void settlementFailurePropagatesAndSendsNoReceipt() throws Exception {
        pendingTransitionReturns(1);
        WompiServiceImpl svc = service(failingSettlement());

        assertThrows(DataIntegrityViolationException.class, () -> svc.procesarWebhook(signedApprovedEvent(null)));

        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
        assertNull(SaedContextHolder.getContext(), "privileged webhook context must be cleared after failure");
    }

    @Test
    void approvedFeeEventSettlesThroughInternalGatewayOperationOnly() throws Exception {
        pendingTransitionReturns(1);
        FinanzasService finanzas = mock(FinanzasService.class);

        service(finanzas).procesarWebhook(signedApprovedEvent(null));

        verify(finanzas).registrarPagoPasarelaVerificado(argThat(req -> req.idCuota() == 55L
                && "PASARELA_WOMPI".equals(req.metodoPago())
                && new java.math.BigDecimal("30000").compareTo(req.valorPagado()) == 0));
        verify(finanzas, never()).registrarPago(any());
    }

    @Test
    void webhookTransactionRollsBackOnCheckedExceptionsToo() throws Exception {
        Method method = WompiServiceImpl.class.getMethod("procesarWebhook", String.class);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertTrue(Arrays.asList(tx.rollbackFor()).contains(Exception.class),
                "procesarWebhook declares 'throws Exception'; checked failures must roll back as well");
    }

    @Test
    void duplicateAlreadyProcessedEventIsAcknowledgedWithoutSecondSettlement() throws Exception {
        pendingTransitionReturns(0);
        FinanzasService finanzas = failingSettlement();

        assertDoesNotThrow(() -> service(finanzas).procesarWebhook(signedApprovedEvent(null)));

        verifyNoInteractions(finanzas);
        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
    }

    @Test
    void invalidSignatureIsIgnoredWithoutAnyWrite() throws Exception {
        FinanzasService finanzas = failingSettlement();

        assertDoesNotThrow(() -> service(finanzas).procesarWebhook(signedApprovedEvent("forged")));

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
        verifyNoInteractions(finanzas);
    }

    @Test
    void malformedPayloadIsDiscardedWithoutFailure() {
        assertDoesNotThrow(() -> service(failingSettlement()).procesarWebhook("{not-json"));
    }

    @Test
    void endpointReturnsSanitizedServerErrorWhenProcessingFails() throws Exception {
        WompiService wompi = mock(WompiService.class);
        doThrow(new IllegalStateException(DB_LEAK + " at com.saed.backend.finanzas.Secret"))
                .when(wompi).procesarWebhook(anyString());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PagosController(mock(FinanzasService.class), wompi)).build();

        for (String path : List.of("/api/v1/pagos/wompi/webhook", "/api/v1/pagos/notificacion")) {
            MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn();
            int code = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            assertTrue(code >= 500 && code < 600, "settlement failure must not be acknowledged, got " + code);
            assertFalse(body.contains("ORA-"), body);
            assertFalse(body.contains("SAED_OWNER"), body);
            assertFalse(body.contains("com.saed"), body);
            assertFalse(body.contains("Exception"), body);
        }
    }

    @Test
    void endpointAcknowledgesSuccessfulProcessingWith200() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PagosController(mock(FinanzasService.class), mock(WompiService.class))).build();

        mvc.perform(post("/api/v1/pagos/wompi/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
