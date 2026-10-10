package com.saed.backend.platform.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.service.DefaultContractTemplateProvisioner;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.impl.OnboardingServiceImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-06, F3-04, F3-05, F3-06, F3-08, DEC-09: public onboarding decides free/trial/paid on the
 * server, never purges or overwrites existing data, never mails passwords, records an explicit
 * recovery state for payments it cannot provision, and creates the default contract template.
 */
class OnboardingProvisioningIntegrityTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcOperations ops = mock(JdbcOperations.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailService email = mock(EmailService.class);
    private final MembershipHistoryService history = mock(MembershipHistoryService.class);
    private final TokenActivacionService tokens = mock(TokenActivacionService.class);
    private final DefaultContractTemplateProvisioner templates = mock(DefaultContractTemplateProvisioner.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private OnboardingServiceImpl service;

    @BeforeEach
    void setUp() {
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        service = new OnboardingServiceImpl(jdbc, encoder, email, mapper, history, tokens, templates);
        ReflectionTestUtils.setField(service, "wompiPublicKey", "pub_test_unit");
        ReflectionTestUtils.setField(service, "wompiIntegritySecret", "test_integrity_unit");
        when(jdbc.queryForList(contains("FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_ROL", 2L)));
        when(jdbc.queryForList(contains("FROM TIPOS_DOCUMENTO"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ID_TIPO_DOCUMENTO", 1L)));
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(2);
            String sql = inv.getArgument(0);
            long key = sql.contains("ORGANIZACIONES") ? 900L : sql.contains("PERSONAS") ? 901L
                    : sql.contains("USUARIOS") ? 902L : 903L;
            kh.getKeyList().add(Map.of("ID", key));
            return 1;
        }).when(jdbc).update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        conflicts(0, 0, 0, 0);
        priorTrials(0);
    }

    private void conflicts(long nit, long mail, long username, long document) {
        when(jdbc.queryForObject(contains("FROM ORGANIZACIONES WHERE IDENTIFICACION_FISCAL = :nit"),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(nit);
        when(jdbc.queryForObject(contains("FROM USUARIOS WHERE LOWER(EMAIL) = :email"),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(mail);
        when(jdbc.queryForObject(contains("FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u"),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(username);
        when(jdbc.queryForObject(contains("FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc"),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(document);
    }

    private void priorTrials(long count) {
        when(jdbc.queryForObject(contains("TIPO_FLUJO = 'PRUEBA'"), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(count);
    }

    private void plan(long price, String trialEligible) {
        Map<String, Object> row = new HashMap<>();
        row.put("ID_PLAN", 3L);
        row.put("CODIGO", "ENTERPRISE");
        row.put("NOMBRE", "Enterprise");
        row.put("PRECIO_MENSUAL", price);
        row.put("PERMITE_PRUEBA", trialEligible);
        when(jdbc.queryForList(contains("FROM PLANES WHERE ID_PLAN = :p"), any(SqlParameterSource.class)))
                .thenReturn(List.of(row));
    }

    private static OnboardingRegistroRequestDTO request(boolean clientAsksForTrial) {
        OnboardingRegistroRequestDTO r = new OnboardingRegistroRequestDTO();
        r.setNombreOrganizacion("Conjunto Uno");
        r.setIdentificacionFiscal("900.123.456-7");
        r.setEmailOrganizacion("org@example.com");
        r.setPrimerNombre("Ana");
        r.setPrimerApellido("Gomez");
        r.setNumeroDocumento("1010");
        r.setEmailAdmin("Admin@Example.com");
        r.setIdPlan(3L);
        r.setEsPrueba(clientAsksForTrial);
        return r;
    }

    private void verifyNothingProvisioned() {
        verify(jdbc, never()).update(contains("INSERT INTO ORGANIZACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
        verify(jdbc, never()).update(contains("INSERT INTO MEMBRESIAS"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    private void verifyNoDestructiveOrOverwritingStatements() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeast(0)).update(sql.capture(), any(SqlParameterSource.class));
        for (String statement : sql.getAllValues()) {
            assertFalse(statement.contains("DELETE"), statement);
            assertFalse(statement.contains("UPDATE PERSONAS"), statement);
            assertFalse(statement.contains("UPDATE USUARIOS"), statement);
        }
    }

    // ---------------------------------------------------------------- DEC-06 / F3-04

    @Test
    void clientRequestedTrialOnAPaidPlanIsIgnoredAndRequiresPayment() {
        plan(799000, "N");

        Map<String, Object> resp = service.registrar(request(true));

        assertEquals(Boolean.TRUE, resp.get("requierePago"));
        verifyNothingProvisioned();
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO ONBOARDING_INTENCIONES"), params.capture());
        assertEquals("PAGO", params.getValue().getValue("tipo"));
        assertEquals("900123456-7", params.getValue().getValue("nit"));
        assertEquals("admin@example.com", params.getValue().getValue("email"));
        assertEquals(3L, params.getValue().getValue("plan"));
    }

    @Test
    void trialEligiblePlanGrantsOneTrialProvisionedWithoutPayment() {
        plan(299000, "S");

        Map<String, Object> resp = service.registrar(request(false));

        assertEquals(Boolean.FALSE, resp.get("requierePago"));
        verify(jdbc).update(contains("INSERT INTO ORGANIZACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO ONBOARDING_INTENCIONES"), params.capture());
        assertEquals("PRUEBA", params.getValue().getValue("tipo"));
    }

    @Test
    void secondTrialForTheSameTaxIdOrEmailRequiresPayment() {
        plan(299000, "S");
        priorTrials(1);

        Map<String, Object> resp = service.registrar(request(true));

        assertEquals(Boolean.TRUE, resp.get("requierePago"));
        verifyNothingProvisioned();
    }

    @Test
    void paidFlowFailsClosedWithoutWompiConfiguration() {
        plan(799000, "N");
        ReflectionTestUtils.setField(service, "wompiIntegritySecret", "");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.registrar(request(false)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(jdbc, never()).update(contains("INSERT INTO TRANSACCIONES_PAGO"), any(SqlParameterSource.class));
    }

    // ---------------------------------------------------------------- F3-05

    @Test
    void existingDocumentOfAnotherTenantIsNeitherReusedNorOverwritten() {
        plan(0, "N");
        conflicts(0, 0, 0, 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.registrar(request(false)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNothingProvisioned();
        verifyNoDestructiveOrOverwritingStatements();
    }

    @Test
    void existingTaxIdIsRejectedEvenForAnInactiveOrganizationWithoutPurging() {
        plan(0, "N");
        conflicts(1, 0, 0, 0);

        assertThrows(ResponseStatusException.class, () -> service.registrar(request(false)));
        verifyNoDestructiveOrOverwritingStatements();
    }

    // ---------------------------------------------------------------- F3-06 / DEC-09

    @Test
    void freeProvisioningSendsAnActivationLinkNotAPasswordAndCreatesTheDefaultTemplate() throws Exception {
        plan(0, "N");

        Map<String, Object> resp = service.registrar(request(false));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(SqlParameterSource.class), any(KeyHolder.class),
                any(String[].class));
        assertTrue(sql.getAllValues().stream().anyMatch(s -> s.contains("INSERT INTO USUARIOS")
                && s.contains("'PENDIENTE_VERIFICACION'")));
        verify(tokens).generarYEnviarTokenActivacion(eq(902L), isNull());
        verify(templates).createDefaultTemplate(900L, 902L);
        verify(email, never()).enviarBienvenidaCredencialesConResultado(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
        assertNull(resp.get("password"));
        verifyNoDestructiveOrOverwritingStatements();
    }

    // ---------------------------------------------------------------- F3-08

    private void intention(String state, long amount) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("REFERENCIA", "SAED-MEMBRESIA-ONB-abc");
        row.put("DATOS_REGISTRO", mapper.writeValueAsString(request(false)));
        row.put("MONTO_CENTAVOS", amount);
        row.put("ESTADO", state);
        when(jdbc.queryForList(contains("FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref FOR UPDATE"),
                any(SqlParameterSource.class))).thenReturn(List.of(row));
    }

    private String recordedRecoveryReason() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("ESTADO = 'PAGO_REQUIERE_REVISION'"), params.capture());
        return String.valueOf(params.getValue().getValue("motivo"));
    }

    @Test
    void paymentForARejectedIntentionIsRecordedForRecoveryNotSilentlyDropped() throws Exception {
        intention("RECHAZADA", 79900000L);

        boolean provisioned = service.materializarOrganizacion("SAED-MEMBRESIA-ONB-abc", 79900000L, "wompi-1");

        assertFalse(provisioned);
        assertTrue(recordedRecoveryReason().contains("RECHAZADA"));
        verifyNothingProvisioned();
    }

    @Test
    void paymentWhoseTaxIdWasTakenMeanwhileIsRecordedForRecovery() throws Exception {
        intention("PENDIENTE", 79900000L);
        plan(799000, "N");
        conflicts(1, 0, 0, 0);

        assertFalse(service.materializarOrganizacion("SAED-MEMBRESIA-ONB-abc", 79900000L, "wompi-1"));
        recordedRecoveryReason();
        verifyNothingProvisioned();
    }

    @Test
    void paymentWithoutAnyIntentionCreatesARecoveryRecord() {
        when(jdbc.queryForList(contains("FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref FOR UPDATE"),
                any(SqlParameterSource.class))).thenReturn(List.of());

        assertFalse(service.materializarOrganizacion("SAED-MEMBRESIA-ONB-zzz", 100L, "wompi-2"));

        verify(jdbc).update(contains("'PAGO_REQUIERE_REVISION'"), any(SqlParameterSource.class));
        verifyNothingProvisioned();
    }

    @Test
    void verifiedPaymentProvisionsOnceWithActivationLinkAndTemplate() throws Exception {
        intention("PENDIENTE", 79900000L);
        plan(799000, "N");
        when(jdbc.update(contains("SET ESTADO = 'COMPLETADA'"), any(SqlParameterSource.class))).thenReturn(1);

        assertTrue(service.materializarOrganizacion("SAED-MEMBRESIA-ONB-abc", 79900000L, "wompi-1"));

        verify(jdbc).update(contains("INSERT INTO ORGANIZACIONES"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
        verify(tokens).generarYEnviarTokenActivacion(eq(902L), isNull());
        verify(templates).createDefaultTemplate(900L, 902L);
        verify(jdbc, never()).update(contains("UPDATE TRANSACCIONES_PAGO"), any(SqlParameterSource.class));
        verifyNoDestructiveOrOverwritingStatements();
    }

    @Test
    void completedIntentionIsIdempotent() throws Exception {
        intention("COMPLETADA", 79900000L);

        assertTrue(service.materializarOrganizacion("SAED-MEMBRESIA-ONB-abc", 79900000L, "wompi-1"));
        verifyNothingProvisioned();
        verify(tokens, never()).generarYEnviarTokenActivacion(anyLong(), any());
    }

    @Test
    void amountMismatchIsRecordedForRecovery() throws Exception {
        intention("PENDIENTE", 79900000L);

        assertFalse(service.materializarOrganizacion("SAED-MEMBRESIA-ONB-abc", 100L, "wompi-1"));
        recordedRecoveryReason();
        verifyNothingProvisioned();
    }
}
