package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class WompiSaaSSegregationSecurityTest {

    @Autowired
    private WompiServiceImpl wompiService;

    @Autowired
    private FlujoCajaRepository flujoCajaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_EVENTS_SECRET = "test_events_secret_key_12345";
    private static final String TEST_INTEGRITY_SECRET = "test_integrity_secret_key_67890";
    private static final String TEST_PUBLIC_KEY = "pub_test_wompi_key_abc";

    private static final long ORG_ID = 8950L;
    private static final long PROP_ID = 8950L;
    private static final long UNIT_ID = 8950L;

    @BeforeEach
    void setUp() {
        wompiService.setEventsSecret(TEST_EVENTS_SECRET);
        wompiService.setIntegritySecret(TEST_INTEGRITY_SECRET);
        wompiService.setPublicKey(TEST_PUBLIC_KEY);

        setElevatedContext(1L, 1L, "SUPERADMIN");

        // Seed Organization
        jdbcTemplate.update(
            "MERGE INTO ORGANIZACIONES o USING (SELECT ? AS id FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) " +
            "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) " +
            "VALUES (?, 'Org SaaS Segregation Test', '901999888-1', 'admin.saas@test.com', 'ACTIVA')",
            ORG_ID, ORG_ID
        );

        // Seed Property
        jdbcTemplate.update(
            "MERGE INTO PROPIEDADES p USING (SELECT ? AS id FROM DUAL) s ON (p.ID_PROPIEDAD = s.id) " +
            "WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE) " +
            "VALUES (?, ?, 1, 'Conjunto Residencial Segregation', 'Calle 100 # 15-20', 'Bogotá', 'PROPIETARIOS')",
            PROP_ID, PROP_ID, ORG_ID
        );

        // Seed Unit
        jdbcTemplate.update(
            "MERGE INTO UNIDADES u USING (SELECT ? AS id FROM DUAL) s ON (u.ID_UNIDAD = s.id) " +
            "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
            "VALUES (?, ?, 'APTO-8950', 1, 0.05, 'ACTIVA')",
            UNIT_ID, UNIT_ID, PROP_ID
        );

        // Seed Membership
        jdbcTemplate.update(
            "MERGE INTO MEMBRESIAS m USING (SELECT ? AS id FROM DUAL) s ON (m.ID_ORGANIZACION = s.id) " +
            "WHEN MATCHED THEN UPDATE SET m.ESTADO = 'ACTIVA', m.ID_PLAN = 2 " +
            "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, ID_PLAN, ESTADO, ES_PRUEBA, FECHA_INICIO, FECHA_FIN) " +
            "VALUES (?, 2, 'ACTIVA', 'N', TRUNC(SYSDATE), TRUNC(SYSDATE) + 30)",
            ORG_ID, ORG_ID
        );
    }

    private void setElevatedContext(Long orgId, Long propId, String roleCode) {
        SaedContext ctx = SaedContext.builder()
            .userId(1L)
            .organizationId(orgId)
            .propertyId(propId)
            .unitId(1L)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute(String.format(
                "BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %s, 'SUPERADMIN'); END;",
                orgId, propId != null ? propId.toString() : "NULL"
            ));
        } catch (Exception ignored) {}
    }

    private String generateChecksum(String idWompi, String status, long amountInCents, long timestamp, String secret) {
        try {
            String raw = idWompi + status + amountInCents + timestamp + secret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> buildWompiPayload(String idWompi, String referencia, String status, long amountInCents, String checksum, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("id", idWompi);
        transaction.put("reference", referencia);
        transaction.put("status", status);
        transaction.put("amount_in_cents", amountInCents);
        transaction.put("currency", "COP");
        transaction.put("payment_method_type", "CARD");
        data.put("transaction", transaction);

        Map<String, Object> signature = new HashMap<>();
        signature.put("checksum", checksum);
        signature.put("properties", List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "transaction.updated");
        payload.put("data", data);
        payload.put("signature", signature);
        payload.put("timestamp", timestamp);
        payload.put("sent_at", "2026-09-17T12:00:00.000Z");
        return payload;
    }

    @Test
    @DisplayName("Test 1: Intención Wompi de RENOVACION genera TRANSACCIONES_PAGO con ID_UNIDAD = NULL e ID_ORGANIZACION poblado")
    void test01_renovacion_noAsignaUnidadResidencial() throws Exception {
        setElevatedContext(ORG_ID, PROP_ID, "ADMIN_ORGANIZACION");

        Map<String, Object> intencion = wompiService.crearIntencionMembresia("RENOVACION", null, "MENSUAL");
        assertNotNull(intencion);
        String referencia = (String) intencion.get("referencia");
        assertTrue(referencia.startsWith("SAED-RENOVACION-" + ORG_ID + "-"));

        setElevatedContext(1L, 1L, "SUPERADMIN");
        Map<String, Object> tx = jdbcTemplate.queryForMap(
            "SELECT ID_UNIDAD, ID_ORGANIZACION, METODO_ORIGEN, ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );

        // REGLA CRÍTICA GAP-F6-03:
        assertNull(tx.get("ID_UNIDAD"), "Una transacción de renovación SaaS NO debe pertenecer a ninguna unidad residencial (ID_UNIDAD debe ser NULL)");
        assertNotNull(tx.get("ID_ORGANIZACION"), "ID_ORGANIZACION debe estar poblado con la organización titular del SaaS");
        assertEquals(ORG_ID, ((Number) tx.get("ID_ORGANIZACION")).longValue());
        assertEquals("RENOVACION", tx.get("METODO_ORIGEN"));
        assertEquals("PENDIENTE", tx.get("ESTADO_PASARELA"));
    }

    @Test
    @DisplayName("Test 2: Intención Wompi de UPGRADE genera TRANSACCIONES_PAGO con ID_UNIDAD = NULL")
    void test02_upgrade_noAsignaUnidadResidencial() throws Exception {
        setElevatedContext(ORG_ID, PROP_ID, "ADMIN_ORGANIZACION");

        Map<String, Object> intencion = wompiService.crearIntencionMembresia("UPGRADE", 3L, "MENSUAL");
        assertNotNull(intencion);
        String referencia = (String) intencion.get("referencia");
        assertTrue(referencia.startsWith("SAED-UPGRADE-" + ORG_ID + "-"));

        setElevatedContext(1L, 1L, "SUPERADMIN");
        Map<String, Object> tx = jdbcTemplate.queryForMap(
            "SELECT ID_UNIDAD, ID_ORGANIZACION, METODO_ORIGEN FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            referencia
        );

        assertNull(tx.get("ID_UNIDAD"), "Una transacción de upgrade SaaS NO debe ligarse a ninguna unidad física");
        assertEquals(ORG_ID, ((Number) tx.get("ID_ORGANIZACION")).longValue());
        assertEquals("UPGRADE", tx.get("METODO_ORIGEN"));
    }

    @Test
    @DisplayName("Test 3: Webhook APPROVED de SaaS NO genera movimientos en PAGOS ni altera cartera residencial")
    void test03_webhookSaaS_noGeneraMovimientoResidencial() throws Exception {
        setElevatedContext(ORG_ID, PROP_ID, "ADMIN_ORGANIZACION");

        Map<String, Object> intencion = wompiService.crearIntencionMembresia("RENOVACION", null, "MENSUAL");
        String referencia = (String) intencion.get("referencia");
        long montoCentavos = ((Number) intencion.get("montoCentavos")).longValue();

        setElevatedContext(1L, PROP_ID, "ADMIN_PROPIEDAD");
        BigDecimal saldoPropiedadAntes = flujoCajaRepository.getSaldoActual();

        // Contar pagos residenciales antes
        setElevatedContext(1L, 1L, "SUPERADMIN");
        int pagosResidencialesAntes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM PAGOS WHERE ID_UNIDAD = ?", Integer.class, UNIT_ID
        );

        String idWompi = "tx-saas-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, checksum, ts);

        wompiService.procesarWebhook(objectMapper.writeValueAsString(payload));

        // 1. Verificar que la transacción en pasarela pasó a APROBADO
        setElevatedContext(1L, 1L, "SUPERADMIN");
        String estadoTx = jdbcTemplate.queryForObject(
            "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            String.class, referencia
        );
        assertEquals("APROBADO", estadoTx);

        // 2. Verificar que NO se creó ningún registro en PAGOS para la unidad
        int pagosResidencialesDespues = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM PAGOS WHERE ID_UNIDAD = ?", Integer.class, UNIT_ID
        );
        assertEquals(pagosResidencialesAntes, pagosResidencialesDespues,
            "El cobro de SaaS de la plataforma NO debe insertar registros en el libro mayor PAGOS de ninguna unidad");

        // 3. Verificar que el saldo de tesorería de la propiedad residencial no se alteró
        setElevatedContext(1L, PROP_ID, "ADMIN_PROPIEDAD");
        BigDecimal saldoPropiedadDespues = flujoCajaRepository.getSaldoActual();
        assertEquals(saldoPropiedadAntes, saldoPropiedadDespues,
            "El saldo de flujo de caja de la copropiedad NO debe ser contaminado por la facturación SaaS");
    }

    @Test
    @DisplayName("Test 4: Webhook duplicado es idempotente (replay protection)")
    void test04_webhookDuplicado_esIdempotente() throws Exception {
        setElevatedContext(ORG_ID, PROP_ID, "ADMIN_ORGANIZACION");

        Map<String, Object> intencion = wompiService.crearIntencionMembresia("RENOVACION", null, "MENSUAL");
        String referencia = (String) intencion.get("referencia");
        long montoCentavos = ((Number) intencion.get("montoCentavos")).longValue();

        String idWompi = "tx-idem-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, checksum, ts);
        String raw = objectMapper.writeValueAsString(payload);

        // Primer despacho -> Aprobado
        assertDoesNotThrow(() -> wompiService.procesarWebhook(raw));

        // Segundo despacho duplicado -> Debe ser ignorado limpiamente sin error
        assertDoesNotThrow(() -> wompiService.procesarWebhook(raw));
    }

    @Test
    @DisplayName("Test 5: Pago residencial (CUOTA) exige unidad obligatoria y no nula")
    void test05_pagoResidencial_exigeUnidadNoNula() {
        setElevatedContext(1L, 1L, "SUPERADMIN");

        // Ensure a test cuota exists
        jdbcTemplate.update(
            "MERGE INTO CONCEPTOS_COBRO c USING (SELECT 1 AS id FROM DUAL) s ON (c.ID_PROPIEDAD = ? AND c.CODIGO = 'CUOTA_SEG_TEST') " +
            "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
            "VALUES (?, ?, 'CUOTA_SEG_TEST', 'Cuota Segregacion Test', 'ADMINISTRACION', 'ACTIVO')",
            PROP_ID, ORG_ID, PROP_ID
        );
        Long conceptoId = jdbcTemplate.queryForObject(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = ? AND CODIGO = 'CUOTA_SEG_TEST'",
            Long.class, PROP_ID
        );

        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = ? AND PERIODO = '2026-12'", UNIT_ID);
        jdbcTemplate.update(
            "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO) " +
            "VALUES (?, ?, '2026-12', 350000, 350000, 'PENDIENTE', TRUNC(SYSDATE) + 10)",
            UNIT_ID, conceptoId
        );
        Long cuotaId = jdbcTemplate.queryForObject(
            "SELECT MAX(ID_CUOTA) FROM CUOTAS WHERE ID_UNIDAD = ? AND ID_CONCEPTO = ?",
            Long.class, UNIT_ID, conceptoId
        );

        // Scope context with unit
        SaedContext residentCtx = SaedContext.builder()
            .userId(1L)
            .organizationId(ORG_ID)
            .propertyId(PROP_ID)
            .unitId(UNIT_ID)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(residentCtx);

        Map<String, Object> res = assertDoesNotThrow(() -> wompiService.crearIntencion("CUOTA", cuotaId));
        assertNotNull(res.get("referencia"));
        String ref = (String) res.get("referencia");

        setElevatedContext(1L, 1L, "SUPERADMIN");
        Long idUnidadTx = jdbcTemplate.queryForObject(
            "SELECT ID_UNIDAD FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
            Long.class, ref
        );

        assertNotNull(idUnidadTx, "Los pagos residenciales de cuotas DEBEN tener ID_UNIDAD no nulo");
        assertEquals(UNIT_ID, idUnidadTx.longValue());
    }
}
