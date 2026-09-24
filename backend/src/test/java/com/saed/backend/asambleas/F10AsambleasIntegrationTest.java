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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10AsambleasIntegrationTest — Comprehensive Integration, Security, Governance and Concurrency
 * Test Suite for F10-03 Asambleas (Horizontally Property Assemblies Ley 675) in SAED 2.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F10AsambleasIntegrationTest {

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

    private static final long UNIDAD_1_ID = 9101L;
    private static final long UNIDAD_2_ID = 9102L;
    private static final long UNIDAD_3_ID = 9103L;
    private static final long UNIDAD_PROP2_ID = 9201L;

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
            // Cleanup previous test assemblies
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM VOTOS WHERE ID_VOTACION IN (
                        SELECT ID_VOTACION FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                            SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                        )
                    );
                    DELETE FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%';
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_1B_ID, ORG_1_ID, "Torre B SAED Org 1");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "101", BigDecimal.valueOf(0.250000));
            ensureUnidad(UNIDAD_2_ID, PROP_1_ID, "102", BigDecimal.valueOf(0.300000));
            ensureUnidad(UNIDAD_3_ID, PROP_1_ID, "103", BigDecimal.valueOf(0.150000));
            ensureUnidad(UNIDAD_PROP2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.500000));

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
            ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
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
                    DELETE FROM VOTOS WHERE ID_VOTACION IN (
                        SELECT ID_VOTACION FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                            SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                        )
                    );
                    DELETE FROM VOTACIONES WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA IN (
                        SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%'
                    );
                    DELETE FROM ASAMBLEAS WHERE TITULO LIKE 'ASAMBLEA-TEST-%';
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
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
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

    // =========================================================================
    // 1. ESCENARIO: CRUD Y CICLO DE VIDA (BORRADOR, CONVOCADA, EDICIÓN)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("F10-03-01: Convocar asamblea en estado BORRADOR y CONVOCADA con validación de contrato")
    public void test01_crearAsambleaBorradorYConvocada() throws Exception {
        // Convocar en Borrador
        AsambleaCreateRequestDTO draftReq = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-01-BORRADOR")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-15T09:00:00")
                .fechaHoraSegundaConv("2026-11-15T10:00:00")
                .lugarOEnlace("Salón Comunal y Zoom")
                .ordenDelDia("1. Verificación de Quórum\n2. Balance General")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("BORRADOR")
                .build();

        MvcResult draftResult = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAsamblea").isNumber())
                .andExpect(jsonPath("$.titulo").value("ASAMBLEA-TEST-01-BORRADOR"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.idOrganizacion").value(ORG_1_ID))
                .andExpect(jsonPath("$.idPropiedad").value(PROP_1_ID))
                .andReturn();

        AsambleaDTO draftDTO = objectMapper.readValue(draftResult.getResponse().getContentAsString(), AsambleaDTO.class);
        assertNotNull(draftDTO.getIdAsamblea());

        // Convocar formalmente
        AsambleaCreateRequestDTO convReq = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("EXTRAORDINARIA")
                .modalidad("PRESENCIAL")
                .titulo("ASAMBLEA-TEST-01-CONVOCADA")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-20T18:00:00")
                .lugarOEnlace("Auditorio Principal")
                .ordenDelDia("1. Cuota Extraordinaria Fachada")
                .quorumRequeridoPct(BigDecimal.valueOf(70.00))
                .estado("CONVOCADA")
                .build();

        mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(convReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONVOCADA"))
                .andExpect(jsonPath("$.tipo").value("EXTRAORDINARIA"));
    }

    @Test
    @Order(2)
    @DisplayName("F10-03-02: Actualizar asamblea en estado BORRADOR con PUT /api/v1/asambleas/{id}")
    public void test02_actualizarAsambleaEnBorrador() throws Exception {
        // Crear
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("PRESENCIAL")
                .titulo("ASAMBLEA-TEST-02-ORIGINAL")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-10T08:00:00")
                .lugarOEnlace("Salón A")
                .ordenDelDia("1. Saludo")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("BORRADOR")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        AsambleaDTO dto = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class);
        Long id = dto.getIdAsamblea();

        // Actualizar
        AsambleaUpdateRequestDTO updateReq = AsambleaUpdateRequestDTO.builder()
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-02-MODIFICADA")
                .convocatoriaNumero(2)
                .fechaHoraPrimeraConv("2026-11-12T09:30:00")
                .fechaHoraSegundaConv("2026-11-12T10:30:00")
                .lugarOEnlace("Salón Comunal y Meet")
                .ordenDelDia("1. Saludo\n2. Modificación de Cuotas")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .build();

        mockMvc.perform(put("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("ASAMBLEA-TEST-02-MODIFICADA"))
                .andExpect(jsonPath("$.modalidad").value("MIXTA"))
                .andExpect(jsonPath("$.convocatoriaNumero").value(2));

        // Verificar por detalle
        mockMvc.perform(get("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("ASAMBLEA-TEST-02-MODIFICADA"));
    }

    // =========================================================================
    // 2. ESCENARIO: MÁQUINA DE ESTADOS Y TRANSICIONES LEGALES
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("F10-03-03: Máquina de estados - Transiciones legales completas BORRADOR -> CONVOCADA -> EN_CURSO -> EN_RECESO -> EN_CURSO -> FINALIZADA")
    public void test03_maquinaEstadosTransicionesLegales() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-03-MAQUINA")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-01T08:00:00")
                .lugarOEnlace("Sede Principal")
                .ordenDelDia("1. Inicio")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("BORRADOR")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 1. BORRADOR -> CONVOCADA
        actualizarEstado(id, "CONVOCADA", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 2. CONVOCADA -> EN_CURSO
        actualizarEstado(id, "EN_CURSO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 3. EN_CURSO -> EN_RECESO
        actualizarEstado(id, "EN_RECESO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 4. EN_RECESO -> EN_CURSO
        actualizarEstado(id, "EN_CURSO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 5. EN_CURSO -> FINALIZADA
        actualizarEstado(id, "FINALIZADA", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // Confirmar estado terminal
        mockMvc.perform(get("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"));
    }

    @Test
    @Order(4)
    @DisplayName("F10-03-04: Máquina de estados - Rechazo de transiciones prohibidas y bloqueo de edición en estado terminal")
    public void test04_rechazoTransicionesProhibidas() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("PRESENCIAL")
                .titulo("ASAMBLEA-TEST-04-INVALIDA")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-05T08:00:00")
                .lugarOEnlace("Sede Principal")
                .ordenDelDia("1. Orden")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .estado("BORRADOR")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // Intento 1: BORRADOR -> FINALIZADA directamente (debe fallar)
        mockMvc.perform(put("/api/v1/asambleas/" + id + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO("FINALIZADA"))))
                .andExpect(status().is4xxClientError());

        // Mover válidamente a FINALIZADA
        actualizarEstado(id, "CONVOCADA", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);
        actualizarEstado(id, "EN_CURSO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);
        actualizarEstado(id, "FINALIZADA", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // Intento 2: Salir de FINALIZADA a EN_CURSO (debe fallar)
        mockMvc.perform(put("/api/v1/asambleas/" + id + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO("EN_CURSO"))))
                .andExpect(status().is4xxClientError());

        // Intento 3: Editar asamblea FINALIZADA con PUT /api/v1/asambleas/{id} (debe fallar)
        AsambleaUpdateRequestDTO updateReq = AsambleaUpdateRequestDTO.builder()
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-04-MUTADA")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-05T08:00:00")
                .lugarOEnlace("Sede Principal")
                .ordenDelDia("1. Mutado")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .build();

        mockMvc.perform(put("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 3. ESCENARIO: ASISTENCIA Y CÁLCULO DE QUÓRUM EN TIEMPO REAL
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("F10-03-05: Asistencia y Quórum en tiempo real con ponderación de coeficientes")
    public void test05_asistenciaYQuorumAlcanzado() throws Exception {
        // Asamblea con quórum requerido = 50.01%
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-05-QUORUM")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-18T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Verificación Quórum")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 1. Quórum inicial = 0% y tieneQuorum == false
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(0))
                .andExpect(jsonPath("$.tieneQuorum").value(false))
                .andExpect(jsonPath("$.totalUnidadesRegistradas").value(0));

        // 2. Registrar Unidad 1 (coeficiente 25%)
        AsistenciaRequestDTO asis1 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaAsistente(USER_RESIDENTE)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(25.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAsistencia").isNumber());

        // Quórum debe ser 25.00% y tieneQuorum == false
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(25.0))
                .andExpect(jsonPath("$.tieneQuorum").value(false))
                .andExpect(jsonPath("$.totalUnidadesRegistradas").value(1));

        // 3. Registrar Unidad 2 (coeficiente 30%)
        AsistenciaRequestDTO asis2 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaAsistente(USER_ADMIN_PROP_1B)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(30.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis2)))
                .andExpect(status().isCreated());

        // Quórum debe ser 55.00% >= 50.01% -> tieneQuorum == true!
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(55.0))
                .andExpect(jsonPath("$.tieneQuorum").value(true))
                .andExpect(jsonPath("$.totalUnidadesRegistradas").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("F10-03-06: Rechazo de asistencia duplicada para misma unidad y rechazo de unidad ajena a la copropiedad")
    public void test06_rechazoAsistenciaDuplicadaYUnidadAjena() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-06-ASISTENCIA-DUP")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-19T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 1. Registro inicial Unidad 1
        AsistenciaRequestDTO asis1 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaAsistente(USER_RESIDENTE)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(25.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis1)))
                .andExpect(status().isCreated());

        // 2. Re-registro de Unidad 1 debe fallar
        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis1)))
                .andExpect(status().is4xxClientError());

        // 3. Registro de Unidad de Propiedad 2 en asamblea de Propiedad 1 debe fallar
        AsistenciaRequestDTO asisProp2 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_PROP2_ID)
                .idPersonaAsistente(USER_ADMIN_PROP_2)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(50.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asisProp2)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(7)
    @DisplayName("F10-03-07: Retiro voluntario de asistencia y recálculo automático de quórum")
    public void test07_retiroAsistenciaYRecalculoQuorum() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-07-RETIRO")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-21T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // Registrar unidades 1 y 2
        AsistenciaRequestDTO asis1 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaAsistente(USER_RESIDENTE)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(25.000000))
                .build();
        AsistenciaRequestDTO asis2 = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaAsistente(USER_ADMIN_PROP_1B)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(30.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asis1))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asis2))).andExpect(status().isCreated());

        // Quórum inicial = 55.00%
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(55.0))
                .andExpect(jsonPath("$.tieneQuorum").value(true));

        // Retirar Unidad 2
        mockMvc.perform(put("/api/v1/asambleas/" + id + "/asistencias/" + UNIDAD_2_ID + "/retiro")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNoContent());

        // Quórum después del retiro = 25.00% y tieneQuorum == false
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(25.0))
                .andExpect(jsonPath("$.tieneQuorum").value(false))
                .andExpect(jsonPath("$.totalUnidadesRegistradas").value(1));
    }

    // =========================================================================
    // 4. ESCENARIO: PODERES DE REPRESENTACIÓN (LEY 675)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("F10-03-08: Poderes de representación - Radicación, prohibición de auto-representación y aprobación por mesa")
    public void test08_poderesRepresentacionLey675() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-08-PODERES")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-25T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 1. Prohibición de auto-representación (mismo propietario y apoderado)
        PoderRequestDTO autoPoder = PoderRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaPropietario(USER_RESIDENTE)
                .idPersonaApoderado(USER_RESIDENTE)
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(autoPoder)))
                .andExpect(status().is4xxClientError());

        // 2. Radicación legítima de poder: Propietario (USER_RESIDENTE) delega a Apoderado (USER_ADMIN_PROP_1B)
        PoderRequestDTO validPoder = PoderRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaPropietario(USER_RESIDENTE)
                .idPersonaApoderado(USER_ADMIN_PROP_1B)
                .documentoPoderUrl("https://storage.saed.com/poderes/poder_101.pdf")
                .build();

        MvcResult poderRes = mockMvc.perform(post("/api/v1/asambleas/" + id + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPoder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPoder").isNumber())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"))
                .andReturn();

        PoderDTO poderDTO = objectMapper.readValue(poderRes.getResponse().getContentAsString(), PoderDTO.class);
        Long idPoder = poderDTO.getIdPoder();

        // 3. Prohibición de poder duplicado para la misma unidad
        mockMvc.perform(post("/api/v1/asambleas/" + id + "/poderes")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPoder)))
                .andExpect(status().is4xxClientError());

        // 4. Decisión por la mesa directiva (APROBADO)
        mockMvc.perform(put("/api/v1/asambleas/" + id + "/poderes/" + idPoder + "/decision")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PoderDecisionDTO("APROBADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.validadoPor").value(USER_ADMIN_PROP_1));
    }

    // =========================================================================
    // 5. ESCENARIO: PUNTOS DE VOTACIÓN Y EMISIÓN DE VOTOS
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("F10-03-09: Puntos de votación - Creación, emisión con coeficiente, unicidad de voto y cierre")
    public void test09_puntosVotacionYEmisionVoto() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-09-VOTACION")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-26T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Aprobación Presupuesto")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // Mover a EN_CURSO
        actualizarEstado(id, "EN_CURSO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 1. Crear punto de votación
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(1)
                .titulo("Aprobación de Estados Financieros 2025")
                .descripcion("Se somete a aprobación el balance presentado por la contaduría.")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        MvcResult votRes = mockMvc.perform(post("/api/v1/asambleas/" + id + "/votaciones")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(votReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVotacion").isNumber())
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andReturn();

        VotacionDTO votDTO = objectMapper.readValue(votRes.getResponse().getContentAsString(), VotacionDTO.class);
        Long idVotacion = votDTO.getIdVotacion();

        // 2. Emitir Voto Unidad 1 (SI)
        VotoRequestDTO voto1 = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaVotante(USER_RESIDENTE)
                .opcionVoto("SI")
                .coeficienteVoto(BigDecimal.valueOf(25.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto1)))
                .andExpect(status().isCreated());

        // 3. Rechazar voto duplicado para Unidad 1 en misma votación
        mockMvc.perform(post("/api/v1/asambleas/" + id + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto1)))
                .andExpect(status().is4xxClientError());

        // 4. Emitir Voto Unidad 2 (NO)
        VotoRequestDTO voto2 = VotoRequestDTO.builder()
                .idUnidad(UNIDAD_2_ID)
                .idPersonaVotante(USER_ADMIN_PROP_1B)
                .opcionVoto("NO")
                .coeficienteVoto(BigDecimal.valueOf(10.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/votaciones/" + idVotacion + "/votar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voto2)))
                .andExpect(status().isCreated());

        // 5. Cerrar Votación y verificar resultado
        mockMvc.perform(put("/api/v1/asambleas/" + id + "/votaciones/" + idVotacion + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.aprobada").value(true)); // 25 > 10
    }

    // =========================================================================
    // 6. ESCENARIO: SEGURIDAD MULTI-TENANT (AISLAMIENTO Y CONTROL DE ACCESO)
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("F10-03-10: Multi-Tenant - Bloqueo cross-organization y cross-property con 403 Forbidden")
    public void test10_aislamientoMultiTenant() throws Exception {
        // Asamblea creada en Org 1, Propiedad 1
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-10-TENANT")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-28T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 1. Admin Propiedad 2 (Organización 2) intenta consultar detalle -> 403 Forbidden o 404 Not Found (RLS)
        int sc1 = mockMvc.perform(get("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andReturn().getResponse().getStatus();
        assertTrue(sc1 == 403 || sc1 == 404, "Debe retornar 403 o 404 bajo RLS");

        // 2. Admin Propiedad 1B (Misma Org 1, distinta Propiedad) intenta consultar detalle -> 403 Forbidden o 404 Not Found (RLS)
        int sc2 = mockMvc.perform(get("/api/v1/asambleas/" + id)
                        .header("Authorization", "Bearer " + tokenAdminProp1B)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1B))
                .andReturn().getResponse().getStatus();
        assertTrue(sc2 == 403 || sc2 == 404, "Debe retornar 403 o 404 bajo RLS");

        // 3. Admin Propiedad 2 intenta registrar asistencia en asamblea de Propiedad 1 -> 403 Forbidden o 404 Not Found (RLS)
        AsistenciaRequestDTO asis = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaAsistente(USER_RESIDENTE)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(25.000000))
                .build();

        int sc3 = mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis)))
                .andReturn().getResponse().getStatus();
        assertTrue(sc3 == 403 || sc3 == 404, "Debe retornar 403 o 404 bajo RLS");
    }

    // =========================================================================
    // 7. ESCENARIO: CONFINAMIENTO DE ROL PORTERO (ZERO ACCESS)
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("F10-03-11: Confinamiento de Seguridad - Rol PORTERO no puede acceder a ningún endpoint de asambleas (403)")
    public void test11_confinamientoRolPortero() throws Exception {
        // GET /api/v1/asambleas -> 403 Forbidden
        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        // POST /api/v1/asambleas -> 403 Forbidden
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("PRESENCIAL")
                .titulo("ASAMBLEA-TEST-PORTERO")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-11-30T09:00:00")
                .lugarOEnlace("Salón")
                .ordenDelDia("1. Orden")
                .build();

        mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. ESCENARIO: AUDITORÍA INMUTABLE (AUDITORIA_LOG)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("F10-03-12: Auditoría inmutable - Registro determinista de eventos en AUDITORIA_LOG")
    public void test12_auditoriaLogRegistroEventos() throws Exception {
        // 1. Convocar asamblea
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-12-AUDIT")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-12-01T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // 2. Registrar asistencia
        AsistenciaRequestDTO asis = AsistenciaRequestDTO.builder()
                .idUnidad(UNIDAD_1_ID)
                .idPersonaAsistente(USER_RESIDENTE)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(25.000000))
                .build();

        mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asis)))
                .andExpect(status().isCreated());

        // 3. Cambiar estado a EN_CURSO
        actualizarEstado(id, "EN_CURSO", tokenAdminProp1, ASSIGN_ADMIN_PROP_1);

        // 4. Verificar trazabilidad en AUDITORIA_LOG
        setElevatedContext();
        try {
            Integer countAsamblea = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'ASAMBLEAS' AND ID_ENTIDAD_AFECTADA = ?",
                    Integer.class, id);
            assertNotNull(countAsamblea);
            assertTrue(countAsamblea >= 2, "Debe existir registro en AUDITORIA_LOG para la asamblea (creación y cambio de estado)");

            Integer countAsistencia = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'ASISTENCIAS_ASAMBLEA' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_1_ID);
            assertNotNull(countAsistencia);
            assertTrue(countAsistencia > 0, "Debe existir registro en AUDITORIA_LOG para ASISTENCIAS_ASAMBLEA");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // 9. ESCENARIO: CONCURRENCIA Y BLOQUEO PESIMISTA
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("F10-03-13: Concurrencia - Registro multi-hilo de asistencia sin corrupción de quórum ni deadlocks")
    public void test13_concurrenciaRegistroAsistencia() throws Exception {
        AsambleaCreateRequestDTO req = AsambleaCreateRequestDTO.builder()
                .idPropiedad(PROP_1_ID)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("ASAMBLEA-TEST-13-CONCURRENT")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-12-05T09:00:00")
                .lugarOEnlace("Salón Principal")
                .ordenDelDia("1. Orden")
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

        Long id = objectMapper.readValue(res.getResponse().getContentAsString(), AsambleaDTO.class).getIdAsamblea();

        // Registro concurrente de unidad 1 y unidad 2
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                AsistenciaRequestDTO asis = AsistenciaRequestDTO.builder()
                        .idUnidad(UNIDAD_1_ID)
                        .idPersonaAsistente(USER_RESIDENTE)
                        .esPropietarioDirecto("S")
                        .coeficientePonderado(BigDecimal.valueOf(25.000000))
                        .build();

                MvcResult r = mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                                .header("Authorization", "Bearer " + tokenAdminProp1)
                                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(asis)))
                        .andReturn();
                statuses.add(r.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                AsistenciaRequestDTO asis = AsistenciaRequestDTO.builder()
                        .idUnidad(UNIDAD_2_ID)
                        .idPersonaAsistente(USER_ADMIN_PROP_1B)
                        .esPropietarioDirecto("S")
                        .coeficientePonderado(BigDecimal.valueOf(30.000000))
                        .build();

                MvcResult r = mockMvc.perform(post("/api/v1/asambleas/" + id + "/asistencias")
                                .header("Authorization", "Bearer " + tokenAdminProp1)
                                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(asis)))
                        .andReturn();
                statuses.add(r.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            } finally {
                latch.countDown();
            }
        });

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(finished, "Las operaciones concurrentes deben completarse dentro del tiempo límite");

        // Ambos registros deben haber sido exitosos (201 Created)
        assertEquals(2, statuses.size());
        for (int status : statuses) {
            assertEquals(201, status, "Cada registro concurrente debe responder 201 Created");
        }

        // Quórum debe ser exactamente 55.00%
        mockMvc.perform(get("/api/v1/asambleas/" + id + "/quorum")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumAlcanzadoPct").value(55.0))
                .andExpect(jsonPath("$.tieneQuorum").value(true))
                .andExpect(jsonPath("$.totalUnidadesRegistradas").value(2));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void actualizarEstado(Long idAsamblea, String nuevoEstado, String token, long assignId) throws Exception {
        mockMvc.perform(put("/api/v1/asambleas/" + idAsamblea + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", assignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AsambleaEstadoUpdateRequestDTO(nuevoEstado))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(nuevoEstado));
    }
}
