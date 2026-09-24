package com.saed.backend.documentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10DocumentosIntegrationTest — Comprehensive Integration, Security, and Functional Test Suite
 * for F10-01 Gestión Documental in SAED 2.0 (Spring Boot 3 + Oracle XE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F10DocumentosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @MockBean
    private AssignmentService assignmentService;

    // Identities
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1 = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_PROP_1B = 804L;
    private static final long ASSIGN_ADMIN_PROP_1B = 907L;

    private static final long USER_ADMIN_PROP_2 = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE = 805L;
    private static final long ASSIGN_RESIDENTE = 905L;

    private static final long USER_PORTERO = 806L;
    private static final long ASSIGN_PORTERO = 906L;

    // Multi-tenant Scopes
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L; // In Org 1
    private static final long PROP_1B_ID = 8899L; // Also In Org 1 (Subordinate property in same organization)
    private static final long PROP_2_ID = 8802L; // In Org 2

    // JWT Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp1B;
    private String tokenAdminProp2;
    private String tokenResidente;
    private String tokenPortero;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Cleanup previous test artifacts
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO IN 
                        (SELECT ID_DOCUMENTO FROM DOCUMENTOS WHERE TITULO LIKE 'DOC-TEST-%');
                    DELETE FROM DOCUMENTOS WHERE TITULO LIKE 'DOC-TEST-%';
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_1B_ID, ORG_1_ID, "Torre B SAED Org 1");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(1L, PROP_1_ID, "101");

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_test", "adminorg1@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_test", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_PROP_1B, "1000000804", "Admin", "PropUnoB", "adminprop1b@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1B, USER_ADMIN_PROP_1B, "admin_prop1b_test", "adminprop1b@saed.com");

            ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_test", "adminprop2@saed.com");

            ensurePersona(USER_RESIDENTE, "1000000805", "Carlos", "Residente", "residente@saed.com");
            ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_test", "residente@saed.com");

            ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero@saed.com");
            ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_test", "portero@saed.com");

            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1B, USER_ADMIN_PROP_1B, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1B_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, 1L);
            ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

            setupMockAssignments();

            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp1B = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1B);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);
            tokenPortero = jwtProvider.generateIdentityToken(USER_PORTERO);

        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        setElevatedContext();
        try {
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO IN 
                        (SELECT ID_DOCUMENTO FROM DOCUMENTOS WHERE TITULO LIKE 'DOC-TEST-%');
                    DELETE FROM DOCUMENTOS WHERE TITULO LIKE 'DOC-TEST-%';
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop1B = new PropertyDTO(PROP_1B_ID, "Torre B SAED Org 1");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

        // SuperAdmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setOrganizacion(org1);
        saAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(saAssign));

        // Admin Org 1
        AssignmentResponseDTO aOrg1 = new AssignmentResponseDTO();
        aOrg1.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        aOrg1.setOrganizacion(org1);
        aOrg1.setPropiedad(null);
        aOrg1.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(aOrg1));

        // Admin Prop 1
        AssignmentResponseDTO aProp1 = new AssignmentResponseDTO();
        aProp1.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        aProp1.setOrganizacion(org1);
        aProp1.setPropiedad(prop1);
        aProp1.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(aProp1));

        // Admin Prop 1B (Same Organization, different Property)
        AssignmentResponseDTO aProp1B = new AssignmentResponseDTO();
        aProp1B.setIdAsignacion(ASSIGN_ADMIN_PROP_1B);
        aProp1B.setOrganizacion(org1);
        aProp1B.setPropiedad(prop1B);
        aProp1B.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1B, USER_ADMIN_PROP_1B)).thenReturn(Optional.of(aProp1B));

        // Admin Prop 2
        AssignmentResponseDTO aProp2 = new AssignmentResponseDTO();
        aProp2.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        aProp2.setOrganizacion(org2);
        aProp2.setPropiedad(prop2);
        aProp2.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(aProp2));

        // Residente
        AssignmentResponseDTO resAssign = new AssignmentResponseDTO();
        resAssign.setIdAsignacion(ASSIGN_RESIDENTE);
        resAssign.setOrganizacion(org1);
        resAssign.setPropiedad(prop1);
        resAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(resAssign));

        // Portero
        AssignmentResponseDTO portAssign = new AssignmentResponseDTO();
        portAssign.setIdAsignacion(ASSIGN_PORTERO);
        portAssign.setOrganizacion(org1);
        portAssign.setPropiedad(prop1);
        portAssign.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(portAssign));
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_SUPERADMIN)
                .organizationId(ORG_1_ID)
                .propertyId(PROP_1_ID)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void ensureOrganizacion(Long idOrg, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, idOrg);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    idOrg, nombre, nit, email);
        }
        Integer mem = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ? AND ESTADO = 'ACTIVA'", Integer.class, idOrg);
        if (mem == null || mem == 0) {
            jdbcTemplate.update("INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) VALUES (?, 1, 'ACTIVA', SYSDATE, SYSDATE + 365, 'N')", idOrg);
        }
    }

    private void ensurePropiedad(Long idProp, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, idProp);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (?, ?, 1, ?, 'Dir Test', 'Bogota', 'PROPIETARIOS', 'ACTIVA')",
                    idProp, idOrg, nombre);
        } else {
            jdbcTemplate.update("UPDATE PROPIEDADES SET ID_ORGANIZACION = ?, NOMBRE = ? WHERE ID_PROPIEDAD = ?",
                    idOrg, nombre, idProp);
        }
    }

    private void ensurePersona(Long idPersona, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    idPersona, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long idUsuario, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (?, ?, ?, ?, '$2a$10$test', 'ACTIVO')",
                    idUsuario, idPersona, username, email);
        }
    }

    private void ensureUnidad(Long idUnidad, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, idUnidad);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    idUnidad, idProp, identificador);
        }
    }

    private void ensureAsignacion(Long idAsignacion, Long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsignacion", idAsignacion, Types.NUMERIC)
                .addValue("idUsuario", idUsuario, Types.NUMERIC)
                .addValue("idRol", idRol, Types.NUMERIC)
                .addValue("idOrg", idOrg, Types.NUMERIC)
                .addValue("idProp", idProp, Types.NUMERIC)
                .addValue("idUnidad", idUnidad, Types.NUMERIC);

        namedJdbc.update("""
            DELETE FROM USUARIO_ASIGNACIONES 
            WHERE (ID_USUARIO = :idUsuario AND ID_ROL = :idRol
                   AND NVL(ID_ORGANIZACION, -1) = NVL(:idOrg, -1)
                   AND NVL(ID_PROPIEDAD, -1) = NVL(:idProp, -1)
                   AND NVL(ID_UNIDAD, -1) = NVL(:idUnidad, -1))
               OR ID_ASIGNACION = :idAsignacion
        """, params);

        namedJdbc.update("""
            INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
            VALUES (:idAsignacion, :idUsuario, :idRol, :idOrg, :idProp, :idUnidad, 'ACTIVA', TRUNC(SYSDATE))
        """, params);
    }

    // =========================================================================
    // TEST 01 — MULTIPART UPLOAD BY ADMIN PROPIEDAD
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("Test 01: Upload multipart de documento válido por ADMIN_PROPIEDAD retorna 201 y persiste en Oracle")
    public void test01_uploadMultipart_adminPropiedad_success() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 Fake test content for reglamento interno".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "reglamento_test.pdf", "application/pdf", pdfBytes);

        String responseStr = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Reglamento-01")
                        .param("categoria", "REGLAMENTO_INTERNO")
                        .param("descripcion", "Reglamento interno de prueba para certificación F10-01")
                        .param("esPublicoResidentes", "S")
                        .param("rolMinimoAcceso", "RESIDENTE")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idDocumento").isNumber())
                .andExpect(jsonPath("$.data.nombreArchivo").value("reglamento_test.pdf"))
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(responseStr).at("/data/idDocumento").numberValue()).longValue();

        // Verify in DB directly
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DOCUMENTOS WHERE ID_DOCUMENTO = ? AND CATEGORIA = 'REGLAMENTO_INTERNO' AND ESTADO = 'ACTIVO'",
                    Integer.class, idDoc);
            assertEquals(1, count);

            Integer versCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ? AND NUMERO_VERSION = 1 AND ARCHIVO_SHA256 IS NOT NULL",
                    Integer.class, idDoc);
            assertEquals(1, versCount);
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 02 — DOWNLOAD DOCUMENT BY ADMIN PROPIEDAD
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("Test 02: Descarga física de documento propio por ADMIN_PROPIEDAD retorna 200 con binary stream íntegro")
    public void test02_download_adminPropiedad_success() throws Exception {
        byte[] fileBytes = "Contenido original del contrato F10-01".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "contrato_servicios.pdf", "application/pdf", fileBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Contrato-02")
                        .param("categoria", "CONTRATO_PROVEEDOR")
                        .param("descripcion", "Contrato de mantenimiento de ascensores")
                        .param("esPublicoResidentes", "N")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Download
        byte[] downloadedBytes = mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", containsString("contrato_servicios.pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(new String(fileBytes), new String(downloadedBytes));
    }

    // =========================================================================
    // TEST 03 — RESIDENT ACCESS TO PUBLIC DOCUMENT (SUCCESS)
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("Test 03: Residente consulta y descarga documento público (esPublicoResidentes='S') exitosamente (200 OK)")
    public void test03_residente_publicDocument_download_success() throws Exception {
        byte[] pdfBytes = "Manual de convivencia oficial 2026".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "manual_convivencia.pdf", "application/pdf", pdfBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Manual-03")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .param("descripcion", "Manual de convivencia actualizado")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // 1. Resident lists public docs
        mockMvc.perform(get("/api/v1/documentos/residente")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasItem(hasEntry("titulo", "DOC-TEST-Manual-03"))));

        // 2. Resident downloads public doc
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("manual_convivencia.pdf")));
    }

    // =========================================================================
    // TEST 04 — RESIDENT ACCESS TO PRIVATE DOCUMENT (DENIED 403)
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("Test 04: Residente intentando descargar documento administrativo confidencial es bloqueado con 403 Forbidden")
    public void test04_residente_privateDocument_download_forbidden403() throws Exception {
        byte[] pdfBytes = "Información confidencial de finanzas".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "estado_financiero_confidencial.pdf", "application/pdf", pdfBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Finanzas-04")
                        .param("categoria", "ESTADO_FINANCIERO")
                        .param("esPublicoResidentes", "N")
                        .param("rolMinimoAcceso", "ADMIN_PROPIEDAD")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Resident tries to download private document -> 403 Forbidden
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // =========================================================================
    // TEST 05 — CROSS-TENANT IDOR ATTEMPT (DENIED 403)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("Test 05: Anti-IDOR: Administrador de Propiedad 2 es bloqueado con 403 al intentar descargar documento de Propiedad 1")
    public void test05_crossTenant_idor_download_forbidden403() throws Exception {
        byte[] pdfBytes = "Planos estructurales propiedad 1".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "planos_prop1.pdf", "application/pdf", pdfBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Planos-05")
                        .param("categoria", "PLANOS")
                        .param("esPublicoResidentes", "N")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Admin of Property 2 / Org 2 attempts to download doc from Property 1 / Org 1
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404,
                            "Debe retornar 403 Forbidden o 404 Not Found ante acceso cross-tenant");
                })
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // TEST 06 — PORTERO ACCESS DENIED (403 FORBIDDEN)
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("Test 06: Confinamiento de rol: PORTERO es rechazado con 403 Forbidden en los 8 endpoints del módulo documental")
    public void test06_portero_documentAccess_forbidden403() throws Exception {
        // 1. GET /api/v1/documentos/admin
        mockMvc.perform(get("/api/v1/documentos/admin")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 2. GET /api/v1/documentos/residente
        mockMvc.perform(get("/api/v1/documentos/residente")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 3. GET /api/v1/documentos/{id}
        mockMvc.perform(get("/api/v1/documentos/1")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 4. GET /api/v1/documentos/{id}/descargar
        mockMvc.perform(get("/api/v1/documentos/1/descargar")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 5. POST /api/v1/documentos/upload
        MockMultipartFile file = new MockMultipartFile("archivo", "dummy.pdf", "application/pdf", "dummy".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Portero")
                        .param("categoria", "OTRO")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 6. PUT /api/v1/documentos/{id}
        mockMvc.perform(put("/api/v1/documentos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"hacked\"}")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 7. POST /api/v1/documentos/{id}/versiones
        mockMvc.perform(multipart("/api/v1/documentos/1/versiones")
                        .file(file)
                        .param("notasCambio", "test")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 8. DELETE /api/v1/documentos/{id}
        mockMvc.perform(delete("/api/v1/documentos/1")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TEST 07 — INVALID CATEGORY REJECTED (400 BAD REQUEST)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("Test 07: Subida con categoría no permitida es rechazada con 400 Bad Request protegiendo CK_DOCUMENTOS_CAT")
    public void test07_invalidCategory_rejected400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("archivo", "dummy.pdf", "application/pdf", "dummy".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-InvalidCat")
                        .param("categoria", "CATEGORIA_INVENTADA")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Categoría inválida")));
    }

    // =========================================================================
    // TEST 08 — UPDATE METADATA (200 OK)
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("Test 08: Modificación de metadatos de documento (título, categoría, público) exitosa en API y Oracle")
    public void test08_updateDocumentMetadata_success() throws Exception {
        byte[] pdfBytes = "Acta de asamblea ordinaria".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "acta_01.pdf", "application/pdf", pdfBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Acta-08")
                        .param("categoria", "ACTA_ASAMBLEA")
                        .param("esPublicoResidentes", "N")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Update to public and new description
        DocumentoDTO updateDto = new DocumentoDTO();
        updateDto.setTitulo("DOC-TEST-Acta-08-Actualizada");
        updateDto.setCategoria("ACTA_ASAMBLEA");
        updateDto.setDescripcion("Acta firmada y aprobada por los copropietarios");
        updateDto.setEsPublicoResidentes("S");

        mockMvc.perform(put("/api/v1/documentos/" + idDoc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto))
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.titulo").value("DOC-TEST-Acta-08-Actualizada"))
                .andExpect(jsonPath("$.data.esPublicoResidentes").value("S"));
    }

    // =========================================================================
    // TEST 09 — SOFT DELETE (200 OK)
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("Test 09: Eliminación lógica (soft delete) marca ESTADO='ELIMINADO' y lo excluye de listas activas")
    public void test09_softDeleteDocument_success() throws Exception {
        byte[] pdfBytes = "Póliza temporal a ser eliminada".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("archivo", "poliza_temp.pdf", "application/pdf", pdfBytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Poliza-09")
                        .param("categoria", "POLIZA_SEGURO")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Delete document
        mockMvc.perform(delete("/api/v1/documentos/" + idDoc)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Direct DB verification: marked ELIMINADO
        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM DOCUMENTOS WHERE ID_DOCUMENTO = ?",
                    String.class, idDoc);
            assertEquals("ELIMINADO", estado);
        } finally {
            clearContext();
        }

        // Must be excluded from /admin list
        mockMvc.perform(get("/api/v1/documentos/admin")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", not(hasItem(hasEntry("idDocumento", idDoc)))));

        // Download fails with 404 (or conflict/not found)
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // TEST 10 — ADD VERSION (VERSION CONTROL)
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("Test 10: Control de versiones: agregar versión 2 a documento existente incrementa NUMERO_VERSION y actualiza contenido")
    public void test10_addVersion_success() throws Exception {
        byte[] v1Bytes = "Versión 1 del RUT".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fileV1 = new MockMultipartFile("archivo", "rut_v1.pdf", "application/pdf", v1Bytes);

        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(fileV1)
                        .param("titulo", "DOC-TEST-RUT-10")
                        .param("categoria", "RUT_MATRICULA")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Upload Version 2
        byte[] v2Bytes = "Versión 2 del RUT con matrícula actualizada 2026".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fileV2 = new MockMultipartFile("archivo", "rut_v2.pdf", "application/pdf", v2Bytes);

        mockMvc.perform(multipart("/api/v1/documentos/" + idDoc + "/versiones")
                        .file(fileV2)
                        .param("notasCambio", "Actualización con vigencia 2026")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.numeroVersion").value(2));

        // Downloading now returns Version 2 content
        byte[] downloaded = mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("rut_v2.pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(new String(v2Bytes), new String(downloaded));
    }

    // =========================================================================
    // TEST 11 — SAME-ORGANIZATION PROPERTY-TO-PROPERTY ISOLATION
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("Test 11: Aislamiento estricto entre propiedades de la misma organización (Propiedad 1 vs Propiedad 1B)")
    public void test11_sameOrganization_propertyToPropertyIsolation() throws Exception {
        // Admin of Prop 1 uploads doc in Prop 1
        MockMultipartFile file = new MockMultipartFile("archivo", "doc_prop1.pdf", "application/pdf", "Contenido Prop 1".getBytes());
        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-Prop1-Isolated-11")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDocProp1 = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // 1. Admin Prop 1B cannot see doc in list
        mockMvc.perform(get("/api/v1/documentos/admin")
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", not(hasItem(hasEntry("idDocumento", idDocProp1)))));

        // 2. Admin Prop 1B cannot get doc details (403 or 404)
        mockMvc.perform(get("/api/v1/documentos/" + idDocProp1)
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 3. Admin Prop 1B cannot download doc (403 or 404)
        mockMvc.perform(get("/api/v1/documentos/" + idDocProp1 + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 4. Admin Prop 1B cannot update metadata (403 or 404)
        DocumentoDTO hackDto = new DocumentoDTO();
        hackDto.setTitulo("Hacked Title");
        hackDto.setCategoria("MANUAL_CONVIVENCIA");
        mockMvc.perform(put("/api/v1/documentos/" + idDocProp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hackDto))
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 5. Admin Prop 1B cannot add version (403 or 404)
        MockMultipartFile hackFile = new MockMultipartFile("archivo", "hack.pdf", "application/pdf", "hack".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/" + idDocProp1 + "/versiones")
                        .file(hackFile)
                        .param("notasCambio", "hack version")
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 6. Admin Prop 1B cannot delete (403 or 404)
        mockMvc.perform(delete("/api/v1/documentos/" + idDocProp1)
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });
    }

    // =========================================================================
    // TEST 12 — CROSS-TENANT VERSION ISOLATION & ORACLE VPD VERIFICATION
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("Test 12: Aislamiento adversarial de VERSIONES_DOCUMENTO entre organizaciones y verificación de política VPD")
    public void test12_crossTenantVersionIsolation_adversarial() throws Exception {
        MockMultipartFile file = new MockMultipartFile("archivo", "secreto_org1.pdf", "application/pdf", "Contenido Secreto Org 1".getBytes());
        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-CrossTenant-12")
                        .param("categoria", "ESTADO_FINANCIERO")
                        .param("esPublicoResidentes", "N")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // 1. Adversary from Org 2 cannot download
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 2. Adversary from Org 2 cannot inject a new version
        MockMultipartFile fileAdv = new MockMultipartFile("archivo", "adv_v2.pdf", "application/pdf", "Inyeccion maliciosa".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/" + idDoc + "/versiones")
                        .file(fileAdv)
                        .param("notasCambio", "Malicious version")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Must return 403 or 404");
                });

        // 3. Direct Oracle DB verification: when session context is Org 2 / Prop 2,
        // Oracle VPD predicate generated by PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD
        // strictly scopes to Prop 2 and filters out idDoc (which belongs to Prop 1).
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ADMIN_PROP_2)
                .organizationId(ORG_2_ID)
                .propertyId(PROP_2_ID)
                .roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD")
                .build());
        try {
            jdbcTemplate.execute(String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %d, %d, 'ADMIN_PROPIEDAD'); END;",
                    USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, ORG_2_ID, PROP_2_ID));
            String predicate = jdbcTemplate.queryForObject(
                    "SELECT PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD('SAED_BASELINE_TEST_01', 'VERSIONES_DOCUMENTO') FROM DUAL",
                    String.class);
            assertNotNull(predicate);
            org.junit.jupiter.api.Assertions.assertTrue(predicate.contains("WHERE id_propiedad = " + PROP_2_ID),
                    "VPD predicate must enforce restriction to Prop 2: " + predicate);

            // Verify evaluating the VPD predicate against the database yields 0 rows for foreign document
            Integer countWithPredicate = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ? AND " + predicate,
                    Integer.class, idDoc);
            assertEquals(0, countWithPredicate, "VPD policy predicate must evaluate to 0 rows for cross-tenant document versions");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 13 — STORAGE SECURITY & PATH TRAVERSAL REJECTION
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("Test 13: Seguridad del storage: rechazo de path traversal (../ o ..\\\\) en nombre de archivo (400 Bad Request)")
    public void test13_storageSecurity_pathTraversalRejected() throws Exception {
        MockMultipartFile traversalFile = new MockMultipartFile("archivo", "../../etc/passwd.pdf", "application/pdf", "malicious payload".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(traversalFile)
                        .param("titulo", "DOC-TEST-Traversal-13")
                        .param("categoria", "OTRO")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        MockMultipartFile traversalWinFile = new MockMultipartFile("archivo", "..\\windows\\system32\\cmd.pdf", "application/pdf", "malicious payload".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(traversalWinFile)
                        .param("titulo", "DOC-TEST-Traversal-Win-13")
                        .param("categoria", "OTRO")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // TEST 14 — CONCURRENT VERSION UPLOADS SERIALIZED BY PESSIMISTIC LOCKING
    // =========================================================================
    @Test
    @Order(14)
    @DisplayName("Test 14: Concurrencia de versionamiento: subidas concurrentes se serializan mediante bloqueo pesimista en Oracle")
    public void test14_concurrentVersionUploads_serializedPessimisticLock() throws Exception {
        MockMultipartFile fileV1 = new MockMultipartFile("archivo", "contrato_v1.pdf", "application/pdf", "Contrato Base v1".getBytes());
        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(fileV1)
                        .param("titulo", "DOC-TEST-Concurrent-14")
                        .param("categoria", "CONTRATO_PROVEEDOR")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Launch 2 concurrent version uploads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> uploadTaskA = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            MockMultipartFile fileA = new MockMultipartFile("archivo", "contrato_vA.pdf", "application/pdf", "Contrato Concurrente A".getBytes());
            return mockMvc.perform(multipart("/api/v1/documentos/" + idDoc + "/versiones")
                            .file(fileA)
                            .param("notasCambio", "Version concurrent A")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andReturn().getResponse().getStatus();
        };

        Callable<Integer> uploadTaskB = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            MockMultipartFile fileB = new MockMultipartFile("archivo", "contrato_vB.pdf", "application/pdf", "Contrato Concurrente B".getBytes());
            return mockMvc.perform(multipart("/api/v1/documentos/" + idDoc + "/versiones")
                            .file(fileB)
                            .param("notasCambio", "Version concurrent B")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(uploadTaskA);
        Future<Integer> f2 = executor.submit(uploadTaskB);

        startLatch.countDown(); // Trigger both simultaneously

        int status1 = f1.get(15, TimeUnit.SECONDS);
        int status2 = f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(201, status1, "Upload 1 must succeed with 201 Created");
        assertEquals(201, status2, "Upload 2 must succeed with 201 Created");

        // Verify that exactly 3 versions exist in Oracle without collision or gaps: 1, 2, 3
        setElevatedContext();
        try {
            List<Integer> versions = jdbcTemplate.queryForList(
                    "SELECT NUMERO_VERSION FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ? ORDER BY NUMERO_VERSION ASC",
                    Integer.class, idDoc);
            assertEquals(List.of(1, 2, 3), versions, "Pessimistic locking must produce sequential versions [1, 2, 3]");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 15 — SOFT DELETE COMPREHENSIVE SAFEGUARDS & AUDIT TRAIL
    // =========================================================================
    @Test
    @Order(15)
    @DisplayName("Test 15: Soft delete integral: documento 'ELIMINADO' bloquea descarga, edición y nuevas versiones preservando historial")
    public void test15_softDelete_comprehensiveSafeguards() throws Exception {
        MockMultipartFile file = new MockMultipartFile("archivo", "manual_a_borrar.pdf", "application/pdf", "Manual inicial".getBytes());
        String uploadResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(file)
                        .param("titulo", "DOC-TEST-SoftDelete-15")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDoc = ((Number) objectMapper.readTree(uploadResp).at("/data/idDocumento").numberValue()).longValue();

        // Delete document
        mockMvc.perform(delete("/api/v1/documentos/" + idDoc)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // 1. GET details -> 404
        mockMvc.perform(get("/api/v1/documentos/" + idDoc)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());

        // 2. Download -> 404
        mockMvc.perform(get("/api/v1/documentos/" + idDoc + "/descargar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());

        // 3. Update metadata -> 404
        DocumentoDTO updateDto = new DocumentoDTO();
        updateDto.setTitulo("Intento Actualizar Eliminado");
        updateDto.setCategoria("MANUAL_CONVIVENCIA");
        mockMvc.perform(put("/api/v1/documentos/" + idDoc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto))
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());

        // 4. Add version -> 404
        MockMultipartFile newVersion = new MockMultipartFile("archivo", "v2_fail.pdf", "application/pdf", "v2 bytes".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/" + idDoc + "/versiones")
                        .file(newVersion)
                        .param("notasCambio", "Should fail")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());

        // 5. Oracle DB historical retention: row still exists with ESTADO='ELIMINADO' and versions preserved
        setElevatedContext();
        try {
            Integer docCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM DOCUMENTOS WHERE ID_DOCUMENTO = ? AND ESTADO = 'ELIMINADO'",
                    Integer.class, idDoc);
            assertEquals(1, docCount, "Historical row in DOCUMENTOS must be retained with ESTADO='ELIMINADO'");

            Integer verCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ?",
                    Integer.class, idDoc);
            assertEquals(1, verCount, "Historical version rows in VERSIONES_DOCUMENTO must be preserved for audit");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 16 — COMPREHENSIVE RESIDENT SECURITY MATRIX
    // =========================================================================
    @Test
    @Order(16)
    @DisplayName("Test 16: Matriz de seguridad para RESIDENTE: público (200), privado (403), ajeno (403/404), eliminado (404), manipulado (404)")
    public void test16_residentSecurityMatrix() throws Exception {
        // A. Public doc in own property
        MockMultipartFile pubFile = new MockMultipartFile("archivo", "pub.pdf", "application/pdf", "Public content".getBytes());
        String pubResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(pubFile)
                        .param("titulo", "DOC-TEST-Matrix-Pub-16")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idPub = ((Number) objectMapper.readTree(pubResp).at("/data/idDocumento").numberValue()).longValue();

        // B. Private doc in own property
        MockMultipartFile privFile = new MockMultipartFile("archivo", "priv.pdf", "application/pdf", "Private content".getBytes());
        String privResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(privFile)
                        .param("titulo", "DOC-TEST-Matrix-Priv-16")
                        .param("categoria", "ESTADO_FINANCIERO")
                        .param("esPublicoResidentes", "N")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idPriv = ((Number) objectMapper.readTree(privResp).at("/data/idDocumento").numberValue()).longValue();

        // C. Public doc in foreign property (Org 2 / Prop 2)
        MockMultipartFile forFile = new MockMultipartFile("archivo", "foreign.pdf", "application/pdf", "Foreign content".getBytes());
        String forResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(forFile)
                        .param("titulo", "DOC-TEST-Matrix-Foreign-16")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idForeign = ((Number) objectMapper.readTree(forResp).at("/data/idDocumento").numberValue()).longValue();

        // D. Deleted doc in own property
        MockMultipartFile delFile = new MockMultipartFile("archivo", "del.pdf", "application/pdf", "Deleted content".getBytes());
        String delResp = mockMvc.perform(multipart("/api/v1/documentos/upload")
                        .file(delFile)
                        .param("titulo", "DOC-TEST-Matrix-Del-16")
                        .param("categoria", "MANUAL_CONVIVENCIA")
                        .param("esPublicoResidentes", "S")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idDel = ((Number) objectMapper.readTree(delResp).at("/data/idDocumento").numberValue()).longValue();
        mockMvc.perform(delete("/api/v1/documentos/" + idDel)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // RESIDENT VERIFICATIONS:
        // 1. Public in own property -> 200 OK
        mockMvc.perform(get("/api/v1/documentos/" + idPub + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk());

        // 2. Private in own property -> 403 Forbidden
        mockMvc.perform(get("/api/v1/documentos/" + idPriv + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        // 3. Foreign doc -> 403 or 404
        mockMvc.perform(get("/api/v1/documentos/" + idForeign + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Foreign doc must be 403 or 404");
                });

        // 4. Deleted doc -> 404 Not Found
        mockMvc.perform(get("/api/v1/documentos/" + idDel + "/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isNotFound());

        // 5. Manipulated / Non-existent ID -> 404 Not Found
        mockMvc.perform(get("/api/v1/documentos/99999999/descargar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isNotFound());
    }
}
