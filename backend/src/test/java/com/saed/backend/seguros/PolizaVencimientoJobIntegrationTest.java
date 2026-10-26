package com.saed.backend.seguros;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.service.PolizaVencimientoScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * F10-08: the daily job discovers the tenants itself (PlatformPolizaTenantSource), runs each one in its own
 * property-scoped context and records one alert per policy and expiry period, even when it runs twice.
 * Database tier: the fixture policy is created and removed by this test.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PolizaVencimientoJobIntegrationTest {

    private static final String POLICY_NUMBER = "TEST-JOB-F1008-001";

    @Autowired
    private PolizaVencimientoScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createFixture() {
        platformContext();
        cleanFixture();
        jdbcTemplate.update("INSERT INTO POLIZAS_SEGURO (ID_PROPIEDAD, COMPANIA_ASEGURADORA, NUMERO_POLIZA, "
                + "RAMO_COBERTURA, VALOR_ASEGURADO, VALOR_PRIMA_ANUAL, FECHA_INICIO, FECHA_FIN, "
                + "DIAS_ALERTA_VENCIMIENTO, ESTADO) VALUES (1, 'Job Insurer', ?, 'TODO_RIESGO', 1000000, 10000, "
                + "TRUNC(SYSDATE) - 300, TRUNC(SYSDATE) + 5, 45, 'VIGENTE')", POLICY_NUMBER);
    }

    @AfterEach
    void removeFixture() {
        platformContext();
        cleanFixture();
        SaedContextHolder.clearContext();
    }

    @Test
    @DisplayName("F10-08: the job enumerates the tenant itself and an expiring policy yields exactly one alert across two runs")
    void oneExpiringPolicyProducesExactlyOneAlertEvenWhenTheJobRunsTwice() {
        SaedContextHolder.clearContext();

        scheduler.verificarYAlertarVencimientos();
        assertNull(SaedContextHolder.getContext(), "the job must leave no tenant context behind");
        scheduler.verificarYAlertarVencimientos();
        assertNull(SaedContextHolder.getContext(), "the job must leave no tenant context behind");

        platformContext();
        Long idPoliza = jdbcTemplate.queryForObject(
                "SELECT ID_POLIZA FROM POLIZAS_SEGURO WHERE NUMERO_POLIZA = ?", Long.class, POLICY_NUMBER);
        assertNotNull(idPoliza);
        Integer alerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ALERTAS_ADMIN WHERE TIPO_ALERTA = 'VENCIMIENTO_POLIZA' AND ID_POLIZA = ?",
                Integer.class, idPoliza);
        assertEquals(1, alerts, "exactly one alert per policy and expiry period");
        String state = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM POLIZAS_SEGURO WHERE ID_POLIZA = ?", String.class, idPoliza);
        assertEquals("POR_VENCER", state);
    }

    private void cleanFixture() {
        jdbcTemplate.update("DELETE FROM ALERTAS_ADMIN WHERE ID_POLIZA IN "
                + "(SELECT ID_POLIZA FROM POLIZAS_SEGURO WHERE NUMERO_POLIZA = ?)", POLICY_NUMBER);
        jdbcTemplate.update("DELETE FROM POLIZAS_SEGURO WHERE NUMERO_POLIZA = ?", POLICY_NUMBER);
    }

    private static void platformContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
    }
}
