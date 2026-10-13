package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiTransactionVerifier;
import com.saed.backend.finanzas.service.WompiTransactionVerifier.VerifiedTransaction;
import com.saed.backend.finanzas.service.impl.DisabledWompiTransactionVerifier;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Webhook hardening: the signature must cover transaction id, status and amount, and, where a verifier is enabled,
 * the provider confirms reference, status and amount before anything touches the database. Mocked persistence.
 */
class WompiSignedPropertiesTest {
    private static final String SECRET = "synthetic-events-secret";
    private static final String REFERENCE = "SAED-CUOTA-55-1700000000000";
    private static final long AMOUNT = 3_000_000L;
    private static final long TS = 1_700_000_000L;
    private static final List<String> FULL =
            List.of("transaction.id", "transaction.status", "transaction.amount_in_cents");

    private NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate plain = mock(JdbcTemplate.class);
        when(plain.getDataSource()).thenReturn(null);
        when(jdbc.getJdbcTemplate()).thenReturn(plain);
        when(jdbc.getJdbcOperations()).thenReturn(mock(JdbcOperations.class));
    }

    private WompiServiceImpl service(WompiTransactionVerifier verifier) {
        WompiServiceImpl svc = new WompiServiceImpl(jdbc, mock(FinanzasService.class), mapper, mock(EmailService.class),
                mock(TokenActivacionService.class), mock(PasswordEncoder.class),
                mock(OnboardingService.class), mock(MembershipHistoryService.class));
        ReflectionTestUtils.setField(svc, "wompiEventsSecret", SECRET);
        ReflectionTestUtils.setField(svc, "wompiEnvironment", "SANDBOX");
        if (verifier != null) {
            ReflectionTestUtils.setField(svc, "transactionVerifier", verifier);
        }
        return svc;
    }

    private String event(List<String> properties) throws Exception {
        Map<String, Object> tx = new HashMap<>();
        tx.put("id", "tx-1");
        tx.put("status", "APPROVED");
        tx.put("reference", REFERENCE);
        tx.put("amount_in_cents", AMOUNT);
        tx.put("currency", "COP");
        StringBuilder base = new StringBuilder();
        for (String property : properties) {
            base.append(tx.get(property.substring("transaction.".length())));
        }
        base.append(TS).append(SECRET);
        Map<String, Object> event = new HashMap<>();
        event.put("event", "transaction.updated");
        event.put("data", Map.of("transaction", tx));
        event.put("signature", Map.of("properties", properties, "checksum", sha256(base.toString())));
        event.put("timestamp", TS);
        event.put("environment", "test");
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

    private static WompiTransactionVerifier verifier(Optional<VerifiedTransaction> answer) throws Exception {
        WompiTransactionVerifier v = mock(WompiTransactionVerifier.class);
        when(v.enabled()).thenReturn(true);
        when(v.find("tx-1")).thenReturn(answer);
        return v;
    }

    @Test
    void aSignatureThatDoesNotCoverTheAmountIsRejected() throws Exception {
        service(null).procesarWebhook(event(List.of("transaction.id", "transaction.status")));
        verify(jdbc, never()).getJdbcTemplate();
    }

    @Test
    void aSignatureThatDoesNotCoverTheTransactionIdIsRejected() throws Exception {
        service(null).procesarWebhook(event(List.of("transaction.status", "transaction.amount_in_cents")));
        verify(jdbc, never()).getJdbcTemplate();
    }

    @Test
    void aSignatureThatDoesNotCoverTheStatusIsRejected() throws Exception {
        service(null).procesarWebhook(event(List.of("transaction.id", "transaction.amount_in_cents")));
        verify(jdbc, never()).getJdbcTemplate();
    }

    @Test
    void aProviderThatDoesNotKnowTheTransactionIsRejected() throws Exception {
        service(verifier(Optional.empty())).procesarWebhook(event(FULL));
        verify(jdbc, never()).getJdbcTemplate();
    }

    @Test
    void aProviderThatReportsAnotherReferenceStatusOrAmountIsRejected() throws Exception {
        service(verifier(Optional.of(new VerifiedTransaction("OTHER-REF", "APPROVED", AMOUNT)))).procesarWebhook(event(FULL));
        service(verifier(Optional.of(new VerifiedTransaction(REFERENCE, "DECLINED", AMOUNT)))).procesarWebhook(event(FULL));
        service(verifier(Optional.of(new VerifiedTransaction(REFERENCE, "APPROVED", AMOUNT + 1)))).procesarWebhook(event(FULL));
        verify(jdbc, never()).getJdbcTemplate();
    }

    @Test
    void aConfirmedTransactionProceedsToTheDatabaseChecks() throws Exception {
        service(verifier(Optional.of(new VerifiedTransaction(REFERENCE, "APPROVED", AMOUNT)))).procesarWebhook(event(FULL));
        verify(jdbc, atLeastOnce()).getJdbcTemplate();
    }

    @Test
    void theDefaultVerifierIsDisabledAndMakesNoCall() throws Exception {
        assertFalse(new DisabledWompiTransactionVerifier().enabled());
        service(new DisabledWompiTransactionVerifier()).procesarWebhook(event(FULL));
        verify(jdbc, atLeastOnce()).getJdbcTemplate();
    }
}
