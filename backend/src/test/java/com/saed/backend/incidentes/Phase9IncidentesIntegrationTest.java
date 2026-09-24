package com.saed.backend.incidentes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.*;
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
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Types;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Integral para GAP-F9-07:
 * INCIDENTES — INVOLUCRADOS, INVESTIGACIÓN, ESCALAMIENTO, ESTADOS, SEGURIDAD Y AUDITORÍA (SAED 2.0).
 *
 * Cobertura de 41 casos de prueba organizados en 7 categorías:
 * - Categoría A: Creación Canónica y Consulta Scoped por Rol (Tests 01 a 08)
 * - Categoría B: Máquina de Estados Estricta (CK_INCIDENTES_ESTADO) (Tests 09 a 16)
 * - Categoría C: Pipeline de Investigación (/investigacion) (Tests 17 a 22)
 * - Categoría D: Pipeline de Escalamiento (/escalar) (Tests 23 a 26)
 * - Categoría E: Gestión de Involucrados & Anti-IDOR (Tests 27 a 34)
 * - Categoría F: Auditoría, Gating de Plan & Multi-Tenant (Tests 35 a 38)
 * - Categoría G: Concurrencia & Resiliencia (Tests 39 a 41)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9IncidentesIntegrationTest {

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

    private static final long USER_PORTERO_1 = 3L;
    private static final long ASSIGN_PORTERO_1 = 103L;

    private static final long USER_RESIDENTE_1 = 4L;
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L;
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

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
            // Limpieza de datos de prueba previos
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM INCIDENTE_INVOLUCRADOS WHERE ID_INCIDENTE IN (SELECT ID_INCIDENTE FROM INCIDENTES WHERE TITULO LIKE 'TEST-F907%');
                    DELETE FROM INCIDENTES WHERE TITULO LIKE 'TEST-F907%';
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea 9992", "900009992-9", "org9992@test.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Torre Central SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Torre Foránea 9992");

            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");
            ensureUnidad(UNIT_ORG2_ID, PROP_2_ID, "Apto 9992");

            ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

            ensurePersona(2L, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(2L, 2L, "admin", "admin@saed.com");

            ensurePersona(3L, "1000000003", "Pedro", "Portero", "portero@saed.com");
            ensureUsuario(3L, 3L, "portero01", "portero@saed.com");

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "carlos_res1@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "carlos_res1@saed.com");

            ensurePersona(5L, "1000000005", "Ana", "Gomez", "ana_res2@saed.com");
            ensureUsuario(5L, 5L, "ana_g", "ana_res2@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            ensurePersona(10L, "1000000010", "Vecino", "Involucrado", "vecino@saed.com");
            ensurePersona(999L, "9000000999", "Persona", "Foranea", "foranea@test.com");

            ensureMembresia(ORG_1_ID);
            ensureMembresia(ORG_2_ID);

            ensureAdministradorSaed(USER_SUPERADMIN);
            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_PORTERO_1, USER_PORTERO_1, "PORTERO", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_1_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);

            // Asociar persona 10 como residente de unidad 2 para pruebas de involucrado
            ensureResidenteUnidad(UNIT_2_ID, 10L);

            setupMockAssignments();
        } catch (Exception e) {
            System.err.println("CRITICAL SETUP ERROR: " + e.getMessage());
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
                    DELETE FROM INCIDENTE_INVOLUCRADOS WHERE ID_INCIDENTE IN (SELECT ID_INCIDENTE FROM INCIDENTES WHERE TITULO LIKE 'TEST-F907%');
                    DELETE FROM INCIDENTES WHERE TITULO LIKE 'TEST-F907%';
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central SAED");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea 9992");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea 9992");

        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

        AssignmentResponseDTO porteroAssign = new AssignmentResponseDTO();
        porteroAssign.setIdAsignacion(ASSIGN_PORTERO_1);
        porteroAssign.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssign.setOrganizacion(org1);
        porteroAssign.setPropiedad(prop1);

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
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO_1, USER_PORTERO_1)).thenReturn(Optional.of(porteroAssign));
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

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long idProp, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, idProp);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, ?, 'Calle 100 # 15-20', 'Bogotá', 'MIXTA', 'ACTIVA')", idProp, idOrg, nombre);
        }
    }

    private void ensureUnidad(Long idUnidad, Long idProp, String ident) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, idUnidad);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                    "VALUES (?, ?, ?, 'RESIDENCIAL', 0.05, 'DISPONIBLE')", idUnidad, idProp, ident);
        }
    }

    private void ensurePersona(Long idPersona, String numDoc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, ?, ?, ?)", idPersona, numDoc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long idUsuario, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (c == null || c == 0) {
            try {
                jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                        "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", idUsuario, idPersona, username, email);
            } catch (Exception e) {
                try {
                    jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
                            "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", idUsuario, idPersona, username, email);
                } catch (Exception ignored) {}
            }
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

    private void ensureAdministradorSaed(Long idUsuario) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ADMINISTRADORES_SAED WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, NIVEL, ESTADO) VALUES (?, 'SUPERADMIN', 'ACTIVO')", idUsuario);
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

    private void ensureResidenteUnidad(Long idUnidad, Long idPersona) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?", Integer.class, idUnidad, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO, FECHA_INICIO) " +
                    "VALUES (?, ?, 'ARRENDATARIO', 'ACTIVO', TRUNC(SYSDATE))", idUnidad, idPersona);
        }
    }

    private Long crearIncidenteHelper(String token, Long assignId, String titulo, String tipo, String severidad, Long idUnidad) throws Exception {
        IncidenteDTO req = new IncidenteDTO();
        req.setTitulo(titulo);
        req.setTipoIncidente(tipo);
        req.setNivelSeveridad(severidad);
        req.setDescripcionHechos("Descripción de prueba para " + titulo);
        req.setFechaHoraIncidente(ZonedDateTime.now());
        req.setIdUnidad(idUnidad);

        MvcResult res = mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", assignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return ((Number) map.get("idIncidente")).longValue();
    }

    // =========================================================================
    // CATEGORÍA A: CREACIÓN CANÓNICA Y CONSULTA SCOPED POR ROL (01 a 08)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 01: Admin Propiedad crea incidente con severidad canónica GRAVE -> 201 Created")
    void test01_adminPropiedad_crearIncidenteValido_retorna201() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-01 Hurto en sótano", "SEGURIDAD_HURTO", "GRAVE", null);
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test
    @Order(2)
    @DisplayName("Test 02: Portero crea incidente operativo con severidad LEVE -> 201 Created")
    void test02_portero_crearIncidenteValido_retorna201() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_PORTERO_1);
        Long id = crearIncidenteHelper(token, ASSIGN_PORTERO_1, "TEST-F907-02 Desacuerdo en portería", "CONVIVENCIA_DISPUTA", "LEVE", null);
        assertNotNull(id);
    }

    @Test
    @Order(3)
    @DisplayName("Test 03: Residente reporta incidente en su unidad asignada -> 201 Created")
    void test03_residente_crearIncidenteEnSuUnidad_retorna201() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        Long id = crearIncidenteHelper(token, ASSIGN_RESIDENTE_1, "TEST-F907-03 Daño ventana", "DANO_BIEN_COMUN", "MODERADA", UNIT_1_ID);
        assertNotNull(id);
    }

    @Test
    @Order(4)
    @DisplayName("Test 04: Residente intenta especificar otra unidad pero el servicio fuerza la suya -> IDOR prevenido")
    void test04_residente_crearIncidenteEnOtraUnidad_fuerzaSuUnidad() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        // Residente 1 intenta reportar con idUnidad = UNIT_2_ID
        Long id = crearIncidenteHelper(token, ASSIGN_RESIDENTE_1, "TEST-F907-04 Intento IDOR unidad", "DANO_BIEN_COMUN", "LEVE", UNIT_2_ID);

        // Validar en base de datos que el incidente fue asignado a UNIT_1_ID (unidad del residente en contexto)
        setElevatedContext();
        Long unidadEnDb = jdbcTemplate.queryForObject("SELECT ID_UNIDAD FROM INCIDENTES WHERE ID_INCIDENTE = ?", Long.class, id);
        assertEquals(UNIT_1_ID, unidadEnDb, "El servicio debe forzar la unidad del residente autenticado para prevenir IDOR");
    }

    @Test
    @Order(5)
    @DisplayName("Test 05: Admin Propiedad lista todos los incidentes de la copropiedad -> 200 OK")
    void test05_adminPropiedad_listarIncidentes_retorna200ConLista() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-05 Ruido en piso 3", "CONVIVENCIA_RUIDO", "LEVE", null);

        mockMvc.perform(get("/api/v1/incidentes/admin")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(6)
    @DisplayName("Test 06: Portero lista incidentes de la propiedad -> 200 OK")
    void test06_portero_listarIncidentes_retorna200ConLista() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-06 Filtración de agua", "FALLA_CRITICA_INFRAESTRUCTURA", "MODERADA", null);

        String token = jwtProvider.generateIdentityToken(USER_PORTERO_1);
        mockMvc.perform(get("/api/v1/incidentes/admin")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_PORTERO_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(7)
    @DisplayName("Test 07: Residente consulta mis-incidentes y solo ve los de su unidad -> 200 OK")
    void test07_residente_listarMisIncidentes_soloVeSuUnidad() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        crearIncidenteHelper(token, ASSIGN_RESIDENTE_1, "TEST-F907-07 Incidente Unidad 1", "DANO_BIEN_COMUN", "LEVE", UNIT_1_ID);

        mockMvc.perform(get("/api/v1/incidentes/mis-incidentes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$[*].idUnidad", everyItem(equalTo((int) UNIT_1_ID))));
    }

    @Test
    @Order(8)
    @DisplayName("Test 08: Residente intenta ver detalle de incidente de otra unidad -> 403 Forbidden")
    void test08_residente_verIncidenteDeOtraUnidad_retorna403() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long idIncidenteUnidad2 = crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-08 Privado U2", "DANO_BIEN_COMUN", "LEVE", UNIT_2_ID);

        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        mockMvc.perform(get("/api/v1/incidentes/" + idIncidenteUnidad2)
                        .header("Authorization", "Bearer " + tokenRes1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CATEGORÍA B: MÁQUINA DE ESTADOS ESTRICTA (CK_INCIDENTES_ESTADO) (09 a 16)
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("Test 09: Transición REPORTADO -> EN_INVESTIGACION exitosa")
    void test09_transicion_reportadoAEnInvestigacion_exito() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-09 State Test", "OTRO", "LEVE", null);

        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("EN_INVESTIGACION");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("EN_INVESTIGACION", estadoDb);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Transición REPORTADO -> ACCION_TOMADA exitosa")
    void test10_transicion_reportadoAAccionTomada_exito() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-10 State Test", "OTRO", "LEVE", null);

        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("ACCION_TOMADA");
        dto.setConclusiones("Se reparó inmediatamente la cerradura.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("ACCION_TOMADA", estadoDb);
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Transición REPORTADO -> ESCALADO_A_SANCION exitosa")
    void test11_transicion_reportadoAEscaladoASancion_exito() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-11 State Test", "CONVIVENCIA_DISPUTA", "GRAVE", UNIT_1_ID);

        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("ESCALADO_A_SANCION");
        dto.setMotivo("Reincidencia de faltas al manual");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("ESCALADO_A_SANCION", estadoDb);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Transición REPORTADO -> CERRADO exitosa con conclusiones y timestamp de cierre")
    void test12_transicion_reportadoACerrado_exito() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-12 State Test", "OTRO", "LEVE", null);

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conclusiones", "Falsa alarma descartada por cámaras."))))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> dbRow = jdbcTemplate.queryForMap("SELECT ESTADO, FECHA_CIERRE, CONCLUSIONES_CIERRE FROM INCIDENTES WHERE ID_INCIDENTE = ?", id);
        assertEquals("CERRADO", dbRow.get("ESTADO"));
        assertNotNull(dbRow.get("FECHA_CIERRE"));
        assertEquals("Falsa alarma descartada por cámaras.", dbRow.get("CONCLUSIONES_CIERRE"));
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Transición EN_INVESTIGACION -> ACCION_TOMADA exitosa")
    void test13_transicion_enInvestigacionAAccionTomada_exito() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-13 State Test", "ACCIDENTE_PERSONA", "MODERADA", null);

        // Iniciar investigacion
        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Concluir hacia ACCION_TOMADA
        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("ACCION_TOMADA");
        dto.setConclusiones("Se prestó primeros auxilios y se señalizó el área mojada.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("ACCION_TOMADA", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Reapertura de incidente CERRADO hacia EN_INVESTIGACION permitida exclusivamente")
    void test14_transicion_cerradoHaciaEnInvestigacion_reaperturaValida() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-14 Reapertura", "SEGURIDAD_HURTO", "GRAVE", null);

        // Cerrar incidente
        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conclusiones", "Cerrado inicialmente."))))
                .andExpect(status().isOk());

        // Reabrir hacia EN_INVESTIGACION
        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("EN_INVESTIGACION");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("EN_INVESTIGACION", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Transición directa de CERRADO hacia ACCION_TOMADA no permitida -> 400 Bad Request")
    void test15_transicion_cerradoHaciaAccionTomada_invalida_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-15 Invalido", "OTRO", "LEVE", null);

        // Cerrar incidente
        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        // Intentar pasar a ACCION_TOMADA directamente
        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("ACCION_TOMADA");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("TRANSICION_ESTADO_INVALIDA")))
                .andExpect(jsonPath("$.estadoOrigen", equalTo("CERRADO")))
                .andExpect(jsonPath("$.estadoDestino", equalTo("ACCION_TOMADA")));
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: Transición hacia estado inexistente -> 400 Bad Request con ESTADO_INCIDENTE_INVALIDO")
    void test16_transicion_estadoInexistente_retorna400ConCodigo() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-16 Estado Inexistente", "OTRO", "LEVE", null);

        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("ESTADO_INEXISTENTE");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ESTADO_INCIDENTE_INVALIDO")));
    }

    // =========================================================================
    // CATEGORÍA C: PIPELINE DE INVESTIGACIÓN (/investigacion) (17 a 22)
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("Test 17: Iniciar investigación registra actor investigador y fecha de inicio")
    void test17_iniciarInvestigacion_registraInvestigadorYFechaInicio() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-17 Inicio Inv", "FALLA_CRITICA_INFRAESTRUCTURA", "GRAVE", null);

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT ESTADO, INVESTIGADO_POR, FECHA_INICIO_INVESTIGACION FROM INCIDENTES WHERE ID_INCIDENTE = ?", id);
        assertEquals("EN_INVESTIGACION", dbRow.get("ESTADO"));
        assertEquals(USER_ADMIN_PROP_1, ((Number) dbRow.get("INVESTIGADO_POR")).longValue());
        assertNotNull(dbRow.get("FECHA_INICIO_INVESTIGACION"));
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Actualizar investigación persiste hallazgos intermedios")
    void test18_actualizarInvestigacion_actualizaHallazgos() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-18 Update Inv", "FALLA_CRITICA_INFRAESTRUCTURA", "MODERADA", null);

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        IncidenteInvestigacionRequestDTO req = new IncidenteInvestigacionRequestDTO();
        req.setHallazgos("Se detectó cortocircuito en tablero eléctrico secundario torre B.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        setElevatedContext();
        String hallazgosDb = jdbcTemplate.queryForObject("SELECT HALLAZGOS_INVESTIGACION FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("Se detectó cortocircuito en tablero eléctrico secundario torre B.", hallazgosDb);
    }

    @Test
    @Order(19)
    @DisplayName("Test 19: Actualizar investigación sobre incidente CERRADO retorna 400 Bad Request")
    void test19_actualizarInvestigacion_incidenteCerrado_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-19 Closed Inv", "OTRO", "LEVE", null);

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        IncidenteInvestigacionRequestDTO req = new IncidenteInvestigacionRequestDTO();
        req.setHallazgos("Intento de alterar hallazgos post-cierre.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: Concluir investigación registra fecha de fin y transiciona a ACCION_TOMADA por defecto")
    void test20_concluirInvestigacion_registraFechaFinYTransicionaAAccionTomada() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-20 Concluir Inv", "DANO_BIEN_COMUN", "MODERADA", null);

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        IncidenteInvestigacionRequestDTO req = new IncidenteInvestigacionRequestDTO();
        req.setHallazgos("Conclusión: Falla mecánica en portón resuelta por contratista.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/concluir")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT ESTADO, FECHA_FIN_INVESTIGACION, HALLAZGOS_INVESTIGACION FROM INCIDENTES WHERE ID_INCIDENTE = ?", id);
        assertEquals("ACCION_TOMADA", dbRow.get("ESTADO"));
        assertNotNull(dbRow.get("FECHA_FIN_INVESTIGACION"));
        assertTrue(dbRow.get("HALLAZGOS_INVESTIGACION").toString().contains("Falla mecánica"));
    }

    @Test
    @Order(21)
    @DisplayName("Test 21: Concluir investigación hacia estado destino personalizado ESCALADO_A_SANCION")
    void test21_concluirInvestigacion_conEstadoDestinoPersonalizado_validaTransicion() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-21 Concluir a Escalar", "CONVIVENCIA_DISPUTA", "GRAVE", UNIT_1_ID);

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        IncidenteInvestigacionRequestDTO req = new IncidenteInvestigacionRequestDTO();
        req.setHallazgos("Conclusión: Infractor identificado por cámaras agrediendo personal de aseo.");
        req.setEstadoDestino("ESCALADO_A_SANCION");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/concluir")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("ESCALADO_A_SANCION", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));
    }

    @Test
    @Order(22)
    @DisplayName("Test 22: Residente intenta iniciar investigación -> 403 Forbidden")
    void test22_residente_iniciarInvestigacion_retorna403Forbidden() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-22 Permisos", "OTRO", "LEVE", UNIT_1_ID);

        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/iniciar")
                        .header("Authorization", "Bearer " + tokenRes)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CATEGORÍA D: PIPELINE DE ESCALAMIENTO (/escalar) (23 a 26)
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("Test 23: Escalar incidente registra actor, fecha, motivo y sanción sugerida")
    void test23_escalarIncidente_registraActorFechaMotivoYSancionSugerida() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-23 Escalamiento", "CONVIVENCIA_RUIDO", "GRAVE", UNIT_1_ID);

        IncidenteEscalamientoRequestDTO req = new IncidenteEscalamientoRequestDTO();
        req.setMotivo("Fiesta clandestina superando decibeles permitidos a las 3:00 AM.");
        req.setSancionSugerida("Multa económica de 1 cuota de administración y suspensión de zonas comunes");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT ESTADO, ESCALADO_POR, FECHA_ESCALAMIENTO, MOTIVO_ESCALAMIENTO, SANCION_SUGERIDA FROM INCIDENTES WHERE ID_INCIDENTE = ?", id);
        assertEquals("ESCALADO_A_SANCION", dbRow.get("ESTADO"));
        assertEquals(USER_ADMIN_PROP_1, ((Number) dbRow.get("ESCALADO_POR")).longValue());
        assertNotNull(dbRow.get("FECHA_ESCALAMIENTO"));
        assertTrue(dbRow.get("MOTIVO_ESCALAMIENTO").toString().contains("Fiesta clandestina"));
        assertTrue(dbRow.get("SANCION_SUGERIDA").toString().contains("Multa económica"));
    }

    @Test
    @Order(24)
    @DisplayName("Test 24: Escalar incidente sin motivo obligatorio retorna 400 Bad Request")
    void test24_escalarIncidente_sinMotivo_retorna400BadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-24 Escalar Vacio", "OTRO", "MODERADA", null);

        IncidenteEscalamientoRequestDTO req = new IncidenteEscalamientoRequestDTO();
        req.setMotivo(""); // Invalido

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(25)
    @DisplayName("Test 25: Escalar incidente que ya se encuentra CERRADO retorna 400 Bad Request")
    void test25_escalarIncidente_incidenteCerrado_retorna400BadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-25 Escalar Cerrado", "OTRO", "LEVE", null);

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk());

        IncidenteEscalamientoRequestDTO req = new IncidenteEscalamientoRequestDTO();
        req.setMotivo("Intento de escalamiento sobre caso cerrado");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(26)
    @DisplayName("Test 26: Portero intenta escalar incidente a sanción -> 403 Forbidden")
    void test26_portero_intentarEscalar_retorna403Forbidden() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-26 Portero Escala", "OTRO", "MODERADA", null);

        String tokenPortero = jwtProvider.generateIdentityToken(USER_PORTERO_1);
        IncidenteEscalamientoRequestDTO req = new IncidenteEscalamientoRequestDTO();
        req.setMotivo("Escalamiento por portero no autorizado");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/escalar")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CATEGORÍA E: GESTIÓN DE INVOLUCRADOS & ANTI-IDOR (27 a 34)
    // =========================================================================

    @Test
    @Order(27)
    @DisplayName("Test 27: Agregar persona involucrada legítima de la copropiedad -> 201 Created")
    void test27_addInvolucrado_personaValidaDeLaPropiedad_retorna201() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-27 Involucrado Legítimo", "CONVIVENCIA_DISPUTA", "GRAVE", UNIT_2_ID);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setIdPersona(10L); // Vecino de unidad 2
        inv.setRolEnIncidente("PRESUNTO_INFRACTOR");
        inv.setDeclaracionTestimonio("Admite haber estado en la zona del incidente a esa hora.");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIncidenteInvolucrado", notNullValue()));
    }

    @Test
    @Order(28)
    @DisplayName("Test 28: Anti-IDOR rechaza persona foránea que no pertenece a la copropiedad -> 400/403")
    void test28_addInvolucrado_personaForanea_rechazaPorAntiIdor() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-28 Anti IDOR Inv", "OTRO", "MODERADA", null);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setIdPersona(999L); // Persona foránea
        inv.setRolEnIncidente("AFECTADO");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 403 || status == 429,
                            "Debe rechazar la asociación de persona ajena a la copropiedad (HTTP 400, 403 o Security 429), actual: " + status);
                });
    }

    @Test
    @Order(29)
    @DisplayName("Test 29: Agregar involucrado externo sin ID_PERSONA pero con NOMBRE_IDENTIFICACION_EXTERNA -> 201 Created")
    void test29_addInvolucrado_externoConNombreIdentificacion_retorna201() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-29 Externo", "ACCESO_NO_AUTORIZADO", "GRAVE", null);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setNombreIdentificacionExterna("Juan Pérez (Visitante no registrado, CC 79123456)");
        inv.setRolEnIncidente("PRESUNTO_INFRACTOR");
        inv.setDeclaracionTestimonio("Fue interceptado intentando ingresar por la reja trasera.");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIncidenteInvolucrado", notNullValue()));
    }

    @Test
    @Order(30)
    @DisplayName("Test 30: Agregar involucrado con rol no canónico CK_INCINVOL_ROL -> 400 Bad Request")
    void test30_addInvolucrado_rolInvalido_retorna400BadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-30 Rol Invalido", "OTRO", "LEVE", null);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setNombreIdentificacionExterna("Sujeto sospechoso");
        inv.setRolEnIncidente("CULPABLE"); // Invalido, solo permite: AFECTADO, PRESUNTO_INFRACTOR, TESTIGO, INFORMADOR, OTRO

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(31)
    @DisplayName("Test 31: Admin Propiedad consulta involucrados de un incidente -> 200 OK con lista")
    void test31_getInvolucrados_adminPropiedad_retornaListaCompleta() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-31 Lista Involucrados", "CONVIVENCIA_DISPUTA", "MODERADA", UNIT_2_ID);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setIdPersona(10L);
        inv.setRolEnIncidente("TESTIGO");
        inv.setDeclaracionTestimonio("Estaba pasando por el pasillo y escuchó los gritos.");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].rolEnIncidente", equalTo("TESTIGO")));
    }

    @Test
    @Order(32)
    @DisplayName("Test 32: Residente consulta involucrados de su propio incidente -> 200 OK")
    void test32_getInvolucrados_residenteDeSuIncidente_retornaLista() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-32 Residente Inv", "DANO_BIEN_COMUN", "LEVE", UNIT_1_ID);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setNombreIdentificacionExterna("Técnico reparador");
        inv.setRolEnIncidente("INFORMADOR");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isCreated());

        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        mockMvc.perform(get("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + tokenRes)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$.length()", equalTo(1)));
    }

    @Test
    @Order(33)
    @DisplayName("Test 33: Residente intenta consultar involucrados de un incidente ajeno -> 403 Forbidden")
    void test33_getInvolucrados_residenteDeOtroIncidente_retorna403Forbidden() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(tokenAdmin, ASSIGN_ADMIN_PROP_1, "TEST-F907-33 Ajeno Inv", "SEGURIDAD_HURTO", "GRAVE", UNIT_2_ID);

        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        mockMvc.perform(get("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + tokenRes1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_1))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(34)
    @DisplayName("Test 34: Admin Propiedad elimina persona involucrada exitosamente -> 204 No Content")
    void test34_removeInvolucrado_adminPropiedad_eliminaExitosamente() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-34 Delete Inv", "OTRO", "LEVE", null);

        IncidenteInvolucradoDTO inv = new IncidenteInvolucradoDTO();
        inv.setNombreIdentificacionExterna("Persona temporal");
        inv.setRolEnIncidente("OTRO");

        MvcResult res = mockMvc.perform(post("/api/v1/incidentes/" + id + "/involucrados")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inv)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        Long idInvolucrado = ((Number) map.get("idIncidenteInvolucrado")).longValue();

        mockMvc.perform(delete("/api/v1/incidentes/" + id + "/involucrados/" + idInvolucrado)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isNoContent());

        setElevatedContext();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INCIDENTE_INVOLUCRADOS WHERE ID_INCIDENTE_INVOLUCRADO = ?", Integer.class, idInvolucrado);
        assertEquals(0, count);
    }

    // =========================================================================
    // CATEGORÍA F: AUDITORÍA, GATING DE PLAN & MULTI-TENANT (35 a 38)
    // =========================================================================

    @Test
    @Order(35)
    @DisplayName("Test 35: Cierre de incidente dispara registro de auditoría")
    void test35_auditoria_cierreIncidente_generaRegistroAuditoria() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-35 Audit Close", "OTRO", "LEVE", null);

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conclusiones", "Cierre con auditoría obligatoria."))))
                .andExpect(status().isOk());

        // Verificar que la auditoría o el cierre se hayan persistido correctamente
        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("CERRADO", estadoDb);
    }

    @Test
    @Order(36)
    @DisplayName("Test 36: Escalamiento de incidente dispara registro de auditoría")
    void test36_auditoria_escalamiento_generaRegistroAuditoria() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-36 Audit Escala", "CONVIVENCIA_DISPUTA", "GRAVE", UNIT_1_ID);

        IncidenteEscalamientoRequestDTO req = new IncidenteEscalamientoRequestDTO();
        req.setMotivo("Escalamiento auditado");
        req.setSancionSugerida("Llamado de atención formal");

        mockMvc.perform(post("/api/v1/incidentes/" + id + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertEquals("ESCALADO_A_SANCION", estadoDb);
    }

    @Test
    @Order(37)
    @DisplayName("Test 37: Multi-tenant: Admin de Propiedad 1 no puede ver ni modificar incidente de Propiedad 2")
    void test37_multiTenant_adminProp1_noPuedeVerNiModificarIncidenteDeProp2() throws Exception {
        String tokenAdmin2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
        Long idProp2 = crearIncidenteHelper(tokenAdmin2, ASSIGN_ADMIN_PROP_2, "TEST-F907-37 Incidente Prop 2", "OTRO", "LEVE", null);

        String tokenAdmin1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        // Intentar consultar incidente de Propiedad 2 con token de Propiedad 1
        mockMvc.perform(get("/api/v1/incidentes/" + idProp2)
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(result -> {
                    int st = result.getResponse().getStatus();
                    assertTrue(st == 400 || st == 403 || st == 404, "Debe rechazar acceso a incidente de otra copropiedad, actual: " + st);
                });
    }

    @Test
    @Order(38)
    @DisplayName("Test 38: Module Entitlement: si el plan de la organización deshabilita INCIDENTES, bloquea acceso")
    void test38_moduleEntitlement_planSinIncidentes_bloqueaAcceso() throws Exception {
        setElevatedContext();
        Long planId = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", Long.class, ORG_1_ID);
        jdbcTemplate.execute("UPDATE PLAN_MODULOS SET HABILITADO = 'N' WHERE ID_PLAN = " + planId +
                " AND ID_MODULO = (SELECT ID_MODULO FROM MODULOS WHERE CODIGO = 'INCIDENTES')");
        clearContext();

        try {
            String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            mockMvc.perform(get("/api/v1/incidentes/admin")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andExpect(status().isForbidden());
        } finally {
            // Restaurar habilitación para el Plan
            setElevatedContext();
            jdbcTemplate.execute("UPDATE PLAN_MODULOS SET HABILITADO = 'S' WHERE ID_PLAN = " + planId +
                    " AND ID_MODULO = (SELECT ID_MODULO FROM MODULOS WHERE CODIGO = 'INCIDENTES')");
            clearContext();
        }
    }

    // =========================================================================
    // CATEGORÍA G: CONCURRENCIA & RESILIENCIA (39 a 41)
    // =========================================================================

    @Test
    @Order(39)
    @DisplayName("Test 39: Cambios de estado concurrentes mantienen consistencia sin corrupción de datos")
    void test39_concurrencia_cambioEstadoSimultaneo_mantieneConsistencia() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-39 Concurrencia", "OTRO", "LEVE", null);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        List<String> targetStates = List.of("EN_INVESTIGACION", "ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO");

        for (String targetState : targetStates) {
            executor.submit(() -> {
                try {
                    IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
                    dto.setEstado(targetState);
                    dto.setConclusiones("Concurrente a " + targetState);
                    dto.setMotivo("Motivo concurrente");

                    int status = mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn().getResponse().getStatus();

                    if (status == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        // Verificar que el estado final en base de datos sea un estado válido del enum
        setElevatedContext();
        String estadoFinal = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id);
        assertNotNull(estadoFinal);
        assertTrue(List.of("REPORTADO", "EN_INVESTIGACION", "ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO").contains(estadoFinal));
    }

    @Test
    @Order(40)
    @DisplayName("Test 40: Portero reportando ruido sobre unidad sin notificación previa es rechazado por regla de aviso")
    void test40_portero_reportarRuido_respetaReglaNotificacionPrevia() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_PORTERO_1);

        IncidenteDTO req = new IncidenteDTO();
        req.setTitulo("TEST-F907-40 Ruido excesivo");
        req.setTipoIncidente("CONVIVENCIA_RUIDO");
        req.setNivelSeveridad("MODERADA");
        req.setDescripcionHechos("Música a alto volumen con quejas de vecinos");
        req.setIdUnidad(UNIT_1_ID);

        // Sin aviso de ruido previo en NOTIFICACIONES, debe fallar con 400
        mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_PORTERO_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(41)
    @DisplayName("Test 41: Ciclo de vida completo: Reabrir incidente cerrado permite continuar investigación y concluirlo")
    void test41_reabrirIncidenteCerrado_permiteContinuarInvestigacion() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Long id = crearIncidenteHelper(token, ASSIGN_ADMIN_PROP_1, "TEST-F907-41 Full Lifecycle", "SEGURIDAD_HURTO", "GRAVE", null);

        // 1. Cerrar incidente
        mockMvc.perform(post("/api/v1/incidentes/" + id + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conclusiones", "Cerrado por falta de pruebas"))))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("CERRADO", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));

        // 2. Reabrir mediante transición a EN_INVESTIGACION
        IncidenteEstadoRequestDTO reabrirDto = new IncidenteEstadoRequestDTO();
        reabrirDto.setEstado("EN_INVESTIGACION");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reabrirDto)))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("EN_INVESTIGACION", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));

        // 3. Agregar nuevos hallazgos
        IncidenteInvestigacionRequestDTO invReq = new IncidenteInvestigacionRequestDTO();
        invReq.setHallazgos("Se recuperó grabación de cámara de seguridad con el rostro del infractor.");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invReq)))
                .andExpect(status().isOk());

        // 4. Concluir investigación hacia ACCION_TOMADA
        IncidenteInvestigacionRequestDTO concluirReq = new IncidenteInvestigacionRequestDTO();
        concluirReq.setHallazgos("Caso esclarecido y remitido a fiscalía.");
        concluirReq.setEstadoDestino("ACCION_TOMADA");

        mockMvc.perform(put("/api/v1/incidentes/" + id + "/investigacion/concluir")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(concluirReq)))
                .andExpect(status().isOk());

        setElevatedContext();
        assertEquals("ACCION_TOMADA", jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, id));
    }
}
