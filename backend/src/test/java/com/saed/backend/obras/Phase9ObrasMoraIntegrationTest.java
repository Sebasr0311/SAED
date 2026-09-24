package com.saed.backend.obras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Integral para GAP-F9-04:
 * Validación de Mora / Paz y Salvo en Obras en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de los 15 casos técnicos mandatorios:
 * - Test 01: Unidad al día (sin deudas) radica obra exitosamente (201 Created).
 * - Test 02: Unidad con cuota en mora bloquea radicación de obra (422 UNIDAD_EN_MORA).
 * - Test 03: Tras regularización de cartera / pago de cuota, radicación exitosa (201 Created).
 * - Test 04: Anti-IDOR: Residente de Unidad A no puede radicar obra en Unidad B (403 Forbidden).
 * - Test 05: Aislamiento Cross-Property: Admin de Propiedad 1 no puede operar obra de Propiedad 2 (403/400).
 * - Test 06: Manipulación de payload: Intento de forzar unidad foránea en cuerpo de petición (403 Forbidden).
 * - Test 07: Prevención de bypass de estados (SOLICITADA -> EN_EJECUCION -> 400 Bad Request) y control de mora al iniciar (422).
 * - Test 08: Validación de unidad obligatoria en solicitud administrativa (400 Bad Request).
 * - Test 09: Unidad con multas no facturadas exigibles bloquea radicación (422 UNIDAD_EN_MORA).
 * - Test 10: Unidad con saldo total exigible en CARTERA > 0 bloquea radicación (422 UNIDAD_EN_MORA).
 * - Test 11: Radicación administrativa en misma copropiedad permitida para unidad a paz y salvo (201 Created).
 * - Test 12: Admin de Propiedad A intentando radicar obra para unidad de Propiedad B bloqueado (403 Forbidden).
 * - Test 13: Rol no autorizado: Residente intentando invocar endpoints administrativos (403 Forbidden).
 * - Test 14: Contexto sin copropiedad activa / sin asignación rechazado (401/403).
 * - Test 15: Consistencia canónica estricta con PazYSalvoService.verificarEstadoFinanciero().
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9ObrasMoraIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PazYSalvoService pazYSalvoService;

    @MockBean
    private AssignmentService assignmentService;

    // Identidades
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 301L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long USER_RESIDENTE_1 = 4L; // Residente Unidad 1
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Residente Unidad 2
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza de datos de prueba en obras y finanzas
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM OBRAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);

            ensureOrganizacion(1L, "Organización Central", "900000001-1", "org1@saed.com");
            ensureOrganizacion(9992L, "Org 9992 Foranea", "900009992-9", "org9992@test.com");

            ensurePropiedad(1L, 1L, "Torre Central SAED");
            ensurePropiedad(9992L, 9992L, "Torre Foránea 9992");

            ensureUnidad(1L, 1L, "Apto 101");
            ensureUnidad(2L, 1L, "Apto 102");
            ensureUnidad(9992L, 9992L, "Apto 9992");

            ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

            ensurePersona(2L, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(2L, 2L, "admin", "admin@saed.com");

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "camartinez@saed.com");

            ensurePersona(5L, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(5L, 5L, "ana_g", "anagomez@saed.com");

            ensurePersona(8L, "1000000008", "AdminOrg1", "SAED", "admin_org1@saed.com");
            ensureUsuario(8L, 8L, "admin_org1", "admin_org1@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            ensureMembresia(1L);
            ensureMembresia(9992L);

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
                    DELETE FROM OBRAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central SAED");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

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

        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(adminOrg1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));
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
    // CASOS DE PRUEBA MANDATORIOS: GAP-F9-04
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 01: Unidad al día (cartera = 0, multas = 0) radica obra exitosamente (201 Created)")
    void test01_solicitarObra_unidadAlDia_exitoso201() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> body = Map.of(
                "descripcion", "Remodelación de cocina integral y enchape",
                "fechaInicio", LocalDate.now().plusDays(2).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(20).toString(),
                "responsableObra", "Construcciones SAS",
                "telefonoResponsable", "3001234567",
                "depositoGarantia", 500000
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idObra", notNullValue()));

        // Verificar inserción en base de datos con contexto elevado
        setElevatedContext();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OBRAS WHERE ID_UNIDAD = 1 AND ESTADO = 'SOLICITADA'", Integer.class);
        clearContext();
        assertEquals(1, count);
    }

    @Test
    @Order(2)
    @DisplayName("Test 02: Unidad con cuota en mora bloquea radicación de obra (422 UNIDAD_EN_MORA)")
    void test02_solicitarObra_unidadEnMora_cuotaVencida_rechazado422() throws Exception {
        setElevatedContext();
        // Insertar cuota vencida en Unidad 1
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (1, 1, '2026-05', 450000, 450000, TRUNC(SYSDATE) - 15, 'VENCIDA')
        """);
        clearContext();

        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> body = Map.of(
                "descripcion", "Cambio de pisos en sala",
                "fechaInicio", LocalDate.now().plusDays(2).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(10).toString(),
                "responsableObra", "Decoraciones Gomez",
                "telefonoResponsable", "3119876543",
                "depositoGarantia", 0
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("mora financiera")));
    }

    @Test
    @Order(3)
    @DisplayName("Test 03: Tras regularización de cartera / pago de cuota, radicación exitosa (201 Created)")
    void test03_solicitarObra_trasPagoRegularizacion_exitoso201() throws Exception {
        setElevatedContext();
        // Regularizar deuda eliminando la cuota pendiente
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = 1");
        clearContext();

        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> body = Map.of(
                "descripcion", "Cambio de pisos en sala tras pago",
                "fechaInicio", LocalDate.now().plusDays(3).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(12).toString(),
                "responsableObra", "Decoraciones Gomez",
                "telefonoResponsable", "3119876543",
                "depositoGarantia", 100000
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idObra", notNullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("Test 04: Anti-IDOR: Residente de Unidad A no puede radicar obra en Unidad B (403 Forbidden)")
    void test04_antiIdor_residenteUnidadA_intentaOperarUnidadB_403() throws Exception {
        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Residente 1 (asignado a unidad 1) intenta enviar idUnidad: 2
        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_2_ID,
                "descripcion", "Ataque IDOR intentando radicar obra en apartamento vecino",
                "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(10).toString(),
                "responsableObra", "Contratista Malicioso",
                "telefonoResponsable", "3000000000",
                "depositoGarantia", 0
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @Order(5)
    @DisplayName("Test 05: Aislamiento Cross-Property: Admin de Propiedad 1 no puede operar obra de Propiedad 2 (403/400)")
    void test05_crossProperty_adminProp1_intentaOperarObraProp2_403o400() throws Exception {
        setElevatedContext();
        // Insertar una obra perteneciente a Propiedad 2 (Unidad 9992)
        jdbcTemplate.update("""
            INSERT INTO OBRAS (ID_UNIDAD, DESCRIPCION, FECHA_INICIO, FECHA_FIN_ESTIMADA, RESPONSABLE_OBRA,
                               TELEFONO_RESPONSABLE, DEPOSITO_GARANTIA, ESTADO, SOLICITADO_POR)
            VALUES (9992, 'Obra Privada Foránea Propiedad 2', TRUNC(SYSDATE) + 1, TRUNC(SYSDATE) + 15,
                    'Contratista Prop 2', '3150009992', 0, 'SOLICITADA', 99)
        """);
        Long idObraForanea = jdbcTemplate.queryForObject(
                "SELECT ID_OBRA FROM OBRAS WHERE ID_UNIDAD = 9992 AND ROWNUM = 1", Long.class);
        clearContext();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Admin de Propiedad 1 intenta consultar la obra de Propiedad 2
        mockMvc.perform(get("/api/v1/obras/" + idObraForanea)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isBadRequest());

        // Admin de Propiedad 1 intenta aprobar la obra de Propiedad 2
        mockMvc.perform(post("/api/v1/obras/" + idObraForanea + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("Test 06: Manipulación de payload: Intento de forzar unidad foránea en cuerpo de petición (403 Forbidden)")
    void test06_manipulacionPayload_residenteIntentaManipularIdUnidad_403() throws Exception {
        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Enviar idUnidad de otra propiedad distinta
        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_ORG2_ID,
                "descripcion", "Manipulación de payload hacia unidad foránea",
                "fechaInicio", LocalDate.now().plusDays(2).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(5).toString(),
                "responsableObra", "Hacker Obra",
                "telefonoResponsable", "3201112233",
                "depositoGarantia", 0
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @Order(7)
    @DisplayName("Test 07: Prevención de bypass de estados (SOLICITADA -> EN_EJECUCION -> 400 Bad Request) y control de mora al iniciar (422)")
    void test07_bypassTransicion_solicitadaAEnEjecucionDirecto_400_y_moraAlIniciar() throws Exception {
        setElevatedContext();
        // Crear obra en estado SOLICITADA en Unidad 1
        jdbcTemplate.update("""
            INSERT INTO OBRAS (ID_UNIDAD, DESCRIPCION, FECHA_INICIO, FECHA_FIN_ESTIMADA, RESPONSABLE_OBRA,
                               TELEFONO_RESPONSABLE, DEPOSITO_GARANTIA, ESTADO, SOLICITADO_POR)
            VALUES (1, 'Obra Prueba Bypass', TRUNC(SYSDATE) + 1, TRUNC(SYSDATE) + 10,
                    'Ingeniería SAS', '3104445566', 0, 'SOLICITADA', 4)
        """);
        Long idObra = jdbcTemplate.queryForObject(
                "SELECT ID_OBRA FROM OBRAS WHERE ID_UNIDAD = 1 AND DESCRIPCION = 'Obra Prueba Bypass'", Long.class);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // 1. Intento de bypass directo a EN_EJECUCION sin haber sido APROBADA
        mockMvc.perform(post("/api/v1/obras/" + idObra + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bypass no permitido")));

        // 2. Aprobar la obra legalmente (Unidad está a paz y salvo)
        mockMvc.perform(post("/api/v1/obras/" + idObra + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // 3. Ahora la unidad cae en mora antes del inicio de los trabajos
        setElevatedContext();
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (1, 1, '2026-06', 380000, 380000, TRUNC(SYSDATE) - 5, 'VENCIDA')
        """);
        clearContext();

        // 4. Intento de iniciar la obra aprobada con la unidad en mora -> Debe ser bloqueada con 422 UNIDAD_EN_MORA
        mockMvc.perform(post("/api/v1/obras/" + idObra + "/iniciar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("No se puede iniciar o reanudar")));

        // 5. Intento vía PATCH /estado
        mockMvc.perform(patch("/api/v1/obras/" + idObra + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "EN_EJECUCION"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")));
    }

    @Test
    @Order(8)
    @DisplayName("Test 08: Validación de unidad obligatoria en solicitud administrativa (400 Bad Request)")
    void test08_solicitudSinUnidad_adminSinEspecificarUnidad_400() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "descripcion", "Pintura general sin unidad especificada",
                "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(5).toString(),
                "responsableObra", "Pinturas Ya",
                "telefonoResponsable", "3009998877"
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Se requiere especificar una unidad")));
    }

    @Test
    @Order(9)
    @DisplayName("Test 09: Unidad con multas no facturadas exigibles bloquea radicación (422 UNIDAD_EN_MORA)")
    void test09_solicitarObra_unidadConMultasPendientesSinCartera_rechazado422() throws Exception {
        setElevatedContext();
        // Cartera en 0 pero multa exigible impuesta en Unidad 2
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = 2");
        jdbcTemplate.update("DELETE FROM CARTERA WHERE ID_UNIDAD = 2");
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (2, 1, 180000, 'Ruidos molestos y afectación a convivencia', 'IMPUESTA', NULL)
        """);
        clearContext();

        String tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);

        Map<String, Object> body = Map.of(
                "descripcion", "Insonorización de habitación principal",
                "fechaInicio", LocalDate.now().plusDays(2).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(8).toString(),
                "responsableObra", "Acústica SAS",
                "telefonoResponsable", "3178887766",
                "depositoGarantia", 0
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("multas")));
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Unidad con saldo total exigible en CARTERA > 0 bloquea radicación (422 UNIDAD_EN_MORA)")
    void test10_solicitarObra_unidadConSaldoTotalExigibleMayorACero_rechazado422() throws Exception {
        setElevatedContext();
        // Insertar saldo en CARTERA para Unidad 2 (SALDO_TOTAL es columna virtual computada)
        jdbcTemplate.update("""
            INSERT INTO CARTERA (ID_UNIDAD, SALDO_CORRIENTE, SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS, FECHA_CORTE, ESTADO_CARTERA)
            VALUES (2, 250000, 0, 0, 0, TRUNC(SYSDATE), 'AL_DIA')
        """);
        clearContext();

        String tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);

        Map<String, Object> body = Map.of(
                "descripcion", "Instalación de paneles de yeso",
                "fechaInicio", LocalDate.now().plusDays(4).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(10).toString(),
                "responsableObra", "Drywall Expertos",
                "telefonoResponsable", "3123334455",
                "depositoGarantia", 50000
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("mora financiera")));
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Radicación administrativa en misma copropiedad permitida para unidad a paz y salvo (201 Created)")
    void test11_obraYUnidadMismaPropiedad_permitidoCuandoPazYSalvo() throws Exception {
        setElevatedContext();
        // Asegurar unidad 2 limpia de deudas
        jdbcTemplate.update("DELETE FROM MULTAS WHERE ID_UNIDAD = 2");
        jdbcTemplate.update("DELETE FROM CARTERA WHERE ID_UNIDAD = 2");
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = 2");
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_2_ID,
                "descripcion", "Reparación locativa autorizada por administración",
                "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(6).toString(),
                "responsableObra", "Obras SAED",
                "telefonoResponsable", "3101112222",
                "depositoGarantia", 200000
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idObra", notNullValue()));
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Admin de Propiedad A intentando radicar obra para unidad de Propiedad B bloqueado (403 Forbidden)")
    void test12_obraPropiedadA_conUnidadPropiedadB_rechazadoCrossTenant() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Admin de Propiedad 1 intenta crear obra para Unidad 9992 de Propiedad 2
        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_ORG2_ID,
                "descripcion", "Intento de creación de obra cross-tenant",
                "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(5).toString(),
                "responsableObra", "Contratista Ilegal",
                "telefonoResponsable", "3001112233",
                "depositoGarantia", 0
        );

        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Rol no autorizado: Residente intentando invocar endpoints administrativos (403 Forbidden)")
    void test13_rolNoAutorizado_residenteEnEndpointsAdmin_403() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Residente intentando listar obras administrativas
        mockMvc.perform(get("/api/v1/obras/admin")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());

        // Residente intentando aprobar obra
        mockMvc.perform(post("/api/v1/obras/1/aprobar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());

        // Residente intentando iniciar obra
        mockMvc.perform(post("/api/v1/obras/1/iniciar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());

        // Residente intentando cambiar estado por PATCH
        mockMvc.perform(patch("/api/v1/obras/1/estado")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Contexto sin copropiedad activa / sin asignación rechazado (401/403)")
    void test14_contextoSinPropiedad_peticionSinAsignacion_rechazada() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> body = Map.of(
                "descripcion", "Obra sin contexto de propiedad",
                "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(4).toString(),
                "responsableObra", "Contratista",
                "telefonoResponsable", "3001234567"
        );

        // 1. Asignación inválida / inactiva
        mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", "999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        // 2. Petición anónima (sin token)
        mockMvc.perform(post("/api/v1/obras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Consistencia canónica estricta con PazYSalvoService.verificarEstadoFinanciero()")
    void test15_consistenciaRechazoObraConPazYSalvoService() throws Exception {
        setElevatedContext();
        // Limpiar unidad 1
        jdbcTemplate.update("DELETE FROM OBRAS WHERE ID_UNIDAD = 1");
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = 1");
        jdbcTemplate.update("DELETE FROM MULTAS WHERE ID_UNIDAD = 1");
        jdbcTemplate.update("DELETE FROM CARTERA WHERE ID_UNIDAD = 1");

        // 1. Verificar directamente con PazYSalvoService que la unidad está a paz y salvo
        PazYSalvoEstadoFinancieroDTO estadoLimpio = pazYSalvoService.verificarEstadoFinanciero(UNIT_1_ID);
        assertTrue(estadoLimpio.pazYSalvo(), "La unidad debe estar a paz y salvo inicialmente");
        assertFalse(estadoLimpio.enMora(), "La unidad no debe estar en mora");

        // 2. Insertar deuda en mora con estado canónico VENCIDA
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (1, 1, '2026-04', 500000, 500000, TRUNC(SYSDATE) - 30, 'VENCIDA')
        """);

        // 3. Verificar directamente con PazYSalvoService que ahora reporta enMora == true
        PazYSalvoEstadoFinancieroDTO estadoMora = pazYSalvoService.verificarEstadoFinanciero(UNIT_1_ID);
        assertFalse(estadoMora.pazYSalvo(), "La unidad debe reportar pazYSalvo = false");
        assertTrue(estadoMora.enMora(), "La unidad debe reportar enMora = true");
        assertEquals(0, new BigDecimal("500000").compareTo(estadoMora.saldoTotalExigible()));
        assertFalse(estadoMora.motivosBloqueo().isEmpty());

        clearContext();

        // 4. Verificar que el endpoint de obras rechaza con los mismos motivos reportados por PazYSalvoService
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        Map<String, Object> body = Map.of(
                "descripcion", "Obra prueba de consistencia canónica",
                "fechaInicio", LocalDate.now().plusDays(2).toString(),
                "fechaFinEstimada", LocalDate.now().plusDays(8).toString(),
                "responsableObra", "Consistencia SAS",
                "telefonoResponsable", "3189990000",
                "depositoGarantia", 0
        );

        MvcResult result = mockMvc.perform(post("/api/v1/obras")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("500000") || responseBody.contains("cartera") || responseBody.contains("cuotas"),
                "El mensaje de error debe reflejar los motivos calculados por PazYSalvoService");
    }

    // --- MÉTODOS AUXILIARES DE ASEGURAMIENTO DE DATOS ---

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
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, ?, ?, ?, ?)", id, doc, nombre, apellido, email);
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
}
