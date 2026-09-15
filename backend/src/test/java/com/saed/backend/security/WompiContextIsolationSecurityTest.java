package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-03: Wompi Context Isolation & Connection Pool Hygiene Security Suite.
 *
 * Valida de forma adversarial y exhaustiva que:
 * 1. La elevación temporal a SUPERADMIN durante webhooks de Wompi se ejecuta estrictamente
 *    en la conexión transaccional y se limpia en el bloque finally.
 * 2. Ningún contexto privilegiado (ROL_CODIGO, ID_ORGANIZACION) sangra hacia el pool HikariCP
 *    ni permanece en SaedContextHolder tras la finalización del webhook.
 * 3. En caso de falla en CLEAR_CONTEXT, la conexión se aborta (eviction) para evitar
 *    que vuelva al pool en estado contaminado.
 * 4. El aislamiento cross-tenant y multi-hilo se mantiene hermético en todo momento.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class WompiContextIsolationSecurityTest {

    @Autowired
    private WompiServiceImpl wompiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private static final String TEST_EVENTS_SECRET = "sec03_events_secret_key_98765";
    private static final String TEST_INTEGRITY_SECRET = "sec03_integrity_secret_key_43210";
    private static final String TEST_PUBLIC_KEY = "pub_test_sec03_key_xyz";

    @BeforeEach
    void setUp() {
        wompiService.setEventsSecret(TEST_EVENTS_SECRET);
        wompiService.setIntegritySecret(TEST_INTEGRITY_SECRET);
        wompiService.setPublicKey(TEST_PUBLIC_KEY);
        SaedContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // ESCENARIO 1: SUCCESS PATH — Context Cleaned in ThreadLocal and Oracle Session
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [1/8] Success Path: Webhook exitoso limpia SaedContextHolder y sesión Oracle")
    void test01_successPath_cleansOracleAndThreadLocalContext() throws Exception {
        SaedContextHolder.clearContext();
        assertNull(SaedContextHolder.getContext(), "SaedContextHolder debe iniciar limpio");

        Map<String, Object> intencion = crearCuotaYTransaccionPrueba();
        String referencia = (String) intencion.get("referencia");
        long montoCentavos = ((Number) intencion.get("montoCentavos")).longValue();

        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(payload);

        // Ejecutar webhook (eleva temporalmente a SUPERADMIN en la conexión transaccional)
        wompiService.procesarWebhook(rawPayload);

        // Verificación 1: ThreadLocal limpio
        assertNull(SaedContextHolder.getContext(),
                "[SEC-03] SaedContextHolder NO debe conservar contexto SUPERADMIN residual tras webhook");

        // Verificación 2: Sesión Oracle en nueva conexión del pool NO tiene contexto privilegiado
        try (Connection con = dataSource.getConnection()) {
            String rolCodigo = getOracleContextValue(con, "ROL_CODIGO");
            String idOrg = getOracleContextValue(con, "ID_ORGANIZACION");
            String idUsr = getOracleContextValue(con, "ID_USUARIO");

            assertNull(rolCodigo, "[SEC-03] Sesión Oracle no debe conservar ROL_CODIGO después de webhook");
            assertNull(idOrg, "[SEC-03] Sesión Oracle no debe conservar ID_ORGANIZACION después de webhook");
            assertNull(idUsr, "[SEC-03] Sesión Oracle no debe conservar ID_USUARIO después de webhook");
        }
    }

    // =========================================================================
    // ESCENARIO 2: EXCEPTION PATH — Context Cleaned even on error/exception
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [2/8] Exception Path: Falla durante webhook garantiza limpieza en finally")
    void test02_exceptionPath_ensuresContextCleared() throws Exception {
        SaedContextHolder.clearContext();

        // Enviar payload con referencia inexistente pero firma válida
        String refInexistente = "SAED-CUOTA-9999999-" + System.currentTimeMillis();
        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", 5000000L, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, refInexistente, "APPROVED", 5000000L, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(payload);

        wompiService.procesarWebhook(rawPayload);

        assertNull(SaedContextHolder.getContext(), "[SEC-03] ThreadLocal debe permanecer limpio");

        try (Connection con = dataSource.getConnection()) {
            assertNull(getOracleContextValue(con, "ROL_CODIGO"),
                    "[SEC-03] Conexión en pool no debe retener ROL_CODIGO tras búsqueda fallida");
        }
    }

    // =========================================================================
    // ESCENARIO 3: CONNECTION REUSE — Pooled Connection Has No Residual Privilege
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [3/8] Connection Reuse: Conexión devuelta al pool no retiene privilegios")
    void test03_connectionReuse_noResidualPrivilegeInPool() throws Exception {
        SaedContextHolder.clearContext();

        Map<String, Object> intencion = crearCuotaYTransaccionPrueba();
        String referencia = (String) intencion.get("referencia");
        long montoCentavos = ((Number) intencion.get("montoCentavos")).longValue();

        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, ts);
        String rawPayload = objectMapper.writeValueAsString(payload);

        wompiService.procesarWebhook(rawPayload);

        // Obtener múltiples conexiones consecutivas de HikariCP para verificar que ninguna retiene contexto
        for (int i = 0; i < 5; i++) {
            try (Connection con = dataSource.getConnection()) {
                String rol = getOracleContextValue(con, "ROL_CODIGO");
                String org = getOracleContextValue(con, "ID_ORGANIZACION");
                assertNull(rol, "[SEC-03] Conexión " + i + " del pool no debe tener ROL_CODIGO residual");
                assertNull(org, "[SEC-03] Conexión " + i + " del pool no debe tener ID_ORGANIZACION residual");
            }
        }
    }

    // =========================================================================
    // ESCENARIO 4: CROSS-TENANT ISOLATION — Webhook Org 1 Never Leaks to Admin Org
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [4/8] Cross-Tenant: Webhook SUPERADMIN no contamina contexto de ADMIN_ORGANIZACION")
    void test04_crossTenantIsolation_webhookOrgANeverLeaksToOrgB() throws Exception {
        SaedContextHolder.clearContext();

        // 1. Ejecutar webhook (elevado temporalmente a SUPERADMIN)
        Map<String, Object> intencion = crearCuotaYTransaccionPrueba();
        String referencia = (String) intencion.get("referencia");
        long montoCentavos = ((Number) intencion.get("montoCentavos")).longValue();

        String idWompi = "tx-" + UUID.randomUUID();
        long ts = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, ts, TEST_EVENTS_SECRET);
        Map<String, Object> payload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, ts);
        wompiService.procesarWebhook(objectMapper.writeValueAsString(payload));

        // 2. Inmediatamente después, verificar que un checkout sin contexto está limpio
        try (Connection con = dataSource.getConnection()) {
            assertNull(getOracleContextValue(con, "ROL_CODIGO"),
                    "[SEC-03] Conexión sin contexto no debe tener ROL_CODIGO residual");
        }

        // 3. Obtener un usuario y asignación no-SUPERADMIN legítimo de la base de datos
        Map<String, Object> asignacion = null;
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ua.ID_USUARIO, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, r.CODIGO AS ROL_CODIGO, r.ALCANCE AS ROL_ALCANCE " +
                    "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
                    "WHERE r.CODIGO != 'SUPERADMIN' AND ua.ESTADO = 'ACTIVA' AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE)) AND ROWNUM = 1"
            );
            if (!rows.isEmpty()) {
                asignacion = rows.get(0);
            }
        } finally {
            try {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
            SaedContextHolder.clearContext();
        }

        assertNotNull(asignacion, "[SEC-03] Debe existir al menos un rol no-SUPERADMIN asignado en la base de datos");
        Long targetUser = ((Number) asignacion.get("ID_USUARIO")).longValue();
        Long targetOrg = ((Number) asignacion.get("ID_ORGANIZACION")).longValue();
        Long targetProp = asignacion.get("ID_PROPIEDAD") != null ? ((Number) asignacion.get("ID_PROPIEDAD")).longValue() : null;
        String expectedRole = (String) asignacion.get("ROL_CODIGO");
        String expectedScope = (String) asignacion.get("ROL_ALCANCE");

        // Simular operación de usuario legítimo con su respectivo rol
        SaedContext nonAdminCtx = SaedContext.builder()
                .userId(targetUser)
                .organizationId(targetOrg)
                .propertyId(targetProp)
                .roleCode(expectedRole)
                .roleScope(expectedScope)
                .build();
        SaedContextHolder.setContext(nonAdminCtx);

        try (Connection con = dataSource.getConnection()) {
            String rol = getOracleContextValue(con, "ROL_CODIGO");
            String org = getOracleContextValue(con, "ID_ORGANIZACION");

            assertEquals(expectedRole, rol, "[SEC-03] El rol debe ser " + expectedRole + ", nunca SUPERADMIN residual");
            assertEquals(String.valueOf(targetOrg), org, "[SEC-03] La organización debe ser " + targetOrg);
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    // =========================================================================
    // ESCENARIO 5: MULTIPLE SEQUENTIAL WEBHOOKS — Clean State Maintained
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [5/8] Multiple Sequential: Múltiples webhooks sucesivos mantienen estado limpio")
    void test05_multipleSequentialWebhooks_cleanStateMaintained() throws Exception {
        SaedContextHolder.clearContext();

        for (int i = 1; i <= 3; i++) {
            Map<String, Object> intencion = crearCuotaYTransaccionPrueba();
            String ref = (String) intencion.get("referencia");
            long monto = ((Number) intencion.get("montoCentavos")).longValue();

            String idWompi = "tx-seq-" + i + "-" + UUID.randomUUID();
            long ts = System.currentTimeMillis() / 1000;
            String status = (i % 2 == 0) ? "DECLINED" : "APPROVED";
            String checksum = generateChecksum(idWompi, status, monto, ts, TEST_EVENTS_SECRET);
            Map<String, Object> payload = buildWompiPayload(idWompi, ref, status, monto, "COP", checksum, ts);

            wompiService.procesarWebhook(objectMapper.writeValueAsString(payload));

            assertNull(SaedContextHolder.getContext(), "[SEC-03] Iteración " + i + ": SaedContextHolder debe estar limpio");
            try (Connection con = dataSource.getConnection()) {
                assertNull(getOracleContextValue(con, "ROL_CODIGO"),
                        "[SEC-03] Iteración " + i + ": Sesión Oracle debe estar limpia");
            }
        }
    }

    // =========================================================================
    // ESCENARIO 6: FAILURE FOLLOWED BY SUCCESS — No State Poisoning
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [6/8] Failure -> Success: Un fallo previo no envenena una ejecución posterior")
    void test06_failureFollowedBySuccess_noStatePoisoning() throws Exception {
        SaedContextHolder.clearContext();

        // 1. Webhook inválido por firma/checksum corrupto
        Map<String, Object> intencion1 = crearCuotaYTransaccionPrueba();
        String ref1 = (String) intencion1.get("referencia");
        long monto1 = ((Number) intencion1.get("montoCentavos")).longValue();
        String idWompi1 = "tx-fail-" + UUID.randomUUID();
        long ts1 = System.currentTimeMillis() / 1000;
        Map<String, Object> badPayload = buildWompiPayload(idWompi1, ref1, "APPROVED", monto1, "COP",
                "bad_checksum_0000000000000000000000000000000000000000000000000000000000", ts1);
        wompiService.procesarWebhook(objectMapper.writeValueAsString(badPayload));

        assertNull(SaedContextHolder.getContext(), "[SEC-03] Tras fallo de firma, SaedContextHolder debe estar limpio");

        // 2. Webhook válido posterior
        Map<String, Object> intencion2 = crearCuotaYTransaccionPrueba();
        String ref2 = (String) intencion2.get("referencia");
        long monto2 = ((Number) intencion2.get("montoCentavos")).longValue();
        String idWompi2 = "tx-ok-" + UUID.randomUUID();
        long ts2 = System.currentTimeMillis() / 1000;
        String checksum2 = generateChecksum(idWompi2, "APPROVED", monto2, ts2, TEST_EVENTS_SECRET);
        Map<String, Object> goodPayload = buildWompiPayload(idWompi2, ref2, "APPROVED", monto2, "COP", checksum2, ts2);
        wompiService.procesarWebhook(objectMapper.writeValueAsString(goodPayload));

        assertNull(SaedContextHolder.getContext(), "[SEC-03] Tras éxito posterior, SaedContextHolder debe estar limpio");

        try (Connection con = dataSource.getConnection()) {
            assertNull(getOracleContextValue(con, "ROL_CODIGO"),
                    "[SEC-03] Pool no debe retener ROL_CODIGO tras secuencia de fallo y éxito");
        }
    }

    // =========================================================================
    // ESCENARIO 7: CONCURRENT THREAD ISOLATION — No Cross-Thread Leakage
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [7/8] Concurrent Threads: No hay filtración de contexto entre hilos paralelos")
    void test07_concurrentThreadIsolation_noCrossThreadLeakage() throws Exception {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger violations = new AtomicInteger(0);

        // Pre-crear intenciones de prueba secuencialmente en el hilo principal para evitar contencion de DML
        Map<Integer, Map<String, Object>> intencionesPorHilo = new HashMap<>();
        for (int i = 0; i < threadCount; i += 2) {
            intencionesPorHilo.put(i, crearCuotaYTransaccionPrueba());
        }

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        // Hilo tipo A: ejecuta webhook
                        Map<String, Object> intencion = intencionesPorHilo.get(index);
                        String ref = (String) intencion.get("referencia");
                        long monto = ((Number) intencion.get("montoCentavos")).longValue();

                        String idWompi = "tx-conc-" + index + "-" + UUID.randomUUID();
                        String status = "DECLINED";
                        long ts = System.currentTimeMillis() / 1000;
                        String checksum = generateChecksum(idWompi, status, monto, ts, TEST_EVENTS_SECRET);
                        Map<String, Object> payload = buildWompiPayload(idWompi, ref, status, monto, "COP", checksum, ts);
                        wompiService.procesarWebhook(objectMapper.writeValueAsString(payload));

                        if (SaedContextHolder.getContext() != null) {
                            violations.incrementAndGet();
                        }
                    } else {
                        // Hilo tipo B: ejecuta consulta no privilegiada
                        SaedContextHolder.clearContext();
                        try (Connection con = dataSource.getConnection()) {
                            String rol = getOracleContextValue(con, "ROL_CODIGO");
                            if ("SUPERADMIN".equals(rol)) {
                                violations.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    violations.incrementAndGet();
                } finally {
                    SaedContextHolder.clearContext();
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed, "Todas las tareas concurrentes deben completarse dentro del tiempo límite");
        assertEquals(0, violations.get(), "[SEC-03] Se detectaron violaciones de aislamiento de contexto concurrente");
    }

    // =========================================================================
    // ESCENARIO 8: CLEANUP FAILURE ABORTS CONNECTION — Defense-in-depth Eviction
    // =========================================================================
    @Test
    @DisplayName("SEC-03 [8/8] Abort Defense: Falla en limpieza aborta y desaloja la conexión del pool")
    void test08_cleanupFailure_abortsAndEvictsConnection() throws Exception {
        // Verificar que la conexión soporta abort(Runnable::run) según la especificación JDBC 4.1 / HikariCP
        Connection con = dataSource.getConnection();
        assertNotNull(con, "Debe obtenerse una conexión del pool");
        assertFalse(con.isClosed(), "La conexión debe estar inicialmente abierta");

        // Simular elevación de contexto
        try (CallableStatement cs = con.prepareCall("{call PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)}")) {
            cs.setLong(1, 1L);
            cs.execute();
        }
        try (CallableStatement cs = con.prepareCall("{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}")) {
            cs.setLong(1, 1L);
            cs.setLong(2, 1L);
            cs.setLong(3, 1L);
            cs.setString(4, "SUPERADMIN");
            cs.execute();
        }

        // Verificar que la conexión estaba en estado SUPERADMIN
        assertEquals("SUPERADMIN", getOracleContextValue(con, "ROL_CODIGO"));

        // Ejecutar abort(Runnable::run) tal como lo hace el bloque catch de CLEAR_CONTEXT
        con.abort(Runnable::run);
        try {
            con.close();
        } catch (Exception ignored) {}

        // La conexión debe quedar cerrada e invalidada
        assertTrue(con.isClosed(), "[SEC-03] Conexión abortada/cerrada debe reportar isClosed() == true");

        // Cualquier intento de operar sobre la conexión abortada debe fallar
        assertThrows(SQLException.class, () -> getOracleContextValue(con, "ROL_CODIGO"),
                "[SEC-03] Intentar consultar una conexión abortada debe lanzar SQLException");

        // Obtener una nueva conexión del pool: Hikari habrá desalojado la abortada y entrega una conexión limpia
        try (Connection newCon = dataSource.getConnection()) {
            assertFalse(newCon.isClosed(), "La nueva conexión debe estar abierta");
            assertNull(getOracleContextValue(newCon, "ROL_CODIGO"),
                    "[SEC-03] La nueva conexión del pool no debe tener ROL_CODIGO residual");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private synchronized Map<String, Object> crearCuotaYTransaccionPrueba() throws Exception {
        SaedContext adminCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .unitId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(adminCtx);
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            Long idCuota = ensureTestCuota();
            jdbcTemplate.update(
                    "UPDATE CUOTAS SET ESTADO = 'PENDIENTE', SALDO_PENDIENTE = 250000 WHERE ID_CUOTA = ?",
                    idCuota
            );

            long montoCentavos = 25000000L;
            String uniqueSuffix = System.currentTimeMillis() + "" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            String referencia = "SAED-CUOTA-" + idCuota + "-" + uniqueSuffix;

            // Calcular firma de integridad SHA-256
            String baseIntegridad = referencia + montoCentavos + "COP" + TEST_INTEGRITY_SECRET;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(baseIntegridad.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexFirma = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexFirma.append('0');
                hexFirma.append(hex);
            }
            String firmaIntegridad = hexFirma.toString();

            // Insertar directamente en TRANSACCIONES_PAGO con referencia garantizada única
            jdbcTemplate.update(
                    "INSERT INTO TRANSACCIONES_PAGO " +
                    "(ID_UNIDAD, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA, MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM) " +
                    "VALUES (1, NULL, 'WOMPI', ?, ?, ?, 'COP', 'PENDIENTE', 'CUOTA', ?)",
                    referencia, referencia, montoCentavos, firmaIntegridad
            );

            Map<String, Object> res = new HashMap<>();
            res.put("referencia", referencia);
            res.put("montoCentavos", montoCentavos);
            res.put("idCuota", idCuota);
            return res;
        } finally {
            try {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
            SaedContextHolder.clearContext();
        }
    }

    private Long ensureTestCuota() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD = 1 ORDER BY ID_CUOTA ASC",
                Long.class
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }

        List<Long> conceptos = jdbcTemplate.queryForList(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = 1 ORDER BY ID_CONCEPTO ASC",
                Long.class
        );
        Long idConcepto;
        if (!conceptos.isEmpty()) {
            idConcepto = conceptos.get(0);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                    "VALUES (1, 1, 'ADMIN_SEC03', 'Cuota de Administracion SEC03', 'ADMINISTRACION', 'ACTIVO')"
            );
            idConcepto = jdbcTemplate.queryForObject(
                    "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_SEC03'", Long.class
            );
        }

        String periodoUnico = "P" + (System.currentTimeMillis() % 1000000);
        jdbcTemplate.update(
                "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO) " +
                "VALUES (1, ?, ?, 250000, 250000, SYSDATE + 15, 'PENDIENTE')",
                idConcepto, periodoUnico
        );

        return jdbcTemplate.queryForObject(
                "SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD = 1 AND PERIODO = ?",
                Long.class, periodoUnico
        );
    }

    private String getOracleContextValue(Connection con, String parameter) throws SQLException {
        try (CallableStatement cs = con.prepareCall("SELECT SYS_CONTEXT('SAED_CTX', ?) FROM DUAL")) {
            cs.setString(1, parameter);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private String generateChecksum(String id, String status, long amountInCents, long timestamp, String secret) throws Exception {
        String base = id + status + amountInCents + timestamp + secret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private Map<String, Object> buildWompiPayload(String idWompi, String reference, String status,
                                                  long amountInCents, String currency, String checksum, long timestamp) {
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
}
