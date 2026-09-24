package com.saed.backend.activos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoEstadoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación para GAP-F9-02:
 * Módulo Completo de Activos e Inventario Físico de la Copropiedad en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de las 17 pruebas de certificación técnica exigidas:
 * - Test 01: Crear activo válido en Propiedad A (201 Created y persistencia en Oracle).
 * - Test 02: Listado tenant-scoped con aislamiento estricto (Prop A vs Prop B).
 * - Test 03: GET por ID de activo propio (200 OK con payload íntegro).
 * - Test 04: Anti-IDOR en GET bloquea acceso a activo de otra propiedad (403 Forbidden).
 * - Test 05: Anti-IDOR en UPDATE bloquea modificación cross-tenant y mantiene datos intactos en Oracle.
 * - Test 06: Anti-IDOR en cambio de estado bloquea alteración cross-tenant y preserva estado original en Oracle.
 * - Test 07: Código de activo duplicado en la misma propiedad es rechazado con 409 Conflict (UIX_ACTIVO_CODIGO).
 * - Test 08: Mismo código de activo en diferente propiedad es permitido (201 Created por unicidad compuesta).
 * - Test 09: Código de activo duplicado case-insensitive es rechazado con 409 Conflict.
 * - Test 10: Valor de adquisición negativo es rechazado con 400 Bad Request (CK_ACTIVOS_VALOR).
 * - Test 11: Estado inicial no reconocido ("BORRADOR") es rechazado con 400 Bad Request.
 * - Test 12: Transición de estado válida (OPERATIVO <-> MANTENIMIENTO) exitosa en API y Oracle XE.
 * - Test 13: Transición de estado inválida desde DADO_DE_BAJA es rechazada con 400 Bad Request (terminal).
 * - Test 14: Baja lógica mediante endpoint DELETE (marca DADO_DE_BAJA, sin borrado físico en Oracle).
 * - Test 15: Petición sin propiedad activa en contexto es rechazada (403 Forbidden).
 * - Test 16: Oracle VPD / RLS directo en base de datos preserva aislamiento por propiedad (POL_RLS_PROP_ACTIVOS).
 * - Test 17: Control de acceso por roles: RESIDENTE bloqueado de operaciones sobre activos (403 Forbidden).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9ActivosIntegrationTest {

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

    private static final long USER_ADMIN_PROP_2 = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE = 805L;
    private static final long ASSIGN_RESIDENTE = 905L;

    // Propiedades y Organizaciones
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L; // en Org 1
    private static final long PROP_2_ID = 8802L; // en Org 2

    // Tokens JWT
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente;

    // IDs de activos persistidos para las pruebas
    private Long idActivoA1;
    private Long idActivoA2;
    private Long idActivoA_Baja;
    private Long idActivoA_ParaBaja;
    private Long idActivoB1;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza controlada de datos de prueba
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM ACTIVOS WHERE CODIGO_ACTIVO LIKE 'ACT-TEST-%';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (902, 903, 904, 905);
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

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

            ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_test", "adminprop2@saed.com");

            ensurePersona(USER_RESIDENTE, "1000000805", "Residente", "Pruebas", "residente@saed.com");
            ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_test", "residente@saed.com");

            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, 1L);

            setupMockAssignments();

            // Insertar activos base para pruebas
            idActivoA1 = insertActivo(PROP_1_ID, "ACT-TEST-A1", "Ascensor Principal Torre 1",
                    "ASCENSORES", LocalDate.of(2023, 5, 10), new BigDecimal("120000000.00"), "OPERATIVO");

            idActivoA2 = insertActivo(PROP_1_ID, "ACT-TEST-A2", "Bomba Hidráulica Eyector",
                    "BOMBAS", LocalDate.of(2022, 8, 1), new BigDecimal("15000000.00"), "MANTENIMIENTO");

            idActivoA_Baja = insertActivo(PROP_1_ID, "ACT-TEST-A-BAJA", "Planta Eléctrica Antigua Diesel",
                    "PLANTAS", LocalDate.of(2010, 1, 1), new BigDecimal("45000000.00"), "DADO_DE_BAJA");

            idActivoA_ParaBaja = insertActivo(PROP_1_ID, "ACT-TEST-A-PARABAJA", "Motobomba Auxiliar Zona Húmeda",
                    "BOMBAS", LocalDate.of(2021, 3, 15), new BigDecimal("8000000.00"), "OPERATIVO");

            idActivoB1 = insertActivo(PROP_2_ID, "ACT-TEST-B1", "Circuito Cerrado CCTV Torre Norte",
                    "SEGURIDAD", LocalDate.of(2023, 11, 20), new BigDecimal("28000000.00"), "OPERATIVO");

            // Tokens
            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
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
                    DELETE FROM ACTIVOS WHERE CODIGO_ACTIVO LIKE 'ACT-TEST-%';
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

        // SuperAdmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setOrganizacion(org1);
        saAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(saAssign));

        // Admin Org 1 (Sin propiedad en contexto)
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
    }

    // =========================================================================
    // TEST 1 — CREAR ACTIVO VÁLIDO EN PROPIEDAD A (201 CREATED)
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("Test 1: Crear activo válido en Propiedad A retorna 201 y persiste correctamente en Oracle XE")
    public void test01_crearActivo_propiedadA_exitoso() throws Exception {
        ActivoCreateDTO dto = new ActivoCreateDTO(
                "ACT-TEST-NEW-01",
                "Subestación Eléctrica 500kVA",
                "PLANTAS",
                LocalDate.of(2024, 2, 1),
                new BigDecimal("85000000.00"),
                "OPERATIVO"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idActivo").isNumber())
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID))
                .andExpect(jsonPath("$.data.codigoActivo").value("ACT-TEST-NEW-01"))
                .andExpect(jsonPath("$.data.nombre").value("Subestación Eléctrica 500kVA"))
                .andExpect(jsonPath("$.data.estado").value("OPERATIVO"));

        // Verificación en Oracle XE
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ACTIVOS WHERE ID_PROPIEDAD = ? AND CODIGO_ACTIVO = ?",
                    Integer.class, PROP_1_ID, "ACT-TEST-NEW-01"
            );
            assertEquals(1, count, "El activo debe estar persistido en Oracle XE");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 2 — LISTADO TENANT-SCOPED (AISLAMIENTO ENTRE PROPIEDADES)
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("Test 2: Listado tenant-scoped garantiza aislamiento estricto entre Propiedad A y Propiedad B")
    public void test02_listarActivos_tenantScoped_aislamiento() throws Exception {
        // Admin Prop 1 consulta -> Solo ve activos de Prop 1
        mockMvc.perform(get("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].idPropiedad", everyItem(equalTo((int) PROP_1_ID))))
                .andExpect(jsonPath("$.data[*].codigoActivo", hasItem("ACT-TEST-A1")))
                .andExpect(jsonPath("$.data[*].codigoActivo", not(hasItem("ACT-TEST-B1"))));

        // Admin Prop 2 consulta -> Solo ve activos de Prop 2
        mockMvc.perform(get("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[*].idPropiedad", everyItem(equalTo((int) PROP_2_ID))))
                .andExpect(jsonPath("$.data[*].codigoActivo", hasItem("ACT-TEST-B1")))
                .andExpect(jsonPath("$.data[*].codigoActivo", not(hasItem("ACT-TEST-A1"))));
    }

    // =========================================================================
    // TEST 3 — GET POR ID DE ACTIVO PROPIO (200 OK)
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("Test 3: GET por ID de activo propio retorna 200 OK con payload íntegro")
    public void test03_obtenerPorId_propio_exitoso() throws Exception {
        mockMvc.perform(get("/api/v1/activos/" + idActivoA1)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idActivo").value(idActivoA1))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_1_ID))
                .andExpect(jsonPath("$.data.codigoActivo").value("ACT-TEST-A1"))
                .andExpect(jsonPath("$.data.categoria").value("ASCENSORES"))
                .andExpect(jsonPath("$.data.estado").value("OPERATIVO"));
    }

    // =========================================================================
    // TEST 4 — ANTI-IDOR EN GET (RECHAZO CROSS-TENANT 403/404)
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("Test 4: Anti-IDOR en GET bloquea acceso a activo perteneciente a otra propiedad (403/404)")
    public void test04_antiIdor_getActivo_rechazoCrossTenant() throws Exception {
        // Admin de Prop 1 intenta consultar Activo B1 (de Prop 2)
        mockMvc.perform(get("/api/v1/activos/" + idActivoB1)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 Forbidden o 404 Not Found ante acceso cross-tenant");
                });
    }

    // =========================================================================
    // TEST 5 — ANTI-IDOR EN UPDATE (RECHAZO 403/404 Y PRESERVACIÓN EN ORACLE)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("Test 5: Anti-IDOR en UPDATE bloquea modificación cross-tenant y mantiene datos intactos en Oracle")
    public void test05_antiIdor_updateActivo_rechazoCrossTenant() throws Exception {
        ActivoUpdateDTO updateDto = new ActivoUpdateDTO(
                "ACT-TEST-HACK",
                "NOMBRE MODIFICADO POR ATACANTE",
                "HACK",
                LocalDate.now(),
                new BigDecimal("100.00")
        );

        mockMvc.perform(put("/api/v1/activos/" + idActivoB1)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 Forbidden o 404 Not Found ante modificación cross-tenant");
                });

        // Verificación en Oracle XE: Activo B1 debe permanecer con su nombre original
        setElevatedContext();
        try {
            String nombreActual = jdbcTemplate.queryForObject(
                    "SELECT NOMBRE FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoB1
            );
            assertEquals("Circuito Cerrado CCTV Torre Norte", nombreActual,
                    "El nombre en Oracle XE debe permanecer inalterado ante ataque IDOR");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 6 — ANTI-IDOR EN CAMBIO DE ESTADO (RECHAZO 403/404 Y ESTADO PRESERVADO)
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("Test 6: Anti-IDOR en cambio de estado bloquea alteración cross-tenant y preserva estado en Oracle")
    public void test06_antiIdor_actualizarEstado_rechazoCrossTenant() throws Exception {
        ActivoEstadoDTO estadoDto = new ActivoEstadoDTO("DADO_DE_BAJA", "Ataque cross-tenant de baja");

        mockMvc.perform(patch("/api/v1/activos/" + idActivoB1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDto)))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(sc == 403 || sc == 404, "Debe retornar 403 Forbidden o 404 Not Found ante alteración cross-tenant");
                });

        // Verificación en Oracle XE
        assertEstadoOracle(idActivoB1, "OPERATIVO");
    }

    // =========================================================================
    // TEST 7 — CÓDIGO DUPLICADO EN MISMO TENANT (409 CONFLICT)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("Test 7: Código de activo duplicado en la misma propiedad es rechazado con 409 Conflict")
    public void test07_codigoDuplicado_mismoTenant_rechazo409() throws Exception {
        ActivoCreateDTO duplicateDto = new ActivoCreateDTO(
                "ACT-TEST-A1", // Código ya existente en Prop 1
                "Otro ascensor con código repetido",
                "ASCENSORES",
                LocalDate.now(),
                new BigDecimal("50000000.00"),
                "OPERATIVO"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODIGO_ACTIVO_DUPLICADO"));
    }

    // =========================================================================
    // TEST 8 — MISMO CÓDIGO EN DIFERENTE PROPIEDAD (PERMITIDO 201 CREATED)
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("Test 8: Mismo código de activo en diferente propiedad es permitido por unicidad compuesta")
    public void test08_mismoCodigo_diferentePropiedad_permitido201() throws Exception {
        // Código 'ACT-TEST-A1' ya existe en Prop 1; se registra en Prop 2
        ActivoCreateDTO dto = new ActivoCreateDTO(
                "ACT-TEST-A1",
                "Ascensor Torre Principal Prop 2",
                "ASCENSORES",
                LocalDate.now(),
                new BigDecimal("99000000.00"),
                "OPERATIVO"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idPropiedad").value(PROP_2_ID))
                .andExpect(jsonPath("$.data.codigoActivo").value("ACT-TEST-A1"));
    }

    // =========================================================================
    // TEST 9 — CÓDIGO DUPLICADO CASE-INSENSITIVE (RECHAZO 409 CONFLICT)
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("Test 9: Código de activo duplicado case-insensitive es rechazado con 409 Conflict")
    public void test09_codigoDuplicado_caseInsensitive_rechazo409() throws Exception {
        ActivoCreateDTO caseDto = new ActivoCreateDTO(
                "act-test-a1", // Minúsculas de 'ACT-TEST-A1'
                "Ascensor con código en minúsculas",
                "ASCENSORES",
                LocalDate.now(),
                new BigDecimal("70000000.00"),
                "OPERATIVO"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(caseDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODIGO_ACTIVO_DUPLICADO"));
    }

    // =========================================================================
    // TEST 10 — VALOR DE ADQUISICIÓN NEGATIVO (RECHAZO 400 BAD REQUEST)
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("Test 10: Valor de adquisición negativo es rechazado con 400 Bad Request")
    public void test10_valorAdquisicionNegativo_rechazo400() throws Exception {
        ActivoCreateDTO negativeDto = new ActivoCreateDTO(
                "ACT-TEST-NEG",
                "Equipo con valor negativo",
                "BOMBAS",
                LocalDate.now(),
                new BigDecimal("-1.00"),
                "OPERATIVO"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(negativeDto)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // TEST 11 — ESTADO INICIAL INVÁLIDO (RECHAZO 400 BAD REQUEST)
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("Test 11: Estado inicial inválido ('BORRADOR') es rechazado con 400 Bad Request")
    public void test11_estadoInvalido_rechazo400() throws Exception {
        ActivoCreateDTO invalidStateDto = new ActivoCreateDTO(
                "ACT-TEST-INV",
                "Equipo con estado inválido",
                "BOMBAS",
                LocalDate.now(),
                new BigDecimal("1000000.00"),
                "BORRADOR"
        );

        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidStateDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESTADO_ACTIVO_INVALIDO"));
    }

    // =========================================================================
    // TEST 12 — TRANSICIÓN VÁLIDA (OPERATIVO <-> MANTENIMIENTO)
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("Test 12: Transición válida (OPERATIVO <-> MANTENIMIENTO) actualiza correctamente en API y Oracle XE")
    public void test12_transicionValida_operativoAMantenimiento_exitoso() throws Exception {
        // 1. OPERATIVO -> MANTENIMIENTO
        ActivoEstadoDTO aMant = new ActivoEstadoDTO("MANTENIMIENTO", "Mantenimiento preventivo anual");
        mockMvc.perform(patch("/api/v1/activos/" + idActivoA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aMant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEstadoOracle(idActivoA1, "MANTENIMIENTO");

        // 2. MANTENIMIENTO -> OPERATIVO
        ActivoEstadoDTO aOper = new ActivoEstadoDTO("OPERATIVO", "Certificación técnica completada");
        mockMvc.perform(patch("/api/v1/activos/" + idActivoA1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aOper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEstadoOracle(idActivoA1, "OPERATIVO");
    }

    // =========================================================================
    // TEST 13 — TRANSICIÓN INVÁLIDA DESDE TERMINAL DADO_DE_BAJA (RECHAZO 400)
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("Test 13: Transición desde estado terminal DADO_DE_BAJA es rechazada con 400 Bad Request")
    public void test13_transicionInvalida_dadoDeBajaEsTerminal_rechazo400() throws Exception {
        // Intento de reactivar el activo en estado DADO_DE_BAJA
        ActivoEstadoDTO reactivarDto = new ActivoEstadoDTO("OPERATIVO", "Intento de reactivación");

        mockMvc.perform(patch("/api/v1/activos/" + idActivoA_Baja + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reactivarDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));

        // Verificación en Oracle XE: debe continuar como DADO_DE_BAJA
        assertEstadoOracle(idActivoA_Baja, "DADO_DE_BAJA");
    }

    // =========================================================================
    // TEST 14 — BAJA LÓGICA (DELETE ENDPOINT MARCA DADO_DE_BAJA)
    // =========================================================================
    @Test
    @Order(14)
    @DisplayName("Test 14: Baja lógica mediante endpoint DELETE marca DADO_DE_BAJA sin borrado físico en Oracle XE")
    public void test14_bajaLogica_deleteEndpoint_marcaDadoDeBaja() throws Exception {
        mockMvc.perform(delete("/api/v1/activos/" + idActivoA_ParaBaja)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verificación en Oracle XE: registro aún existe físicamente con estado DADO_DE_BAJA
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    Integer.class, idActivoA_ParaBaja
            );
            assertEquals(1, count, "El activo NO debe borrarse físicamente de Oracle XE");

            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoA_ParaBaja
            );
            assertEquals("DADO_DE_BAJA", estado, "El estado debe haber pasado a DADO_DE_BAJA");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 15 — SIN PROPIEDAD EN CONTEXTO (RECHAZO 403 FORBIDDEN)
    // =========================================================================
    @Test
    @Order(15)
    @DisplayName("Test 15: Petición sin propiedad activa en contexto de seguridad es rechazada con 403 Forbidden")
    public void test15_sinPropiedadEnContexto_rechazo() throws Exception {
        // Admin Org 1 tiene asignación a nivel Organización, sin propiedad activa seleccionada
        mockMvc.perform(get("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TEST 16 — ORACLE VPD / RLS DIRECTO EN BASE DE DATOS
    // =========================================================================
    @Test
    @Order(16)
    @DisplayName("Test 16: Oracle VPD / RLS directo en BD garantiza aislamiento de activos por propiedad")
    public void test16_oracleRls_aislamientoDirectoEnBaseDeDatos() {
        setTenantContext(USER_ADMIN_PROP_1, ORG_1_ID, PROP_1_ID, "ADMIN_PROPIEDAD");
        try {
            List<Long> propsConsultadas = jdbcTemplate.query(
                    "SELECT DISTINCT ID_PROPIEDAD FROM ACTIVOS",
                    (rs, rowNum) -> rs.getLong("ID_PROPIEDAD")
            );

            // Bajo el contexto de sesión de Prop 1, RLS solo debe permitir ver filas de Prop 1
            for (Long p : propsConsultadas) {
                assertEquals(PROP_1_ID, p, "Oracle RLS solo debe permitir retornar registros de PROP_1_ID");
            }
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // TEST 17 — CONTROL DE ACCESO POR ROL: RESIDENTE BLOQUEADO (403 FORBIDDEN)
    // =========================================================================
    @Test
    @Order(17)
    @DisplayName("Test 17: Rol no autorizado (RESIDENTE) es bloqueado con 403 Forbidden en todas las operaciones")
    public void test17_rolNoAutorizado_residenteBloqueado_403() throws Exception {
        // GET catálogo
        mockMvc.perform(get("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        // POST crear activo
        ActivoCreateDTO dto = new ActivoCreateDTO(
                "ACT-TEST-RES",
                "Intento de creación por residente",
                "OTROS",
                LocalDate.now(),
                new BigDecimal("1000.00"),
                "OPERATIVO"
        );
        mockMvc.perform(post("/api/v1/activos")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // PUT modificar activo
        ActivoUpdateDTO updateDto = new ActivoUpdateDTO(
                "ACT-TEST-A1",
                "Intento de modificación por residente",
                "ASCENSORES",
                LocalDate.now(),
                new BigDecimal("1000.00")
        );
        mockMvc.perform(put("/api/v1/activos/" + idActivoA1)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());

        // DELETE dar de baja
        mockMvc.perform(delete("/api/v1/activos/" + idActivoA1)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // UTILIDADES DE PERSISTENCIA Y ELEVACIÓN DE CONTEXTO
    // =========================================================================
    private Long insertActivo(Long idProp, String codigo, String nombre, String cat,
                              LocalDate fecha, BigDecimal valor, String estado) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ACTIVOS (" +
                "ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA, " +
                "FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                new String[]{"ID_ACTIVO"}
            );
            ps.setLong(1, idProp);
            ps.setString(2, codigo);
            ps.setString(3, nombre);
            ps.setString(4, cat);
            ps.setDate(5, fecha != null ? java.sql.Date.valueOf(fecha) : null);
            ps.setBigDecimal(6, valor);
            ps.setString(7, estado);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void assertEstadoOracle(Long idActivo, String estadoEsperado) {
        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivo
            );
            assertEquals(estadoEsperado, estado, "El estado en Oracle debe ser " + estadoEsperado);
        } finally {
            clearContext();
        }
    }

    private void ensureOrganizacion(Long idOrg, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, idOrg);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
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
                .roleScope("PROPIEDAD")
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
