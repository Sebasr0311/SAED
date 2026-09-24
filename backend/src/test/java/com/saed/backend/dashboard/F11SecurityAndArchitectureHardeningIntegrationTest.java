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
import com.saed.backend.finanzas.dto.ResidenteDashboardDTO;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F11SecurityAndArchitectureHardeningIntegrationTest
 *
 * Deterministic test suite verifying F11 P0/P1 fixes and architectural hardening:
 * 1. FlujoCajaRepository multi-tenant isolation and 403 on missing context (F11-SEC-P0-01).
 * 2. ReportesController role authorization and tenant isolation (F11-SEC-P0-02 validation).
 * 3. Presupuesto CRUD and ID_PRESUPUESTO contract verification (F11-UX-P1-01).
 * 4. GET /api/v1/dashboard/propiedad atomic aggregation and anti-IDOR confinement.
 * 5. GET /api/v1/dashboard/porteria operational metrics and financial data exclusion.
 * 6. ResidenteDashboardDTO unit identifier enhancement (F11-UX-P2-01).
 * 7. ThreadLocal context isolation & zero-trust session hygiene.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11SecurityAndArchitectureHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlujoCajaRepository flujoCajaRepository;

    @Autowired(required = false)
    private FinanzasService finanzasService;

    @MockBean
    private AssignmentService assignmentService;

    // Identities
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
            try {
                jdbcTemplate.execute("DELETE FROM PRESUPUESTOS WHERE RUBRO LIKE '%F11%'");
            } catch (Exception ignored) {}
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
        setElevatedContext();
        try {
            jdbcTemplate.execute("DELETE FROM PRESUPUESTOS WHERE RUBRO LIKE '%F11%'");
        } catch (Exception ignored) {}
        clearContext();
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

    private void ensureBaseData() {
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");
        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));

        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2@saed.com");
        ensurePersona(USER_RESIDENTE, "1000000805", "Carlos", "Residente", "residente@saed.com");
        ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_f11_test", "superadmin@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_f11_test", "adminorg1@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_f11_test", "adminprop1@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_f11_test", "adminprop2@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_f11_test", "residente@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_f11_test", "portero@saed.com");

        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);
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

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coef);
        } else {
            jdbcTemplate.update("UPDATE UNIDADES SET ID_PROPIEDAD = ?, IDENTIFICADOR = ?, COEFICIENTE_COPROPIEDAD = ?, ESTADO = 'ACTIVA' WHERE ID_UNIDAD = ?",
                    propId, identificador, coef, id);
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
        } else {
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = ?",
                    idUsuario, idOrg, idProp, idUnidad, id);
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
        res.setUnidad(new com.saed.backend.authorization.dto.UnitDTO(UNIDAD_1_ID, "A101"));
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
    // 1. FLUJO DE CAJA & TENANT ISOLATION (F11-SEC-P0-01)
    // =========================================================================

    @Test
    @Order(1)
    public void test01_flujoCaja_sinContexto_debeLanzarAccessDeniedException() {
        SaedContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> {
            flujoCajaRepository.getTotalIngresos();
        }, "F11-SEC-P0-01: ausencia total de tenant context debe provocar AccessDeniedException");
    }

    @Test
    @Order(2)
    public void test02_flujoCaja_contextoPropiedad_debeRetornarDatosAislados() {
        SaedContext ctx = SaedContext.builder()
                .userId(USER_ADMIN_PROP_1)
                .organizationId(ORG_1_ID)
                .propertyId(PROP_1_ID)
                .roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD")
                .build();
        SaedContextHolder.setContext(ctx);
        try {
            BigDecimal total = flujoCajaRepository.getTotalIngresos();
            assertNotNull(total, "El total de ingresos para la propiedad debe ser no nulo");
            assertTrue(total.compareTo(BigDecimal.ZERO) >= 0);
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    // =========================================================================
    // 2. REPORTES CONTROLLER AUTH & ISOLATION (F11-SEC-P0-02 VALIDATION)
    // =========================================================================

    @Test
    @Order(3)
    public void test03_reportes_unauthenticated_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    public void test04_reportes_residente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    public void test05_reportes_portero_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    public void test06_reportes_adminPropiedad_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(7)
    public void test07_reportes_adminOrganizacion_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-cuotas")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // 3. DASHBOARD OPERATIVO PROPIEDAD (GET /api/v1/dashboard/propiedad)
    // =========================================================================

    @Test
    @Order(8)
    public void test08_dashboardPropiedad_unauthenticated_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/propiedad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    public void test09_dashboardPropiedad_residenteYPortero_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/propiedad")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/propiedad")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    public void test10_dashboardPropiedad_adminPropiedad_retornaKPIsCompletos() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/propiedad")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID))
                .andExpect(jsonPath("$.data.totalUnidades").isNumber())
                .andExpect(jsonPath("$.data.totalPersonas").isNumber())
                .andExpect(jsonPath("$.data.carteraTotal").isNumber())
                .andExpect(jsonPath("$.data.moraTotal").isNumber())
                .andExpect(jsonPath("$.data.cuotasPendientesCount").isNumber())
                .andExpect(jsonPath("$.data.paquetesPendientes").isNumber())
                .andExpect(jsonPath("$.data.visitasActivas").isNumber())
                .andExpect(jsonPath("$.data.multasPendientes").isNumber())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.get("data").get("totalUnidades").asLong() >= 1, "Debe reportar al menos una unidad configurada");
    }

    @Test
    @Order(11)
    public void test11_dashboardPropiedad_antiIdor_ignoraParametrosQuery() throws Exception {
        // Un admin de la propiedad 1 intenta pasar query param solicitando la propiedad 2
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/propiedad?propertyId=" + PROP_2_ID)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID)) // Sigue devolviendo la propiedad 1!
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PROP_1_ID, root.get("data").get("idPropiedad").asLong(), "Anti-IDOR: No debe permitir cambiar de propiedad por query parameter");
    }

    // =========================================================================
    // 4. DASHBOARD OPERATIVO PORTERIA (GET /api/v1/dashboard/porteria)
    // =========================================================================

    @Test
    @Order(12)
    public void test12_dashboardPorteria_unauthenticated_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/porteria"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    public void test13_dashboardPorteria_residente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/porteria")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    public void test14_dashboardPorteria_portero_retornaMetricasOperativasSinFinanzas() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/porteria")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID))
                .andExpect(jsonPath("$.data.visitasActivas").isNumber())
                .andExpect(jsonPath("$.data.domiciliosActivos").isNumber())
                .andExpect(jsonPath("$.data.totalPases").isNumber())
                .andExpect(jsonPath("$.data.parqueaderosDisponibles").isNumber())
                .andExpect(jsonPath("$.data.paquetesEnCustodia").isNumber())
                .andReturn();

        // Verificar que no expone campos financieros al portero
        String json = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(json).get("data");
        assertNull(data.get("carteraTotal"), "Portero dashboard no debe exponer carteraTotal");
        assertNull(data.get("moraTotal"), "Portero dashboard no debe exponer moraTotal");
        assertNull(data.get("pagosRecientes"), "Portero dashboard no debe exponer pagosRecientes");
    }

    @Test
    @Order(15)
    public void test15_dashboardPorteria_adminPropiedad_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/porteria")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID));
    }

    // =========================================================================
    // 5. PRESUPUESTO CRUD & DTO CONTRACT (F11-UX-P1-01)
    // =========================================================================

    @Test
    @Order(16)
    public void test16_presupuesto_residente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/presupuestos")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(17)
    public void test17_presupuesto_adminPropiedad_lifecycleYContratoIdPresupuesto() throws Exception {
        String rubroTest = "Mantenimiento Ascensores F11 " + System.currentTimeMillis();
        Map<String, Object> req = new HashMap<>();
        req.put("rubro", rubroTest);
        req.put("tipo", "EGRESO");
        req.put("montoPresupuestado", 1500000);
        req.put("vigenciaAnio", 2026);

        MvcResult createResult = mockMvc.perform(post("/api/v1/presupuestos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long idPresupuesto = createdNode.has("ID_PRESUPUESTO") ? createdNode.get("ID_PRESUPUESTO").asLong() : createdNode.get("id").asLong();
        assertNotNull(idPresupuesto);

        // 2. Listar presupuestos y confirmar contrato con ID_PRESUPUESTO (lo que espera la UI corregida)
        MvcResult listResult = mockMvc.perform(get("/api/v1/presupuestos")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listNode = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data");
        assertTrue(listNode.isArray());
        boolean foundCreated = false;
        for (JsonNode item : listNode) {
            assertTrue(item.has("ID_PRESUPUESTO"), "Cada fila de presupuesto debe contener ID_PRESUPUESTO");
            if (item.get("ID_PRESUPUESTO").asLong() == idPresupuesto) {
                foundCreated = true;
                assertEquals(rubroTest, item.get("RUBRO").asText());
            }
        }
        assertTrue(foundCreated, "El presupuesto creado debe aparecer en la lista");

        // 3. Actualizar presupuesto
        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("rubro", rubroTest + " Actualizado");
        updateReq.put("montoPresupuestado", 1800000);
        updateReq.put("estado", "APROBADO");

        mockMvc.perform(put("/api/v1/presupuestos/" + idPresupuesto)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // 4. Eliminar presupuesto
        mockMvc.perform(delete("/api/v1/presupuestos/" + idPresupuesto)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 6. RESIDENTE DASHBOARD DTO ENHANCEMENT (F11-UX-P2-01)
    // =========================================================================

    @Test
    @Order(18)
    public void test18_residenteDashboard_incluyeIdentificadorUnidad() {
        if (finanzasService != null) {
            SaedContext ctx = SaedContext.builder()
                    .userId(USER_RESIDENTE)
                    .organizationId(ORG_1_ID)
                    .propertyId(PROP_1_ID)
                    .unitId(UNIDAD_1_ID)
                    .roleCode("RESIDENTE")
                    .roleScope("UNIDAD")
                    .build();
            SaedContextHolder.setContext(ctx);
            try {
                ResidenteDashboardDTO dto = finanzasService.getDashboardResidente(USER_RESIDENTE);
                assertNotNull(dto, "El dashboard de residente debe ser no nulo");
                assertEquals(UNIDAD_1_ID, dto.getIdUnidad(), "idUnidad debe coincidir");
                assertEquals("A101", dto.getIdentificadorUnidad(), "identificadorUnidad debe poblarse correctamente");
            } finally {
                SaedContextHolder.clearContext();
            }
        }
    }

    // =========================================================================
    // 7. CONTEXT ISOLATION & HYGIENE
    // =========================================================================

    @Test
    @Order(19)
    public void test19_contextHygiene_aislamientoEntreHilos() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean thread2SawContext = new AtomicBoolean(false);

        Future<?> f1 = executor.submit(() -> {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(9999L)
                    .propertyId(8888L)
                    .roleCode("ADMIN_PROPIEDAD")
                    .build());
            latch.countDown();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
            SaedContextHolder.clearContext();
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                latch.await(2, TimeUnit.SECONDS);
                SaedContext c = SaedContextHolder.getContext();
                if (c != null && c.getUserId() != null) {
                    thread2SawContext.set(true);
                }
            } catch (Exception ignored) {}
        });

        f1.get(2, TimeUnit.SECONDS);
        f2.get(2, TimeUnit.SECONDS);
        executor.shutdown();

        assertFalse(thread2SawContext.get(), "El contexto ThreadLocal no debe filtrarse a otros hilos");
    }
}
