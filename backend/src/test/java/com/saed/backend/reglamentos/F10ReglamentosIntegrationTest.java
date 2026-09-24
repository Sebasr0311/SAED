package com.saed.backend.reglamentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.reglamentos.dto.ReglamentoCreateRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoPublicarRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoUpdateRequestDTO;
import com.saed.backend.security.jwt.JwtProvider;
import java.security.MessageDigest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10ReglamentosIntegrationTest — Comprehensive Integration, Security, and Concurrency Test Suite
 * for F10-02 Reglamentos y Normativa in SAED 2.0 (Spring Boot 3 + Oracle XE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F10ReglamentosIntegrationTest {

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

    // Users and Assignments
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

    // Multi-tenant scopes
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_1B_ID = 8899L;
    private static final long PROP_2_ID = 8802L;

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
                    DELETE FROM REGLAMENTOS_NORMATIVA WHERE TITULO LIKE 'REGLAM-TEST-%';
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
                    DELETE FROM REGLAMENTOS_NORMATIVA WHERE TITULO LIKE 'REGLAM-TEST-%';
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

        // Admin Prop 1B
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
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
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

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
        Integer mem = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ? AND ESTADO = 'ACTIVA'", Integer.class, id);
        if (mem == null || mem == 0) {
            jdbcTemplate.update("INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) VALUES (?, 1, 'ACTIVA', SYSDATE, SYSDATE + 365, 'N')", id);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) " +
                            "VALUES (?, ?, 1, ?, 'Calle Test 123', 'Bogota', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, COEFICIENTE, ESTADO) VALUES (?, ?, ?, 0.05, 'ACTIVA')",
                    id, propId, identificador);
        }
    }

    private void ensurePersona(long id, String doc, String primerNom, String primerApe, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) " +
                            "VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, primerNom, primerApe, email);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
                            "VALUES (?, ?, ?, ?, '$2a$10$dummyHashTestingPurposesOnly1234567890', 'ACTIVO')",
                    id, personaId, username, email);
        }
    }

    private void ensureAsignacion(long id, long userId, String rol, Long orgId, Long propId, Long unidadId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) " +
                            "VALUES (?, ?, (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), ?, ?, ?, 'ACTIVA')",
                    id, userId, rol, orgId, propId, unidadId);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long createTestDocumento(long orgId, Long propId, String titulo, String esPublico) {
        String docSql = "INSERT INTO DOCUMENTOS (ID_ORGANIZACION, ID_PROPIEDAD, CATEGORIA, TITULO, " +
                        "ES_PUBLICO_RESIDENTES, ESTADO, CREADO_POR, FECHA_CREACION) " +
                        "VALUES (?, ?, 'REGLAMENTO_INTERNO', ?, ?, 'ACTIVO', 1, CURRENT_TIMESTAMP)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(docSql, new String[]{"ID_DOCUMENTO"});
            ps.setLong(1, orgId);
            if (propId != null) ps.setLong(2, propId); else ps.setNull(2, Types.NUMERIC);
            ps.setString(3, titulo);
            ps.setString(4, esPublico);
            return ps;
        }, kh);
        Long docId = kh.getKey().longValue();

        byte[] content = ("PDF Content for " + titulo).getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(content);
        var stored = fileStorageService.storeBytes(content, "test_" + docId + ".pdf", "application/pdf", "documentos", orgId);
        String storedPath = stored.relativePath();

        String verSql = "INSERT INTO VERSIONES_DOCUMENTO (ID_DOCUMENTO, NUMERO_VERSION, ARCHIVO_URL, " +
                        "ARCHIVO_NOMBRE_ORIG, ARCHIVO_TAMANO_BYTES, ARCHIVO_MIME_TYPE, ARCHIVO_SHA256, SUBIDO_POR) " +
                        "VALUES (?, 1, ?, ?, ?, 'application/pdf', ?, 1)";
        jdbcTemplate.update(verSql, docId, storedPath, titulo + ".pdf", (long) content.length, sha256);

        return docId;
    }

    // =========================================================================
    // 1. CRUD & BASIC LIFECYCLE
    // =========================================================================

    @Test
    @Order(1)
    public void test01_crearBorrador_adminPropiedad_exitoso() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-01", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-01 Reglamento Interno Copropiedad");
        req.setDescripcion("Normativa interna 2026");
        req.setIdDocumento(docId);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idReglamento").isNumber())
                .andExpect(jsonPath("$.data.tipoNormativa").value("REGLAMENTO_INTERNO"))
                .andExpect(jsonPath("$.data.titulo").value("REGLAM-TEST-01 Reglamento Interno Copropiedad"))
                .andExpect(jsonPath("$.data.estado").value("BORRADOR"));
    }

    @Test
    @Order(2)
    public void test02_crearBorrador_validarDocumentoInexistente_falla400() throws Exception {
        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-02 Doc Inexistente");
        req.setIdDocumento(999999999L);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    public void test03_crearBorrador_documentoOtraPropiedad_falla400() throws Exception {
        setElevatedContext();
        Long docProp2 = createTestDocumento(ORG_2_ID, PROP_2_ID, "DOC-TEST-03-PROP2", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-03 Doc Foraneo");
        req.setIdDocumento(docProp2);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    public void test04_crearBorrador_tipoNormativaInvalido_falla400() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-04", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("TIPO_INVALIDO_XYZ");
        req.setTitulo("REGLAM-TEST-04 Tipo Invalido");
        req.setIdDocumento(docId);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    public void test05_crearBorrador_tituloVacio_falla400() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-05", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("");
        req.setIdDocumento(docId);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    public void test06_actualizarBorrador_exitoso() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-06", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("MANUAL_CONVIVENCIA");
        req.setTitulo("REGLAM-TEST-06 Original");
        req.setIdDocumento(docId);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        ReglamentoUpdateRequestDTO updateReq = new ReglamentoUpdateRequestDTO();
        updateReq.setTipoNormativa("MANUAL_CONVIVENCIA");
        updateReq.setTitulo("REGLAM-TEST-06 Titulo Modificado");
        updateReq.setDescripcion("Nueva descripcion de convivencia");
        updateReq.setIdDocumento(docId);

        mockMvc.perform(put("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titulo").value("REGLAM-TEST-06 Titulo Modificado"))
                .andExpect(jsonPath("$.data.descripcion").value("Nueva descripcion de convivencia"));
    }

    @Test
    @Order(7)
    public void test07_actualizarBorrador_yaPublicado_falla400() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-07", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("MANUAL_ZONAS_COMUNES");
        req.setTitulo("REGLAM-TEST-07 Ya Publicado");
        req.setIdDocumento(docId);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Publicar
        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Intentar actualizar
        ReglamentoUpdateRequestDTO updateReq = new ReglamentoUpdateRequestDTO();
        updateReq.setTipoNormativa("MANUAL_ZONAS_COMUNES");
        updateReq.setTitulo("REGLAM-TEST-07 Intento Modificar Publicado");
        updateReq.setIdDocumento(docId);

        mockMvc.perform(put("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    public void test08_publicarReglamento_primerReglamento_exitoso() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-08", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("MANUAL_POLITICA_MASCOTAS");
        req.setTitulo("REGLAM-TEST-08 Mascotas");
        req.setIdDocumento(docId);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        ReglamentoPublicarRequestDTO pubReq = new ReglamentoPublicarRequestDTO();
        pubReq.setFechaEntradaEnVigor(LocalDate.now());

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pubReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.data.fechaPublicacion").isNotEmpty());

        // Verificar que el documento fue marcado como publico para residentes
        setElevatedContext();
        String esPublico = jdbcTemplate.queryForObject(
                "SELECT ES_PUBLICO_RESIDENTES FROM DOCUMENTOS WHERE ID_DOCUMENTO = ?",
                String.class, docId);
        clearContext();
        assertEquals("S", esPublico);
    }

    @Test
    @Order(9)
    public void test09_publicarReglamento_yaPublicado_falla409() throws Exception {
        setElevatedContext();
        Long docId = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-09", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("ESTATUTO_COPROPIEDAD");
        req.setTitulo("REGLAM-TEST-09 Estatutos");
        req.setIdDocumento(docId);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Segundo intento de publicacion
        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(10)
    public void test10_publicarReglamento_reemplazaAnteriorVigente_atomico() throws Exception {
        setElevatedContext();
        Long docA = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-10-A", "N");
        Long docB = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-10-B", "N");
        clearContext();

        // 1. Crear y publicar version A
        ReglamentoCreateRequestDTO reqA = new ReglamentoCreateRequestDTO();
        reqA.setTipoNormativa("REGLAMENTO_INTERNO");
        reqA.setTitulo("REGLAM-TEST-10 Version A");
        reqA.setIdDocumento(docA);

        MvcResult resA = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idA = objectMapper.readTree(resA.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idA + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("PUBLICADO"));

        // 2. Crear y publicar version B
        ReglamentoCreateRequestDTO reqB = new ReglamentoCreateRequestDTO();
        reqB.setTipoNormativa("REGLAMENTO_INTERNO");
        reqB.setTitulo("REGLAM-TEST-10 Version B");
        reqB.setIdDocumento(docB);

        MvcResult resB = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idB = objectMapper.readTree(resB.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idB + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("PUBLICADO"));

        // 3. Verificar en base de datos: A debe ser REEMPLAZADO, B debe ser PUBLICADO
        setElevatedContext();
        String estadoA = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM REGLAMENTOS_NORMATIVA WHERE ID_REGLAMENTO = ?",
                String.class, idA);
        String estadoB = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM REGLAMENTOS_NORMATIVA WHERE ID_REGLAMENTO = ?",
                String.class, idB);
        clearContext();

        assertEquals("REEMPLAZADO", estadoA);
        assertEquals("PUBLICADO", estadoB);
    }

    @Test
    @Order(11)
    public void test11_inactivarReglamento_desdePublicado_exitoso() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-11", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("OTRO");
        req.setTitulo("REGLAM-TEST-11 Para Inactivar");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Inactivar
        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/inactivar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("INACTIVO"));
    }

    @Test
    @Order(12)
    public void test12_inactivarReglamento_yaInactivo_falla409() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-12", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("OTRO");
        req.setTitulo("REGLAM-TEST-12 Ya Inactivo");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/inactivar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Segundo intento de inactivar
        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/inactivar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 2. RESIDENT ACCESSIBILITY & VISIBILITY
    // =========================================================================

    @Test
    @Order(13)
    public void test13_consultarVigente_residente_exitoso() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-13", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("MANUAL_CONVIVENCIA");
        req.setTitulo("REGLAM-TEST-13 Convivencia Vigente");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Residente consulta vigente
        mockMvc.perform(get("/api/v1/reglamentos/vigente?tipoNormativa=MANUAL_CONVIVENCIA")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idReglamento").value(idReg))
                .andExpect(jsonPath("$.data.estado").value("PUBLICADO"));
    }

    @Test
    @Order(14)
    public void test14_consultarVigente_sinNormativaVigente_retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/reglamentos/vigente?tipoNormativa=ESTATUTO_COPROPIEDAD")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    public void test15_listarReglamentos_admin_veTodosEstados() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-15", "N");
        clearContext();

        // Crear un borrador
        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("OTRO");
        req.setTitulo("REGLAM-TEST-15 Borrador Admin");
        req.setIdDocumento(doc);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Admin consulta
        mockMvc.perform(get("/api/v1/reglamentos/admin")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(16)
    public void test16_listarReglamentos_residente_soloVePublicados() throws Exception {
        setElevatedContext();
        Long docBorrador = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-16-BORR", "N");
        Long docPub = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-16-PUB", "N");
        clearContext();

        // 1. Crear borrador
        ReglamentoCreateRequestDTO req1 = new ReglamentoCreateRequestDTO();
        req1.setTipoNormativa("MANUAL_ZONAS_COMUNES");
        req1.setTitulo("REGLAM-TEST-16 Zonas Borrador");
        req1.setIdDocumento(docBorrador);

        mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // 2. Crear y publicar otro
        ReglamentoCreateRequestDTO req2 = new ReglamentoCreateRequestDTO();
        req2.setTipoNormativa("MANUAL_POLITICA_MASCOTAS");
        req2.setTitulo("REGLAM-TEST-16 Mascotas Publicado");
        req2.setIdDocumento(docPub);

        MvcResult res2 = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idPub = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idPub + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // 3. Residente lista reglamentos: NO debe ver BORRADOR
        mockMvc.perform(get("/api/v1/reglamentos/residente")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].estado", everyItem(equalTo("PUBLICADO"))));
    }

    // =========================================================================
    // 3. BINARY STREAMING & INTEGRITY
    // =========================================================================

    @Test
    @Order(17)
    public void test17_descargarDocumento_residente_reglamentoPublicado_exitoso() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-17", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-17 Descarga Residente");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Descarga por residente
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().string(containsString("PDF Content for DOC-TEST-17")));
    }

    @Test
    @Order(18)
    public void test18_descargarDocumento_residente_reglamentoBorrador_falla403() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-18", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-18 Borrador Bloqueado");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Intento de descarga de borrador por residente: debe ser rechazado con 403 o 404 (oculto por RLS)
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });
    }

    @Test
    @Order(19)
    public void test19_descargarDocumento_portero_falla403() throws Exception {
        // 1. GET /admin -> 403
        mockMvc.perform(get("/api/v1/reglamentos/admin")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 2. GET /residente -> 403
        mockMvc.perform(get("/api/v1/reglamentos/residente")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 3. GET /vigente -> 403
        mockMvc.perform(get("/api/v1/reglamentos/vigente")
                .param("tipoNormativa", "REGLAMENTO_INTERNO")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 4. GET /{id} -> 403
        mockMvc.perform(get("/api/v1/reglamentos/1")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 5. GET /{id}/descargar -> 403
        mockMvc.perform(get("/api/v1/reglamentos/1/descargar")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 6. POST / -> 403
        mockMvc.perform(post("/api/v1/reglamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoNormativa\":\"REGLAMENTO_INTERNO\",\"titulo\":\"Hacked\",\"idDocumento\":1}")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 7. PUT /{id} -> 403
        mockMvc.perform(put("/api/v1/reglamentos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoNormativa\":\"REGLAMENTO_INTERNO\",\"titulo\":\"Hacked\",\"idDocumento\":1}")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 8. POST /{id}/publicar -> 403
        mockMvc.perform(post("/api/v1/reglamentos/1/publicar")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // 9. POST /{id}/inactivar -> 403
        mockMvc.perform(post("/api/v1/reglamentos/1/inactivar")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. MULTI-TENANT ISOLATION
    // =========================================================================

    @Test
    @Order(20)
    public void test20_aislamientoMultiTenant_adminPropiedadA_noVeReglamentosPropiedadB() throws Exception {
        setElevatedContext();
        Long docProp1 = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-20-PROP1", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-20 En Prop 1");
        req.setIdDocumento(docProp1);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Admin Prop 1B (misma organizacion, pero propiedad diferente) NO debe tener acceso
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenAdminProp1B)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });
    }

    @Test
    @Order(21)
    public void test21_aislamientoMultiTenant_residentePropiedadA_noVeReglamentosPropiedadB() throws Exception {
        setElevatedContext();
        Long docProp2 = createTestDocumento(ORG_2_ID, PROP_2_ID, "DOC-TEST-21-PROP2", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-21 En Prop 2");
        req.setIdDocumento(docProp2);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(status().isOk());

        // Residente de Prop 1 intenta acceder a reglamento de Prop 2
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });

        // Residente de Prop 1 intenta descargar reglamento de Prop 2
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });
    }

    @Test
    @Order(22)
    public void test22_aislamientoOrganizacion_adminOrgA_noVeReglamentosOrgB() throws Exception {
        setElevatedContext();
        Long docOrg2 = createTestDocumento(ORG_2_ID, PROP_2_ID, "DOC-TEST-22-ORG2", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-22 En Org 2");
        req.setIdDocumento(docOrg2);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Admin de Org 1 intenta ver reglamento de Org 2
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });
    }

    // =========================================================================
    // 5. DATABASE CONDITIONAL UNIQUE INDEX & CONCURRENCY
    // =========================================================================

    @Test
    @Order(23)
    public void test23_indiceUnicoCondicional_evitaDosVigentesEnBaseDeDatos() {
        setElevatedContext();
        Long doc1 = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-23-A", "N");
        Long doc2 = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-23-B", "N");

        String insertSql = "INSERT INTO REGLAMENTOS_NORMATIVA (ID_ORGANIZACION, ID_PROPIEDAD, TIPO_NORMATIVA, " +
                           "TITULO, ID_DOCUMENTO, ESTADO, FECHA_ENTRADA_EN_VIGOR, FECHA_PUBLICACION) " +
                           "VALUES (?, ?, 'REGLAMENTO_INTERNO', ?, ?, 'PUBLICADO', CURRENT_DATE, CURRENT_TIMESTAMP)";

        // First published insert succeeds
        jdbcTemplate.update(insertSql, ORG_1_ID, PROP_1_ID, "REGLAM-TEST-23 First", doc1);

        // Second published insert of the same type and property MUST violate UQ_REGLAM_PROP_TIPO_VIG
        assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(insertSql, ORG_1_ID, PROP_1_ID, "REGLAM-TEST-23 Second Conflict", doc2);
        });

        clearContext();
    }

    @Test
    @Order(24)
    public void test24_concurrencia_publicacionSimultanea_serializada() throws Exception {
        setElevatedContext();
        Long doc1 = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-24-A", "N");
        Long doc2 = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-24-B", "N");
        clearContext();

        // Create draft 1
        ReglamentoCreateRequestDTO req1 = new ReglamentoCreateRequestDTO();
        req1.setTipoNormativa("MANUAL_CONVIVENCIA");
        req1.setTitulo("REGLAM-TEST-24 Draft 1");
        req1.setIdDocumento(doc1);

        MvcResult res1 = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Create draft 2
        ReglamentoCreateRequestDTO req2 = new ReglamentoCreateRequestDTO();
        req2.setTipoNormativa("MANUAL_CONVIVENCIA");
        req2.setTitulo("REGLAM-TEST-24 Draft 2");
        req2.setIdDocumento(doc2);

        MvcResult res2 = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Execute concurrent publication
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Integer> pubTask1 = () -> {
            startGate.await();
            return mockMvc.perform(post("/api/v1/reglamentos/" + idReg1 + "/publicar")
                    .header("Authorization", "Bearer " + tokenAdminProp1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andReturn().getResponse().getStatus();
        };

        Callable<Integer> pubTask2 = () -> {
            startGate.await();
            return mockMvc.perform(post("/api/v1/reglamentos/" + idReg2 + "/publicar")
                    .header("Authorization", "Bearer " + tokenAdminProp1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(pubTask1);
        Future<Integer> f2 = executor.submit(pubTask2);

        startGate.countDown();

        int status1 = f1.get(10, TimeUnit.SECONDS);
        int status2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Both executions must complete with 200 OK (serialized via pessimistic lock)
        assertEquals(200, status1);
        assertEquals(200, status2);

        // Verify database: exactly one is PUBLICADO, the other is REEMPLAZADO
        setElevatedContext();
        Integer publicadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM REGLAMENTOS_NORMATIVA WHERE ID_PROPIEDAD = ? AND TIPO_NORMATIVA = 'MANUAL_CONVIVENCIA' AND ESTADO = 'PUBLICADO'",
                Integer.class, PROP_1_ID);
        Integer reemplazadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM REGLAMENTOS_NORMATIVA WHERE ID_PROPIEDAD = ? AND TIPO_NORMATIVA = 'MANUAL_CONVIVENCIA' AND ESTADO = 'REEMPLAZADO'",
                Integer.class, PROP_1_ID);
        clearContext();

        assertEquals(1, publicadas, "Debe haber exactamente un reglamento publicado");
        assertEquals(1, reemplazadas, "Debe haber exactamente un reglamento reemplazado");
    }

    @Test
    @Order(25)
    public void test25_descargaDocumento_hashSha256_e_integridad() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-25", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-25 Integridad");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Descargar bytes
        byte[] downloadedBytes = mockMvc.perform(get("/api/v1/reglamentos/" + idReg + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String downloadedSha = sha256Hex(downloadedBytes);

        setElevatedContext();
        String storedSha = jdbcTemplate.queryForObject(
                "SELECT ARCHIVO_SHA256 FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ?",
                String.class, doc);
        clearContext();

        assertEquals(storedSha, downloadedSha, "El hash SHA-256 de descarga debe coincidir exactamente con el almacenado");
    }

    @Test
    @Order(26)
    public void test26_publicacion_auditoriaRegistrada() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-26", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("MANUAL_POLITICA_MASCOTAS");
        req.setTitulo("REGLAM-TEST-26 Auditoria");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        mockMvc.perform(post("/api/v1/reglamentos/" + idReg + "/publicar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        setElevatedContext();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'REGLAMENTOS_NORMATIVA' AND ID_ENTIDAD_AFECTADA = ?",
                Integer.class, idReg);
        clearContext();

        assertTrue(auditCount != null && auditCount > 0, "Debe existir al menos un registro de auditoria para la publicacion");
    }

    @Test
    @Order(27)
    public void test27_residente_accesoDirectoPorId_borrador_retorna403() throws Exception {
        setElevatedContext();
        Long doc = createTestDocumento(ORG_1_ID, PROP_1_ID, "DOC-TEST-27", "N");
        clearContext();

        ReglamentoCreateRequestDTO req = new ReglamentoCreateRequestDTO();
        req.setTipoNormativa("REGLAMENTO_INTERNO");
        req.setTitulo("REGLAM-TEST-27 Borrador Oculto");
        req.setIdDocumento(doc);

        MvcResult res = mockMvc.perform(post("/api/v1/reglamentos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReg = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("idReglamento").asLong();

        // Residente intenta consultar por ID un borrador
        mockMvc.perform(get("/api/v1/reglamentos/" + idReg)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 o 404 bajo RLS");
                });
    }
}
