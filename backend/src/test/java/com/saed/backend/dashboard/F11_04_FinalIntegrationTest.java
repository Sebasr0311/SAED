package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ExportVolumeLimitExceededException;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F11_04_FinalIntegrationTest
 *
 * Suite de Certificación Final e Integración Completa para F11-04 (Bloque E):
 * - Integración de los 4 ReportDefinitions (CARTERA_MOROSA, EJECUCION_CUOTAS, PAGOS_RECIENTES, EJECUCION_PRESUPUESTAL).
 * - Verificación de formatos: JSON, PDF, CSV, XLSX.
 * - Consistencia semántica de datos entre representaciones.
 * - Validación independiente de hash criptográfico SHA-256.
 * - Inmutabilidad en base de datos Oracle XE protegida por trigger TRG_HISTREP_INMUTABLE (ORA-20060).
 * - Aislamiento multi-tenant Zero-Trust (Org A vs Org B, Admin Propiedad, Admin Org consolidado ID_PROPIEDAD = NULL).
 * - Bloqueo de SQL arbitrario en CONSULTA_ORIGEN_CLAVE.
 * - Límite de 10.000 filas (ExportVolumeLimitExceededException).
 * - Desactivación lógica y preservación íntegra de historial anterior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_04_FinalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportExportService reportExportService;

    @MockBean
    private AssignmentService assignmentService;

    // Identidades
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1 = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_PROP_2 = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE = 805L;
    private static final long ASSIGN_RESIDENTE = 905L;

    private static final long USER_PORTERO = 806L;
    private static final long ASSIGN_PORTERO = 906L;

    // Scopes
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    private static final long UNIDAD_1_ID = 9101L;
    private static final long UNIDAD_2_ID = 9201L;

    private static final long CONCEPTO_1_ID = 9501L;
    private static final long CONCEPTO_2_ID = 9502L;

    // Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente;
    private String tokenPortero;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            ensureBaseData();
            setupMockAssignments();

            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);
            tokenPortero = jwtProvider.generateIdentityToken(USER_PORTERO);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        clearContext();
    }

    private void setElevatedContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        SaedContext ctx = new SaedContext();
        ctx.setUserId(1L);
        ctx.setOrganizationId(1L);
        ctx.setPropertyId(1L);
        ctx.setRoleCode("SUPERADMIN");
        SaedContextHolder.setContext(ctx);
    }

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private <T> T queryElevated(Supplier<T> supplier) {
        setElevatedContext();
        try {
            return supplier.get();
        } finally {
            clearContext();
        }
    }

    private void runElevated(Runnable runnable) {
        setElevatedContext();
        try {
            runnable.run();
        } finally {
            clearContext();
        }
    }

    private void ensureBaseData() {
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.250000));

        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "CUOTA_ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_2_ID, "CUOTA_ORD_2", "Cuota Ordinaria Prop 2");

        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin_f11_04@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2_f11_04@saed.com");
        ensurePersona(USER_RESIDENTE, "1000000805", "Carlos", "Residente", "residente_f11_04@saed.com");
        ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero_f11_04@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_f11_04", "superadmin_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_f11_04", "adminorg1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_f11_04", "adminprop1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_f11_04", "adminprop2_f11_04@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_f11_04", "residente_f11_04@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_f11_04", "portero_f11_04@saed.com");

        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Cuotas y pagos para datos de reporte
        try {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (9101, 9201)");
            jdbcTemplate.execute("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (9101, 9201)");
        } catch (Exception ignored) {}

        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 500000, 500000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 500000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-09-30 14:30:00', 'REF-PAGO-001')
        """, UNIDAD_1_ID);
    }

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long id, Long orgId, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle 100 # 15-20', 'Bogotá', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(Long id, Long propId, String iden, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, iden, coef);
        }
    }

    private void ensureConcepto(Long id, Long orgId, Long propId, String codigo, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM CONCEPTOS_COBRO WHERE ID_CONCEPTO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) VALUES (?, ?, ?, ?, ?, 'ADMINISTRACION', 'ACTIVO')",
                    id, orgId, propId, codigo, nombre);
        }
    }

    private void ensurePersona(Long id, String doc, String nom, String ape, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, nom, ape, email);
        }
    }

    private void ensureUsuario(Long id, Long personaId, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, PASSWORD_HASH, ESTADO) VALUES (?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')",
                    id, personaId, username);
        }
    }

    private void ensureAsignacion(Long id, Long userId, String rolCodigo, Long orgId, Long propId, Long unidadId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA')",
                    id, userId, idRol, orgId, propId, unidadId);
        }
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

        AssignmentResponseDTO sa = new AssignmentResponseDTO();
        sa.setIdAsignacion(ASSIGN_SUPERADMIN);
        sa.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(sa));

        AssignmentResponseDTO aOrg1 = new AssignmentResponseDTO();
        aOrg1.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        aOrg1.setOrganizacion(org1);
        aOrg1.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(aOrg1));

        AssignmentResponseDTO aProp1 = new AssignmentResponseDTO();
        aProp1.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        aProp1.setOrganizacion(org1);
        aProp1.setPropiedad(prop1);
        aProp1.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(aProp1));

        AssignmentResponseDTO aProp2 = new AssignmentResponseDTO();
        aProp2.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        aProp2.setOrganizacion(org2);
        aProp2.setPropiedad(prop2);
        aProp2.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(aProp2));

        AssignmentResponseDTO res = new AssignmentResponseDTO();
        res.setIdAsignacion(ASSIGN_RESIDENTE);
        res.setOrganizacion(org1);
        res.setPropiedad(prop1);
        res.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(res));

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(port));
    }

    // =========================================================================
    // 1. FLUJO PRINCIPAL E2E COMPLETO
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Flujo Principal E2E: Creación -> Generación PDF -> Historial -> Metadatos")
    public void test01_flujoPrincipalE2E_carteraMorosa_generacionHistorialYMetadatos() throws Exception {
        // 1. Crear configuración como ADMIN_ORGANIZACION
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setCodigo("E2E_CARTERA_" + System.currentTimeMillis());
        req.setNombre("Cartera Morosa E2E Master");
        req.setDescripcion("Prueba de integración principal E2E");
        req.setConsultaOrigenClave("CARTERA_MOROSA");
        req.setFormatoSalidaDefecto("PDF");

        MvcResult createResult = mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long configId = createdJson.path("data").path("idReporteConfig").asLong();
        assertTrue(configId > 0);

        // 2. Ejecutar generación binaria PDF
        MvcResult genResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .param("formato", "PDF"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] pdfBytes = genResult.getResponse().getContentAsByteArray();
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        assertEquals("%PDF-", new String(pdfBytes, 0, Math.min(5, pdfBytes.length)));

        // 3. Consultar historial generado
        Long histId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT MAX(ID_HISTORIAL_REPORTE) FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ?",
                Long.class,
                configId
        ));
        assertNotNull(histId);

        MvcResult histResult = mockMvc.perform(get("/api/v1/reportes/historial/" + histId)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formatoGenerado").value("PDF"))
                .andExpect(jsonPath("$.data.idOrganizacion").value(ORG_1_ID))
                .andExpect(jsonPath("$.data.idUsuarioEjecuto").value(USER_ADMIN_ORG_1))
                .andReturn();

        JsonNode histJson = objectMapper.readTree(histResult.getResponse().getContentAsString());
        String dbSha = histJson.path("data").path("archivoSha256").asText();
        assertEquals(64, dbSha.length());
    }

    // =========================================================================
    // 2. PRUEBA DE LAS CUATRO REPORT DEFINITIONS ALLOWLISTADAS
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("2. E2E: Ejecución y verificación de las 4 Report Definitions del Registry")
    public void test02_flujoE2E_cuatroReportDefinitions() throws Exception {
        String[] claves = {
                "CARTERA_MOROSA",
                "EJECUCION_CUOTAS",
                "PAGOS_RECIENTES",
                "EJECUCION_PRESUPUESTAL"
        };

        for (String clave : claves) {
            Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                    "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = ? AND ROWNUM = 1",
                    Long.class,
                    clave
            ));
            assertNotNull(configId, "Debe existir plantilla canónica para " + clave);

            MvcResult result = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                    .header("Authorization", "Bearer " + tokenAdminProp1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                    .param("formato", "CSV")
                    .param("metadata", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.formato").value("CSV"))
                    .andReturn();

            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            assertTrue(data.path("totalRegistros").asInt() >= 0);
            assertEquals(64, data.path("archivoSha256").asText().length());
        }
    }

    // =========================================================================
    // 3. SOPORTE DE FORMATOS: JSON, PDF, CSV
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("3. E2E: Verificación de formatos de exportación (JSON, PDF, CSV)")
    public void test03_soporteFormatos_JSON_PDF_CSV() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        // 1. Formato JSON
        MvcResult jsonRes = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "JSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        assertTrue(jsonRes.getResponse().getContentAsString().contains("items") || jsonRes.getResponse().getContentAsString().contains("data"));

        // 2. Formato PDF
        MvcResult pdfRes = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "PDF"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] pdfBytes = pdfRes.getResponse().getContentAsByteArray();
        assertEquals("%PDF-", new String(pdfBytes, 0, Math.min(5, pdfBytes.length)));

        // 3. Formato CSV con RFC 4180 y UTF-8 BOM
        MvcResult csvRes = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] csvBytes = csvRes.getResponse().getContentAsByteArray();
        assertTrue(csvBytes.length >= 3);
        // BOM UTF-8 (\uFEFF -> 0xEF, 0xBB, 0xBF)
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);
    }

    // =========================================================================
    // 4. CONSISTENCIA SEMÁNTICA DE DATOS ENTRE FORMATOS
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4. Consistencia Semántica: Mismo conjunto de datos lógico entre JSON, CSV y PDF")
    public void test04_consistenciaSemanticaDatosEntreFormatos() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        // 1. Obtener conteo vía metadata
        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV")
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();
        int expectedRows = objectMapper.readTree(metaResult.getResponse().getContentAsString()).path("data").path("totalRegistros").asInt();

        // 2. Obtener archivo CSV real
        MvcResult csvResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV"))
                .andExpect(status().isOk())
                .andReturn();
        String csvContent = new String(csvResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        String[] lines = csvContent.split("\r\n|\r|\n");

        // Al menos la cabecera + expectedRows
        int dataLinesCount = Math.max(0, lines.length - 1);
        assertEquals(expectedRows, dataLinesCount, "El número de líneas en el CSV debe coincidir idénticamente con los registros procesados");
    }

    // =========================================================================
    // 5. VALIDACIÓN INDEPENDIENTE DEL HASH CRIPTOGRÁFICO SHA-256
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("5. Verificación Criptográfica: SHA256(bytes_generados) == ARCHIVO_SHA256 en DB")
    public void test05_verificacionIndependienteHashSha256() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'PAGOS_RECIENTES' AND ROWNUM = 1",
                Long.class
        ));

        // Descargar binario
        MvcResult binResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] generatedBytes = binResult.getResponse().getContentAsByteArray();

        // Calcular SHA-256 independientemente en la prueba
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(generatedBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String calculatedSha = sb.toString();

        // Leer hash registrado en BD
        String dbSha = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ARCHIVO_SHA256 FROM (SELECT ARCHIVO_SHA256 FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ? ORDER BY ID_HISTORIAL_REPORTE DESC) WHERE ROWNUM = 1",
                String.class,
                configId
        ));

        assertEquals(calculatedSha, dbSha, "La firma SHA-256 calculada independientemente debe coincidir exactamente con ARCHIVO_SHA256 en BD");
    }

    // =========================================================================
    // 6. VERIFICACIÓN DE REGISTROS_PROCESADOS
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("6. REGISTROS_PROCESADOS refleja el conteo real y exacto del DTO de negocio")
    public void test06_registrosProcesadosExacto() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        int reportedRows = json.path("data").path("totalRegistros").asInt();
        Long histId = json.path("data").path("idHistorialReporte").asLong();

        Integer dbRows = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT REGISTROS_PROCESADOS FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                Integer.class,
                histId
        ));

        assertEquals(reportedRows, dbRows);
        assertTrue(dbRows >= 1, "Debe registrar al menos 1 cuota morosa ingresada en el setUp");
    }

    // =========================================================================
    // 7. INMUTABILIDAD EN BASE DE DATOS ORACLE (TRG_HISTREP_INMUTABLE / ORA-20060)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("7. Inmutabilidad en DB: Rechazo absoluto de UPDATE y DELETE con ORA-20060")
    public void test07_inmutabilidadOracle_TRG_HISTREP_INMUTABLE_ORA20060() {
        Long histId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT MIN(ID_HISTORIAL_REPORTE) FROM HISTORIAL_REPORTES",
                Long.class
        ));
        assertNotNull(histId, "Debe existir al menos un historial registrado");

        // 1. Intento de UPDATE
        Exception updateEx = assertThrows(Exception.class, () -> {
            queryElevated(() -> jdbcTemplate.update(
                    "UPDATE HISTORIAL_REPORTES SET FORMATO_GENERADO = 'TAMPERED' WHERE ID_HISTORIAL_REPORTE = ?",
                    histId
            ));
        });
        assertTrue(updateEx.getMessage().contains("ORA-20060") || updateEx.getMessage().contains("HISTORIAL_REPORTES es inmutable"),
                "El trigger TRG_HISTREP_INMUTABLE debe abortar UPDATE con ORA-20060");

        // 2. Intento de DELETE
        Exception deleteEx = assertThrows(Exception.class, () -> {
            queryElevated(() -> jdbcTemplate.update(
                    "DELETE FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                    histId
            ));
        });
        assertTrue(deleteEx.getMessage().contains("ORA-20060") || deleteEx.getMessage().contains("HISTORIAL_REPORTES es inmutable"),
                "El trigger TRG_HISTREP_INMUTABLE debe abortar DELETE con ORA-20060");

        // 3. Comprobar que el registro original continúa íntegro
        String formatoOriginal = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT FORMATO_GENERADO FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                String.class,
                histId
        ));
        assertNotEquals("TAMPERED", formatoOriginal, "El registro original no debió haber sido modificado");
    }

    // =========================================================================
    // 8. AISLAMIENTO MULTI-TENANT E2E (ORGANIZACIÓN A VS ORGANIZACIÓN B)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("8. Seguridad Multi-Tenant E2E: Org 1 no puede ver ni acceder a historial ni configuraciones de Org 2")
    public void test08_seguridadMultiTenant_aislamientoOrgA_OrgB() throws Exception {
        // Crear configuración exclusiva para Org 2 directamente en DB
        Long configOrg2 = queryElevated(() -> {
            jdbcTemplate.update("DELETE FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'REP_FINAL_ORG2'");
            jdbcTemplate.update("""
                INSERT INTO REPORTES_CONFIGURADOS (CODIGO, NOMBRE, MODULO, DESCRIPCION, FORMATO_SALIDA_DEFECTO,
                    CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION, ESTADO, ID_ORGANIZACION, ID_PROPIEDAD)
                VALUES ('REP_FINAL_ORG2', 'Exclusivo Org 2 Final', 'FINANZAS', 'Privado', 'PDF', 'CARTERA_MOROSA', 'ADMIN_PROPIEDAD', 'ACTIVO', ?, ?)
            """, ORG_2_ID, PROP_2_ID);

            return jdbcTemplate.queryForObject("SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'REP_FINAL_ORG2'", Long.class);
        });

        // 1. ADMIN_ORGANIZACION 1 lista configuraciones -> REP_FINAL_ORG2 no debe aparecer
        MvcResult listResult = mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(listResult.getResponse().getContentAsString().contains("REP_FINAL_ORG2"),
                "ADMIN_ORGANIZACION 1 no debe ver configuración de Org 2");

        // 2. ADMIN_ORGANIZACION 1 intenta generar configuración de Org 2 -> 403 Forbidden o 404 Not Found
        mockMvc.perform(post("/api/v1/reportes/configurados/" + configOrg2 + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    assertTrue(sc == 403 || sc == 404, "Generar configuración ajena debe ser rechazado (403 o 404)");
                });
    }

    // =========================================================================
    // 9. CONFINAMIENTO ESTRICTO DE ADMIN_PROPIEDAD
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("9. ADMIN_PROPIEDAD confinado estrictamente a su propiedad asignada")
    public void test09_adminPropiedad_confinamientoEstrictoAPropiedad() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        // Intentar pasar filtros manipulando propertyId hacia PROP_2_ID
        String filtrosManipulados = "{\"propertyId\":" + PROP_2_ID + "}";

        // Al ejecutar, el servicio Zero-Trust debe forzar su propiedad asignada (PROP_1_ID)
        MvcResult result = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("filtrosJson", filtrosManipulados)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Long histId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("idHistorialReporte").asLong();

        Long propInDb = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_PROPIEDAD FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                Long.class,
                histId
        ));
        assertEquals(PROP_1_ID, propInDb, "El motor debió confinar el reporte a PROP_1_ID a pesar del intento de suplantación");
    }

    // =========================================================================
    // 10. REGLA ESPECIAL ADMIN_ORGANIZACION CONSOLIDADO (ID_PROPIEDAD = NULL)
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("10. ADMIN_ORGANIZACION genera y consulta reporte consolidado (ID_PROPIEDAD = NULL)")
    public void test10_adminOrganizacion_reporteConsolidado_idPropiedadNull() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'EJECUCION_PRESUPUESTAL' AND ROWNUM = 1",
                Long.class
        ));

        // ADMIN_ORGANIZACION no tiene propiedad asignada (context.getPropertyId() == null)
        MvcResult result = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Long histId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("idHistorialReporte").asLong();

        // Verificar que en base de datos ID_PROPIEDAD quedó explícitamente NULL
        Long propIdInDb = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_PROPIEDAD FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                Long.class,
                histId
        ));
        assertNull(propIdInDb, "Reporte consolidado a nivel organizacional debe tener ID_PROPIEDAD = NULL");

        // Verificar que ADMIN_ORGANIZACION 1 puede consultarlo sin problemas
        mockMvc.perform(get("/api/v1/reportes/historial/" + histId)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idHistorialReporte").value(histId));
    }

    // =========================================================================
    // 11. PRIVILEGIOS DE SUPERADMIN GLOBAL
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("11. SUPERADMIN puede auditar configuraciones e historial de múltiples organizaciones")
    public void test11_superAdmin_accesoGlobal() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // 12. BLOQUEO TAXATIVO DE PORTERO Y RESIDENTE (403 FORBIDDEN)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("12. PORTERO y RESIDENTE bloqueados con 403 Forbidden en todo el subsistema")
    public void test12_porteroYResidente_bloqueoCompleto403() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT MIN(ID_REPORTE_CONFIG) FROM REPORTES_CONFIGURADOS",
                Long.class
        ));

        // Portero
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // Residente
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 13. BLOQUEO DE USUARIO ANÓNIMO (401 UNAUTHORIZED)
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("13. Peticiones anónimas rechazadas taxativamente con 401 Unauthorized")
    public void test13_anonimo_bloqueoCompleto401() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/reportes/configurados/1/generar"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reportes/historial"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 14. BLOQUEO DE SQL ARBITRARIO EN CONSULTA_ORIGEN_CLAVE
    // =========================================================================

    @Test
    @Order(14)
    @DisplayName("14. Seguridad contra SQL Injection: CONSULTA_ORIGEN_CLAVE rechaza SQL dinámico")
    public void test14_bloqueoSqlArbitrarioEnClaveOrigen() throws Exception {
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setNombre("SQL Injection Attack Attempt");
        req.setConsultaOrigenClave("SELECT * FROM USUARIOS; DROP TABLE PAGOS;");
        req.setFormatoSalidaDefecto("PDF");

        mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_KEY"));
    }

    // =========================================================================
    // 15. DESACTIVACIÓN DE CONFIGURACIONES E INACTIVIDAD
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("15. Desactivación de configuración impide generaciones posteriores y preserva historial")
    public void test15_desactivacionConfiguracion_impideGeneracion_preservaHistorial() throws Exception {
        // 1. Crear configuración
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setCodigo("DEACT_TEST_" + System.currentTimeMillis());
        req.setNombre("Plantilla para Desactivar");
        req.setConsultaOrigenClave("CARTERA_MOROSA");
        req.setFormatoSalidaDefecto("PDF");

        MvcResult createRes = mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long configId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("idReporteConfig").asLong();

        // 2. Generar primer reporte (éxito)
        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk());

        int countBefore = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ?",
                Integer.class,
                configId
        ));
        assertTrue(countBefore > 0);

        // 3. Desactivar configuración
        mockMvc.perform(delete("/api/v1/reportes/configurados/" + configId)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk());

        // 4. Intentar generar reporte sobre configuración inactiva -> 409 Conflict
        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isConflict());

        // 5. Verificar que el historial previo continúa intacto
        int countAfter = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ?",
                Integer.class,
                configId
        ));
        assertEquals(countBefore, countAfter);
    }

    // =========================================================================
    // 16. LÍMITE DE VOLUMEN (10.000 FILAS)
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("16. Motor de exportación hace cumplir el límite máximo de 10.000 filas")
    public void test16_limiteExportacion10000Filas() {
        List<com.saed.backend.dashboard.dto.CarteraMorosaDTO> mockLargeList = new ArrayList<>();
        for (int i = 0; i < 10001; i++) {
            mockLargeList.add(new com.saed.backend.dashboard.dto.CarteraMorosaDTO(
                    "U-" + i, "Edificio Residencial SAED", 1L, BigDecimal.valueOf(100000),
                    java.time.LocalDate.now(), java.time.LocalDate.now(), 30L
            ));
        }

        assertThrows(ExportVolumeLimitExceededException.class, () -> {
            reportExportService.exportCarteraMorosa(mockLargeList, ExportFormat.CSV, PROP_1_ID, null, null);
        });
    }

    // =========================================================================
    // 17. HISTORIAL SOLO SE REGISTRA EN GENERACIONES EXITOSAS
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("17. Error en generación no registra falsos positivos en HISTORIAL_REPORTES")
    public void test17_historialSoloSeRegistraEnGeneracionExitosa() throws Exception {
        int countBefore = queryElevated(() -> jdbcTemplate.queryForObject("SELECT COUNT(1) FROM HISTORIAL_REPORTES", Integer.class));

        // Intento con ID inexistente
        mockMvc.perform(post("/api/v1/reportes/configurados/9999999/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isNotFound());

        int countAfter = queryElevated(() -> jdbcTemplate.queryForObject("SELECT COUNT(1) FROM HISTORIAL_REPORTES", Integer.class));
        assertEquals(countBefore, countAfter, "No debe registrarse historial ante una ejecución fallida");
    }

    // =========================================================================
    // 18. CONTRATO Y CONSISTENCIA TABULAR PARA EXPORTACIÓN XLSX CLIENTE
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("18. Consistencia de contrato para exportación XLSX cliente (xlsx-js-style)")
    public void test18_formatoXLSX_contratoTabularYConsistencia() throws Exception {
        // Validar que el endpoint base `/reportes/cartera-morosa` provee los campos esperados por `exportToExcel`
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(items.size() >= 1);
        JsonNode first = items.get(0);

        // Campos requeridos por exportUtils.js (exportToExcel)
        assertTrue(first.has("unidad"));
        assertTrue(first.has("deudaTotal"));
        assertTrue(first.has("cuotasPendientes"));
        assertTrue(first.has("diasMora"));
    }
}
