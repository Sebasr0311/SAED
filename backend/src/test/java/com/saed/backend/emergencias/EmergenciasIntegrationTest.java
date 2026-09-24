package com.saed.backend.emergencias;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.emergencias.dto.*;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EmergenciasIntegrationTest — Dedicated Full Integration, Security, Lifecycle,
 * Multi-Tenant and Anti-IDOR Test Suite for F10-08 Emergencias (SAED 2.0).
 *
 * Covers:
 * 1. Plan creation with all 6 canonical contingency types (INCENDIO, TERREMOTO, INUNDACION, FUGA_GAS, AMENAZA_SEGURIDAD, GENERAL).
 * 2. Contact creation with all 8 canonical service types (POLICIA_CAI, BOMBEROS, CRUZ_ROJA_AMBULANCIA, GAS_EMERGENCIAS, ACUEDUCTO_URGENCIAS, ENERGIA_URGENCIAS, DEFENSA_CIVIL, OTRO).
 * 3. Negative validation tests for invalid enums (HTTP 400 Bad Request, never HTTP 500 / ORA-02290).
 * 4. Granular role-based authorization matrix (isolated per-role testing for ADMIN_PROPIEDAD, ADMIN_ORGANIZACION, PORTERO, RESIDENTE, unauthenticated).
 * 5. Tactical Minuta directory filtering (ES_PRIORITARIO_MINUTA = 'S') and ordering (ORDEN_VISUALIZACION ASC).
 * 6. Multi-tenant property isolation and anti-IDOR checks between Property 1 and Property 2.
 * 7. Audit trail logging in AUDITORIA_LOG for mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EmergenciasIntegrationTest {

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

    // Test Users & Assignments
    private static final long USER_SUPERADMIN    = 1L;
    private static final long ASSIGN_SUPERADMIN  = 101L;

    private static final long USER_ADMIN_ORG_1   = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1  = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_PROP_2  = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE     = 805L;
    private static final long ASSIGN_RESIDENTE   = 905L;

    private static final long USER_PORTERO       = 806L;
    private static final long ASSIGN_PORTERO     = 906L;

    private static final long ORG_1_ID  = 1L;
    private static final long ORG_2_ID  = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    // JWT Tokens
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
            cleanupEmergenciasData();
            ensureBaseData();
            setupMockAssignments();

            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1  = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente  = jwtProvider.generateIdentityToken(USER_RESIDENTE);
            tokenPortero    = jwtProvider.generateIdentityToken(USER_PORTERO);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        setElevatedContext();
        try {
            cleanupEmergenciasData();
        } catch (Exception ignored) {}
        clearContext();
    }

    // =========================================================================
    // SECURITY CONTEXT & SEED INFRASTRUCTURE
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

    private void cleanupEmergenciasData() {
        jdbcTemplate.execute("""
            BEGIN
                DELETE FROM PLANES_EMERGENCIA WHERE ID_PROPIEDAD IN (1, 8802);
                DELETE FROM CONTACTOS_EMERGENCIA WHERE ID_PROPIEDAD IN (1, 8802);
                COMMIT;
            END;
        """);
    }

    private void ensureBaseData() {
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900111222-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "900222333-2", "org2@saed.com");

        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED Test");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte Test");

        ensureUnidad(1L, PROP_1_ID, "101");

        ensurePersona(1L, "CC-1", "Super", "Admin", "super@saed.com");
        ensurePersona(801L, "CC-801", "Admin", "OrgUno", "adminorg1@saed.com");
        ensurePersona(802L, "CC-802", "Admin", "PropUno", "adminprop1@saed.com");
        ensurePersona(803L, "CC-803", "Admin", "PropDos", "adminprop2@saed.com");
        ensurePersona(805L, "CC-805", "Resi", "Dente", "residente@saed.com");
        ensurePersona(806L, "CC-806", "Por", "Tero", "portero@saed.com");

        ensureUsuario(USER_SUPERADMIN, 1L, "superadmin_test", "super@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, 802L, "adminprop1_test", "adminprop1@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, 803L, "adminprop2_test", "adminprop2@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, 801L, "adminorg1_test", "adminorg1@saed.com");
        ensureUsuario(USER_RESIDENTE, 805L, "residente_test", "residente@saed.com");
        ensureUsuario(USER_PORTERO, 806L, "portero_test", "portero@saed.com");

        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, 1L);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED Test");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte Test");

        AssignmentResponseDTO sa = new AssignmentResponseDTO();
        sa.setIdAsignacion(ASSIGN_SUPERADMIN);
        sa.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(sa));

        AssignmentResponseDTO aOrg1 = new AssignmentResponseDTO();
        aOrg1.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        aOrg1.setOrganizacion(org1);
        aOrg1.setPropiedad(prop1);
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
        res.setUnidad(new UnitDTO(1L, "101"));
        res.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(res));

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(port));
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
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle Test 123', 'Bogota', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    id, propId, identificador);
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
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) " +
                            "VALUES (?, ?, (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, rol, idOrg, idProp, idUnidad);
        } else {
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = ?), ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = ?",
                    idUsuario, rol, idOrg, idProp, idUnidad, id);
        }
    }

    // =========================================================================
    // 1. CREACIÓN DE PLANES CON LOS 6 TIPOS CANÓNICOS (CK_PLANEMERG_TIPO)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("G1-P01: Crear planes con los 6 tipos canónicos de contingencia")
    public void testCrearPlanesTodosLosTiposContingencia() throws Exception {
        List<String> tiposCanónicos = List.of(
                "INCENDIO",
                "TERREMOTO",
                "INUNDACION",
                "FUGA_GAS",
                "AMENAZA_SEGURIDAD",
                "GENERAL"
        );

        for (String tipo : tiposCanónicos) {
            PlanEmergenciaRequestDTO req = new PlanEmergenciaRequestDTO();
            req.setTitulo("Plan Maestro ante " + tipo);
            req.setTipoContingencia(tipo);
            req.setPuntosEncuentro("Parqueadero exterior zona verde");
            req.setRutasEvacuacionDesc("Escaleras de emergencia principales y salida norte.");
            req.setMapaEvacuacionUrl("https://storage.saed.com/mapas/" + tipo.toLowerCase() + ".png");
            req.setDocumentoPlanUrl("https://storage.saed.com/docs/" + tipo.toLowerCase() + ".pdf");
            req.setFechaUltimaRevision(LocalDate.now());
            req.setEstado("ACTIVO");

            MvcResult res = mockMvc.perform(post("/api/v1/emergencias/planes")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.idPlanEmergencia").isNumber())
                    .andExpect(jsonPath("$.message").value("Plan creado exitosamente"))
                    .andReturn();

            Map<?, ?> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
            Long idPlan = ((Number) map.get("idPlanEmergencia")).longValue();
            assertTrue(idPlan > 0, "El ID del plan creado debe ser positivo");
        }

        // Verificar que los 6 planes están listados para la propiedad 1
        mockMvc.perform(get("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    // =========================================================================
    // 2. CREACIÓN DE CONTACTOS CON LOS 8 TIPOS CANÓNICOS (CK_CONTEMERG_TIPO)
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("G1-P02: Crear contactos con los 8 tipos canónicos de servicio")
    public void testCrearContactosTodosLosTiposServicio() throws Exception {
        List<String> tiposServicio = List.of(
                "POLICIA_CAI",
                "BOMBEROS",
                "CRUZ_ROJA_AMBULANCIA",
                "GAS_EMERGENCIAS",
                "ACUEDUCTO_URGENCIAS",
                "ENERGIA_URGENCIAS",
                "DEFENSA_CIVIL",
                "OTRO"
        );

        int orden = 1;
        for (String tipo : tiposServicio) {
            ContactoEmergenciaRequestDTO req = new ContactoEmergenciaRequestDTO();
            req.setEntidad("Organismo " + tipo);
            req.setTipoServicio(tipo);
            req.setTelefonoPrincipal("123456" + orden);
            req.setTelefonoAlterno("30000000" + orden);
            req.setDireccion("Avenida Siempre Viva #" + orden);
            req.setEsPrioritarioMinuta(orden % 2 == 1 ? "S" : "N");
            req.setOrdenVisualizacion(orden++);

            MvcResult res = mockMvc.perform(post("/api/v1/emergencias/contactos")
                            .header("Authorization", "Bearer " + tokenAdminProp1)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.idContactoEmergencia").isNumber())
                    .andExpect(jsonPath("$.message").value("Contacto creado exitosamente"))
                    .andReturn();

            Map<?, ?> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
            Long idContacto = ((Number) map.get("idContactoEmergencia")).longValue();
            assertTrue(idContacto > 0);
        }

        // Verificar que los 8 contactos están listados para la propiedad 1
        mockMvc.perform(get("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    // =========================================================================
    // 3. VALIDACIONES NEGATIVAS DE ENUMS (HTTP 400 Bad Request, NUNCA HTTP 500)
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("G1-P03: Validación negativa - Enums inválidos son rechazados con HTTP 400 antes de Oracle")
    public void testValidacionNegativaEnums() throws Exception {
        // A. Tipo de contingencia inválido
        PlanEmergenciaRequestDTO reqPlanInvalido = new PlanEmergenciaRequestDTO();
        reqPlanInvalido.setTitulo("Plan Invalido");
        reqPlanInvalido.setTipoContingencia("METEORITO_ALIENIGENA");
        reqPlanInvalido.setPuntosEncuentro("Zona 1");
        reqPlanInvalido.setRutasEvacuacionDesc("Salida A");

        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPlanInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.tipoContingencia").exists());

        // B. Estado de plan inválido
        PlanEmergenciaRequestDTO reqEstadoInvalido = new PlanEmergenciaRequestDTO();
        reqEstadoInvalido.setTitulo("Plan Invalido Estado");
        reqEstadoInvalido.setTipoContingencia("INCENDIO");
        reqEstadoInvalido.setPuntosEncuentro("Zona 1");
        reqEstadoInvalido.setRutasEvacuacionDesc("Salida A");
        reqEstadoInvalido.setEstado("ARCHIVADO_DESCONOCIDO");

        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEstadoInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.estado").exists());

        // C. Tipo de servicio inválido (e.g. valor antiguo 'POLICIA')
        ContactoEmergenciaRequestDTO reqContactoInvalido = new ContactoEmergenciaRequestDTO();
        reqContactoInvalido.setEntidad("Estación Antigua");
        reqContactoInvalido.setTipoServicio("POLICIA"); // Invalido: en DB es POLICIA_CAI
        reqContactoInvalido.setTelefonoPrincipal("123");

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqContactoInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.tipoServicio").exists());

        // D. Campo esPrioritarioMinuta inválido (debe ser S o N)
        ContactoEmergenciaRequestDTO reqPrioridadInvalida = new ContactoEmergenciaRequestDTO();
        reqPrioridadInvalida.setEntidad("Cruz Roja");
        reqPrioridadInvalida.setTipoServicio("CRUZ_ROJA_AMBULANCIA");
        reqPrioridadInvalida.setTelefonoPrincipal("132");
        reqPrioridadInvalida.setEsPrioritarioMinuta("X"); // Invalido

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPrioridadInvalida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.esPrioritarioMinuta").exists());
    }

    // =========================================================================
    // 4. MATRIZ DE AUTORIZACIÓN INDIVIDUAL POR ROL
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("G1-P04: Matriz de autorización - Pruebas individuales de acceso y denegación")
    public void testMatrizAutorizacionIndividual() throws Exception {
        PlanEmergenciaRequestDTO planReq = new PlanEmergenciaRequestDTO();
        planReq.setTitulo("Plan Seguridad");
        planReq.setTipoContingencia("AMENAZA_SEGURIDAD");
        planReq.setPuntosEncuentro("Lobby");
        planReq.setRutasEvacuacionDesc("Salida peatonal");

        ContactoEmergenciaRequestDTO contactoReq = new ContactoEmergenciaRequestDTO();
        contactoReq.setEntidad("CAI Local");
        contactoReq.setTipoServicio("POLICIA_CAI");
        contactoReq.setTelefonoPrincipal("3101112233");

        // A. MUTACIONES: Solo ADMIN_PROPIEDAD tiene permiso
        // 1. ADMIN_PROPIEDAD -> Permitido (201)
        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planReq)))
                .andExpect(status().isCreated());

        // 2. ADMIN_ORGANIZACION -> Denegado (403 Forbidden)
        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planReq)))
                .andExpect(status().isForbidden());

        // 3. PORTERO -> Denegado (403 Forbidden)
        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planReq)))
                .andExpect(status().isForbidden());

        // 4. RESIDENTE -> Denegado (403 Forbidden)
        mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planReq)))
                .andExpect(status().isForbidden());

        // 5. Contactos mutaciones para PORTERO y RESIDENTE -> 403 Forbidden
        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactoReq)))
                .andExpect(status().isForbidden());

        // B. LECTURAS: Permitidas para ADMIN_PROPIEDAD, ADMIN_ORGANIZACION, PORTERO y RESIDENTE
        mockMvc.perform(get("/api/v1/emergencias/resumen")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk());

        // C. Solicitud no autenticada -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/emergencias/planes"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 5. MINUTA TÁCTICA: FILTRADO Y ORDENAMIENTO (ORDEN_VISUALIZACION ASC)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("G1-P05: Minuta táctica - Solo retorna contactos prioritarios (ES_PRIORITARIO_MINUTA = 'S') en orden")
    public void testMinutaTacticaFiltroYOrden() throws Exception {
        // 1. Crear Contacto A: Prioritario S, orden 2
        ContactoEmergenciaRequestDTO cA = new ContactoEmergenciaRequestDTO();
        cA.setEntidad("Bomberos Central");
        cA.setTipoServicio("BOMBEROS");
        cA.setTelefonoPrincipal("119");
        cA.setEsPrioritarioMinuta("S");
        cA.setOrdenVisualizacion(2);

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cA)))
                .andExpect(status().isCreated());

        // 2. Crear Contacto B: No Prioritario N, orden 1 (secundario)
        ContactoEmergenciaRequestDTO cB = new ContactoEmergenciaRequestDTO();
        cB.setEntidad("Soporte Ascensores S.A.");
        cB.setTipoServicio("OTRO");
        cB.setTelefonoPrincipal("6019998877");
        cB.setEsPrioritarioMinuta("N");
        cB.setOrdenVisualizacion(1);

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cB)))
                .andExpect(status().isCreated());

        // 3. Crear Contacto C: Prioritario S, orden 1
        ContactoEmergenciaRequestDTO cC = new ContactoEmergenciaRequestDTO();
        cC.setEntidad("CAI Cuadrante Inmediato");
        cC.setTipoServicio("POLICIA_CAI");
        cC.setTelefonoPrincipal("123");
        cC.setEsPrioritarioMinuta("S");
        cC.setOrdenVisualizacion(1);

        mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cC)))
                .andExpect(status().isCreated());

        // 4. Consultar /contactos/minuta como PORTERO
        MvcResult res = mockMvc.perform(get("/api/v1/emergencias/contactos/minuta")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // Solo A y C, B es 'N'
                .andExpect(jsonPath("$[0].entidad").value("CAI Cuadrante Inmediato")) // Orden 1 primero
                .andExpect(jsonPath("$[1].entidad").value("Bomberos Central"))       // Orden 2 segundo
                .andReturn();

        // 5. Consultar /contactos/minuta como RESIDENTE
        mockMvc.perform(get("/api/v1/emergencias/contactos/minuta")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // =========================================================================
    // 6. AISLAMIENTO MULTI-TENANT Y ANTI-IDOR ENTRE PROPIEDADES
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("G1-P06: Multi-tenant y Anti-IDOR - Propiedad A no puede ver ni modificar datos de Propiedad B")
    public void testAislamientoMultiTenantYAntiIdor() throws Exception {
        // A. Crear Plan en Propiedad 1
        PlanEmergenciaRequestDTO p1 = new PlanEmergenciaRequestDTO();
        p1.setTitulo("Plan Evacuacion Propiedad Uno");
        p1.setTipoContingencia("INCENDIO");
        p1.setPuntosEncuentro("Patio 1");
        p1.setRutasEvacuacionDesc("Escaleras Torre 1");

        MvcResult resP1 = mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isCreated())
                .andReturn();
        Long idPlanP1 = ((Number) objectMapper.readValue(resP1.getResponse().getContentAsString(), Map.class).get("idPlanEmergencia")).longValue();

        // B. Crear Plan en Propiedad 2
        PlanEmergenciaRequestDTO p2 = new PlanEmergenciaRequestDTO();
        p2.setTitulo("Plan Evacuacion Propiedad Dos");
        p2.setTipoContingencia("TERREMOTO");
        p2.setPuntosEncuentro("Patio 2");
        p2.setRutasEvacuacionDesc("Escaleras Torre 2");

        MvcResult resP2 = mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2)))
                .andExpect(status().isCreated())
                .andReturn();
        Long idPlanP2 = ((Number) objectMapper.readValue(resP2.getResponse().getContentAsString(), Map.class).get("idPlanEmergencia")).longValue();

        // C. Crear Contacto en Propiedad 2
        ContactoEmergenciaRequestDTO c2 = new ContactoEmergenciaRequestDTO();
        c2.setEntidad("CAI Norte Propiedad Dos");
        c2.setTipoServicio("POLICIA_CAI");
        c2.setTelefonoPrincipal("3112223344");
        c2.setEsPrioritarioMinuta("S");

        MvcResult resC2 = mockMvc.perform(post("/api/v1/emergencias/contactos")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(c2)))
                .andExpect(status().isCreated())
                .andReturn();
        Long idContactoP2 = ((Number) objectMapper.readValue(resC2.getResponse().getContentAsString(), Map.class).get("idContactoEmergencia")).longValue();

        // D. Verificación de Aislamiento en Listados:
        // Admin Propiedad 1 solo ve sus planes (idPlanP1), no los de Propiedad 2 (idPlanP2)
        mockMvc.perform(get("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPlanEmergencia", hasItem(idPlanP1.intValue())))
                .andExpect(jsonPath("$[*].idPlanEmergencia", not(hasItem(idPlanP2.intValue()))));

        // Admin Propiedad 2 solo ve sus planes (idPlanP2)
        mockMvc.perform(get("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPlanEmergencia", hasItem(idPlanP2.intValue())))
                .andExpect(jsonPath("$[*].idPlanEmergencia", not(hasItem(idPlanP1.intValue()))));

        // E. Anti-IDOR: Admin Propiedad 1 NO puede leer, modificar ni eliminar el plan de Propiedad 2
        mockMvc.perform(get("/api/v1/emergencias/planes/" + idPlanP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest()); // IllegalArgumentException mapeado a 400 por GlobalExceptionHandler

        mockMvc.perform(put("/api/v1/emergencias/planes/" + idPlanP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/emergencias/planes/" + idPlanP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest());

        // F. Anti-IDOR: Admin Propiedad 1 NO puede leer, modificar ni eliminar el contacto de Propiedad 2
        mockMvc.perform(get("/api/v1/emergencias/contactos/" + idContactoP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/emergencias/contactos/" + idContactoP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(c2)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/emergencias/contactos/" + idContactoP2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 7. TRAZABILIDAD Y AUDITORÍA EN AUDITORIA_LOG
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("G1-P07: Auditoría - Las mutaciones registran eventos en AUDITORIA_LOG")
    public void testAuditoriaMutaciones() throws Exception {
        PlanEmergenciaRequestDTO p = new PlanEmergenciaRequestDTO();
        p.setTitulo("Plan Auditoria");
        p.setTipoContingencia("FUGA_GAS");
        p.setPuntosEncuentro("Zona Cero");
        p.setRutasEvacuacionDesc("Sector Sur");

        MvcResult res = mockMvc.perform(post("/api/v1/emergencias/planes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idPlan = ((Number) objectMapper.readValue(res.getResponse().getContentAsString(), Map.class).get("idPlanEmergencia")).longValue();

        // Actualizar el plan
        p.setTitulo("Plan Auditoria Actualizado");
        mockMvc.perform(put("/api/v1/emergencias/planes/" + idPlan)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());

        // Eliminar el plan
        mockMvc.perform(delete("/api/v1/emergencias/planes/" + idPlan)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNoContent());

        // Verificar registro de auditoría en la base de datos
        setElevatedContext();
        try {
            Integer logsPlan = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM AUDITORIA_LOG WHERE ENTIDAD = 'PLAN_EMERGENCIA'",
                    Integer.class
            );
            assertNotNull(logsPlan);
            assertTrue(logsPlan >= 3, "Deben existir al menos 3 eventos de auditoria para PLAN_EMERGENCIA (CREATE, UPDATE, DELETE)");
        } finally {
            clearContext();
        }
    }
}
