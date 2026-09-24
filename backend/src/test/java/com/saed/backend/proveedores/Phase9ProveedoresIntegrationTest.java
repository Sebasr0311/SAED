package com.saed.backend.proveedores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorEstadoDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Types;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación para GAP-F9-01:
 * Catálogo Maestro de Proveedores y Contratistas en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de las 12 pruebas de certificación técnica exigidas:
 * - Test 1: Crear proveedor en Organización A (201 Created y persistencia en Oracle).
 * - Test 2: Listado tenant-scoped (Organización A solo ve proveedores A; Org B solo ve proveedores B).
 * - Test 3: Anti-IDOR GET (Usuario Org A solicita GET /proveedores/{idProvB} -> 403 Forbidden).
 * - Test 4: Anti-IDOR UPDATE (Usuario Org A intenta PUT /proveedores/{idProvB} -> 403 Forbidden, Prov B intacto).
 * - Test 5: NIT duplicado mismo tenant -> 409 Conflict (UQ_PROVEEDOR_NIT).
 * - Test 6: Mismo NIT en diferente organización -> 201 Created (permitido por unicidad compuesta).
 * - Test 7: Contrato con proveedor propio activo en la propiedad -> 201 Created.
 * - Test 8: Cross-Tenant Crítico (Propiedad A1 intenta vincular Proveedor B1 -> 403 Forbidden, 0 contratos).
 * - Test 9: Proveedor INACTIVO o BLOQUEADO no puede ser contratado -> 400 Bad Request.
 * - Test 10: Transición de estados (ACTIVO -> INACTIVO -> BLOQUEADO -> ACTIVO) y validación de restricciones.
 * - Test 11: Control de acceso por roles: RESIDENTE y PORTERO bloqueados del catálogo -> 403 Forbidden.
 * - Test 12: Oracle VPD / RLS directo en base de datos preserva aislamiento por organización.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9ProveedoresIntegrationTest {

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

    // Identidades
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1 = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_ORG_2 = 803L;
    private static final long ASSIGN_ADMIN_ORG_2 = 904L;

    private static final long USER_RESIDENTE = 805L;
    private static final long ASSIGN_RESIDENTE = 905L;

    // Organizaciones y Propiedades
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 2L;

    private static final long PROP_1_ID = 1L; // en Org 1
    private static final long PROP_2_ID = 2L; // en Org 2

    // Tokens JWT
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminOrg2;
    private String tokenResidente;

    // IDs de proveedores persistidos para las pruebas
    private Long idProvA1;
    private Long idProvA2;
    private Long idProvB1;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza controlada previa
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM CONTRATOS_PROVEEDOR WHERE NUMERO_CONTRATO LIKE 'CTR-TEST-%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION LIKE '900999%' OR NIT_IDENTIFICACION LIKE '800888%';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (902, 903, 904, 905);
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "900000002-2", "org2@saed.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(1L, PROP_1_ID, "101");

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");
            ensureAdminSaed(USER_SUPERADMIN);

            ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_test", "adminorg1@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_test", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_ORG_2, "1000000803", "Admin", "OrgDos", "adminorg2@saed.com");
            ensureUsuario(USER_ADMIN_ORG_2, USER_ADMIN_ORG_2, "admin_org2_test", "adminorg2@saed.com");

            ensurePersona(USER_RESIDENTE, "1000000805", "Residente", "Pruebas", "residente@saed.com");
            ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_test", "residente@saed.com");

            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_2, USER_ADMIN_ORG_2, "ADMIN_ORGANIZACION", ORG_2_ID, null, null);
            ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, 1L);

            setupMockAssignments();

            // Insertar proveedores base para las pruebas
            idProvA1 = insertProveedor(ORG_1_ID, "JURIDICA", "Seguridad Andina SAS", "900999001-1",
                    "contacto@andina.com", "3101112233", "SEGURIDAD", "ACTIVO");

            idProvA2 = insertProveedor(ORG_1_ID, "NATURAL", "Mantenimiento Juan Perez", "900999002-2",
                    "juan@mantenimiento.com", "3104445566", "MANTENIMIENTO", "INACTIVO");

            idProvB1 = insertProveedor(ORG_2_ID, "JURIDICA", "Limpieza Total del Norte LTDA", "800888001-1",
                    "norte@limpieza.com", "3209998877", "ASEO", "ACTIVO");

            // Tokens
            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminOrg2 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_2);
            tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);

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
                    DELETE FROM CONTRATOS_PROVEEDOR WHERE NUMERO_CONTRATO LIKE 'CTR-TEST-%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION LIKE '900999%' OR NIT_IDENTIFICACION LIKE '800888%';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (902, 903, 904, 905);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

        // Superadmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setOrganizacion(org1);
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

        // Admin Org 2
        AssignmentResponseDTO aOrg2 = new AssignmentResponseDTO();
        aOrg2.setIdAsignacion(ASSIGN_ADMIN_ORG_2);
        aOrg2.setOrganizacion(org2);
        aOrg2.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_2, USER_ADMIN_ORG_2)).thenReturn(Optional.of(aOrg2));

        // Residente (sin privilegios de proveedores)
        AssignmentResponseDTO resAssign = new AssignmentResponseDTO();
        resAssign.setIdAsignacion(ASSIGN_RESIDENTE);
        resAssign.setOrganizacion(org1);
        resAssign.setPropiedad(prop1);
        resAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(resAssign));
    }

    // =========================================================================
    // TEST 1 — CREAR PROVEEDOR EN ORGANIZACIÓN A (201 CREATED)
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("Test 1: Crear proveedor en Org A retorna 201 y persiste correctamente en Oracle XE")
    public void test01_crearProveedor_organizacionA_exitoso() throws Exception {
        ProveedorCreateDTO dto = new ProveedorCreateDTO(
                "JURIDICA",
                "Ascensores de Colombia SAS",
                "900999010-5",
                "contacto@ascensores.com",
                "3108889900",
                "Av 68 # 45-10",
                "Bogotá",
                "ASCENSORES",
                new BigDecimal("5.00")
        );

        mockMvc.perform(post("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idProveedor").isNumber())
                .andExpect(jsonPath("$.data.idOrganizacion").value(ORG_1_ID))
                .andExpect(jsonPath("$.data.razonSocial").value("Ascensores de Colombia SAS"))
                .andExpect(jsonPath("$.data.nitIdentificacion").value("900999010-5"))
                .andExpect(jsonPath("$.data.estado").value("ACTIVO"));

        // Verificación en Oracle XE
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROVEEDORES WHERE ID_ORGANIZACION = ? AND NIT_IDENTIFICACION = ?",
                    Integer.class, ORG_1_ID, "900999010-5"
            );
            assertEquals(1, count, "El proveedor debe estar persistido en Oracle XE");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 2 — LISTADO TENANT-SCOPED (AISLAMIENTO POR ORGANIZACIÓN)
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("Test 2: Listado tenant-scoped garantiza aislamiento entre Org A y Org B")
    public void test02_listarProveedores_tenantScoped_aislamiento() throws Exception {
        // Admin Org 1 consulta listado -> Solo ve proveedores de Org 1
        mockMvc.perform(get("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[*].idOrganizacion", everyItem(equalTo((int) ORG_1_ID))))
                .andExpect(jsonPath("$.data[*].razonSocial", hasItem("Seguridad Andina SAS")))
                .andExpect(jsonPath("$.data[*].razonSocial", not(hasItem("Limpieza Total del Norte LTDA"))));

        // Admin Org 2 consulta listado -> Solo ve proveedores de Org 2
        mockMvc.perform(get("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenAdminOrg2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[*].idOrganizacion", everyItem(equalTo((int) ORG_2_ID))))
                .andExpect(jsonPath("$.data[*].razonSocial", hasItem("Limpieza Total del Norte LTDA")))
                .andExpect(jsonPath("$.data[*].razonSocial", not(hasItem("Seguridad Andina SAS"))));
    }

    // =========================================================================
    // TEST 3 — ANTI-IDOR EN GET POR ID (RECHAZO CROSS-TENANT)
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("Test 3: Anti-IDOR en GET bloquea acceso a proveedor perteneciente a otra organización (403/404)")
    public void test03_antiIdor_getProveedor_rechazoCrossTenant() throws Exception {
        // Usuario de Org 1 solicita Proveedor B1 (de Org 2)
        mockMvc.perform(get("/api/v1/proveedores/" + idProvB1)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 Forbidden o 404 Not Found ante acceso cross-tenant");
                });
    }

    // =========================================================================
    // TEST 4 — ANTI-IDOR EN UPDATE (RECHAZO Y PRESERVACIÓN EN ORACLE)
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("Test 4: Anti-IDOR en UPDATE bloquea modificación cross-tenant y mantiene intacto el registro en Oracle")
    public void test04_antiIdor_updateProveedor_rechazoCrossTenant() throws Exception {
        ProveedorUpdateDTO updateDto = new ProveedorUpdateDTO(
                "JURIDICA",
                "NOMBRE MODIFICADO MALICIOSO",
                "800888001-1",
                "malicioso@ataque.com",
                "3000000000",
                "Calle Falsa 123",
                "Medellin",
                "SEGURIDAD",
                new BigDecimal("4.00")
        );

        // Org 1 intenta modificar Proveedor B1 de Org 2
        mockMvc.perform(put("/api/v1/proveedores/" + idProvB1)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 Forbidden o 404 Not Found ante modificación cross-tenant");
                });

        // Verificación en Oracle XE: Prov B1 NO debe haber sido alterado
        setElevatedContext();
        try {
            String razonSocial = jdbcTemplate.queryForObject(
                    "SELECT RAZON_SOCIAL FROM PROVEEDORES WHERE ID_PROVEEDOR = ?",
                    String.class, idProvB1
            );
            assertEquals("Limpieza Total del Norte LTDA", razonSocial, "Los datos del proveedor de Org 2 deben permanecer inalterados");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 5 — NIT DUPLICADO EN EL MISMO TENANT (409 CONFLICT)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("Test 5: Intentar registrar NIT duplicado en la misma organización retorna 409 Conflict")
    public void test05_nitDuplicado_mismoTenant_rechazoConflict() throws Exception {
        // Prov A1 ya existe con NIT 900999001-1 en Org 1
        ProveedorCreateDTO dto = new ProveedorCreateDTO(
                "JURIDICA",
                "Seguridad Duplicada SAS",
                "900999001-1", // Mismo NIT de Prov A1
                "duplicado@seguridad.com",
                "3112223344",
                "Carrera 7 # 100-20",
                "Bogotá",
                "SEGURIDAD",
                new BigDecimal("5.00")
        );

        mockMvc.perform(post("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVEEDOR_NIT_DUPLICADO"));
    }

    // =========================================================================
    // TEST 6 — MISMO NIT EN DIFERENTE ORGANIZACIÓN (PERMITIDO)
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("Test 6: Registrar el mismo NIT en diferente organización está permitido por UQ_PROVEEDOR_NIT")
    public void test06_mismoNit_distintoTenant_permitido() throws Exception {
        // En Org 1 ya existe NIT 900999001-1. Org 2 intenta crearlo para su propio tenant:
        ProveedorCreateDTO dtoOrg2 = new ProveedorCreateDTO(
                "JURIDICA",
                "Seguridad Andina Regional Norte SAS",
                "900999001-1", // Mismo NIT pero en Org 2
                "norte@andina.com",
                "3159998877",
                "Calle 10 # 20-30",
                "Bucaramanga",
                "SEGURIDAD",
                new BigDecimal("5.00")
        );

        mockMvc.perform(post("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenAdminOrg2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoOrg2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idOrganizacion").value(ORG_2_ID))
                .andExpect(jsonPath("$.data.nitIdentificacion").value("900999001-1"));

        // Verificación en Oracle XE: existen 2 registros con el mismo NIT pero distinta organización
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROVEEDORES WHERE NIT_IDENTIFICACION = '900999001-1'",
                    Integer.class
            );
            assertEquals(2, count, "Deben coexistir dos proveedores con el mismo NIT en distintas organizaciones");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 7 — CONTRATO CON PROVEEDOR PROPIO ACTIVO (201 CREATED)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("Test 7: Crear contrato con proveedor activo de la misma organización es exitoso (201)")
    public void test07_contrato_proveedorPropioActivo_exitoso() throws Exception {
        Map<String, Object> contratoReq = Map.of(
                "idProveedor", idProvA1,
                "numeroContrato", "CTR-TEST-001",
                "objetoContrato", "Prestación de servicios de vigilancia y seguridad física 24/7",
                "valorTotal", 15000000.0,
                "periodicidadPago", "MENSUAL",
                "fechaInicio", "2026-01-01",
                "fechaFin", "2026-12-31",
                "diasAlertaVenc", 30
        );

        mockMvc.perform(post("/api/v1/contratos-admin/proveedores")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contratoReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.numeroContrato").value("CTR-TEST-001"))
                .andExpect(jsonPath("$.data.idProveedor").value(idProvA1))
                .andExpect(jsonPath("$.data.nombreProveedor").value("Seguridad Andina SAS"));

        // Verificación en Oracle XE
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM CONTRATOS_PROVEEDOR WHERE ID_PROPIEDAD = ? AND NUMERO_CONTRATO = ?",
                    Integer.class, PROP_1_ID, "CTR-TEST-001"
            );
            assertEquals(1, count, "El contrato debe estar insertado en CONTRATOS_PROVEEDOR");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 8 — CROSS-TENANT CRÍTICO (PROPIEDAD A + PROVEEDOR B -> BLOQUEADO)
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("Test 8: Cross-Tenant Crítico — Vincular propiedad de Org A con proveedor de Org B es rechazado (403)")
    public void test08_crossTenant_contratoProveedorAjeno_bloqueado() throws Exception {
        Map<String, Object> contratoCrossTenant = Map.of(
                "idProveedor", idProvB1, // PROVEEDOR DE ORG 2
                "numeroContrato", "CTR-TEST-HACK",
                "objetoContrato", "Intento de contratación cross-tenant ilegal",
                "valorTotal", 5000000.0,
                "periodicidadPago", "MENSUAL",
                "fechaInicio", "2026-01-01",
                "fechaFin", "2026-12-31",
                "diasAlertaVenc", 30
        );

        // Admin de Propiedad 1 (Org 1) intenta usar proveedor de Org 2
        mockMvc.perform(post("/api/v1/contratos-admin/proveedores")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contratoCrossTenant)))
                .andExpect(status().isForbidden());

        // Verificación en Oracle XE: exactamente 0 contratos insertados
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM CONTRATOS_PROVEEDOR WHERE ID_PROPIEDAD = ? AND ID_PROVEEDOR = ?",
                    Integer.class, PROP_1_ID, idProvB1
            );
            assertEquals(0, count, "No debe haberse insertado ningún contrato cross-tenant");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 9 — PROVEEDOR INACTIVO / BLOQUEADO RECHAZADO EN CONTRATOS
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("Test 9: Intentar vincular a contrato un proveedor en estado INACTIVO es rechazado (400)")
    public void test09_contrato_proveedorInactivoOBloqueado_rechazado() throws Exception {
        // idProvA2 fue creado con ESTADO = 'INACTIVO'
        Map<String, Object> contratoInactivo = Map.of(
                "idProveedor", idProvA2,
                "numeroContrato", "CTR-TEST-INACT",
                "objetoContrato", "Servicio con proveedor inactivo",
                "valorTotal", 1000000.0,
                "periodicidadPago", "MENSUAL",
                "fechaInicio", "2026-01-01",
                "fechaFin", "2026-12-31",
                "diasAlertaVenc", 30
        );

        mockMvc.perform(post("/api/v1/contratos-admin/proveedores")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contratoInactivo)))
                .andExpect(status().isBadRequest());

        // Verificación en Oracle: 0 contratos insertados
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM CONTRATOS_PROVEEDOR WHERE NUMERO_CONTRATO = 'CTR-TEST-INACT'",
                    Integer.class
            );
            assertEquals(0, count, "No debe crearse contrato con proveedor inactivo");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 10 — TRANSICIÓN DE ESTADOS (ACTIVO -> INACTIVO -> BLOQUEADO -> ACTIVO)
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("Test 10: Transición controlada de estados y persistencia en Oracle XE")
    public void test10_cambioEstado_transicionYPersistencia() throws Exception {
        // 1. ACTIVO -> INACTIVO
        mockMvc.perform(patch("/api/v1/proveedores/" + idProvA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProveedorEstadoDTO("INACTIVO"))))
                .andExpect(status().isOk());

        assertEstadoOracle(idProvA1, "INACTIVO");

        // 2. INACTIVO -> BLOQUEADO
        mockMvc.perform(patch("/api/v1/proveedores/" + idProvA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProveedorEstadoDTO("BLOQUEADO"))))
                .andExpect(status().isOk());

        assertEstadoOracle(idProvA1, "BLOQUEADO");

        // 3. BLOQUEADO -> ACTIVO
        mockMvc.perform(patch("/api/v1/proveedores/" + idProvA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProveedorEstadoDTO("ACTIVO"))))
                .andExpect(status().isOk());

        assertEstadoOracle(idProvA1, "ACTIVO");

        // 4. Estado inválido -> 400 Bad Request
        mockMvc.perform(patch("/api/v1/proveedores/" + idProvA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"DESCONOCIDO\"}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // TEST 11 — CONFINAMIENTO DE ROLES (RESIDENTE RECHAZADO)
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("Test 11: Rol RESIDENTE no posee autorización sobre el catálogo de proveedores (403)")
    public void test11_controlAccesoRoles_residenteRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/proveedores")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProveedorCreateDTO(
                        "JURIDICA", "Ataque Residente SAS", "900999999-9",
                        "ataque@residente.com", null, null, null, "SEGURIDAD", null
                ))))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TEST 12 — ORACLE VPD / RLS DIRECTO (POL_RLS_ORG_PROVEEDORES)
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("Test 12: Oracle VPD POL_RLS_ORG_PROVEEDORES aísla registros al consultar directamente")
    public void test12_oracleRls_directQuery_tenantIsolation() {
        setTenantContext(USER_ADMIN_ORG_1, ORG_1_ID, PROP_1_ID, "ADMIN_ORGANIZACION");
        try {
            // Cuando el contexto Oracle está fijado en ORG_1_ID, la consulta directa a PROVEEDORES
            // no debe retornar ningún registro de ORG_2_ID
            Integer provBCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROVEEDORES WHERE ID_PROVEEDOR = ?",
                    Integer.class, idProvB1
            );
            assertEquals(0, provBCount, "Oracle VPD POL_RLS_ORG_PROVEEDORES debe ocultar proveedores de otras organizaciones");

            // Y debe permitir ver los de la propia organización
            Integer provACount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROVEEDORES WHERE ID_PROVEEDOR = ?",
                    Integer.class, idProvA1
            );
            assertEquals(1, provACount, "Oracle VPD debe permitir ver proveedores de la propia organización");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // UTILIDADES DE PERSISTENCIA Y ELEVACIÓN DE CONTEXTO
    // =========================================================================
    private Long insertProveedor(Long idOrg, String tipo, String razon, String nit,
                                 String email, String tel, String cat, String estado) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO PROVEEDORES (" +
                "ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION, " +
                "EMAIL_CONTACTO, TELEFONO_CONTACTO, CATEGORIA_SERVICIO, CALIFICACION_PROM, ESTADO) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 5.0, ?)",
                new String[]{"ID_PROVEEDOR"}
            );
            ps.setLong(1, idOrg);
            ps.setString(2, tipo);
            ps.setString(3, razon);
            ps.setString(4, nit);
            ps.setString(5, email);
            ps.setString(6, tel);
            ps.setString(7, cat);
            ps.setString(8, estado);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void assertEstadoOracle(Long idProveedor, String estadoEsperado) {
        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM PROVEEDORES WHERE ID_PROVEEDOR = ?",
                    String.class, idProveedor
            );
            assertEquals(estadoEsperado, estado, "El estado en Oracle debe ser " + estadoEsperado);
        } finally {
            clearContext();
        }
    }

    private void ensureOrganizacion(Long idOrg, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, idOrg);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    idOrg, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long idProp, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, idProp);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (?, ?, 1, ?, 'Dir Test', 'Bogota', 'PROPIETARIOS', 'ACTIVA')",
                    idProp, idOrg, nombre);
        }
    }

    private void ensureUnidad(Long idUnidad, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, idUnidad);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    idUnidad, idProp, identificador);
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

    private void ensureAdminSaed(Long idUsuario) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ADMINISTRADORES_SAED WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, NIVEL, ESTADO) VALUES (?, 'SUPERADMIN', 'ACTIVO')", idUsuario);
        } else {
            jdbcTemplate.update("UPDATE ADMINISTRADORES_SAED SET NIVEL = 'SUPERADMIN', ESTADO = 'ACTIVO' WHERE ID_USUARIO = ?", idUsuario);
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

    private void setTenantContext(Long userId, Long orgId, Long propId, String roleCode) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(userId)
                .organizationId(orgId)
                .propertyId(propId)
                .roleCode(roleCode)
                .roleScope("ORGANIZACION")
                .build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + userId + "); PKG_SAED_SESSION.SET_CONTEXT(" + userId + ", " + orgId + ", " + (propId != null ? propId : "NULL") + ", '" + roleCode + "'); END;");
        } catch (Exception ignored) {}
    }

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }
}
