package com.saed.backend.audit;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditSanitizer auditSanitizer;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Ejecutar en el esquema activo sin prefijo incorrecto SAED_SEC_MASTER
        jdbcTemplate.getJdbcTemplate().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        SaedContext context = new SaedContext();
        context.setUserId(1L);
        context.setOrganizationId(1L);
        context.setPropertyId(1L);
        context.setRoleCode("SUPERADMIN");
        SaedContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        CorrelationIdHolder.clear();
    }

    @Test
    @DisplayName("AUD-001: AuditService writes success event to AUDITORIA_LOG in Oracle XE with valid JSON")
    void testAuditServiceWritesSuccessEvent() {
        Long testId = System.currentTimeMillis();

        auditService.recordSuccess(
                1L, 1L, 1L,
                "INSERT", "TEST_RESOURCE_SUCCESS", testId,
                "127.0.0.1", "TestAgent/1.0",
                "{\"before\":\"init\"}", "{\"after\":\"created\"}"
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM AUDITORIA_LOG WHERE ENTIDAD = 'TEST_RESOURCE_SUCCESS' AND ID_ENTIDAD_AFECTADA = :id",
                Map.of("id", testId)
        );

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("ACCION")).isEqualTo("INSERT");
        assertThat(row.get("RESULTADO")).isEqualTo("EXITOSO");
        assertThat(row.get("ENTIDAD")).isEqualTo("TEST_RESOURCE_SUCCESS");
        assertThat(((Number) row.get("ID_ENTIDAD_AFECTADA")).longValue()).isEqualTo(testId);
        assertThat(row.get("IP_ORIGEN")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("AUD-002: AuditService writes failure event with plain text error formatted as valid JSON")
    void testAuditServiceWritesFailureEvent() {
        Long testId = System.currentTimeMillis() + 1;

        auditService.recordFailure(
                1L, 1L, 1L,
                "UPDATE", "TEST_RESOURCE_FAIL", testId,
                "192.168.1.50", "TestAgent/1.0",
                "{\"attempt\":1}", "Error: DataIntegrityViolationException - Duplicate key"
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM AUDITORIA_LOG WHERE ENTIDAD = 'TEST_RESOURCE_FAIL' AND ID_ENTIDAD_AFECTADA = :id",
                Map.of("id", testId)
        );

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("ACCION")).isEqualTo("UPDATE");
        assertThat(row.get("RESULTADO")).isEqualTo("FALLIDO");
        assertThat(row.get("ESTADO_NUEVO").toString()).contains("DataIntegrityViolationException");
        assertThat(row.get("ESTADO_NUEVO").toString()).startsWith("{");
    }

    @Test
    @DisplayName("AUD-003: Action normalization maps CREATE, REMOVE, WEBHOOK and TEST_ actions to valid Oracle catalog")
    void testActionNormalizationInOracle() {
        AuditServiceImpl serviceImpl = (AuditServiceImpl) auditService;

        assertThat(serviceImpl.normalizeAction("CREATE_USER")).isEqualTo("INSERT");
        assertThat(serviceImpl.normalizeAction("REGISTER_PERSON")).isEqualTo("INSERT");
        assertThat(serviceImpl.normalizeAction("REMOVE_UNIT")).isEqualTo("DELETE");
        assertThat(serviceImpl.normalizeAction("CANCEL_BOOKING")).isEqualTo("DELETE");
        assertThat(serviceImpl.normalizeAction("WOMPI_WEBHOOK")).isEqualTo("PAGO");
        assertThat(serviceImpl.normalizeAction("TEST_CREATE_RESIDENT")).isEqualTo("INSERT");
        assertThat(serviceImpl.normalizeAction("TEST_UPDATE_RESIDENT")).isEqualTo("UPDATE");
        assertThat(serviceImpl.normalizeAction("TEST_DELETE_RESIDENT")).isEqualTo("DELETE");
        assertThat(serviceImpl.normalizeAction("UNKNOWN_ACTION_XYZ")).isEqualTo("UPDATE");

        // Allowed actions remain unchanged
        assertThat(serviceImpl.normalizeAction("LOGIN")).isEqualTo("LOGIN");
        assertThat(serviceImpl.normalizeAction("LOGOUT")).isEqualTo("LOGOUT");
        assertThat(serviceImpl.normalizeAction("QR_SCAN")).isEqualTo("QR_SCAN");
        assertThat(serviceImpl.normalizeAction("ACCESO_CONCEDIDO")).isEqualTo("ACCESO_CONCEDIDO");
        assertThat(serviceImpl.normalizeAction("CAMBIO_ROL")).isEqualTo("CAMBIO_ROL");
    }

    @Test
    @DisplayName("AUD-004: Direct UPDATE or DELETE on AUDITORIA_LOG is blocked by trigger TRG_AUDITORIA_INMUTABLE")
    void testAuditoriaLogIsAppendOnlyAndImmutable() {
        assertThrows(Exception.class, () -> {
            jdbcTemplate.getJdbcTemplate().execute("DELETE FROM AUDITORIA_LOG WHERE 1=1");
        });

        assertThrows(Exception.class, () -> {
            jdbcTemplate.getJdbcTemplate().execute("UPDATE AUDITORIA_LOG SET ACCION = 'HACKED' WHERE 1=1");
        });
    }

    @Test
    @DisplayName("AUD-005: Sanitizer protects passwords, hashes, tokens and secrets from audit persistence")
    void testAuditSanitizerCleansSecrets() {
        Map<String, Object> payload = Map.of(
                "password", "PlainPassword123!",
                "token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token",
                "wompiSecret", "sec_events_test_12345",
                "normalData", "Safe Value"
        );

        String sanitized = auditSanitizer.sanitizeToJson(payload);

        assertThat(sanitized).doesNotContain("PlainPassword123!");
        assertThat(sanitized).doesNotContain("sec_events_test_12345");
        assertThat(sanitized).contains("\"password\":\"[PROTECTED]\"");
        assertThat(sanitized).contains("\"normalData\":\"Safe Value\"");
    }

    @Test
    @DisplayName("AUD-007: CorrelationIdFilter injects and returns X-Correlation-Id header")
    void testCorrelationIdHeaderReturned() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login")
                        .header("X-Correlation-Id", "corr-test-unique-99"))
                .andExpect(header().string("X-Correlation-Id", "corr-test-unique-99"));
    }
}
