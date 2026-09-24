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
import com.saed.backend.dashboard.service.ReportesService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F11_04_ReportesBaseMotorIntegrationTest
 *
 * Suite determinista de integración para el Motor Base de Reportes (F11-04 Bloque A):
 * 1. Seguridad: ADMIN_PROPIEDAD solo ve su propiedad.
 * 2. Seguridad: ADMIN_PROPIEDAD no puede adulterar contexto mediante propertyId.
 * 3. Seguridad: ADMIN_ORGANIZACION puede consultar su organización completa.
 * 4. Seguridad: ADMIN_ORGANIZACION puede consultar una propiedad perteneciente a su org.
 * 5. Seguridad: ADMIN_ORGANIZACION recibe 403 al consultar propiedad ajena.
 * 6. Seguridad: RESIDENTE recibe 403 FORBIDDEN.
 * 7. Seguridad: PORTERO recibe 403 FORBIDDEN.
 * 8. Seguridad: Usuario anónimo recibe 401 UNAUTHORIZED.
 * 9. Filtros: fechaInicio acota resultados.
 * 10. Filtros: fechaFin acota resultados.
 * 11. Filtros: Rango inválido (inicio > fin) retorna 400 VALIDATION_FAILED.
 * 12. Filtros: Ausencia de filtros opera con valores seguros por defecto.
 * 13. Reportes: Cartera morosa incluye cálculo determinista de diasMora.
 * 14. Reportes: Ejecución de cuotas incluye facturado, recaudado y porcentaje.
 * 15. Reportes: Pagos recientes incluye campos de pago tipados.
 * 16. Contrato: JSON expone estricto camelCase y desacoplamiento Oracle.
 * 17. Contrato: Campos requeridos presentes en la respuesta.
 * 18. Contrato: Columnas internas de base de datos no expuestas.
 * 19. Contexto: ThreadLocal libre de fugas de contexto tras ejecución.
 * 20. Concurrencia: Aislamiento estricto multi-hilo entre diferentes tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_04_ReportesBaseMotorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportesService reportesService;

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

    private void ensureBaseData() {
        // Organizaciones
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

        // Propiedades
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

        // Unidades
        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.250000));

        // Conceptos de Cobro
        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "CUOTA_ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_2_ID, "CUOTA_ORD_2", "Cuota Ordinaria Prop 2");

        // Personas & Usuarios
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

        // Asignaciones
        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Semillas de Cuotas y Pagos para reportes
        try {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (9101, 9201)");
            jdbcTemplate.execute("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (9101, 9201)");
        } catch (Exception ignored) {}

        // Cuota vencida en Prop 1 (Unidad 9101) con monto mayor para ser determinista en orden
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 99000000, 99000000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        // Cuota pagada en Prop 1 (Unidad 9101)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-07', 200000, 0, 'PAGADA', DATE '2026-07-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        // Cuota vencida en Prop 2 (Unidad 9201)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 500000, 500000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_2_ID, CONCEPTO_2_ID);

        // Pago en Prop 1 (Unidad 9101) con timestamp reciente para aparecer primero en orden desc
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 200000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-09-30 14:30:00', 'REF-PAGO-001')
        """, UNIDAD_1_ID);
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
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle 100 # 15-20', 'Bogotá', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coef);
        }
    }

    private void ensureConcepto(long id, long orgId, long propId, String codigo, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM CONCEPTOS_COBRO WHERE ID_CONCEPTO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) VALUES (?, ?, ?, ?, ?, 'ADMINISTRACION', 'ACTIVO')",
                    id, orgId, propId, codigo, nombre);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, PASSWORD_HASH, ESTADO) VALUES (?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')",
                    id, personaId, username);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, idRol, idOrg, idProp, idUnidad);
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

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(port));
    }

    // =========================================================================
    // 1. SEGURIDAD & TENANT SCOPE (Tests 1-8)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("01: ADMIN_PROPIEDAD solo ve su propiedad asignada")
    public void test01_adminPropiedad_soloVeSuPropiedad() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.path("data");
        assertTrue(data.size() > 0, "Debe retornar registros de la propiedad 1");
        for (JsonNode row : data) {
            assertEquals("Edificio Residencial SAED", row.path("propiedad").asText());
            assertNotEquals("Condominio Campestre Norte", row.path("propiedad").asText());
        }
    }

    @Test
    @Order(2)
    @DisplayName("02: ADMIN_PROPIEDAD no puede alterar contexto mediante propertyId ajeno (403)")
    public void test02_adminPropiedad_noPuedeAlterarContextoMediantePropertyId() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?propertyId=" + PROP_2_ID)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @Order(3)
    @DisplayName("03: ADMIN_ORGANIZACION puede consultar toda su organización")
    public void test03_adminOrganizacion_puedeConsultarSuOrganizacion() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    @DisplayName("04: ADMIN_ORGANIZACION puede consultar una propiedad perteneciente a su org")
    public void test04_adminOrganizacion_puedeConsultarPropiedadPerteneciente() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?propertyId=" + PROP_1_ID)
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].propiedad").value("Edificio Residencial SAED"));
    }

    @Test
    @Order(5)
    @DisplayName("05: ADMIN_ORGANIZACION recibe 403 al consultar propiedad ajena a su org")
    public void test05_adminOrganizacion_noPuedeConsultarPropiedadDeOtraOrganizacion() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?propertyId=" + PROP_2_ID)
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @Order(6)
    @DisplayName("06: RESIDENTE recibe 403 en reportes administrativos")
    public void test06_residente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("07: PORTERO recibe 403 en reportes administrativos")
    public void test07_portero_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("08: Usuario anónimo recibe 401 UNAUTHORIZED")
    public void test08_usuarioAnonimo_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. FILTROS TEMPORALES Y VALIDACIONES (Tests 9-12)
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("09: Filtro fechaInicio acota correctamente los resultados")
    public void test09_filtroFechaInicio_aplicaCorrectamente() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?fechaInicio=2026-08-01")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].unidad").value("A101"));
    }

    @Test
    @Order(10)
    @DisplayName("10: Filtro fechaFin excluye cuotas posteriores")
    public void test10_filtroFechaFin_aplicaCorrectamente() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?fechaFin=2020-01-01")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(11)
    @DisplayName("11: Rango inválido (fechaInicio > fechaFin) retorna 400 VALIDATION_FAILED")
    public void test11_rangoInvalido_retorna400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa?fechaInicio=2026-12-31&fechaFin=2026-01-01")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(12)
    @DisplayName("12: Ausencia de filtros opera de forma segura por defecto")
    public void test12_ausenciaDeFiltros_operaPorDefecto() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // 3. REPORTES DETALLADOS Y AGREGADOS (Tests 13-15)
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("13: Reporte Cartera Morosa calcula diasMora determinista")
    public void test13_carteraMorosa_calculaDiasMora() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        assertTrue(data.size() > 0);
        JsonNode first = data.get(0);
        assertNotNull(first.path("diasMora"));
        assertTrue(first.path("diasMora").asLong() >= 0, "diasMora debe ser no negativo");
        assertEquals("A101", first.path("unidad").asText());
        assertTrue(first.path("deudaTotal").asDouble() > 0);
    }

    @Test
    @Order(14)
    @DisplayName("14: Reporte Ejecución Cuotas consolida facturado, recaudado y porcentaje")
    public void test14_ejecucionCuotas_consolidaValores() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reportes/ejecucion-cuotas")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].periodo").exists())
                .andExpect(jsonPath("$.data[0].totalFacturado").exists())
                .andExpect(jsonPath("$.data[0].totalRecaudado").exists())
                .andExpect(jsonPath("$.data[0].porcentajeRecaudado").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        assertTrue(data.size() > 0);
    }

    @Test
    @Order(15)
    @DisplayName("15: Reporte Pagos Recientes retorna listado de pagos tipado")
    public void test15_pagosRecientes_retornaListado() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/pagos-recientes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idPago").exists())
                .andExpect(jsonPath("$.data[0].unidad").value("A101"))
                .andExpect(jsonPath("$.data[0].montoTotal").value(200000))
                .andExpect(jsonPath("$.data[0].metodoPago").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.data[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$.data[0].referenciaComprobante").value("REF-PAGO-001"));
    }

    // =========================================================================
    // 4. CONTRATOS API Y SEGREGACIÓN JSON (Tests 16-18)
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("16: JSON expone estricto camelCase y desacoplamiento de columnas Oracle")
    public void test16_jsonUsaCamelCase() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cuotasPendientes").exists())
                .andExpect(jsonPath("$.data[0].CUOTAS_PENDIENTES").doesNotExist())
                .andExpect(jsonPath("$.data[0].deudaTotal").exists())
                .andExpect(jsonPath("$.data[0].DEUDA_TOTAL").doesNotExist())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("CUOTAS_PENDIENTES"), "No debe exponer identificadores de base de datos en mayúscula");
        assertFalse(body.contains("DEUDA_TOTAL"), "No debe exponer identificadores de base de datos en mayúscula");
    }

    @Test
    @Order(17)
    @DisplayName("17: Campos esperados de respuesta estándar presentes")
    public void test17_camposEsperadosPresentes() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(18)
    @DisplayName("18: Columnas internas de base de datos no expuestas")
    public void test18_camposInternosNoExpuestos() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reportes/pagos-recientes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("ROWNUM"));
        assertFalse(body.contains("ORA-"));
        assertFalse(body.contains("SYS_NC"));
    }

    // =========================================================================
    // 5. AISLAMIENTO DE CONTEXTO Y CONCURRENCIA (Tests 19-20)
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("19: Context Bleed: El contexto ThreadLocal queda limpio post-ejecución")
    public void test19_contextBleed_limpiaThreadLocalCorrectamente() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        assertNull(SaedContextHolder.getContext(), "El ThreadLocal de SaedContextHolder debe quedar nulo al terminar el request");
    }

    @Test
    @Order(20)
    @DisplayName("20: Aislamiento concurrente: Peticiones concurrentes entre tenants no mezclan datos")
    public void test20_aislamientoConcurrenteEntreHilos() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final boolean useProp1 = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    String token = useProp1 ? tokenAdminProp1 : tokenAdminProp2;
                    long assignId = useProp1 ? ASSIGN_ADMIN_PROP_1 : ASSIGN_ADMIN_PROP_2;
                    String expectedProp = useProp1 ? "Edificio Residencial SAED" : "Condominio Campestre Norte";

                    MvcResult r = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Assignment-Id", assignId))
                            .andExpect(status().isOk())
                            .andReturn();

                    JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
                    if (data.size() > 0) {
                        for (JsonNode row : data) {
                            if (!expectedProp.equals(row.path("propiedad").asText())) {
                                return; // Fuga detectada
                            }
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // error
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Las peticiones concurrentes debieron terminar dentro del tiempo límite");
        assertEquals(threads, successCount.get(), "Todas las peticiones concurrentes debieron aislar correctamente sus datos sin fugas");
    }
}
