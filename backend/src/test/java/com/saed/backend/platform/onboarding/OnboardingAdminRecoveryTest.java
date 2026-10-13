package com.saed.backend.platform.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.service.OnboardingService;
import com.saed.backend.platform.service.impl.PlatformOnboardingAdminServiceImpl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3-08 / F3-06: the onboarding queue cannot reject or delete paid/provisioned intentions, cannot
 * delete an intention whose payment may still arrive, approves through the explicit recovery
 * path, and never generates, sets or e-mails passwords.
 */
class OnboardingAdminRecoveryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final OnboardingService onboarding = mock(OnboardingService.class);
    private final TokenActivacionService tokens = mock(TokenActivacionService.class);
    private PlatformOnboardingAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        when(jdbc.getJdbcOperations()).thenReturn(mock(JdbcOperations.class));
        service = new PlatformOnboardingAdminServiceImpl(jdbc, mock(PasswordEncoder.class), mock(EmailService.class),
                new ObjectMapper(), onboarding, tokens);
    }

    private void intentionState(String state) {
        when(jdbc.queryForList(contains("FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ESTADO", state)));
    }

    @Test
    void completedIntentionCannotBeRejected() {
        intentionState("COMPLETADA");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rechazarSolicitud("REF", "motivo"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(jdbc, never()).update(contains("RECHAZADA"), any(SqlParameterSource.class));
    }

    @Test
    void paidIntentionAwaitingReviewCannotBeRejected() {
        intentionState("PAGO_REQUIERE_REVISION");

        assertThrows(ResponseStatusException.class, () -> service.rechazarSolicitud("REF", "motivo"));
    }

    @Test
    void pendingIntentionIsRejectedWithReasonAndItsPaymentStaysTraceable() {
        intentionState("PENDIENTE");

        service.rechazarSolicitud("REF", "datos inconsistentes");

        verify(jdbc).update(contains("SET ESTADO = 'RECHAZADA'"), any(SqlParameterSource.class));
        verify(jdbc, never()).update(contains("TRANSACCIONES_PAGO"), any(SqlParameterSource.class));
    }

    @Test
    void intentionWhosePaymentMayStillArriveCannotBeDeleted() {
        intentionState("RECHAZADA");
        when(jdbc.queryForObject(contains("FROM TRANSACCIONES_PAGO"), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);

        assertThrows(ResponseStatusException.class, () -> service.eliminarSolicitud("REF"));
        verify(jdbc, never()).update(contains("DELETE"), any(SqlParameterSource.class));
    }

    @Test
    void completedIntentionCannotBeDeleted() {
        intentionState("COMPLETADA");

        assertThrows(ResponseStatusException.class, () -> service.eliminarSolicitud("REF"));
        verify(jdbc, never()).update(contains("DELETE"), any(SqlParameterSource.class));
    }

    @Test
    void manualApprovalUsesTheRecoveryProvisioningPath() {
        when(onboarding.aprobarManualmente(eq("REF"), anyString())).thenReturn(true);

        service.aprobarManualmente("REF");

        verify(onboarding).aprobarManualmente(eq("REF"), anyString());
        verify(onboarding, never()).materializarOrganizacion(anyString(), any(), anyString());
    }

    @Test
    void sourceNeverGeneratesSetsOrMailsPasswords() throws IOException {
        String admin = Files.readString(Paths.get("src", "main", "java", "com", "saed", "backend", "platform",
                "service", "impl", "PlatformOnboardingAdminServiceImpl.java"), StandardCharsets.UTF_8);
        String wompi = Files.readString(Paths.get("src", "main", "java", "com", "saed", "backend", "finanzas",
                "service", "impl", "WompiServiceImpl.java"), StandardCharsets.UTF_8);
        String onboardingSource = Files.readString(Paths.get("src", "main", "java", "com", "saed", "backend",
                "platform", "service", "impl", "OnboardingServiceImpl.java"), StandardCharsets.UTF_8);
        for (String src : List.of(admin, wompi, onboardingSource)) {
            assertFalse(src.contains("PasswordGenerator"), "no generated passwords");
            assertFalse(src.contains("HASH_PASSWORD = :pwd"), "no password set for another user");
            assertFalse(src.contains("enviarBienvenidaCredenciales"), "no clear-text credential e-mail");
        }
    }
}
