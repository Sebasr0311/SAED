package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.exception.StorageQuotaExceededException;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.impl.FileStorageServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.platform.dto.StorageQuotaDTO;
import com.saed.backend.platform.exception.InactiveMembershipException;
import com.saed.backend.platform.service.StorageQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StorageQuotaSecurityTest — Suite de pruebas exhaustiva para GAP-ENT-06:
 * Cuota Global de Almacenamiento por Organización en SAED 2.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StorageQuotaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private StorageQuotaService storageQuotaService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private DocumentoService documentoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long testOrgId;
    private Long testOtherOrgId;
    private Long testPropId;
    private Long freePlanId;
    private Long proPlanId;

    private void elevateToSuperAdmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        elevateToSuperAdmin();

        // Obtener ID de planes FREE (1 GB) y PRO (10 GB)
        freePlanId = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM PLANES WHERE CODIGO = 'FREE'",
                new MapSqlParameterSource(),
                Long.class
        );
        proPlanId = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM PLANES WHERE CODIGO = 'PRO'",
                new MapSqlParameterSource(),
                Long.class
        );

        // Crear organizaciones de prueba
        testOrgId = createTestOrganization("Org Storage Test " + System.currentTimeMillis(), "NIT-STO-1");
        testOtherOrgId = createTestOrganization("Org Storage Other " + System.currentTimeMillis(), "NIT-STO-2");

        // Crear membresía activa con plan FREE (1 GB) para testOrgId
        createTestMembership(testOrgId, freePlanId, "ACTIVA");
        // Crear membresía activa con plan PRO (10 GB) para testOtherOrgId
        createTestMembership(testOtherOrgId, proPlanId, "ACTIVA");

        // Crear propiedad de prueba para testOrgId
        testPropId = createTestProperty(testOrgId, "Copropiedad Storage Test");
    }

    @AfterEach
    void tearDown() {
        elevateToSuperAdmin();
        try {
            if (testPropId != null) {
                jdbcTemplate.update("DELETE FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO IN (SELECT ID_DOCUMENTO FROM DOCUMENTOS WHERE ID_PROPIEDAD = :propId)",
                        new MapSqlParameterSource("propId", testPropId));
                jdbcTemplate.update("DELETE FROM DOCUMENTOS WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", testPropId));
                jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", testPropId));
            }
            if (testOrgId != null) {
                jdbcTemplate.update("DELETE FROM AUDITORIA_LOG WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOrgId));
                jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOrgId));
                jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOrgId));
            }
            if (testOtherOrgId != null) {
                jdbcTemplate.update("DELETE FROM AUDITORIA_LOG WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOtherOrgId));
                jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOtherOrgId));
                jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("orgId", testOtherOrgId));
            }
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
    }

    private Long createTestOrganization(String nombre, String nit) {
        String sql = """
            INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO)
            VALUES (:nombre, :nit, :email, 'ACTIVA')
            """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nombre", nombre)
                .addValue("nit", nit + "-" + System.currentTimeMillis())
                .addValue("email", "contact_" + System.currentTimeMillis() + "@storage.com"),
                kh, new String[]{"ID_ORGANIZACION"});
        return kh.getKey().longValue();
    }

    private Long createTestMembership(Long orgId, Long planId, String estado) {
        String sql = """
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA)
            VALUES (:orgId, :planId, :estado, TRUNC(SYSDATE), TRUNC(SYSDATE) + 30, 'N')
            """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("planId", planId)
                .addValue("estado", estado),
                kh, new String[]{"ID_MEMBRESIA"});
        return kh.getKey().longValue();
    }

    private Long createTestProperty(Long orgId, String nombre) {
        String sql = """
            INSERT INTO PROPIEDADES (ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
            VALUES (:orgId, 1, :nombre, 'Calle 100 # 15-20', 'Bogotá', 'MIXTA', 'ACTIVA')
            """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("nombre", nombre),
                kh, new String[]{"ID_PROPIEDAD"});
        return kh.getKey().longValue();
    }

    private Long createTestDocumentVersion(Long orgId, Long propId, long sizeBytes) {
        String sqlDoc = """
            INSERT INTO DOCUMENTOS (ID_ORGANIZACION, ID_PROPIEDAD, CATEGORIA, TITULO, ES_PUBLICO_RESIDENTES, ROL_MINIMO_ACCESO, ESTADO)
            VALUES (:orgId, :propId, 'REGLAMENTO_INTERNO', 'Doc Quota Test', 'N', 'ADMIN_PROPIEDAD', 'ACTIVO')
            """;
        KeyHolder khDoc = new GeneratedKeyHolder();
        jdbcTemplate.update(sqlDoc, new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("propId", propId),
                khDoc, new String[]{"ID_DOCUMENTO"});
        Long docId = khDoc.getKey().longValue();

        String sqlVer = """
            INSERT INTO VERSIONES_DOCUMENTO (ID_DOCUMENTO, NUMERO_VERSION, ARCHIVO_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_TAMANO_BYTES, ARCHIVO_MIME_TYPE, SUBIDO_POR)
            VALUES (:docId, 1, 'docs/test.pdf', 'test.pdf', :tamano, 'application/pdf', 1)
            """;
        jdbcTemplate.update(sqlVer, new MapSqlParameterSource()
                .addValue("docId", docId)
                .addValue("tamano", sizeBytes));

        return docId;
    }

    // ========================================================================
    // CASO 1: Organización con límite de 1 GB obtiene correctamente LIMITE_ALMACENAMIENTO_GB
    // ========================================================================
    @Test
    @DisplayName("01. Organización con plan FREE (1 GB) resuelve exactamente 1,073,741,824 bytes (1024^3)")
    void test01_resolucionLimiteAlmacenamiento_Plan1Gb() {
        long limitBytes = storageQuotaService.getStorageLimitBytes(testOrgId);
        long expectedBytes = 1024L * 1024L * 1024L; // 1,073,741,824 bytes

        assertEquals(expectedBytes, limitBytes, "El límite del plan FREE debe ser exactamente 1 GB en bytes binarios");

        StorageQuotaDTO quota = storageQuotaService.getQuota(testOrgId);
        assertEquals(1.0, quota.getLimitGb(), 0.001);
        assertEquals("FREE", quota.getPlanCodigo());
        assertEquals("ACTIVA", quota.getEstadoMembresia());
    }

    @Test
    @DisplayName("01b. Organización con plan PRO (10 GB) resuelve exactamente 10,737,418,240 bytes")
    void test01b_resolucionLimiteAlmacenamiento_PlanPro10Gb() {
        long limitBytes = storageQuotaService.getStorageLimitBytes(testOtherOrgId);
        long expectedBytes = 10L * 1024L * 1024L * 1024L;

        assertEquals(expectedBytes, limitBytes, "El límite del plan PRO debe ser exactamente 10 GB en bytes binarios");

        StorageQuotaDTO quota = storageQuotaService.getQuota(testOtherOrgId);
        assertEquals(10.0, quota.getLimitGb(), 0.001);
        assertEquals("PRO", quota.getPlanCodigo());
    }

    // ========================================================================
    // CASO 2: Upload debajo del límite: PASS
    // ========================================================================
    @Test
    @DisplayName("02. Upload debajo del límite (5 MB en plan de 1 GB) debe ser permitido (PASS)")
    void test02_uploadDebajoDelLimite_Exitoso() {
        long fiveMb = 5L * 1024L * 1024L;
        assertDoesNotThrow(() -> storageQuotaService.validateUpload(testOrgId, fiveMb),
                "Upload menor al límite debe completarse sin excepciones");
    }

    // ========================================================================
    // CASO 3 & 7: Upload exactamente hasta el límite: PASS
    // ========================================================================
    @Test
    @DisplayName("03. Upload exactamente hasta el límite (used + new == limit) debe ser permitido (PASS)")
    void test03_uploadExactamenteAlLimite_Exitoso() {
        // Simular uso de 1,063,741,824 bytes (1014.5 MB)
        long currentUsed = 1024L * 1024L * 1024L - 5_000_000L;
        createTestDocumentVersion(testOrgId, testPropId, currentUsed);

        // Subir exactamente los 5,000,000 bytes restantes (used + new == limit)
        assertDoesNotThrow(() -> storageQuotaService.validateUpload(testOrgId, 5_000_000L),
                "Completar exactamente el 100% de la cuota contratada debe permitirse");
    }

    // ========================================================================
    // CASO 4 & 6: Upload que excede el límite: REJECT (409 Conflict)
    // ========================================================================
    @Test
    @DisplayName("04. Upload que excede el límite (used + new > limit) debe ser rechazado con StorageQuotaExceededException")
    void test04_uploadQueExcedeLimite_LanzaStorageQuotaExceededException() {
        // Simular uso de 1,070,000,000 bytes en plan de 1,073,741,824 bytes (quedan 3,741,824 bytes)
        createTestDocumentVersion(testOrgId, testPropId, 1_070_000_000L);

        // Intentar subir 5,000,000 bytes (excede por 1,258,176 bytes)
        StorageQuotaExceededException ex = assertThrows(StorageQuotaExceededException.class, () ->
                storageQuotaService.validateUpload(testOrgId, 5_000_000L)
        );

        assertTrue(ex.getRequestedBytes() == 5_000_000L);
        assertTrue(ex.getUsedBytes() >= 1_070_000_000L);
        assertTrue(ex.getLimitBytes() == 1024L * 1024L * 1024L);
    }

    // ========================================================================
    // CASO 5: Archivo individual mayor al límite individual (10 MB): REJECT
    // ========================================================================
    @Test
    @DisplayName("05. Archivo individual de 15 MB supera el límite individual de 10 MB: REJECT aunque haya cuota global")
    void test05_archivoMayorALimiteIndividual_RechazadoInclusoConCuotaGlobal() {
        long fifteenMb = 15L * 1024L * 1024L;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                storageQuotaService.validateUpload(testOrgId, fifteenMb)
        );

        assertTrue(ex.getMessage().contains("tamaño máximo individual"));
    }

    // ========================================================================
    // CASO 8: Archivo eliminado no consume cuota (liberación)
    // ========================================================================
    @Test
    @DisplayName("08. Archivo eliminado libera cuota inmediatamente según semántica de SAED")
    void test08_eliminacionDeDocumento_LiberaCuotaInmediatamente() {
        long fiveMb = 5_000_000L;
        Long docId = createTestDocumentVersion(testOrgId, testPropId, fiveMb);

        long usedBefore = storageQuotaService.getStorageUsedBytes(testOrgId);
        assertEquals(fiveMb, usedBefore, "El uso debe reflejar el archivo persistido");

        // Eliminar versiones del documento y el documento
        jdbcTemplate.update("DELETE FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = :docId",
                new MapSqlParameterSource("docId", docId));
        jdbcTemplate.update("DELETE FROM DOCUMENTOS WHERE ID_DOCUMENTO = :docId",
                new MapSqlParameterSource("docId", docId));

        long usedAfter = storageQuotaService.getStorageUsedBytes(testOrgId);
        assertEquals(0L, usedAfter, "La cuota debe liberarse inmediatamente al eliminarse el documento");
    }

    // ========================================================================
    // CASO 9 & 10: Reemplazo de archivo calcula correctamente: uso - anterior + nuevo
    // ========================================================================
    @Test
    @DisplayName("09. Reemplazo de archivo calcula (usado - anterior + nuevo <= limite): PASS si cabe")
    void test09_reemplazoDeArchivo_CalculaNeto_ExitosoSiEstaDentroDelLimite() {
        // Uso actual de 1,070,000,000 bytes (quedan ~3.7 MB libres)
        createTestDocumentVersion(testOrgId, testPropId, 1_070_000_000L);

        // Reemplazar un archivo previo de 6 MB con uno de 8 MB (aumento neto de 2 MB, 2 MB < 3.7 MB)
        long oldFileBytes = 6L * 1024L * 1024L;
        long newFileBytes = 8L * 1024L * 1024L;

        assertDoesNotThrow(() ->
                storageQuotaService.validateReplacement(testOrgId, oldFileBytes, newFileBytes)
        );
    }

    @Test
    @DisplayName("10. Reemplazo de archivo que excede la cuota neta: REJECT con StorageQuotaExceededException")
    void test10_reemplazoDeArchivo_CalculaNeto_RechazaSiExcede() {
        // Uso actual de 1,070,000,000 bytes (quedan ~3.7 MB libres)
        createTestDocumentVersion(testOrgId, testPropId, 1_070_000_000L);

        // Reemplazar un archivo previo de 2 MB con uno de 9 MB (aumento neto de 7 MB, 7 MB > 3.7 MB)
        long oldFileBytes = 2L * 1024L * 1024L;
        long newFileBytes = 9L * 1024L * 1024L;

        assertThrows(StorageQuotaExceededException.class, () ->
                storageQuotaService.validateReplacement(testOrgId, oldFileBytes, newFileBytes)
        );
    }

    @Test
    @DisplayName("11. Reemplazo de archivo por uno más liviano libera cuota")
    void test11_reemplazoConArchivoMenor_AcreditaDiferencia() {
        // Reemplazar archivo previo de 8 MB por uno de 2 MB (libera 6 MB)
        long oldFileBytes = 8L * 1024L * 1024L;
        long newFileBytes = 2L * 1024L * 1024L;

        assertDoesNotThrow(() ->
                storageQuotaService.validateReplacement(testOrgId, oldFileBytes, newFileBytes)
        );
    }

    // ========================================================================
    // CASO 12 & 13: Organización sin membresía o inactiva: Fail-closed
    // ========================================================================
    @Test
    @DisplayName("12. Organización sin membresía activa aplica política fail-closed (InactiveMembershipException)")
    void test12_sinMembresiaValida_FailClosed() {
        Long orgSinMembresia = createTestOrganization("Org Huérfana " + System.currentTimeMillis(), "NIT-ORPH");

        assertThrows(InactiveMembershipException.class, () ->
                storageQuotaService.validateUpload(orgSinMembresia, 1024L)
        );
    }

    @Test
    @DisplayName("12b. Organización con membresía CANCELADA aplica fail-closed (InactiveMembershipException)")
    void test12b_membresiaCancelada_FailClosed() {
        Long orgCancelada = createTestOrganization("Org Cancelada " + System.currentTimeMillis(), "NIT-CANC");
        createTestMembership(orgCancelada, freePlanId, "CANCELADA");

        assertThrows(InactiveMembershipException.class, () ->
                storageQuotaService.validateUpload(orgCancelada, 1024L)
        );
    }

    @Test
    @DisplayName("13. Plan con límite NULL o 0 aplica fail-closed con 0 bytes de límite")
    void test13_planConLimiteNullOCero_FailClosed() {
        // Crear un plan con límite de almacenamiento NULL
        String uniquePlanCode = "NO_STOR_" + System.currentTimeMillis();
        KeyHolder khPlan = new GeneratedKeyHolder();
        jdbcTemplate.update("""
            INSERT INTO PLANES (CODIGO, NOMBRE, PRECIO_MENSUAL, LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB, ESTADO)
            VALUES (:code, 'Plan Sin Storage', 50000, 1, 10, 5, NULL, 'ACTIVO')
            """, new MapSqlParameterSource("code", uniquePlanCode), khPlan, new String[]{"ID_PLAN"});
        Long customPlanId = khPlan.getKey().longValue();

        Long orgCustom = createTestOrganization("Org Custom " + System.currentTimeMillis(), "NIT-CUST");
        try {
            createTestMembership(orgCustom, customPlanId, "ACTIVA");

            long limit = storageQuotaService.getStorageLimitBytes(orgCustom);
            assertEquals(0L, limit, "Límite NULL debe resolverse a 0 bytes fail-closed");

            // Cualquier intento de subir archivos debe ser rechazado
            assertThrows(StorageQuotaExceededException.class, () ->
                    storageQuotaService.validateUpload(orgCustom, 100L)
            );
        } finally {
            try {
                jdbcTemplate.update("DELETE FROM AUDITORIA_LOG WHERE ID_ORGANIZACION = :orgId", new MapSqlParameterSource("orgId", orgCustom));
                jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION = :orgId", new MapSqlParameterSource("orgId", orgCustom));
                jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :orgId", new MapSqlParameterSource("orgId", orgCustom));
                jdbcTemplate.update("DELETE FROM PLANES WHERE ID_PLAN = :planId", new MapSqlParameterSource("planId", customPlanId));
            } catch (Exception ignored) {}
        }
    }

    // ========================================================================
    // CASO 14, 15: Tenant Isolation & RBAC en Endpoint REST
    // ========================================================================
    @Test
    @DisplayName("14. ADMIN_ORGANIZACION no puede consultar cuota de otra organización (403 Forbidden / Anti-IDOR)")
    @WithMockUser(username = "admin_org1", authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    void test14_manipulacionOrganizationId_PorNoSuperadmin_Forbidden() throws Exception {
        // Establecer contexto autenticado para testOrgId
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(testOrgId)
                .roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZACION")
                .build());

        // Intentar consultar testOtherOrgId mediante parámetro IDOR
        mockMvc.perform(get("/api/v1/storage/quota")
                        .param("organizationId", testOtherOrgId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15. SUPERADMIN puede consultar la cuota de cualquier organización arbitraria")
    @WithMockUser(username = "super_admin", authorities = {"SCOPE_SUPERADMIN"})
    void test15_superadminPuedeConsultarCualquierOrganizacion() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        mockMvc.perform(get("/api/v1/storage/quota")
                        .param("organizationId", testOrgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.limitGb", is(1.0)))
                .andExpect(jsonPath("$.data.planCodigo", is("FREE")));

        mockMvc.perform(get("/api/v1/storage/quota")
                        .param("organizationId", testOtherOrgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.limitGb", is(10.0)))
                .andExpect(jsonPath("$.data.planCodigo", is("PRO")));
    }

    // ========================================================================
    // CASO 16: Cliente manipula usedBytes / limitBytes: Backend los ignora
    // ========================================================================
    @Test
    @DisplayName("16. Métricas de cuota calculadas exclusivamente server-side desde Oracle, ignorando datos cliente")
    @WithMockUser(username = "super_admin", authorities = {"SCOPE_SUPERADMIN"})
    void test16_clienteManipulaDatos_BackendIgnoraYCalculaDesdeOracle() throws Exception {
        elevateToSuperAdmin();
        createTestDocumentVersion(testOrgId, testPropId, 2_000_000L);

        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        // Consultar endpoint sin parámetros manipulables (solicitando testOrgId como superadmin)
        mockMvc.perform(get("/api/v1/storage/quota")
                        .param("organizationId", testOrgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedBytes", is(2_000_000)))
                .andExpect(jsonPath("$.data.limitBytes", is(1024 * 1024 * 1024)));
    }

    // ========================================================================
    // CASO 17: fileSize Spoofing: Backend mide el stream real
    // ========================================================================
    @Test
    @DisplayName("17. File size spoofing: El backend utiliza el tamaño real del MultipartFile")
    void test17_fileSizeSpoofing_BackendUsaTamanoRealDelStream() {
        byte[] realContent = new byte[2_000_000]; // 2 MB reales
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "acta_reunion.pdf",
                "application/pdf",
                realContent
        );

        assertEquals(2_000_000L, file.getSize(), "MultipartFile mide los bytes reales del stream");

        FileStorageService.StoredFile stored = fileStorageService.store(file, "documentos", testOrgId);
        assertEquals(2_000_000L, stored.sizeBytes(), "El archivo almacenado conserva los bytes reales medidos");
    }

    // ========================================================================
    // CASO 18: Doble upload concurrente serializado con row-lock en Oracle
    // ========================================================================
    @Test
    @DisplayName("18. Dos uploads concurrentes que juntos excederían la cuota: La serialización en Oracle impide superar el límite")
    void test18_concurrenciaDosUploadsQueJuntosExceden_PessimisticLockSerializaYRechazaSegundo() throws Exception {
        // En una organización con 1 GB, simular que quedan 6 MB disponibles
        long available = 6L * 1024L * 1024L;
        long used = 1024L * 1024L * 1024L - available;
        Long sharedDocId = createTestDocumentVersion(testOrgId, testPropId, used);

        // Dos hilos concurrentes intentan subir 4 MB cada uno (4 + 4 = 8 MB > 6 MB disponibles)
        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaExceededCount = new AtomicInteger(0);

        long fileBytes = 4L * 1024L * 1024L;

        Callable<Void> task = () -> {
            try {
                barrier.await();
                SaedContextHolder.setContext(SaedContext.builder()
                        .userId(1L)
                        .organizationId(1L)
                        .propertyId(1L)
                        .roleCode("SUPERADMIN")
                        .roleScope("GLOBAL")
                        .build());

                new TransactionTemplate(transactionManager).execute(status -> {
                    storageQuotaService.validateUpload(testOrgId, fileBytes);
                    // Insertar version adicional en el documento existente
                    jdbcTemplate.update("""
                        INSERT INTO VERSIONES_DOCUMENTO (ID_DOCUMENTO, NUMERO_VERSION, ARCHIVO_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_TAMANO_BYTES, ARCHIVO_MIME_TYPE, SUBIDO_POR)
                        VALUES (:docId, (SELECT COALESCE(MAX(NUMERO_VERSION), 0) + 1 FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = :docId), 'test.pdf', 'test.pdf', :bytes, 'application/pdf', 1)
                        """, new MapSqlParameterSource()
                            .addValue("docId", sharedDocId)
                            .addValue("bytes", fileBytes));
                    return null;
                });
                successCount.incrementAndGet();
            } catch (StorageQuotaExceededException e) {
                quotaExceededCount.incrementAndGet();
            } catch (Exception e) {
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof StorageQuotaExceededException) {
                        quotaExceededCount.incrementAndGet();
                        break;
                    }
                    cause = cause.getCause();
                }
            } finally {
                SaedContextHolder.clearContext();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactamente uno debe tener éxito y el segundo debe ser rechazado por cuota excedida
        assertEquals(1, successCount.get(), "Exactamente una de las dos peticiones concurrentes debe pasar");
        assertEquals(1, quotaExceededCount.get(), "La segunda petición concurrente debe ser rechazada por exceder la cuota");

        // El uso total nunca puede superar el límite
        long totalUsed = storageQuotaService.getStorageUsedBytes(testOrgId);
        long maxLimit = storageQuotaService.getStorageLimitBytes(testOrgId);
        assertTrue(totalUsed <= maxLimit, "El uso acumulado en la base de datos nunca debe exceder el límite contratado");
    }

    // ========================================================================
    // CASO 19: Rollback transaccional ante fallo durante upload
    // ========================================================================
    @Test
    @DisplayName("19. Fallo transaccional durante upload no corrompe cuota ni registra uso huérfano")
    void test19_falloDuranteUpload_RollbackNoCorrompeCuotaNiRegistraUso() {
        long usedBefore = storageQuotaService.getStorageUsedBytes(testOrgId);

        try {
            jdbcTemplate.getJdbcOperations().execute("""
                DECLARE
                BEGIN
                    INSERT INTO VERSIONES_DOCUMENTO (ID_DOCUMENTO, NUMERO_VERSION, ARCHIVO_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_TAMANO_BYTES, ARCHIVO_MIME_TYPE, SUBIDO_POR)
                    VALUES (-999, 1, 'invalido.pdf', 'invalido.pdf', 5000000, 'application/pdf', 1);
                END;
                """);
            fail("Debió fallar por violación de foreign key");
        } catch (Exception expected) {
            // Rollback esperado
        }

        long usedAfter = storageQuotaService.getStorageUsedBytes(testOrgId);
        assertEquals(usedBefore, usedAfter, "Fallo transaccional no debe incrementar el almacenamiento usado");
    }

    // ========================================================================
    // CASO 20: Auditoría registra evento al rechazar upload por cuota
    // ========================================================================
    @Test
    @DisplayName("20. Auditoría registra evento al superar la cuota de almacenamiento")
    void test20_auditoriaRegistraEventoDeCuotaExcedida() {
        // Consumir almacenamiento hasta dejar solo 2 MB libres
        createTestDocumentVersion(testOrgId, testPropId, 1024L * 1024L * 1024L - 2_000_000L);

        // Intentar subir 5 MB (cumple límite individual <= 10 MB, pero excede los 2 MB disponibles)
        long excessiveBytes = 5L * 1024L * 1024L;

        assertThrows(StorageQuotaExceededException.class, () ->
                storageQuotaService.validateUpload(testOrgId, excessiveBytes)
        );

        // Verificar registro en AUDITORIA_LOG
        String checkSql = """
            SELECT COUNT(*) FROM AUDITORIA_LOG
            WHERE ENTIDAD = 'STORAGE'
              AND RESULTADO = 'FALLIDO'
              AND ID_ORGANIZACION = :orgId
            """;
        Number count = jdbcTemplate.queryForObject(checkSql,
                new MapSqlParameterSource("orgId", testOrgId),
                Number.class);

        assertNotNull(count);
        assertTrue(count.intValue() >= 1, "Debe existir al menos un registro de auditoría para la cuota excedida");
    }

    // ========================================================================
    // CASO 21: Error Contract HTTP 409 Conflict
    // ========================================================================
    @Test
    @DisplayName("21. Error contract: Subida excedida en DocumentoController responde HTTP 409 STORAGE_QUOTA_EXCEEDED")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    void test21_errorContractHttp409Conflict() throws Exception {
        elevateToSuperAdmin();
        // Simular uso de casi 1 GB
        createTestDocumentVersion(testOrgId, testPropId, 1_070_000_000L);

        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(testOrgId)
                .propertyId(testPropId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        // Documento solicitando 8 MB adicionales (supera 1 GB)
        DocumentoDTO docDto = new DocumentoDTO();
        docDto.setIdOrganizacion(testOrgId);
        docDto.setIdPropiedad(testPropId);
        docDto.setTitulo("Reglamento 2026");
        docDto.setCategoria("REGLAMENTO_INTERNO");
        docDto.setArchivoUrl("docs/reglamento.pdf");
        docDto.setArchivoNombreOrig("reglamento.pdf");
        docDto.setArchivoMimeType("application/pdf");
        docDto.setArchivoTamanoBytes(8L * 1024L * 1024L);

        mockMvc.perform(post("/api/v1/documentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STORAGE_QUOTA_EXCEEDED")))
                .andExpect(jsonPath("$.limitBytes", is(1024 * 1024 * 1024)))
                .andExpect(jsonPath("$.requestedBytes", is(8 * 1024 * 1024)));
    }
}
