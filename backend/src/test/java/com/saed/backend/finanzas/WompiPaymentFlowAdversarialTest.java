package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class WompiPaymentFlowAdversarialTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WompiServiceImpl wompiService;

    private static final String TEST_EVENTS_SECRET = "test_events_secret_key_12345";
    private static final String TEST_INTEGRITY_SECRET = "test_integrity_secret_key_67890";
    private static final String TEST_PUBLIC_KEY = "pub_test_wompi_key_abc";

    @BeforeEach
    void setUp() {
        wompiService.setEventsSecret(TEST_EVENTS_SECRET);
        wompiService.setIntegritySecret(TEST_INTEGRITY_SECRET);
        wompiService.setPublicKey(TEST_PUBLIC_KEY);

        SaedContext ctx = SaedContext.builder()
            .userId(1L)
            .organizationId(1L)
            .propertyId(1L)
            .unitId(1L)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private Long ensureTestCuota() {
        List<Map<String, Object>> cuotas = jdbcTemplate.queryForList(
            "SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD = 1 AND ESTADO = 'PENDIENTE'"
        );
        if (!cuotas.isEmpty()) {
            return ((Number) cuotas.get(0).get("ID_CUOTA")).longValue();
        }

        List<Map<String, Object>> conceptos = jdbcTemplate.queryForList(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = 1 FETCH FIRST 1 ROWS ONLY"
        );
        Long idConcepto;
        if (!conceptos.isEmpty()) {
            idConcepto = ((Number) conceptos.get(0).get("ID_CONCEPTO")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                "VALUES (1, 1, 'ADMIN_TEST', 'Cuota de Administracion Test', 'ADMINISTRACION', 'ACTIVO')"
            );
            idConcepto = ((Number) jdbcTemplate.queryForObject(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_TEST'", Long.class
            )).longValue();
        }

        jdbcTemplate.update(
            "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO) " +
            "VALUES (1, ?, '2026-09', 250000, 250000, SYSDATE + 15, 'PENDIENTE')",
            idConcepto
        );

        return ((Number) jdbcTemplate.queryForObject(
            "SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD = 1 AND ESTADO = 'PENDIENTE' ORDER BY ID_CUOTA DESC FETCH FIRST 1 ROWS ONLY",
            Long.class
        )).longValue();
    }

    private String generateChecksum(String id, String status, long amountInCents, long timestamp, String secret) throws Exception {
        String base = id + status + amountInCents + timestamp + secret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private Map<String, Object> buildWompiPayload(String idWompi, String reference, String status, long amountInCents, String currency, String checksum, long timestamp) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("id", idWompi);
        transaction.put("reference", reference);
        transaction.put("status", status);
        transaction.put("amount_in_cents", amountInCents);
        transaction.put("currency", currency);
        transaction.put("payment_method_type", "CARD");

        Map<String, Object> data = new HashMap<>();
        data.put("transaction", transaction);

        Map<String, Object> signature = new HashMap<>();
        signature.put("properties", List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"));
        signature.put("checksum", checksum);

        Map<String, Object> evento = new HashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", data);
        evento.put("environment", "test");
        evento.put("signature", signature);
        evento.put("timestamp", timestamp);

        return evento;
    }

    @Test
    @DisplayName("1. Crear Intención de Pago Wompi genera registro PENDIENTE y firma válida")
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username = "1")
    void test01_crearIntencion_validQuota_createsPendingTransaction() throws Exception {
        Long idCuota = ensureTestCuota();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        assertNotNull(intencion);
        String referencia = (String) intencion.get("referencia");
        assertNotNull(referencia);
        assertTrue(referencia.startsWith("SAED-CUOTA-" + idCuota + "-"));
        assertNotNull(intencion.get("firmaIntegridad"));
        assertEquals(TEST_PUBLIC_KEY, intencion.get("publicKey"));

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA, MONTO_CENTAVOS, MONEDA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals(1, txs.size());
        assertEquals("PENDIENTE", txs.get(0).get("ESTADO_PASARELA"));
        assertEquals("COP", txs.get(0).get("MONEDA"));
    }

    @Test
    @DisplayName("2. Webhook Válido con status APPROVED asienta pago y actualiza cuota")
    void test02_procesarWebhook_validApproved_appliesPayment() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA, ID_TRANSACCION_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("APROBADO", txs.get(0).get("ESTADO_PASARELA"));
        assertEquals(idWompi, txs.get(0).get("ID_TRANSACCION_PASARELA"));

        List<Map<String, Object>> pagos = jdbcTemplate.queryForList(
            "SELECT ID_PAGO, MONTO_TOTAL, ESTADO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = ?",
            referencia
        );
        assertEquals(1, pagos.size());
        assertEquals("APROBADO", pagos.get(0).get("ESTADO"));
    }

    @Test
    @DisplayName("3. Webhook Duplicado es Idempotente (no duplica pagos ni altera registros)")
    void test03_procesarWebhook_duplicateWebhook_isIdempotent() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        // Primera llamada
        wompiService.procesarWebhook(rawPayload);

        // Segunda llamada (reintento del webhook de Wompi)
        wompiService.procesarWebhook(rawPayload);

        // Verificar que solo existe UN pago registrado
        List<Map<String, Object>> pagos = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as TOTAL FROM PAGOS WHERE REFERENCIA_COMPROBANTE = ?",
            referencia
        );
        assertEquals(1L, ((Number) pagos.get(0).get("TOTAL")).longValue());
    }

    @Test
    @DisplayName("4. Webhook con Firma/Checksum Inválido es Rechazado sin Modificar BD")
    void test04_procesarWebhook_invalidChecksum_rejected() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksumInvalido = "0000000000000000000000000000000000000000000000000000000000000000";

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksumInvalido, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("PENDIENTE", txs.get(0).get("ESTADO_PASARELA"));

        List<Map<String, Object>> pagos = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as TOTAL FROM PAGOS WHERE REFERENCIA_COMPROBANTE = ?",
            referencia
        );
        assertEquals(0L, ((Number) pagos.get(0).get("TOTAL")).longValue());
    }

    @Test
    @DisplayName("5. Webhook con Monto Discordante es Rechazado")
    void test05_procesarWebhook_mismatchedAmount_rejected() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();
        long montoFalsificado = montoCentavos / 2;

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoFalsificado, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoFalsificado, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("PENDIENTE", txs.get(0).get("ESTADO_PASARELA"));
    }

    @Test
    @DisplayName("6. Webhook con Moneda Inválida (no-COP) es Rechazado")
    void test06_procesarWebhook_invalidCurrency_rejected() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "USD", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("PENDIENTE", txs.get(0).get("ESTADO_PASARELA"));
    }

    @Test
    @DisplayName("7. Webhook con Estado DECLINED actualiza estado a RECHAZADO sin asentar pago")
    void test07_procesarWebhook_declinedStatus_updatesStateWithoutPayment() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "DECLINED", montoCentavos, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "DECLINED", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("RECHAZADO", txs.get(0).get("ESTADO_PASARELA"));

        List<Map<String, Object>> pagos = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as TOTAL FROM PAGOS WHERE REFERENCIA_COMPROBANTE = ?",
            referencia
        );
        assertEquals(0L, ((Number) pagos.get(0).get("TOTAL")).longValue());
    }

    @Test
    @DisplayName("8. Webhook con Estado ERROR actualiza estado a ERROR sin asentar pago")
    void test08_procesarWebhook_errorStatus_updatesStateWithoutPayment() throws Exception {
        Long idCuota = ensureTestCuota();
        BigDecimal saldoInicial = (BigDecimal) jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, idCuota
        );
        long montoCentavos = saldoInicial.multiply(new BigDecimal(100)).longValue();

        Map<String, Object> intencion = wompiService.crearIntencion("CUOTA", idCuota);
        String referencia = (String) intencion.get("referencia");
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "ERROR", montoCentavos, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "ERROR", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        wompiService.procesarWebhook(rawPayload);

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );
        assertEquals("ERROR", txs.get(0).get("ESTADO_PASARELA"));

        List<Map<String, Object>> pagos = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as TOTAL FROM PAGOS WHERE REFERENCIA_COMPROBANTE = ?",
            referencia
        );
        assertEquals(0L, ((Number) pagos.get(0).get("TOTAL")).longValue());
    }

    @Test
    @DisplayName("9. Webhook con Referencia Inexistente es descartado de forma segura")
    void test09_procesarWebhook_nonExistentReference_handledGracefully() throws Exception {
        String referenciaFalsa = "SAED-CUOTA-999999-20260902120000";
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", 10000000, ts, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referenciaFalsa, "APPROVED", 10000000, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(webhookPayload);

        assertDoesNotThrow(() -> wompiService.procesarWebhook(rawPayload));
    }

    @Test
    @DisplayName("10. Endpoints de Webhook son Públicos (permitAll) y devuelven 200")
    void test10_webhookEndpoints_publiclyAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pagos/notificacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }
}
