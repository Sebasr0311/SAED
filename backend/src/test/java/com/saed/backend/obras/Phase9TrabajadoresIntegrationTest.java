package com.saed.backend.obras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Integral para GAP-F9-05:
 * TRABAJADORES, VIGENCIA ARL Y AUTORIZACIÓN DE EJECUCIÓN EN OBRAS (SAED 2.0).
 *
 * Cobertura de los 28 casos técnicos mandatorios:
 * - Test 01: Crear trabajador con datos válidos y ARL vigente -> 201 Created.
 * - Test 02: Crear trabajador con ARL vencida -> 422 ARL_VENCIDA.
 * - Test 03: Crear trabajador con proveedor inactivo -> 422 PROVEEDOR_INACTIVO.
 * - Test 04: Crear trabajador con proveedor inexistente -> 404 NOT_FOUND.
 * - Test 05: Crear trabajador con documento duplicado para el mismo proveedor -> 409 CONFLICT.
 * - Test 06: Crear trabajador con mismo documento para diferente proveedor -> 201 Created.
 * - Test 07: Listar trabajadores por organización activa -> 200 OK.
 * - Test 08: Filtrar trabajadores por idProveedor -> 200 OK.
 * - Test 09: Filtrar trabajadores por estado (ACTIVO / INACTIVO) -> 200 OK.
 * - Test 10: Obtener trabajador por ID existente -> 200 OK con detalles.
 * - Test 11: Obtener trabajador por ID inexistente -> 404 NOT_FOUND.
 * - Test 12: Actualizar trabajador (oficio, empresa, ARL) exitoso -> 200 OK.
 * - Test 13: Actualizar trabajador con ARL vencida -> 422 ARL_VENCIDA.
 * - Test 14: Cambiar estado de trabajador a INACTIVO -> 200 OK.
 * - Test 15: Asignar trabajador activo y con ARL vigente a obra -> 201 Created.
 * - Test 16: Asignar trabajador ya asignado a la misma obra -> 409 TRABAJADOR_OBRA_DUPLICADO.
 * - Test 17: Asignar trabajador inactivo a obra -> 422 TRABAJADOR_INACTIVO.
 * - Test 18: Asignar trabajador con ARL vencida a obra -> 422 ARL_VENCIDA.
 * - Test 19: Asignar trabajador con proveedor inactivo a obra -> 422 PROVEEDOR_INACTIVO.
 * - Test 20: Autorizar trabajador en obra -> 200 OK (AUTORIZADO = 'S').
 * - Test 21: Revocar autorización de trabajador en obra -> 200 OK (AUTORIZADO = 'N').
 * - Test 22: Iniciar obra sin trabajadores asignados (autogestión residente) -> 200 OK.
 * - Test 23: Iniciar obra con trabajador autorizado y ARL vigente -> 200 OK.
 * - Test 24: Iniciar obra con trabajadores asignados pero ninguno autorizado -> 422 TRABAJADOR_NO_AUTORIZADO.
 * - Test 25: Iniciar obra con trabajador autorizado pero con ARL vencida -> 422 ARL_VENCIDA.
 * - Test 26: Iniciar obra con trabajador autorizado pero en estado INACTIVO -> 422 TRABAJADOR_INACTIVO.
 * - Test 27: Iniciar obra con trabajador autorizado pero con proveedor INACTIVO -> 422 PROVEEDOR_INACTIVO.
 * - Test 28: Anti-IDOR Cross-Tenant: Prohibido operar o asignar trabajadores de otra organización -> 403/404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9TrabajadoresIntegrationTest {

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

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 301L;

    private static final long USER_RESIDENTE_1 = 4L;
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    // IDs de prueba de proveedores
    private static final long PROV_ACTIVO_1 = 101L;
    private static final long PROV_ACTIVO_2 = 102L;
    private static final long PROV_INACTIVO = 103L;
    private static final long PROV_ORG_2 = 9992L;

    // IDs de prueba de obras
    private static final long OBRA_1_ID = 1001L;
    private static final long OBRA_ORG2_ID = 9992L;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza de datos de prueba
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM OBRA_TRABAJADORES;
                    DELETE FROM TRABAJADORES WHERE ID_PROVEEDOR IN (101, 102, 103, 9992);
                    DELETE FROM PROVEEDORES WHERE ID_PROVEEDOR IN (101, 102, 103, 9992);
                    DELETE FROM OBRAS WHERE ID_OBRA IN (1001, 9992);
                    DELETE FROM PERSONAS WHERE NUMERO_DOCUMENTO LIKE '79888%';
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Org 9992 Foranea", "900009992-9", "org9992@test.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Torre Central SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Torre Foránea 9992");

            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_ORG2_ID, PROP_2_ID, "Apto 9992");

            ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

            ensurePersona(2L, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(2L, 2L, "admin", "admin@saed.com");

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "camartinez@saed.com");

            ensurePersona(8L, "1000000008", "AdminOrg1", "SAED", "admin_org1@saed.com");
            ensureUsuario(8L, 8L, "admin_org1", "admin_org1@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            ensureMembresia(ORG_1_ID);
            ensureMembresia(ORG_2_ID);

            // Asegurar proveedores de prueba
            ensureProveedor(PROV_ACTIVO_1, ORG_1_ID, "Constructora Alpha SAS", "901111111-1", "ACTIVO");
            ensureProveedor(PROV_ACTIVO_2, ORG_1_ID, "Constructora Gamma SAS", "901222222-2", "ACTIVO");
            ensureProveedor(PROV_INACTIVO, ORG_1_ID, "Constructora Inactiva SAS", "901333333-3", "INACTIVO");
            ensureProveedor(PROV_ORG_2, ORG_2_ID, "Constructora Foranea Org2 SAS", "901444444-4", "ACTIVO");

            // Asegurar obra de prueba en estado APROBADA
            ensureObra(OBRA_1_ID, UNIT_1_ID, "APROBADA", USER_RESIDENTE_1);
            ensureObra(OBRA_ORG2_ID, UNIT_ORG2_ID, "APROBADA", USER_ADMIN_PROP_2);

            setupMockAssignments();
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN SETUP: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        try {
            setElevatedContext();
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM OBRA_TRABAJADORES;
                    DELETE FROM TRABAJADORES WHERE ID_PROVEEDOR IN (101, 102, 103, 9992);
                    DELETE FROM PROVEEDORES WHERE ID_PROVEEDOR IN (101, 102, 103, 9992);
                    DELETE FROM OBRAS WHERE ID_OBRA IN (1001, 9992);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central SAED");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea 9992");
        UnitDTO unitOrg2 = new UnitDTO(UNIT_ORG2_ID, "Apto 9992");

        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        AssignmentResponseDTO adminOrg1Assign = new AssignmentResponseDTO();
        adminOrg1Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        adminOrg1Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg1Assign.setOrganizacion(org1);

        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

        AssignmentResponseDTO adminProp2Assign = new AssignmentResponseDTO();
        adminProp2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        adminProp2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp2Assign.setOrganizacion(org2);
        adminProp2Assign.setPropiedad(prop2);

        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(adminOrg1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
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

    // =========================================================================
    // CASOS DE PRUEBA MANDATORIOS: GAP-F9-05 (1 a 28)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 01: Crear trabajador con datos válidos y ARL vigente -> 201 Created")
    void test01_crearTrabajador_conDatosValidos_exitoso() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idProveedor", PROV_ACTIVO_1,
                "numeroDocumento", "79888001",
                "primerNombre", "Pedro",
                "primerApellido", "Perez",
                "oficioEspecialidad", "Maestro de Obra",
                "arlAseguradora", "Sura ARL",
                "arlFechaAfiliacion", LocalDate.now().minusDays(10).toString(),
                "arlFechaVencimiento", LocalDate.now().plusDays(30).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTrabajador", notNullValue()))
                .andExpect(jsonPath("$.nombreCompleto", containsString("Pedro")))
                .andExpect(jsonPath("$.arlVigente", is(true)))
                .andExpect(jsonPath("$.estado", is("ACTIVO")));
    }

    @Test
    @Order(2)
    @DisplayName("Test 02: Crear trabajador con ARL vencida -> 422 ARL_VENCIDA")
    void test02_crearTrabajador_conArlVencida_falla() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idProveedor", PROV_ACTIVO_1,
                "numeroDocumento", "79888002",
                "primerNombre", "Mario",
                "primerApellido", "Casas",
                "oficioEspecialidad", "Pintor",
                "arlAseguradora", "Positiva",
                "arlFechaVencimiento", LocalDate.now().minusDays(2).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("ARL_VENCIDA")));
    }

    @Test
    @Order(3)
    @DisplayName("Test 03: Crear trabajador con proveedor inactivo -> 422 PROVEEDOR_INACTIVO")
    void test03_crearTrabajador_proveedorInactivo_falla() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idProveedor", PROV_INACTIVO,
                "numeroDocumento", "79888003",
                "primerNombre", "Luis",
                "primerApellido", "Rivas",
                "oficioEspecialidad", "Plomero",
                "arlAseguradora", "Bolivar ARL",
                "arlFechaVencimiento", LocalDate.now().plusDays(30).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("PROVEEDOR_INACTIVO")));
    }

    @Test
    @Order(4)
    @DisplayName("Test 04: Crear trabajador con proveedor inexistente -> 404 NOT_FOUND")
    void test04_crearTrabajador_proveedorInexistente_falla() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idProveedor", 999999L,
                "numeroDocumento", "79888004",
                "primerNombre", "Hector",
                "primerApellido", "Mora",
                "oficioEspecialidad", "Electricista",
                "arlAseguradora", "Sura ARL",
                "arlFechaVencimiento", LocalDate.now().plusDays(30).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("Test 05: Crear trabajador con documento duplicado para el mismo proveedor -> 409 CONFLICT")
    void test05_crearTrabajador_documentoDuplicadoMismoProveedor_falla() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idProveedor", PROV_ACTIVO_1,
                "numeroDocumento", "79888005",
                "primerNombre", "Jorge",
                "primerApellido", "Vargas",
                "oficioEspecialidad", "Soldador",
                "arlAseguradora", "Colmena",
                "arlFechaVencimiento", LocalDate.now().plusDays(45).toString()
        );

        // Primer registro exitoso
        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // Segundo registro idéntico con mismo proveedor -> 409 Conflict
        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DOCUMENTO_DUPLICADO")));
    }

    @Test
    @Order(6)
    @DisplayName("Test 06: Crear trabajador con mismo documento para diferente proveedor -> 201 Created")
    void test06_crearTrabajador_mismoDocumentoDiferenteProveedor_exitoso() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body1 = Map.of(
                "idProveedor", PROV_ACTIVO_1,
                "numeroDocumento", "79888006",
                "primerNombre", "Andres",
                "primerApellido", "Castaño",
                "oficioEspecialidad", "Enchapador",
                "arlAseguradora", "Sura ARL",
                "arlFechaVencimiento", LocalDate.now().plusDays(40).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isCreated());

        Map<String, Object> body2 = Map.of(
                "idProveedor", PROV_ACTIVO_2,
                "numeroDocumento", "79888006",
                "primerNombre", "Andres",
                "primerApellido", "Castaño",
                "oficioEspecialidad", "Enchapador",
                "arlAseguradora", "Sura ARL",
                "arlFechaVencimiento", LocalDate.now().plusDays(40).toString()
        );

        mockMvc.perform(post("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTrabajador", notNullValue()));
    }

    @Test
    @Order(7)
    @DisplayName("Test 07: Listar trabajadores por organización activa -> 200 OK")
    void test07_listarTrabajadores_porOrganizacion_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(201L, 10L, PROV_ACTIVO_1, "Oficio 1", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        ensureTrabajador(202L, 11L, PROV_ACTIVO_1, "Oficio 2", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(get("/api/v1/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(8)
    @DisplayName("Test 08: Filtrar trabajadores por idProveedor -> 200 OK")
    void test08_listarTrabajadores_filtrarPorProveedor_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(203L, 12L, PROV_ACTIVO_1, "Pintor", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        ensureTrabajador(204L, 13L, PROV_ACTIVO_2, "Carpintero", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(get("/api/v1/trabajadores?idProveedor=" + PROV_ACTIVO_1)
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idProveedor", everyItem(is((int) PROV_ACTIVO_1))));
    }

    @Test
    @Order(9)
    @DisplayName("Test 09: Filtrar trabajadores por estado (ACTIVO / INACTIVO) -> 200 OK")
    void test09_listarTrabajadores_filtrarPorEstado_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(205L, 14L, PROV_ACTIVO_1, "Oficio Activo", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        ensureTrabajador(206L, 15L, PROV_ACTIVO_1, "Oficio Inactivo", "Sura", LocalDate.now().plusDays(20), "INACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(get("/api/v1/trabajadores?estado=INACTIVO")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].estado", everyItem(is("INACTIVO"))));
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Obtener trabajador por ID existente -> 200 OK")
    void test10_obtenerTrabajadorPorId_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(207L, 16L, PROV_ACTIVO_1, "Instalador Gas", "Sura", LocalDate.now().plusDays(30), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(get("/api/v1/trabajadores/207")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTrabajador", is(207)))
                .andExpect(jsonPath("$.oficioEspecialidad", is("Instalador Gas")));
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Obtener trabajador por ID inexistente -> 404 NOT_FOUND")
    void test11_obtenerTrabajadorPorId_inexistente_falla() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(get("/api/v1/trabajadores/999999")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("TRABAJADOR_NO_ENCONTRADO")));
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Actualizar trabajador exitoso -> 200 OK")
    void test12_actualizarTrabajador_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(208L, 17L, PROV_ACTIVO_1, "Albanil", "Sura", LocalDate.now().plusDays(15), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> updateBody = Map.of(
                "oficioEspecialidad", "Maestro Acabados",
                "arlAseguradora", "Positiva",
                "arlFechaVencimiento", LocalDate.now().plusDays(60).toString()
        );

        mockMvc.perform(put("/api/v1/trabajadores/208")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oficioEspecialidad", is("Maestro Acabados")))
                .andExpect(jsonPath("$.arlAseguradora", is("Positiva")));
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Actualizar trabajador con ARL vencida -> 422 ARL_VENCIDA")
    void test13_actualizarTrabajador_arlVencida_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(209L, 18L, PROV_ACTIVO_1, "Electricista", "Sura", LocalDate.now().plusDays(15), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> updateBody = Map.of(
                "arlFechaVencimiento", LocalDate.now().minusDays(5).toString()
        );

        mockMvc.perform(put("/api/v1/trabajadores/209")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("ARL_VENCIDA")));
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Cambiar estado de trabajador a INACTIVO -> 200 OK")
    void test14_cambiarEstadoTrabajador_aInactivo_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(210L, 19L, PROV_ACTIVO_1, "Pintor", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("estado", "INACTIVO");

        mockMvc.perform(patch("/api/v1/trabajadores/210/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Verificar cambio a INACTIVO
        mockMvc.perform(get("/api/v1/trabajadores/210")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("INACTIVO")));
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Asignar trabajador activo y con ARL vigente a obra -> 201 Created")
    void test15_asignarTrabajadorAObra_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(211L, 20L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/211")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isCreated());

        // Verificar asignación
        mockMvc.perform(get("/api/v1/obras/" + OBRA_1_ID + "/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idTrabajador", is(211)))
                .andExpect(jsonPath("$[0].autorizado", is("S")));
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: Asignar trabajador ya asignado a la misma obra -> 409 TRABAJADOR_OBRA_DUPLICADO")
    void test16_asignarTrabajadorAObra_duplicado_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(212L, 21L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Primera asignación exitosa
        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/212")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isCreated());

        // Segunda asignación idéntica -> 409 Conflict
        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/212")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("TRABAJADOR_OBRA_DUPLICADO")));
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: Asignar trabajador inactivo a obra -> 422 TRABAJADOR_INACTIVO")
    void test17_asignarTrabajadorAObra_trabajadorInactivo_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(213L, 22L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().plusDays(20), "INACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/213")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("TRABAJADOR_INACTIVO")));
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Asignar trabajador con ARL vencida a obra -> 422 ARL_VENCIDA")
    void test18_asignarTrabajadorAObra_arlVencida_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(214L, 23L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().minusDays(5), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/214")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("ARL_VENCIDA")));
    }

    @Test
    @Order(19)
    @DisplayName("Test 19: Asignar trabajador con proveedor inactivo a obra -> 422 PROVEEDOR_INACTIVO")
    void test19_asignarTrabajadorAObra_proveedorInactivo_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(215L, 24L, PROV_INACTIVO, "Oficial", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/215")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("PROVEEDOR_INACTIVO")));
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: Autorizar trabajador en obra -> 200 OK")
    void test20_autorizarTrabajadorEnObra_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(216L, 25L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        // Pre-asignar con AUTORIZADO = 'N'
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')", OBRA_1_ID, 216L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/216/autorizar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar que quedó autorizado ('S')
        mockMvc.perform(get("/api/v1/obras/" + OBRA_1_ID + "/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].autorizado", is("S")));
    }

    @Test
    @Order(21)
    @DisplayName("Test 21: Revocar autorización de trabajador en obra -> 200 OK")
    void test21_revocarTrabajadorEnObra_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(217L, 26L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().plusDays(20), "ACTIVO");
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')", OBRA_1_ID, 217L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/217/revocar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar que quedó revocado ('N')
        mockMvc.perform(get("/api/v1/obras/" + OBRA_1_ID + "/trabajadores")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].autorizado", is("N")));
    }

    @Test
    @Order(22)
    @DisplayName("Test 22: Iniciar obra sin trabajadores asignados (autogestión residente) -> 200 OK")
    void test22_iniciarObra_sinTrabajadores_exitoso() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // OBRA_1_ID está APROBADA y tiene 0 trabajadores asignados
        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar estado EN_EJECUCION
        mockMvc.perform(get("/api/v1/obras/" + OBRA_1_ID)
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_EJECUCION")));
    }

    @Test
    @Order(23)
    @DisplayName("Test 23: Iniciar obra con trabajador autorizado y ARL vigente -> 200 OK")
    void test23_iniciarObra_conTrabajadorAutorizadoYArlVigente_exitoso() throws Exception {
        setElevatedContext();
        ensureTrabajador(218L, 27L, PROV_ACTIVO_1, "Maestro", "Sura", LocalDate.now().plusDays(25), "ACTIVO");
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')", OBRA_1_ID, 218L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/obras/" + OBRA_1_ID)
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_EJECUCION")));
    }

    @Test
    @Order(24)
    @DisplayName("Test 24: Iniciar obra con trabajadores asignados pero ninguno autorizado -> 422 TRABAJADOR_NO_AUTORIZADO")
    void test24_iniciarObra_conTrabajadoresPeroNingunoAutorizado_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(219L, 28L, PROV_ACTIVO_1, "Maestro", "Sura", LocalDate.now().plusDays(25), "ACTIVO");
        // Trabajador asignado pero con AUTORIZADO = 'N'
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')", OBRA_1_ID, 219L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("TRABAJADOR_NO_AUTORIZADO")));
    }

    @Test
    @Order(25)
    @DisplayName("Test 25: Iniciar obra con trabajador autorizado pero con ARL vencida -> 422 ARL_VENCIDA")
    void test25_iniciarObra_conTrabajadorAutorizadoPeroArlVencida_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(220L, 29L, PROV_ACTIVO_1, "Maestro", "Sura", LocalDate.now().minusDays(1), "ACTIVO");
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')", OBRA_1_ID, 220L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("ARL_VENCIDA")));
    }

    @Test
    @Order(26)
    @DisplayName("Test 26: Iniciar obra con trabajador autorizado pero en estado INACTIVO -> 422 TRABAJADOR_INACTIVO")
    void test26_iniciarObra_conTrabajadorAutorizadoPeroInactivo_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(221L, 30L, PROV_ACTIVO_1, "Maestro", "Sura", LocalDate.now().plusDays(25), "INACTIVO");
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')", OBRA_1_ID, 221L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("TRABAJADOR_INACTIVO")));
    }

    @Test
    @Order(27)
    @DisplayName("Test 27: Iniciar obra con trabajador autorizado pero con proveedor INACTIVO -> 422 PROVEEDOR_INACTIVO")
    void test27_iniciarObra_conTrabajadorAutorizadoPeroProveedorInactivo_falla() throws Exception {
        setElevatedContext();
        ensureTrabajador(222L, 31L, PROV_INACTIVO, "Maestro", "Sura", LocalDate.now().plusDays(25), "ACTIVO");
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')", OBRA_1_ID, 222L);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("PROVEEDOR_INACTIVO")));
    }

    @Test
    @Order(28)
    @DisplayName("Test 28: Anti-IDOR Cross-Tenant: Prohibido operar o asignar trabajadores de otra organización -> 403/404")
    void test28_seguridad_crossTenant_noPuedeAsignarTrabajadorDeOtraOrganizacion() throws Exception {
        setElevatedContext();
        // Trabajador perteneciente a proveedor de Organización 2 (Foránea)
        ensureTrabajador(9992L, 9992L, PROV_ORG_2, "Especialista Foraneo", "Colpatria", LocalDate.now().plusDays(30), "ACTIVO");
        clearContext();

        String tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Admin de Org 1 intenta consultar trabajador de Org 2 -> 404 (aislado por RLS/tenant)
        mockMvc.perform(get("/api/v1/trabajadores/9992")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());

        // Admin de Org 1 intenta asignar trabajador de Org 2 a su Obra 1 -> 404/403
        mockMvc.perform(post("/api/v1/obras/" + OBRA_1_ID + "/trabajadores/9992")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE ASEGURAMIENTO DE DATOS
    // =========================================================================

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long id, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, ?, 'Calle 123', 'Bogotá', 'RESIDENCIAL', 'ACTIVA')", id, idOrg, nombre);
        }
    }

    private void ensureUnidad(Long id, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                    "VALUES (?, ?, ?, 'RESIDENCIAL', 0.05, 'DISPONIBLE')", id, idProp, identificador);
        }
    }

    private void ensurePersona(Long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, ?, ?, ?)", id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long id, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", id, idPersona, username, email);
        }
    }

    private void ensureMembresia(Long idOrg) {
        Long defaultPlanId = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM PLANES WHERE (LIMITE_USUARIOS IS NULL OR LIMITE_USUARIOS = 0 OR LIMITE_USUARIOS >= 50) AND ROWNUM = 1",
                Long.class
        );
        if (defaultPlanId != null) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", Integer.class, idOrg);
            if (count == null || count == 0) {
                jdbcTemplate.update("INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                        "VALUES (?, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')", idOrg, defaultPlanId);
            }
        }
    }

    private void ensureProveedor(Long idProveedor, Long idOrganizacion, String razonSocial, String nit, String estado) {
        jdbcTemplate.update("""
            MERGE INTO PROVEEDORES target
            USING (SELECT ? AS id, ? AS id_org, ? AS razon, ? AS nit, ? AS estado FROM DUAL) src
            ON (target.ID_PROVEEDOR = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_ORGANIZACION = src.id_org,
                           target.RAZON_SOCIAL = src.razon,
                           target.NIT_IDENTIFICACION = src.nit,
                           target.ESTADO = src.estado
            WHEN NOT MATCHED THEN
                INSERT (ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION, EMAIL_CONTACTO, TELEFONO_CONTACTO, CATEGORIA_SERVICIO, ESTADO, CALIFICACION_PROM)
                VALUES (src.id, src.id_org, 'JURIDICA', src.razon, src.nit, 'prov' || src.id || '@test.com', '3001234567', 'Construccion y Mantenimiento', src.estado, 5.0)
        """, idProveedor, idOrganizacion, razonSocial, nit, estado);
    }

    private void ensureObra(Long idObra, Long idUnidad, String estado, Long solicitadoPor) {
        jdbcTemplate.update("""
            MERGE INTO OBRAS target
            USING (SELECT ? AS id, ? AS id_u, ? AS estado, ? AS sol FROM DUAL) src
            ON (target.ID_OBRA = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_UNIDAD = src.id_u,
                           target.ESTADO = src.estado,
                           target.SOLICITADO_POR = src.sol
            WHEN NOT MATCHED THEN
                INSERT (ID_OBRA, ID_UNIDAD, DESCRIPCION, FECHA_INICIO, FECHA_FIN_ESTIMADA,
                        RESPONSABLE_OBRA, TELEFONO_RESPONSABLE, DEPOSITO_GARANTIA, ESTADO, SOLICITADO_POR)
                VALUES (src.id, src.id_u, 'Obra de prueba', TRUNC(SYSDATE), TRUNC(SYSDATE + 30),
                        'Ing. Perez', '3001234567', 0, src.estado, src.sol)
        """, idObra, idUnidad, estado, solicitadoPor);
    }

    private void ensureTrabajador(Long idTrabajador, Long idPersona, Long idProveedor, String oficio, String arl, LocalDate vencimiento, String estado) {
        ensurePersona(idPersona, "999" + idPersona, "Trabajador", "Num" + idPersona, "trab" + idPersona + "@test.com");

        jdbcTemplate.update("""
            MERGE INTO TRABAJADORES target
            USING (SELECT ? AS id, ? AS id_per, ? AS id_prov, ? AS oficio, ? AS arl, ? AS venc, ? AS estado FROM DUAL) src
            ON (target.ID_TRABAJADOR = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_PERSONA = src.id_per,
                           target.ID_PROVEEDOR = src.id_prov,
                           target.OFICIO_ESPECIALIDAD = src.oficio,
                           target.ARL_ASEGURADORA = src.arl,
                           target.ARL_FECHA_VENCIMIENTO = src.venc,
                           target.ESTADO = src.estado
            WHEN NOT MATCHED THEN
                INSERT (ID_TRABAJADOR, ID_PERSONA, ID_PROVEEDOR, OFICIO_ESPECIALIDAD, ARL_ASEGURADORA, ARL_FECHA_VENCIMIENTO, ESTADO)
                VALUES (src.id, src.id_per, src.id_prov, src.oficio, src.arl, src.venc, src.estado)
        """, idTrabajador, idPersona, idProveedor, oficio, arl, java.sql.Date.valueOf(vencimiento), estado);
    }
}
