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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10VotacionesIntegrationTest — Dedicated Integration, Security, Governance and Concurrency
 * Test Suite for F10-04 Votaciones y Toma de Decisiones en Asambleas (Ley 675) in SAED 2.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F10VotacionesIntegrationTest {

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

    // Users and Assignments
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1 = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_PROP_2 = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE_1 = 805L;
    private static final long ASSIGN_RESIDENTE_1 = 905L;

    private static final long USER_RESIDENTE_2 = 812L;
    private static final long ASSIGN_RESIDENTE_2 = 912L;

    private static final long USER_APODERADO = 814L;
    private static final long ASSIGN_APODERADO = 914L;

    private static final long USER_PORTERO = 806L;
    private static final long ASSIGN_PORTERO = 906L;

    // Multi-tenant scopes
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    private static final long UNIDAD_1_ID = 9101L; // 25% coef
    private static final long UNIDAD_2_ID = 9102L; // 30% coef
    private static final long UNIDAD_3_ID = 9103L; // 15% coef
    private static final long UNIDAD_4_ID = 9104L; // 20% coef
    private static final long UNIDAD_5_ID = 9105L; // 10% coef
    private static final long UNIDAD_PROP2_ID = 9201L; // Prop 2 unit

    // JWT Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente1;
    private String tokenResidente2;
    private String tokenApoderado;
    private String tokenPortero;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            cleanupTestData();

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "101", BigDecimal.valueOf(0.250000));
            ensureUnidad(UNIDAD_2_ID, PROP_1_ID, "102", BigDecimal.valueOf(0.300000));
            ensureUnidad(UNIDAD_3_ID, PROP_1_ID, "103", BigDecimal.valueOf(0.150000));
            ensureUnidad(UNIDAD_4_ID, PROP_1_ID, "104", BigDecimal.valueOf(0.200000));
            ensureUnidad(UNIDAD_5_ID, PROP_1_ID, "105", BigDecimal.valueOf(0.100000));
            ensureUnidad(UNIDAD_PROP2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.500000));

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_test", "adminorg1@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_test", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_test", "adminprop2@saed.com");

            ensurePersona(USER_RESIDENTE_1, "1000000805", "Carlos", "Residente1", "residente1@saed.com");
            ensureUsuario(USER_RESIDENTE_1, USER_RESIDENTE_1, "residente1_test", "residente1@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000812", "Maria", "Residente2", "residente2@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "residente2_test", "residente2@saed.com");

            ensurePersona(USER_APODERADO, "1000000814", "Felipe", "Apoderado", "apoderado@saed.com");
            ensureUsuario(USER_APODERADO, USER_APODERADO, "apoderado_test", "apoderado@saed.com");

            ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero@saed.com");
            ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_test", "portero@saed.com");

            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_2_ID);
            ensureAsignacion(ASSIGN_APODERADO, USER_APODERADO, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_3_ID);
            ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

            setupMockAssignments();

            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenApoderado = jwtProvider.generateIdentityToken(USER_APODERADO);
            tokenPortero = jwtProvider.generateIdentityToken(USER_PORTERO);

        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        setElevatedContext();
        try {
            cleanupTestData();
        } catch (Exception ignored) {}
        clearContext();
    }

    private void cleanupTestData() {
        jdbcTemplate.execute("""
            BEGIN
                DELETE FROM VOTOS WHERE ID_VOTACION IN (
                    SELECT ID_VOTACION FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'VOTACION-TEST-%'
                    )
                );
                DELETE FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'VOTACION-TEST-%'
                );
                DELETE FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'VOTACION-TEST-%'
                );
                DELETE FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA IN (
                    SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'VOTACION-TEST-%'
                );
                DELETE FROM ASAMBLEAS WHERE TITULO LIKE 'VOTACION-TEST-%';
            END;
        """);
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

        // SuperAdmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(saAssign));

        // Admin Org 1
        AssignmentResponseDTO aOrg1 = new AssignmentResponseDTO();
        aOrg1.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        aOrg1.setOrganizacion(org1);
        aOrg1.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(aOrg1));

        // Admin Prop 1
        AssignmentResponseDTO aProp1 = new AssignmentResponseDTO();
        aProp1.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        aProp1.setOrganizacion(org1);
        aProp1.setPropiedad(prop1);
        aProp1.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(aProp1));

        // Admin Prop 2
        AssignmentResponseDTO aProp2 = new AssignmentResponseDTO();
        aProp2.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        aProp2.setOrganizacion(org2);
        aProp2.setPropiedad(prop2);
        aProp2.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(aProp2));

        // Residente 1 (Unidad 1)
        AssignmentResponseDTO res1 = new AssignmentResponseDTO();
        res1.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1.setOrganizacion(org1);
        res1.setPropiedad(prop1);
        res1.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1));

        // Residente 2 (Unidad 2)
        AssignmentResponseDTO res2 = new AssignmentResponseDTO();
        res2.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2.setOrganizacion(org1);
        res2.setPropiedad(prop1);
        res2.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2));

        // Apoderado (Unidad 3)
        AssignmentResponseDTO apod = new AssignmentResponseDTO();
        apod.setIdAsignacion(ASSIGN_APODERADO);
        apod.setOrganizacion(org1);
        apod.setPropiedad(prop1);
        apod.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_APODERADO, USER_APODERADO)).thenReturn(Optional.of(apod));

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
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) " +
                            "VALUES (?, ?, 1, ?, 'Calle Test 123', 'Bogota', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coeficiente) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coeficiente);
        } else {
            jdbcTemplate.update("UPDATE UNIDADES SET COEFICIENTE_COPROPIEDAD = ? WHERE ID_UNIDAD = ?", coeficiente, id);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) " +
                            "VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(long id, long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                            "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyzABCDEF12345678901234567890', 'ACTIVO')",
                    id, idPersona, username, email);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rol, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) " +
                            "VALUES (?, ?, (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, rol, idOrg, idProp, idUnidad);
        } else {
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), " +
                            "ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = ?",
                    idUsuario, rol, idOrg, idProp, idUnidad, id);
        }
    }

    private Long crearAsambleaHelper(String titulo, String estado) throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo(titulo)
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-20T09:00:00")
                .lugarOEnlace("Salón Comunal")
                .ordenDelDia("1. Orden del día")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("CONVOCADA")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idAsamblea = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        if (!"CONVOCADA".equals(estado)) {
            mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/estado")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO(estado))))
                    .andExpect(status().isOk());
        }

        return idAsamblea;
    }

    // =========================================================================
    // 1. CICLO DE VIDA DE VOTACIÓN: CREACIÓN, CIERRE, ANULACIÓN
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("F10-04-01: Creación de punto de votación en estado ABIERTA con mayoría requerida")
    public void test01_crearPuntoVotacionAbierta() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-01-CREAR", "EN_CURSO");

        VotacionCreateRequestDTO req = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Aprobación de Estados Financieros 2025")
                .descripcion("Dictamen de revisoría fiscal y estados financieros.")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVotacion").isNumber())
                .andExpect(jsonPath("$.puntoOrdenDia").value(1))
                .andExpect(jsonPath("$.titulo").value("Aprobación de Estados Financieros 2025"))
                .andExpect(jsonPath("$.tipoMayoriaRequerida").value("SIMPLE_50_MAS_1"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.horaApertura").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("F10-04-02: Rechazo de creación de punto de votación en asamblea FINALIZADA")
    public void test02_rechazoCrearVotacionEnAsambleaInvalida() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-02-FINALIZADA", "EN_CURSO");

        // Mover a FINALIZADA
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO("FINALIZADA"))))
                .andExpect(status().isOk());

        VotacionCreateRequestDTO req = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto no permitido")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 2. EMISIÓN DE VOTOS, UNICIDAD Y ELEGIBILIDAD (LEY 675)
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("F10-04-03: Emisión legítima de voto por copropietario directo asignado a su unidad")
    public void test03_emisionVotoPropietarioDirectoExitoso() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-03-VOTO-DIRECTO", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Elección Consejo de Administración")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Residente 1 vota por su unidad (UNIDAD_1_ID)
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isCreated());

        // Consultar votaciones y verificar acumulado
        mockMvc.perform(get("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalVotos").value(1))
                .andExpect(jsonPath("$[0].votosSi").value(0.25));
    }

    @Test
    @Order(4)
    @DisplayName("F10-04-04: Prohibición estricta de voto duplicado para la misma unidad en la misma votación")
    public void test04_rechazoVotoDuplicadoMismaUnidad() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-04-DUP", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Presupuesto Operativo 2026")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .opcionVoto("SI")
                .build();

        // Primer voto -> 201
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isCreated());

        // Segundo voto para la misma unidad -> 4xx Rejection
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5)
    @DisplayName("F10-04-05: Rechazo con 403 Forbidden cuando residente intenta votar por unidad no asignada sin poder")
    public void test05_rechazoVotoUnidadAjenaSinPoder() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-05-AJENA", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto General")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Residente 1 (asignado a UNIDAD_1_ID) intenta votar por UNIDAD_2_ID sin poder
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("F10-04-06: Rechazo con 403 Forbidden al votar con poder en estado PENDIENTE_REVISION")
    public void test06_rechazoVotoConPoderPendiente() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-06-PODER-PEND", "EN_CURSO");

        // Radicar poder: Residente 2 (Unidad 2) otorga poder a Apoderado (USER_APODERADO)
        PoderRequestDTO poderReq = PoderRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaPropietario(USER_RESIDENTE_2)
                .idPersonaApoderado(USER_APODERADO)
                .documentoPoderUrl("https://storage.saed.com/poderes/poder_2.pdf")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poderReq)))
                .andExpect(status().isCreated());

        // Crear punto de votación
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto Votación Poder Pendiente")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Apoderado intenta votar antes de que el poder sea aprobado -> 403 Forbidden
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("F10-04-07: Rechazo con 403 Forbidden al votar con poder RECHAZADO")
    public void test07_rechazoVotoConPoderRechazado() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-07-PODER-RECH", "EN_CURSO");

        // Radicar poder
        PoderRequestDTO poderReq = PoderRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaPropietario(USER_RESIDENTE_2)
                .idPersonaApoderado(USER_APODERADO)
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poderReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idPoder = objectMapper.readValue(pRes.getResponse().getContentAsString(), PoderDTO.class).getIdPoder();

        // Admin RECHAZA el poder
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/poderes/" + idPoder + "/decision")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PoderDecisionDTO("RECHAZADO"))))
                .andExpect(status().isOk());

        // Crear votación
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto Votación Poder Rechazado")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Apoderado intenta votar con poder rechazado -> 403 Forbidden
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .opcionVoto("NO")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("F10-04-08: Emisión legítima de voto por apoderado con poder debidamente APROBADO")
    public void test08_emisionVotoConPoderAprobado() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-08-PODER-OK", "EN_CURSO");

        // Radicar poder
        PoderRequestDTO poderReq = PoderRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaPropietario(USER_RESIDENTE_2)
                .idPersonaApoderado(USER_APODERADO)
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poderReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idPoder = objectMapper.readValue(pRes.getResponse().getContentAsString(), PoderDTO.class).getIdPoder();

        // Admin APRUEBA el poder
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/poderes/" + idPoder + "/decision")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PoderDecisionDTO("APROBADO"))))
                .andExpect(status().isOk());

        // Crear votación
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Aprobación Cuota Extraordinaria")
                .tipoMayoriaRequerida("CALIFICADA_70_PCT")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Apoderado emite voto por Unidad 2 -> 201 Created
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().isCreated());

        // Verificar registro con coeficiente de Unidad 2 (30%)
        mockMvc.perform(get("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].votosSi").value(0.30))
                .andExpect(jsonPath("$[0].totalVotos").value(1));
    }

    @Test
    @Order(9)
    @DisplayName("F10-04-09: Rechazo de voto para unidad perteneciente a otra copropiedad")
    public void test09_rechazoVotoUnidadCopropiedadDistinta() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-09-CROSS-PROP", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto Votación")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Intentar votar con unidad de la Propiedad 2 en asamblea de la Propiedad 1
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_PROP2_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 3. CONCURRENCIA: PROTECCIÓN CONTRA RACE CONDITION EN VOTO SIMULTÁNEO
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("F10-04-10: Concurrencia multihilo — 10 peticiones simultáneas de voto para la misma unidad: exactamente 1 éxito")
    public void test10_concurrenciaAltaDemandaDobleVoto() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-10-CONCURRENCIA", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Votación Concurrente")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    VotoRequestDTO voto = VotoRequestDTO.builder()
                            .idUnidad(UNIDAD_1_ID)
                            .opcionVoto("SI")
                            .build();

                    MvcResult r = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                                    .header("Authorization", "Bearer " + tokenResidente1)
                                    .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(voto)))
                            .andReturn();

                    int status = r.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status >= 400) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Los hilos de votación concurrente no terminaron a tiempo");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactamente una petición debió registrar el voto");
        assertEquals(threadCount - 1, rejectedCount.get(), "Las demás peticiones debieron ser rechazadas por unicidad");
    }

    // =========================================================================
    // 4. ANULACIÓN Y BLOQUEO DE ESTADOS TERMINALES
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("F10-04-11: Anulación legítima de punto de votación por el administrador")
    public void test11_anularVotacionAdmin() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-11-ANULAR", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto a ser Anulado")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Anular votación
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/anular")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.horaCierre").isNotEmpty());
    }

    @Test
    @Order(12)
    @DisplayName("F10-04-12: Rechazo con 4xx al intentar votar en punto de votación ANULADO")
    public void test12_rechazoVotoEnVotacionAnulada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-12-VOTO-ANULADO", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto Anulado")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Anular
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/anular")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Intentar votar -> 4xx
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(13)
    @DisplayName("F10-04-13: Rechazo con 4xx al intentar votar en punto de votación CERRADO")
    public void test13_rechazoVotoEnVotacionCerrada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-13-VOTO-CERRADO", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto a Cerrar")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Cerrar
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"));

        // Intentar votar -> 4xx
        VotoRequestDTO voto = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .opcionVoto("SI")
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 5. CÁLCULO DE MAYORÍAS SEGÚN LEY 675 (SIMPLE, CALIFICADA, UNANIMIDAD)
    // =========================================================================

    @Test
    @Order(14)
    @DisplayName("F10-04-14: Escrutinio Mayoría Simple (50% + 1) — Decisión Aprobada cuando SI > NO y SI > 50%")
    public void test14_escrutinioMayoriaSimpleAprobada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-14-MAY-SIMPLE-OK", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Aprobación de Reglamento Interno")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 (25% coef) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 2 (30% coef) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_2_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 3: Unidad 3 (15% coef) -> NO
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_3_ID).opcionVoto("NO").build())))
                .andExpect(status().isCreated());

        // Cerrar y verificar: SI = 55%, NO = 15% -> aprobada == true
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.votosSi").value(0.55))
                .andExpect(jsonPath("$.votosNo").value(0.15))
                .andExpect(jsonPath("$.totalVotos").value(3));
    }

    @Test
    @Order(15)
    @DisplayName("F10-04-15: Escrutinio Mayoría Simple (50% + 1) — Decisión No Aprobada cuando NO >= SI")
    public void test15_escrutinioMayoriaSimpleRechazada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-15-MAY-SIMPLE-FAIL", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Propuesta de Cambio de Fachada")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 (25%) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 2 (30%) -> NO
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_2_ID).opcionVoto("NO").build())))
                .andExpect(status().isCreated());

        // Cerrar y verificar: SI = 25%, NO = 30% -> aprobada == false
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(false));
    }

    @Test
    @Order(16)
    @DisplayName("F10-04-16: Escrutinio Mayoría Calificada (70%) — No aprobada cuando SI alcanza 68% (< 70%)")
    public void test16_escrutinioMayoriaCalificadaRechazada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-16-MAY-CALIF-FAIL", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Desafectación de Bienes Comunes")
                .tipoMayoriaRequerida("CALIFICADA_70_PCT")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 (25%) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 3 (15%) -> NO
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_3_ID).opcionVoto("NO").build())))
                .andExpect(status().isCreated());

        // Total votos emitidos: 25 + 15 = 40. SI = 25/40 = 62.5% (< 70%) -> aprobada == false
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(false));
    }

    @Test
    @Order(17)
    @DisplayName("F10-04-17: Escrutinio Mayoría Calificada (70%) — Aprobada cuando SI alcanza 78.5% (>= 70%)")
    public void test17_escrutinioMayoriaCalificadaAprobada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-17-MAY-CALIF-OK", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Cambio de Destinación de Bien Común")
                .tipoMayoriaRequerida("CALIFICADA_70_PCT")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 (25%) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 2 (30%) -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_2_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 3: Unidad 3 (15%) -> NO
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenApoderado)
                        .header("X-Assignment-Id", ASSIGN_APODERADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_3_ID).opcionVoto("NO").build())))
                .andExpect(status().isCreated());

        // SI = 55, total = 70. 55 / 70 = 78.57% >= 70% -> aprobada == true
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true));
    }

    @Test
    @Order(18)
    @DisplayName("F10-04-18: Escrutinio Unanimidad (100%) — Rechazada si existe al menos un voto en BLANCO, ABSTENCIÓN o NO")
    public void test18_escrutinioUnanimidadRechazada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-18-UNAN-FAIL", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Extinción de Propiedad Horizontal")
                .tipoMayoriaRequerida("UNANIMIDAD_100_PCT")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 2 -> BLANCO
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_2_ID).opcionVoto("BLANCO").build())))
                .andExpect(status().isCreated());

        // Cerrar y verificar: No es 100% SI -> aprobada == false
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(false));
    }

    @Test
    @Order(19)
    @DisplayName("F10-04-19: Escrutinio Unanimidad (100%) — Aprobada cuando el 100% de los votos son SI")
    public void test19_escrutinioUnanimidadAprobada() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-19-UNAN-OK", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Decisión Unánime")
                .tipoMayoriaRequerida("UNANIMIDAD_100_PCT")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Voto 1: Unidad 1 -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Voto 2: Unidad 2 -> SI
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_2_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Cerrar y verificar: 100% SI -> aprobada == true
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true));
    }

    // =========================================================================
    // 6. CONGELAMIENTO DE COEFICIENTES, MULTI-TENANCY, ROLES Y AUDITORÍA
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("F10-04-20: Congelamiento de coeficiente nominal en VOTOS al momento de votar")
    public void test20_congelamientoCoeficienteEnVoto() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-20-COEF", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Votación Coeficiente Snapshot")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Emitir voto
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // Verificar directamente en BD que COEFICIENTE_VOTO es exactamente 0.250000
        BigDecimal coefEnBd = jdbcTemplate.queryForObject(
                "SELECT COEFICIENTE_VOTO FROM VOTOS WHERE ID_VOTACION = ? AND ID_UNIDAD = ?",
                BigDecimal.class, idVotacion, UNIDAD_1_ID
        );
        assertNotNull(coefEnBd);
        assertEquals(0, coefEnBd.compareTo(BigDecimal.valueOf(0.250000)));
    }

    @Test
    @Order(21)
    @DisplayName("F10-04-21: Multi-Tenant — Rechazo con 403 o 404 a administradores de otra organización o propiedad")
    public void test21_aislamientoMultiTenantOrgYPropiedad() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-21-TENANT", "EN_CURSO");

        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Punto Privado Propiedad 1")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // Admin Propiedad 2 (Org 2) intenta listar votaciones -> 403 o 404
        int sc1 = mockMvc.perform(get("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andReturn().getResponse().getStatus();
        assertTrue(sc1 == 403 || sc1 == 404);

        // Admin Propiedad 2 intenta cerrar la votación -> 403 o 404
        int sc2 = mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andReturn().getResponse().getStatus();
        assertTrue(sc2 == 403 || sc2 == 404);
    }

    @Test
    @Order(22)
    @DisplayName("F10-04-22: Bloqueo de operaciones de votación para rol PORTERO con 403 Forbidden")
    public void test22_porteroRestringidoVotacion() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-22-PORTERO", "EN_CURSO");

        // Portero intenta crear punto de votación -> 403
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotacionCreateRequestDTO.builder()
                                .puntoOrdenDia(1).titulo("Punto Portero").tipoMayoriaRequerida("SIMPLE_50_MAS_1").build())))
                .andExpect(status().isForbidden());

        // Portero intenta emitir voto -> 403
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/1/votar")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder()
                                .idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(23)
    @DisplayName("F10-04-23: Auditoría y trazabilidad — Registro inmutable de CREAR, VOTAR, CERRAR y ANULAR en AUDITORIA_LOG")
    public void test23_auditoriaLogRegistroCompleto() throws Exception {
        Long idAsamblea = crearAsambleaHelper("VOTACION-TEST-23-AUDIT", "EN_CURSO");

        // 1. Crear Votación
        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotacionCreateRequestDTO.builder()
                                .puntoOrdenDia(1).titulo("Punto Audit").tipoMayoriaRequerida("SIMPLE_50_MAS_1").build())))
                .andExpect(status().isCreated())
                .andReturn();

        Long idVotacion = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class).getIdVotacion();

        // 2. Emitir Voto
        mockMvc.perform(post("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VotoRequestDTO.builder().idUnidad(UNIDAD_1_ID).opcionVoto("SI").build())))
                .andExpect(status().isCreated());

        // 3. Anular Votación
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/votaciones/" + idVotacion + "/anular")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Verificar que existan registros en AUDITORIA_LOG
        setElevatedContext();
        try {
            Integer votCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'VOTACIONES' AND ID_ENTIDAD_AFECTADA = ?",
                    Integer.class, idVotacion
            );
            assertNotNull(votCount);
            assertTrue(votCount >= 2, "Debe existir log de auditoría para la votación (creación y anulación)");

            Integer votoCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'VOTOS' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_1_ID
            );
            assertNotNull(votoCount);
            assertTrue(votoCount > 0, "Debe existir log de auditoría para VOTOS");
        } finally {
            clearContext();
        }
    }
}
