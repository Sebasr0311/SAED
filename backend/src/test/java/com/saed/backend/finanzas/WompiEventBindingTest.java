package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.OnboardingService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-04: a Wompi event is bound to the persisted intent (tenant unit, amount, currency), to the
 * configured environment and to the provider transaction id; a settled payment is linked back to its
 * intent in the same transaction and receipts are sent only after commit, once. Mocked persistence.
 */
class WompiEventBindingTest {
    private static final String EVENTS_SECRET = "synthetic-events-secret";
    private static final String REFERENCE = "SAED-CUOTA-55-1700000000000";
    private static final long AMOUNT_CENTS = 3_000_000L;
    private static final long INTENT_UNIT = 1000L;

    private NamedParameterJdbcTemplate jdbc;
    private EmailService email;
    private FinanzasService finanzas;
    private final ObjectMapper mapper = new ObjectMapper();
    private long feeUnit;
    private int providerIdAlreadyBound;
    private String intentCurrency;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        email = mock(EmailService.class);
        finanzas = mock(FinanzasService.class);
        feeUnit = INTENT_UNIT;
        providerIdAlreadyBound = 0;
        intentCurrency = "COP";
        JdbcTemplate plain = mock(JdbcTemplate.class);
        when(plain.getDataSource()).thenReturn(null);
        when(jdbc.getJdbcTemplate()).thenReturn(plain);
        when(jdbc.getJdbcOperations()).thenReturn(mock(JdbcOperations.class));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM TRANSACCIONES_PAGO")) {
                Map<String, Object> intent = new HashMap<>();
                intent.put("ID_TRANSACCION", 9L);
                intent.put("ID_UNIDAD", INTENT_UNIT);
                intent.put("MONTO_CENTAVOS", AMOUNT_CENTS);
                intent.put("MONEDA", intentCurrency);
                intent.put("ESTADO_PASARELA", "PENDIENTE");
                return List.of(intent);
            }
            if (sql.contains("FROM UNIDADES u")) {
                return List.of(Map.of("ID_PROPIEDAD", 100L, "ID_ORGANIZACION", 10L));
            }
            if (sql.contains("FROM CUOTAS")) {
                return List.of(Map.of("ID_UNIDAD", feeUnit));
            }
            if (sql.contains("FROM PERSONAS P")) {
                return List.of(Map.of("EMAIL", "resident@example.invalid"));
            }
            return List.of();
        });
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenAnswer(invocation -> providerIdAlreadyBound);
        when(jdbc.update(contains("UPDATE TRANSACCIONES_PAGO SET ESTADO_PASARELA"), any(SqlParameterSource.class)))
                .thenReturn(1);
        when(finanzas.registrarPagoPasarelaVerificado(any())).thenReturn(900L);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private WompiServiceImpl service(String environment) {
        WompiServiceImpl svc = new WompiServiceImpl(jdbc, finanzas, mapper, email,
                mock(TokenActivacionService.class), mock(PasswordEncoder.class),
                mock(OnboardingService.class), mock(MembershipHistoryService.class));
        ReflectionTestUtils.setField(svc, "wompiEventsSecret", EVENTS_SECRET);
        ReflectionTestUtils.setField(svc, "wompiEnvironment", environment);
        return svc;
    }

    private String event(String environment, String currency, long amount, String id) throws Exception {
        long timestamp = 1_700_000_000L;
        String statusValue = "APPROVED";
        String checksum = sha256(id + statusValue + amount + timestamp + EVENTS_SECRET);
        Map<String, Object> tx = new HashMap<>();
        tx.put("id", id);
        tx.put("status", statusValue);
        tx.put("reference", REFERENCE);
        tx.put("amount_in_cents", amount);
        if (currency != null) {
            tx.put("currency", currency);
        }
        Map<String, Object> event = new HashMap<>();
        event.put("event", "transaction.updated");
        event.put("data", Map.of("transaction", tx));
        event.put("signature", Map.of("properties",
                List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"), "checksum", checksum));
        event.put("timestamp", timestamp);
        if (environment != null) {
            event.put("environment", environment);
        }
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

    private void assertIgnored() throws Exception {
        verify(jdbc, never()).update(contains("UPDATE TRANSACCIONES_PAGO"), any(SqlParameterSource.class));
        verify(finanzas, never()).registrarPagoPasarelaVerificado(any());
        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
    }

    @Test
    void sandboxEventIsAcceptedByASandboxDeployment() throws Exception {
        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        verify(finanzas).registrarPagoPasarelaVerificado(any());
    }

    @Test
    void sandboxEventIsRejectedByALiveDeployment() throws Exception {
        service("live").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void liveEventIsRejectedByASandboxDeployment() throws Exception {
        service("sandbox").procesarWebhook(event("prod", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void eventsWithoutAnEnvironmentAreRejected() throws Exception {
        service("sandbox").procesarWebhook(event(null, "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void anUnconfiguredEnvironmentFailsClosed() throws Exception {
        service("").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void theEnvironmentIsDerivedFromThePublicKeyWhenNotConfigured() throws Exception {
        WompiServiceImpl svc = service("");
        svc.setPublicKey("pub_test_synthetic");

        svc.procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        verify(finanzas).registrarPagoPasarelaVerificado(any());
    }

    @Test
    void aMissingCurrencyIsRejected() throws Exception {
        service("sandbox").procesarWebhook(event("test", null, AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void aForeignCurrencyIsRejected() throws Exception {
        service("sandbox").procesarWebhook(event("test", "USD", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void anIntentStoredInAnotherCurrencyIsRejected() throws Exception {
        intentCurrency = "USD";

        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void aTamperedAmountIsRejected() throws Exception {
        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS - 100, "tx-1"));

        assertIgnored();
    }

    @Test
    void aFeeThatBelongsToAnotherUnitThanTheIntentIsRejected() throws Exception {
        feeUnit = 2000L;

        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void aProviderTransactionAlreadyBoundToAnotherIntentIsIgnored() throws Exception {
        providerIdAlreadyBound = 1;

        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        assertIgnored();
    }

    @Test
    void theSettledPaymentIsLinkedToItsIntentInTheSameTransaction() throws Exception {
        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("SET ID_PAGO"), params.capture());
        assertEquals(900L, params.getValue().getValue("pago"));
        assertEquals(9L, params.getValue().getValue("id"));
    }

    @Test
    void theReceiptIsSentOnlyAfterCommitAndOnlyOnce() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        service("sandbox").procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));

        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(email, times(1)).enviarReciboPago(eq("resident@example.invalid"), any(), any(), any(), any());
    }

    @Test
    void aRolledBackAttemptSendsNothingAndTheRetryOnlyOneReceipt() throws Exception {
        when(finanzas.registrarPagoPasarelaVerificado(any()))
                .thenThrow(new IllegalStateException("settlement failed")).thenReturn(900L);
        WompiServiceImpl svc = service("sandbox");

        TransactionSynchronizationManager.initSynchronization();
        assertThrows(IllegalStateException.class, () -> svc.procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1")));
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clearSynchronization();
        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());

        TransactionSynchronizationManager.initSynchronization();
        svc.procesarWebhook(event("test", "COP", AMOUNT_CENTS, "tx-1"));
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(email, times(1)).enviarReciboPago(any(), any(), any(), any(), any());
    }

    @Test
    void theWebhookSourceHasNoStrayEmptyBlocks() throws Exception {
        String source = Files.readString(Path.of("src", "main", "java", "com", "saed", "backend", "finanzas",
                "service", "impl", "WompiServiceImpl.java"));
        for (String line : source.split("\\R")) {
            assertNotEquals("{", line.trim(), "a line holding only '{' is a stray block");
        }
    }

    @Test
    void unconfiguredGatewayFailsAsAnIllegalState() {
        WompiServiceImpl svc = service("sandbox");
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(10L).propertyId(100L)
                .unitId(INTENT_UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());

        assertThrows(IllegalStateException.class, () -> svc.crearIntencion("CUOTA", 55L));
    }

    private WompiServiceImpl configuredService() {
        WompiServiceImpl svc = service("sandbox");
        svc.setPublicKey("pub_test_synthetic");
        svc.setIntegritySecret("synthetic-integrity");
        SaedContextHolder.setContext(SaedContext.builder().userId(5L).organizationId(10L).propertyId(100L)
                .unitId(INTENT_UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());
        return svc;
    }

    @Test
    void aPartiallyPaidFeeCanBePaidOnline() throws Exception {
        WompiServiceImpl svc = configuredService();
        when(jdbc.queryForList(contains("SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_CUOTA", 55L, "ID_UNIDAD", INTENT_UNIT,
                        "SALDO_PENDIENTE", new java.math.BigDecimal("70.00"))));

        svc.crearIntencion("CUOTA", 55L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sql.capture(), any(SqlParameterSource.class));
        String feeLookup = sql.getAllValues().stream().filter(s -> s.contains("FROM CUOTAS")).findFirst().orElseThrow();
        assertTrue(feeLookup.contains("'PAGADA_PARCIAL'"), feeLookup);
    }

    @Test
    void aFeeOfAnotherUnitIsNotFoundAndNeverRetriedWithoutTheUnitFilter() throws Exception {
        WompiServiceImpl svc = configuredService();
        when(jdbc.queryForList(contains("FROM CUOTAS"), any(SqlParameterSource.class))).thenReturn(List.of());

        assertThrows(java.util.NoSuchElementException.class, () -> svc.crearIntencion("CUOTA", 55L));

        verify(jdbc, times(1)).queryForList(contains("FROM CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void aSecondIntentForTheSameFeeWhileOneIsPendingIsRefused() throws Exception {
        WompiServiceImpl svc = configuredService();
        when(jdbc.queryForList(contains("SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_CUOTA", 55L, "ID_UNIDAD", INTENT_UNIT,
                        "SALDO_PENDIENTE", new java.math.BigDecimal("70.00"))));
        providerIdAlreadyBound = 1;   // the pending-intent count reuses the same stub

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.crearIntencion("CUOTA", 55L));

        assertEquals(409, ex.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO TRANSACCIONES_PAGO"), any(SqlParameterSource.class));
    }
}
