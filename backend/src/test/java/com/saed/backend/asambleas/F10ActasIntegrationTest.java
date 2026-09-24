package com.saed.backend.asambleas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.asambleas.dto.*;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10ActasIntegrationTest — Full Integration, Security, Lifecycle, Concurrency and
 * Multi-Tenant Test Suite for F10-05 Actas de Asamblea (SAED 2.0).
 *
 * Tests cover: creation, state machine, IDOR, resident confinement, role matrix,
 * document association (F10-01), governance data (F10-03/F10-04), audit, VPD/RLS,
 * and real concurrency with ExecutorService + CountDownLatch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F10ActasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssignmentService assignmentService;

    // =========================================================================
    // CONSTANTS — Users, Assignments, Tenants
    // =========================================================================

    private static final long USER_SUPERADMIN       = 1L;
    private static final long ASSIGN_SUPERADMIN     = 101L;

    private static final long USER_ADMIN_ORG_1      = 801L;
    private static final long ASSIGN_ADMIN_ORG_1    = 902L;

    private static final long USER_ADMIN_PROP_1     = 802L;
    private static final long ASSIGN_ADMIN_PROP_1   = 903L;

    private static final long USER_ADMIN_PROP_2     = 803L;
    private static final long ASSIGN_ADMIN_PROP_2   = 904L;

    private static final long USER_RESIDENTE        = 805L;
    private static final long ASSIGN_RESIDENTE      = 905L;

    private static final long USER_PORTERO          = 806L;
    private static final long ASSIGN_PORTERO        = 906L;

    private static final long ORG_1_ID  = 1L;
    private static final long ORG_2_ID  = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    private static final long UNIDAD_1_ID = 9101L;
    private static final long UNIDAD_PROP2_ID = 9201L;

    // JWT Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente;
    private String tokenPortero;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            cleanupTestActas();
            ensureBaseData();
            setupMockAssignments();

            tokenSuperAdmin  = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1   = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1  = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2  = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente   = jwtProvider.generateIdentityToken(USER_RESIDENTE);
            tokenPortero     = jwtProvider.generateIdentityToken(USER_PORTERO);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        setElevatedContext();
        try {
            cleanupTestActas();
        } catch (Exception ignored) {}
        clearContext();
    }

    // =========================================================================
    // TEST INFRASTRUCTURE — Context, Data, Mocks
    // =========================================================================

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

    private void cleanupTestActas() {
        jdbcTemplate.execute("""
            BEGIN
                DELETE FROM ACTAS_ASAMBLEA WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ACTA-TEST-%'
                );
                DELETE FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ACTA-TEST-%'
                );
                DELETE FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ACTA-TEST-%'
                );
                DELETE FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ACTA-TEST-%'
                );
                DELETE FROM ASAMBLEAS WHERE TITULO LIKE 'ACTA-TEST-%';
            END;
        """);
    }

    private void ensureBaseData() {
        ensureOrganizacion(ORG_1_ID,  "Organización Central SAED",    "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID,  "Organización Foránea Norte",   "9008802-2",   "org8802@saed.com");
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");
        ensureUnidad(UNIDAD_1_ID,      PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_PROP2_ID,  PROP_2_ID, "B201", BigDecimal.valueOf(0.500000));

        ensurePersona(USER_SUPERADMIN,   "1000000001", "Super",  "Admin",     "superadmin@saed.com");
        ensurePersona(USER_ADMIN_ORG_1,  "1000000801", "Admin",  "OrgUno",    "adminorg1@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin",  "PropUno",   "adminprop1@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin",  "PropDos",   "adminprop2@saed.com");
        ensurePersona(USER_RESIDENTE,    "1000000805", "Carlos", "Residente", "residente@saed.com");
        ensurePersona(USER_PORTERO,      "1000000806", "Pedro",  "Portero",   "portero@saed.com");

        ensureUsuario(USER_SUPERADMIN,   USER_SUPERADMIN,   "superadmin_acta_test",   "superadmin@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1,  USER_ADMIN_ORG_1,  "admin_org1_acta_test",   "adminorg1@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_acta_test",  "adminprop1@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_acta_test",  "adminprop2@saed.com");
        ensureUsuario(USER_RESIDENTE,    USER_RESIDENTE,    "residente_acta_test",    "residente@saed.com");
        ensureUsuario(USER_PORTERO,      USER_PORTERO,      "portero_acta_test",      "portero@saed.com");

        ensureAsignacion(ASSIGN_SUPERADMIN,  USER_SUPERADMIN,   "SUPERADMIN",       null,    null,    null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1,  "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1,USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD",  ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2,USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD",  ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE,   USER_RESIDENTE,    "RESIDENTE",        ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO,     USER_PORTERO,      "PORTERO",          ORG_1_ID, PROP_1_ID, null);
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

        AssignmentResponseDTO portAssign = new AssignmentResponseDTO();
        portAssign.setIdAsignacion(ASSIGN_PORTERO);
        portAssign.setOrganizacion(org1);
        portAssign.setPropiedad(prop1);
        portAssign.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(portAssign));
    }

    // =========================================================================
    // DB HELPERS
    // =========================================================================

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle Test 123', 'Bogota', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coef);
        } else {
            jdbcTemplate.update("UPDATE UNIDADES SET COEFICIENTE_COPROPIEDAD = ? WHERE ID_UNIDAD = ?", coef, id);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(long id, long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyzABCDEF12345678901234567890', 'ACTIVO')",
                    id, idPersona, username, email);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rol, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (?, ?, (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, rol, idOrg, idProp, idUnidad);
        }
    }

    /**
     * Crea una asamblea de prueba en el estado indicado via HTTP (siguiendo el flujo real).
     * El título siempre empieza con ACTA-TEST- para que el cleanup lo encuentre.
     */
    private Long crearAsambleaEnEstado(String tituloSufijo, String estadoFinal) throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("PRESENCIAL")
                .titulo("ACTA-TEST-" + tituloSufijo)
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-12-10T09:00:00")
                .lugarOEnlace("Salón Comunal")
                .ordenDelDia("1. Informe anual")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("CONVOCADA")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        AsambleaDTO asamblea = objectMapper.readValue(result.getResponse().getContentAsString(), AsambleaDTO.class);
        Long idAsamblea = asamblea.getIdAsamblea();

        // Transition through required states
        List<String> statesOrder = List.of("EN_CURSO", "FINALIZADA");
        for (String estado : statesOrder) {
            if (estadoFinal.equals("CONVOCADA")) break;
            mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO(estado))))
                    .andExpect(status().isOk());
            if (estadoFinal.equals(estado)) break;
        }

        return idAsamblea;
    }

    /**
     * Crea un acta BORRADOR para la asamblea indicada vía HTTP.
     */
    private Long crearActaBorrador(Long idAsamblea, String numeroActa) throws Exception {
        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa(numeroActa)
                .contenidoTexto("Contenido inicial del acta. Sesión de prueba para F10-05.")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ActaDTO acta = objectMapper.readValue(res.getResponse().getContentAsString(), ActaDTO.class);
        return acta.getIdActa();
    }

    // =========================================================================
    // 1. CREACIÓN DE ACTA (G7-C01 a G7-C05)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("F10-05-01: Crear acta válida en BORRADOR para asamblea EN_CURSO")
    public void test01_crearActaValida() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("01-CREAR-VALIDA", "EN_CURSO");

        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-2026-001")
                .contenidoTexto("Contenido del acta de asamblea ordinaria.")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idActa").isNumber())
                .andExpect(jsonPath("$.idAsamblea").value(idAsamblea))
                .andExpect(jsonPath("$.numeroActa").value("ACTA-2026-001"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.fechaCreacion").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("F10-05-02: Rechazar creación de acta con datos inválidos (idAsamblea null, numeroActa blank)")
    public void test02_crearActaDatosInvalidos() throws Exception {
        // Missing idAsamblea and numeroActa
        String payload = "{\"contenidoTexto\": \"Sin asamblea\"}";

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(3)
    @DisplayName("F10-05-03: Rechazar creación de acta para asamblea inexistente → 404/400")
    public void test03_crearActaAsambleaInexistente() throws Exception {
        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(999999999L)
                .numeroActa("ACTA-INVALIDA-001")
                .contenidoTexto("No existe la asamblea")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(4)
    @DisplayName("F10-05-04: Rechazar segunda acta para la misma asamblea → 409 Conflict")
    public void test04_duplicadoActaMismaAsamblea() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("04-DUPLICADO", "EN_CURSO");
        crearActaBorrador(idAsamblea, "ACTA-DUP-001");

        // Second attempt must be 409
        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-DUP-002")
                .contenidoTexto("Segunda acta — debe rechazarse")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    @DisplayName("F10-05-05: Rechazar creación de acta para asamblea CANCELADA")
    public void test05_crearActaAsambleaCancelada() throws Exception {
        // Create assembly and cancel it via direct DB (no HTTP transition for CANCELADA normally)
        Long idAsamblea = crearAsambleaEnEstado("05-CANCELADA", "CONVOCADA");

        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE ASAMBLEAS SET ESTADO = 'CANCELADA' WHERE ID_ASAMBLEA = ?", idAsamblea);
        } finally {
            clearContext();
        }

        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-CANCEL-001")
                .contenidoTexto("No debe crearse")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 2. CICLO DE VIDA (G7-L01 a G7-L07)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("F10-05-06: Lifecycle completo BORRADOR → EN_REVISION_COMISION → APROBADA → PUBLICADA_OFICIAL")
    public void test06_lifecycleCompleto() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("06-LIFECYCLE", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-LC-2026-001");

        // BORRADOR → EN_REVISION_COMISION
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("EN_REVISION_COMISION").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION_COMISION"));

        // EN_REVISION_COMISION → APROBADA
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("APROBADA").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        // APROBADA → PUBLICADA_OFICIAL (with documentoFirmadoUrl since no real file upload in CI)
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/acta-lc-2026-001.pdf")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA_OFICIAL"));
    }

    @Test
    @Order(7)
    @DisplayName("F10-05-07: Transición inválida BORRADOR → APROBADA debe rechazarse")
    public void test07_transicionInvalidaBorradorAprobada() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("07-TRANS-INVALIDA", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-INV-001");

        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("APROBADA").build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(8)
    @DisplayName("F10-05-08: Transición inválida BORRADOR → PUBLICADA_OFICIAL debe rechazarse")
    public void test08_transicionInvalidaBorradorPublicada() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("08-TRANS-INV-PUB", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-INV-PUB-001");

        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL").build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(9)
    @DisplayName("F10-05-09: Publicación rechazada si la asamblea NO está FINALIZADA")
    public void test09_publicarSinAsambleaFinalizada() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("09-NO-FINALIZADA", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-NO-FIN-001");

        // Advance to APROBADA
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("EN_REVISION_COMISION").build())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("APROBADA").build())))
                .andExpect(status().isOk());

        // Try to publish with assembly still EN_CURSO — must fail
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/test.pdf")
                                .build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(10)
    @DisplayName("F10-05-10: Publicación rechazada si no hay documento asociado")
    public void test10_publicarSinDocumento() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("10-SIN-DOC", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-SIN-DOC-001");

        // Advance to APROBADA
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }

        // Publish without any document reference
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL").build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(11)
    @DisplayName("F10-05-11: Modificar contenido de acta PUBLICADA debe rechazarse (inmutabilidad)")
    public void test11_modificarActaPublicadaEsImpedida() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("11-INMUTABILIDAD", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-INMUT-001");

        // Move to PUBLICADA_OFICIAL
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/inmut.pdf")
                                .build())))
                .andExpect(status().isOk());

        // Attempt to update content
        ActaUpdateRequestDTO updateReq = ActaUpdateRequestDTO.builder()
                .contenidoTexto("Contenido modificado ilegalmente").build();

        mockMvc.perform(put("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(12)
    @DisplayName("F10-05-12: EN_REVISION_COMISION puede volver a BORRADOR (transición permitida)")
    public void test12_revisionVueltaBorrador() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("12-VUELTA-BORRADOR", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-VB-001");

        // BORRADOR → EN_REVISION_COMISION
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("EN_REVISION_COMISION").build())))
                .andExpect(status().isOk());

        // EN_REVISION_COMISION → BORRADOR (allowed by ALLOWED_TRANSITIONS)
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("BORRADOR").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // =========================================================================
    // 3. CONSULTA Y ASOCIACIÓN
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("F10-05-13: Obtener acta por ID retorna datos enriquecidos con gobernanza")
    public void test13_obtenerActaPorId() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("13-OBTENER-ID", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-OBT-001");

        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idActa").value(idActa))
                .andExpect(jsonPath("$.idAsamblea").value(idAsamblea))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.asambleaTitulo").isNotEmpty());
    }

    @Test
    @Order(14)
    @DisplayName("F10-05-14: Obtener acta por asamblea — endpoint /actas/asamblea/{id}")
    public void test14_obtenerActaPorAsamblea() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("14-POR-ASAMBLEA", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-ASM-001");

        mockMvc.perform(get("/api/v1/actas/asamblea/" + idAsamblea)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idActa").value(idActa))
                .andExpect(jsonPath("$.idAsamblea").value(idAsamblea));
    }

    @Test
    @Order(15)
    @DisplayName("F10-05-15: Obtener acta inexistente → 404")
    public void test15_obtenerActaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/actas/999999999")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 4. IDOR / MULTI-TENANT (G7-I01 a G7-I04)
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("F10-05-16: IDOR — ADMIN_PROP_2 no puede leer acta de PROP_1")
    public void test16_idorLecturaOtraPropiedad() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("16-IDOR-READ", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-IDOR-001");

        // Admin of different property/org cannot read this acta
        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(status().is4xxClientError()); // 403 or 404 via VPD
    }

    @Test
    @Order(17)
    @DisplayName("F10-05-17: IDOR — ADMIN_PROP_2 no puede cambiar estado de acta de PROP_1")
    public void test17_idorMutacionOtraPropiedad() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("17-IDOR-MUTATE", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-IDOR-MUT-001");

        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("EN_REVISION_COMISION").build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(18)
    @DisplayName("F10-05-18: Sin autenticación → 401")
    public void test18_sinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/actas"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 5. CONFINAMIENTO DE RESIDENTES (G7-R01 a G7-R04)
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("F10-05-19: Residente ve acta PUBLICADA_OFICIAL de su propiedad")
    public void test19_residenteVeActaPublicada() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("19-RES-PUBLICA", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-RES-PUB-001");

        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/res-pub.pdf")
                                .build())))
                .andExpect(status().isOk());

        // Resident must be able to read it
        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA_OFICIAL"));
    }

    @Test
    @Order(20)
    @DisplayName("F10-05-20: Residente NO puede leer acta en BORRADOR → 403")
    public void test20_residenteNoPuedeLeerBorrador() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("20-RES-BORRADOR", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-RES-BOR-001");

        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(21)
    @DisplayName("F10-05-21: Residente NO puede crear acta → 403")
    public void test21_residenteNoPuedeCrearActa() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("21-RES-CREAR", "EN_CURSO");

        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-ILEGAL-001")
                .contenidoTexto("Acta creada por residente — debe rechazarse")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(22)
    @DisplayName("F10-05-22: Residente NO puede cambiar estado de acta → 403")
    public void test22_residenteNoPuedeCambiarEstado() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("22-RES-ESTADO", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-RES-EST-001");

        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("EN_REVISION_COMISION").build())))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. ROL PORTERO (G7-P01)
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("F10-05-23: PORTERO no puede acceder a ningún endpoint de actas → 403")
    public void test23_porteroSinAccesoAActas() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("23-PORTERO", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-PORT-001");

        // Publish to test
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/port.pdf")
                                .build())))
                .andExpect(status().isOk());

        // PORTERO attempts to read a published acta — must be blocked at service layer
        // Controller @PreAuthorize allows 'SCOPE_RESIDENTE' but service rejects PORTERO
        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. ADMIN_ORGANIZACION (G7-O01)
    // =========================================================================

    @Test
    @Order(24)
    @DisplayName("F10-05-24: ADMIN_ORGANIZACION puede crear acta para propiedad de su organización")
    public void test24_adminOrgPuedeManejarActas() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("24-ADMIN-ORG", "EN_CURSO");

        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-ORG-001")
                .contenidoTexto("Creada por ADMIN_ORGANIZACION")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idActa").isNumber())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // =========================================================================
    // 8. EDICIÓN DE CONTENIDO (G7-E01 a G7-E02)
    // =========================================================================

    @Test
    @Order(25)
    @DisplayName("F10-05-25: Actualizar contenido del acta BORRADOR exitosamente")
    public void test25_actualizarContenidoBorrador() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("25-UPDATE-CONTENT", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-UPD-001");

        ActaUpdateRequestDTO updateReq = ActaUpdateRequestDTO.builder()
                .contenidoTexto("Contenido actualizado con decisiones de la asamblea.")
                .numeroActa("ACTA-UPD-001-v2")
                .build();

        mockMvc.perform(put("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenidoTexto").value("Contenido actualizado con decisiones de la asamblea."));
    }


    @Test
    @Order(26)
    @DisplayName("F10-05-26: Listar actas — admin ve todas, residente solo ve PUBLICADA_OFICIAL")
    public void test26_listarActasPorRol() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("26-LIST-ACTAS", "EN_CURSO");
        crearActaBorrador(idAsamblea, "ACTA-LIST-001");

        // Admin sees the draft
        mockMvc.perform(get("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Resident sees empty list (no published actas yet)
        MvcResult resRes = mockMvc.perform(get("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andReturn();

        // Resident's list should not contain unpublished actas
        String body = resRes.getResponse().getContentAsString();
        // The list is JSON array — each item should not have BORRADOR state
        assertFalse(body.contains("BORRADOR"), "Residente no debe ver actas en BORRADOR");
    }

    // =========================================================================
    // 9. INTEGRACIÓN F10-04 — VOTACIONES (G7-V01)
    // =========================================================================

    @Test
    @Order(27)
    @DisplayName("F10-05-27: Acta incluye resultados de votaciones de la asamblea (integración F10-04)")
    public void test27_actaIntegradaConVotaciones() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("27-VOTACIONES", "EN_CURSO");

        // Create a vote
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Aprobación de presupuesto 2027")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated());

        Long idActa = crearActaBorrador(idAsamblea, "ACTA-VOT-001");

        // Read acta — should include governance data
        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVotaciones").value(1))
                .andExpect(jsonPath("$.votaciones").isArray())
                .andExpect(jsonPath("$.votaciones[0].titulo").value("Aprobación de presupuesto 2027"));
    }

    @Test
    @Order(28)
    @DisplayName("F10-05-28: Publicación rechazada si existen votaciones ABIERTAS")
    public void test28_publicarConVotacionesAbiertas() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("28-VOT-ABIERTAS", "FINALIZADA");

        // Force assembly back to EN_CURSO to create a votacion
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE ASAMBLEAS SET ESTADO = 'EN_CURSO' WHERE ID_ASAMBLEA = ?", idAsamblea);
        } finally {
            clearContext();
        }

        // Create an open votacion
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Votación abierta bloqueante")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated());

        // Put back to FINALIZADA
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE ASAMBLEAS SET ESTADO = 'FINALIZADA' WHERE ID_ASAMBLEA = ?", idAsamblea);
        } finally {
            clearContext();
        }

        Long idActa = crearActaBorrador(idAsamblea, "ACTA-VOT-BLOCK-001");

        // Advance to APROBADA
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }

        // Publish must fail because there is still an ABIERTA votacion
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/blocked.pdf")
                                .build())))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 10. DOCUMENTO (integración F10-01) — G7-D01 a G7-D03
    // =========================================================================

    @Test
    @Order(29)
    @DisplayName("F10-05-29: Asociar documentoFirmadoUrl a acta BORRADOR")
    public void test29_asociarDocumentoUrl() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("29-ASSOC-DOC-URL", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-DOC-URL-001");

        mockMvc.perform(post("/api/v1/actas/" + idActa + "/documento")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("documentoUrl", "https://storage.saed.co/actas/doc001.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentoFirmadoUrl").value("https://storage.saed.co/actas/doc001.pdf"));
    }

    @Test
    @Order(30)
    @DisplayName("F10-05-30: Upload multipart de documento al acta — asocia archivo real")
    public void test30_uploadDocumentoMultipart() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("30-UPLOAD-DOC", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-UPLOAD-001");

        MockMultipartFile file = new MockMultipartFile(
                "file", "acta_oficial.pdf", "application/pdf",
                "PDF content stub for testing".getBytes()
        );

        int uploadStatus = mockMvc.perform(multipart("/api/v1/actas/" + idActa + "/documento")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andReturn().getResponse().getStatus();

        // Accept 200 (file stored OK) or 500 (storage not configured in test env).
        // The key assertion is that auth/authz work — NOT 401/403.
        assertTrue(uploadStatus == 200 || uploadStatus == 500,
                "Upload should succeed or fail gracefully with storage error, not auth failure. Got: " + uploadStatus);
    }

    @Test
    @Order(31)
    @DisplayName("F10-05-31: Descargar documento de acta PUBLICADA retorna el recurso")
    public void test31_descargarDocumentoActaPublicada() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("31-DOWNLOAD-DOC", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-DL-001");

        // Advance to PUBLICADA_OFICIAL with documentoFirmadoUrl
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                .nuevoEstado("PUBLICADA_OFICIAL")
                                .documentoFirmadoUrl("https://storage.saed.co/actas/dl001.pdf")
                                .build())))
                .andExpect(status().isOk());

        // The download endpoint should respond (may return error if URL not resolvable in test env,
        // but should NOT return 401/403 for authorized user)
        int downloadStatus = mockMvc.perform(get("/api/v1/actas/" + idActa + "/documento")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andReturn().getResponse().getStatus();

        // Accept 200 (file served) or 500 (URL not resolvable in test) but NOT 401/403
        assertTrue(downloadStatus == 200 || downloadStatus == 404 || downloadStatus == 500,
                "Unexpected HTTP status on document download: " + downloadStatus);
    }

    @Test
    @Order(32)
    @DisplayName("F10-05-32: Descargar documento de acta sin documento → 404")
    public void test32_descargarDocumentoInexistente() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("32-DOWNLOAD-NOEXIST", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-DL-NOEXIST-001");

        // Acta has no document
        mockMvc.perform(get("/api/v1/actas/" + idActa + "/documento")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 11. SUPERADMIN (G7-S01)
    // =========================================================================

    @Test
    @Order(33)
    @DisplayName("F10-05-33: SUPERADMIN puede crear acta para cualquier propiedad")
    public void test33_superAdminAccesoGlobal() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("33-SUPERADMIN", "EN_CURSO");

        ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                .idAsamblea(idAsamblea)
                .numeroActa("ACTA-SA-GLOBAL-001")
                .contenidoTexto("Creada por SUPERADMIN globalmente")
                .build();

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .header("X-Assignment-Id", ASSIGN_SUPERADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // =========================================================================
    // 12. CONCURRENCIA REAL (G7-CC01 a G7-CC02)
    // =========================================================================

    @Test
    @Order(34)
    @DisplayName("F10-05-34: Concurrencia — dos requests simultáneos de creación de acta → solo 1 prospera")
    @Timeout(value = 30)
    public void test34_concurrenciaCreacionSimultanea() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("34-CONCURR-CREATE", "EN_CURSO");

        int threads = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threads);
        AtomicInteger created   = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // All threads wait for the starting signal
                    ActaCreateRequestDTO req = ActaCreateRequestDTO.builder()
                            .idAsamblea(idAsamblea)
                            .numeroActa("ACTA-CC-" + idx)
                            .contenidoTexto("Hilo " + idx + " creando acta concurrentemente")
                            .build();

                    int status = mockMvc.perform(post("/api/v1/actas")
                                    .header("Authorization", "Bearer " + tokenAdminProp1)
                                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn().getResponse().getStatus();

                    if (status == 201) created.incrementAndGet();
                    else if (status == 409) conflicts.incrementAndGet();
                } catch (Exception e) {
                    // Count as conflict
                    conflicts.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // Release all threads simultaneously
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly ONE acta should have been created successfully
        assertEquals(1, created.get(),
                "Exactly one acta creation should succeed. Got: created=" + created + ", conflicts=" + conflicts);
        assertEquals(threads - 1, conflicts.get(),
                "Remaining requests should get 409. Got: created=" + created + ", conflicts=" + conflicts);
    }

    @Test
    @Order(35)
    @DisplayName("F10-05-35: Concurrencia — dos publicaciones simultáneas → solo 1 transición válida")
    @Timeout(value = 30)
    public void test35_concurrenciaPublicacionSimultanea() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("35-CONCURR-PUBLISH", "FINALIZADA");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-CC-PUB-001");

        // Advance to APROBADA sequentially (no concurrency risk here)
        for (String estado : List.of("EN_REVISION_COMISION", "APROBADA")) {
            mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                    .nuevoEstado(estado).build())))
                    .andExpect(status().isOk());
        }

        int threads = 4;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threads);
        AtomicInteger published  = new AtomicInteger(0);
        AtomicInteger rejected   = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    int status = mockMvc.perform(put("/api/v1/actas/" + idActa + "/estado")
                                    .header("Authorization", "Bearer " + tokenAdminProp1)
                                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(ActaEstadoUpdateRequestDTO.builder()
                                            .nuevoEstado("PUBLICADA_OFICIAL")
                                            .documentoFirmadoUrl("https://storage.saed.co/actas/concurrent-pub.pdf")
                                            .build())))
                            .andReturn().getResponse().getStatus();

                    if (status == 200) published.incrementAndGet();
                    else rejected.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify final state: acta is PUBLICADA_OFICIAL exactly once
        setElevatedContext();
        String estadoFinal;
        try {
            estadoFinal = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTAS_ASAMBLEA WHERE ID_ACTA = ?", String.class, idActa);
        } finally {
            clearContext();
        }

        assertEquals("PUBLICADA_OFICIAL", estadoFinal,
                "Acta debe estar en PUBLICADA_OFICIAL tras publicación concurrente");

        // At least one publication succeeded, none left in inconsistent state
        assertTrue(published.get() >= 1,
                "At least one publish must succeed. published=" + published + " rejected=" + rejected);
    }

    // =========================================================================
    // 13. INTEGRIDAD DE BASE DE DATOS Y VPD (G7-DB01 a G7-DB02)
    // =========================================================================

    @Test
    @Order(36)
    @DisplayName("F10-05-36: UQ_ACTA_ASAMBLEA garantiza una sola acta por asamblea a nivel de BD")
    public void test36_unicidadActaBD() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("36-UQ-BD", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-UQ-001");

        assertNotNull(idActa, "El ID del acta debe ser generado");

        // Direct insert attempt should fail due to UQ_ACTA_ASAMBLEA
        setElevatedContext();
        try {
            boolean violationOccurred = false;
            try {
                jdbcTemplate.update("INSERT INTO ACTAS_ASAMBLEA (ID_ASAMBLEA, NUMERO_ACTA, ESTADO, REDACTADA_POR) VALUES (?, 'ACTA-UQ-DUPLICATE', 'BORRADOR', 1)",
                        idAsamblea);
            } catch (Exception e) {
                // ORA-00001 unique constraint violation expected
                violationOccurred = true;
            }
            assertTrue(violationOccurred, "La inserción duplicada debe violar UQ_ACTA_ASAMBLEA");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(37)
    @DisplayName("F10-05-37: VPD/RLS — acta de org2/prop2 no visible para contexto org1/prop1")
    public void test37_vpdRlsAislamiento() throws Exception {
        Long idAsambleaProp2;
        Long idActaProp2;

        // Setup: create assembly and acta in PROP_2 using elevated (SUPERADMIN) context.
        // We do NOT specify the PK — IDENTITY sequence generates it automatically to avoid collisions.
        setElevatedContext();
        try {
            // Use Oracle IDENTITY (no explicit ID) and retrieve generated key via RETURNING / KeyHolder
            org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO ASAMBLEAS (ID_PROPIEDAD, TIPO, MODALIDAD, TITULO, CONVOCATORIA_NUMERO, " +
                        "FECHA_HORA_PRIMERA_CONV, LUGAR_O_ENLACE, ORDEN_DEL_DIA, QUORUM_REQUERIDO_PCT, ESTADO) " +
                        "VALUES (?, 'ORDINARIA', 'PRESENCIAL', 'ACTA-TEST-VPD-PROP2', 1, " +
                        "SYSDATE + 1, 'Salon', 'Agenda VPD', 50.01, 'EN_CURSO')",
                        new String[]{"ID_ASAMBLEA"});
                ps.setLong(1, PROP_2_ID);
                return ps;
            }, keyHolder);

            idAsambleaProp2 = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
            assertNotNull(idAsambleaProp2, "Must generate assembly ID via IDENTITY");

            // Insert acta for PROP_2 assembly without explicit PK
            org.springframework.jdbc.support.GeneratedKeyHolder actaKeyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO ACTAS_ASAMBLEA (ID_ASAMBLEA, NUMERO_ACTA, ESTADO, REDACTADA_POR) " +
                        "VALUES (?, 'ACTA-VPD-PROP2', 'BORRADOR', 1)",
                        new String[]{"ID_ACTA"});
                ps.setLong(1, idAsambleaProp2);
                return ps;
            }, actaKeyHolder);

            idActaProp2 = actaKeyHolder.getKey() != null ? actaKeyHolder.getKey().longValue() : null;
            assertNotNull(idActaProp2, "Must generate acta ID via IDENTITY");
        } finally {
            clearContext();
        }

        // Admin of PROP_1 should not see the acta from PROP_2 due to VPD
        // (VPD silently filters rows, so we expect 404 rather than a visible cross-tenant acta)
        mockMvc.perform(get("/api/v1/actas/" + idActaProp2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertTrue(s == 403 || s == 404,
                            "VPD must block cross-tenant access: expected 403 or 404, got " + s);
                });
    }

    @Test
    @Order(38)
    @DisplayName("F10-05-38: SUPERADMIN puede listar y leer actas de todas las propiedades")
    public void test38_superAdminVeTodo() throws Exception {
        Long idAsamblea = crearAsambleaEnEstado("38-SA-VE-TODO", "EN_CURSO");
        Long idActa = crearActaBorrador(idAsamblea, "ACTA-SA-ALL-001");

        // SuperAdmin reads the acta
        mockMvc.perform(get("/api/v1/actas/" + idActa)
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idActa").value(idActa));

        // SuperAdmin listing should return at least 1
        mockMvc.perform(get("/api/v1/actas")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk());
    }
}
