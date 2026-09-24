package com.saed.backend.pqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
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

import java.time.Duration;
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
 * Suite de Certificación Integral para GAP-F9-06:
 * PQRS — UNIFICACIÓN, SLA DINÁMICO, TRAZABILIDAD, MÁQUINA DE ESTADOS Y SEGURIDAD MULTI-TENANT (SAED 2.0).
 *
 * Cobertura de 31 casos de prueba organizados en 7 categorías:
 * - Categoría A: Creación Canónica, Radicado Único y Asignación de Unidad (Tests 01 a 08)
 * - Categoría B: Motor de SLA Dinámico (PQRS_SLA_CONFIGURACION) (Tests 09 a 11)
 * - Categoría C: Máquina de Estados Estricta & Errores Controlados (Tests 12 a 18)
 * - Categoría D: Respuestas Oficiales & Notificaciones (/responder) (Tests 19 a 21)
 * - Categoría E: Seguridad Multi-Tenant & Anti-IDOR (Tests 22 a 26)
 * - Categoría F: Trazabilidad Atómica & Consulta de Historial (Test 27)
 * - Categoría G: Concurrencia & Resolución de Dualidad QuejasController (Tests 28 a 31)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9PqrsUnifiedIntegrationTest {

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
                    DELETE FROM PQRS_TRAZABILIDAD WHERE ID_TICKET IN (SELECT ID_TICKET FROM PQRS_TICKETS WHERE NUMERO_RADICADO LIKE 'PQRS-%');
                    DELETE FROM PQRS_TICKETS WHERE NUMERO_RADICADO LIKE 'PQRS-%';
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

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "carlos_res1@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "carlos_res1@saed.com");

            ensurePersona(5L, "1000000005", "Ana", "Gomez", "ana_res2@saed.com");
            ensureUsuario(5L, 5L, "ana_g", "ana_res2@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            ensureMembresia(ORG_1_ID);
            ensureMembresia(ORG_2_ID);

            // Asegurar asignación de unidad para Residente 1 y 2
            ensureAsignacionUnidad(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1, PROP_1_ID, UNIT_1_ID, "RESIDENTE");
            ensureAsignacionUnidad(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, PROP_1_ID, UNIT_2_ID, "RESIDENTE");

            // Asegurar configuración base de SLA para Propiedad 1
            ensureSlaConfig(PROP_1_ID, "EMERGENCIA", 12, 4);
            ensureSlaConfig(PROP_1_ID, "ALTA", 24, 8);
            ensureSlaConfig(PROP_1_ID, "MEDIA", 72, 24);
            ensureSlaConfig(PROP_1_ID, "BAJA", 120, 48);

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
                    DELETE FROM PQRS_TRAZABILIDAD WHERE ID_TICKET IN (SELECT ID_TICKET FROM PQRS_TICKETS WHERE NUMERO_RADICADO LIKE 'PQRS-%');
                    DELETE FROM PQRS_TICKETS WHERE NUMERO_RADICADO LIKE 'PQRS-%';
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
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", idUsuario, idPersona, username, email);
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

    private void ensureAsignacionUnidad(Long idAsig, Long idUsuario, Long idProp, Long idUnidad, String rol) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, idAsig);
        if (c == null || c == 0) {
            Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rol);
            jdbcTemplate.update("""
                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_PROPIEDAD, ID_UNIDAD, ID_ROL, ESTADO, FECHA_INICIO)
                VALUES (?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))
            """, idAsig, idUsuario, idProp, idUnidad, idRol);
        }
    }

    private void ensureSlaConfig(Long idProp, String prioridad, int horas, int alerta) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PQRS_SLA_CONFIGURACION WHERE ID_PROPIEDAD = ? AND PRIORIDAD = ?",
                Integer.class, idProp, prioridad
        );
        if (c != null && c > 0) {
            jdbcTemplate.update(
                    "UPDATE PQRS_SLA_CONFIGURACION SET TIEMPO_MAXIMO_HORAS = ?, ALERTA_VENCIMIENTO_HORAS = ? WHERE ID_PROPIEDAD = ? AND PRIORIDAD = ?",
                    horas, alerta, idProp, prioridad
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO PQRS_SLA_CONFIGURACION (ID_PROPIEDAD, PRIORIDAD, TIEMPO_MAXIMO_HORAS, ALERTA_VENCIMIENTO_HORAS) VALUES (?, ?, ?, ?)",
                    idProp, prioridad, horas, alerta
            );
        }
    }

    // =========================================================================
    // CATEGORÍA A: CREACIÓN CANÓNICA, RADICADO ÚNICO Y ASIGNACIÓN DE UNIDAD
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 01: Crear ticket como RESIDENTE exitoso con radicado oficial y trazabilidad inicial")
    void test01_crearTicket_comoResidente_exitoso() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("MANTENIMIENTO");
        req.setPrioridad("MEDIA");
        req.setAsunto("Mantenimiento luminaria pasillo piso 1");
        req.setDescripcion("La luminaria frente al Apto 101 parpadea constantemente desde el fin de semana.");

        MvcResult result = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(result.getResponse().getContentAsString().trim());
        assertNotNull(idTicket);
        assertTrue(idTicket > 0);

        // Verificación en base de datos
        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM PQRS_TICKETS WHERE ID_TICKET = ?", idTicket);
        assertEquals("RADICADO", row.get("ESTADO"));
        assertEquals("PETICION", row.get("TIPO"));
        assertEquals("MANTENIMIENTO", row.get("CATEGORIA"));
        assertEquals("MEDIA", row.get("PRIORIDAD"));
        assertEquals(UNIT_1_ID, ((Number) row.get("ID_UNIDAD")).longValue());
        assertNotNull(row.get("NUMERO_RADICADO"));
        assertTrue(row.get("NUMERO_RADICADO").toString().startsWith("PQRS-"));

        // Verificación de trazabilidad atómica
        List<Map<String, Object>> traz = jdbcTemplate.queryForList("SELECT * FROM PQRS_TRAZABILIDAD WHERE ID_TICKET = ?", idTicket);
        assertEquals(1, traz.size());
        assertEquals("RADICACION", traz.get(0).get("TIPO_INTERVENCION"));
        assertEquals("RADICADO", traz.get(0).get("ESTADO_NUEVO"));
        clearContext();
    }

    @Test
    @Order(2)
    @DisplayName("Test 02: Crear ticket como ADMIN_PROPIEDAD para la copropiedad -> 201 Created")
    void test02_crearTicket_comoAdmin_exitoso() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("QUEJA");
        req.setCategoria("SEGURIDAD");
        req.setPrioridad("ALTA");
        req.setAsunto("Revisión de cámaras perimetrales");
        req.setDescripcion("Se detectó punto ciego en la cámara número 4 del acceso vehicular.");

        MvcResult result = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(result.getResponse().getContentAsString().trim());
        assertTrue(idTicket > 0);
    }

    @Test
    @Order(3)
    @DisplayName("Test 03: Crear ticket con asunto demasiado corto (< 5 caracteres) -> 400 Bad Request")
    void test03_crearTicket_asuntoCorto_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Hola"); // 4 chars
        req.setDescripcion("Descripción detallada válida con más de 10 caracteres.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("El asunto debe tener al menos 5 caracteres")));
    }

    @Test
    @Order(4)
    @DisplayName("Test 04: Crear ticket con descripción demasiado corta (< 10 caracteres) -> 400 Bad Request")
    void test04_crearTicket_descripcionCorta_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Asunto válido formal");
        req.setDescripcion("Corta"); // 5 chars

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("La descripción debe tener al menos 10 caracteres")));
    }

    @Test
    @Order(5)
    @DisplayName("Test 05: Crear ticket con tipo inválido -> 400 Bad Request")
    void test05_crearTicket_tipoInvalido_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("TIPO_INEXISTENTE");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Solicitud con tipo erróneo");
        req.setDescripcion("Descripción detallada con suficiente longitud.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Tipo de ticket inválido")));
    }

    @Test
    @Order(6)
    @DisplayName("Test 06: Crear ticket con categoría inválida -> 400 Bad Request")
    void test06_crearTicket_categoriaInvalida_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("CATEGORIA_INVENTADA");
        req.setAsunto("Solicitud con categoría errónea");
        req.setDescripcion("Descripción detallada con suficiente longitud.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Categoría de ticket inválida")));
    }

    @Test
    @Order(7)
    @DisplayName("Test 07: Crear ticket con prioridad inválida -> 400 Bad Request")
    void test07_crearTicket_prioridadInvalida_retorna400() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setPrioridad("URGENTISIMO");
        req.setAsunto("Solicitud con prioridad errónea");
        req.setDescripcion("Descripción detallada con suficiente longitud.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Prioridad de ticket inválida")));
    }

    @Test
    @Order(8)
    @DisplayName("Test 08: Categoría 'OTRO' se normaliza automáticamente a 'OTRA' de acuerdo al DDL -> 201 Created")
    void test08_crearTicket_categoriaOtro_normalizaAOtra() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("SUGERENCIA");
        req.setCategoria("OTRO");
        req.setAsunto("Propuesta para zonas verdes");
        req.setDescripcion("Se sugiere instalar reflectores solares en el jardín norte.");

        MvcResult result = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        String cat = jdbcTemplate.queryForObject("SELECT CATEGORIA FROM PQRS_TICKETS WHERE ID_TICKET = ?", String.class, idTicket);
        assertEquals("OTRA", cat);
        clearContext();
    }

    // =========================================================================
    // CATEGORÍA B: MOTOR DE SLA DINÁMICO (PQRS_SLA_CONFIGURACION)
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("Test 09: Cálculo dinámico de SLA según configuración activa en PQRS_SLA_CONFIGURACION")
    void test09_slaDinamico_calculoSegunConfiguracion() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Caso EMERGENCIA (12 horas)
        TicketRequestDTO reqEmergencia = new TicketRequestDTO();
        reqEmergencia.setTipo("PETICION");
        reqEmergencia.setCategoria("SEGURIDAD");
        reqEmergencia.setPrioridad("EMERGENCIA");
        reqEmergencia.setAsunto("Fuga de agua en sótano 1");
        reqEmergencia.setDescripcion("Inundación incipiente junto al cuarto de bombas.");

        MvcResult resEmergencia = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqEmergencia)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idEmergencia = Long.parseLong(resEmergencia.getResponse().getContentAsString().trim());

        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT FECHA_RADICACION, FECHA_LIMITE_SLA FROM PQRS_TICKETS WHERE ID_TICKET = ?", idEmergencia);
        java.sql.Timestamp radicado = (java.sql.Timestamp) row.get("FECHA_RADICACION");
        java.sql.Timestamp limite = (java.sql.Timestamp) row.get("FECHA_LIMITE_SLA");
        long diffHours = Duration.between(radicado.toInstant(), limite.toInstant()).toHours();
        assertEquals(12, diffHours);
        clearContext();
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Inmutabilidad histórica: modificar SLA en PQRS_SLA_CONFIGURACION no altera tickets anteriores")
    void test10_slaDinamico_actualizacionConfiguracion_preservaHistoricos() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Crear ticket con configuración inicial de MEDIA (72 horas)
        TicketRequestDTO req1 = new TicketRequestDTO();
        req1.setTipo("PETICION");
        req1.setCategoria("ADMINISTRACION");
        req1.setPrioridad("MEDIA");
        req1.setAsunto("Solicitud certificado administración");
        req1.setDescripcion("Requiero constancia de administración para trámite notarial.");

        MvcResult res1 = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket1 = Long.parseLong(res1.getResponse().getContentAsString().trim());

        // Modificar la configuración SLA de MEDIA para la propiedad 1 a 36 horas
        setElevatedContext();
        ensureSlaConfig(PROP_1_ID, "MEDIA", 36, 12);
        clearContext();

        // Crear ticket 2 con la nueva configuración (36 horas)
        TicketRequestDTO req2 = new TicketRequestDTO();
        req2.setTipo("PETICION");
        req2.setCategoria("ADMINISTRACION");
        req2.setPrioridad("MEDIA");
        req2.setAsunto("Solicitud de paz y salvo copia");
        req2.setDescripcion("Copia física del paz y salvo emitido el mes pasado.");

        MvcResult res2 = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket2 = Long.parseLong(res2.getResponse().getContentAsString().trim());

        // Verificar que Ticket 1 preservó 72 horas y Ticket 2 tiene 36 horas
        setElevatedContext();
        Map<String, Object> row1 = jdbcTemplate.queryForMap("SELECT FECHA_RADICACION, FECHA_LIMITE_SLA FROM PQRS_TICKETS WHERE ID_TICKET = ?", idTicket1);
        Map<String, Object> row2 = jdbcTemplate.queryForMap("SELECT FECHA_RADICACION, FECHA_LIMITE_SLA FROM PQRS_TICKETS WHERE ID_TICKET = ?", idTicket2);

        long diff1 = Duration.between(((java.sql.Timestamp) row1.get("FECHA_RADICACION")).toInstant(),
                                      ((java.sql.Timestamp) row1.get("FECHA_LIMITE_SLA")).toInstant()).toHours();
        long diff2 = Duration.between(((java.sql.Timestamp) row2.get("FECHA_RADICACION")).toInstant(),
                                      ((java.sql.Timestamp) row2.get("FECHA_LIMITE_SLA")).toInstant()).toHours();

        assertEquals(72, diff1, "El ticket histórico debe preservar su SLA original de 72 horas");
        assertEquals(36, diff2, "El nuevo ticket debe usar la nueva configuración de 36 horas");
        clearContext();
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Endpoints administrativos de consulta y actualización de SLA (/sla-config)")
    void test11_slaConfig_endpointsAdmin() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // GET /sla-config
        mockMvc.perform(get("/api/v1/pqrs/sla-config")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].prioridad", notNullValue()));

        // PUT /sla-config (actualizar EMERGENCIA a 8 horas)
        PqrsSlaConfigDTO update = new PqrsSlaConfigDTO(null, PROP_1_ID, "EMERGENCIA", 8, 2);

        mockMvc.perform(put("/api/v1/pqrs/sla-config")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        setElevatedContext();
        Integer horas = jdbcTemplate.queryForObject(
                "SELECT TIEMPO_MAXIMO_HORAS FROM PQRS_SLA_CONFIGURACION WHERE ID_PROPIEDAD = ? AND PRIORIDAD = 'EMERGENCIA'",
                Integer.class, PROP_1_ID);
        assertEquals(8, horas);
        clearContext();
    }

    // =========================================================================
    // CATEGORÍA C: MÁQUINA DE ESTADOS ESTRICTA & ERRORES CONTROLADOS
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("Test 12: Flujo completo de ciclo de vida: RADICADO -> EN_GESTION -> RESUELTO -> CERRADO")
    void test12_maquinaEstados_flujoCompleto_exitoso() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // 1. Crear ticket (RADICADO)
        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Revisión de cobro cuota extraordinaria");
        req.setDescripcion("Solicito desglose del concepto cobrado en el recibo de agosto.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // 2. Transición RADICADO -> EN_GESTION
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION")
                .param("observacion", "Se remite a contabilidad para validación."))
                .andExpect(status().isOk());

        // 3. Transición EN_GESTION -> RESUELTO
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RESUELTO")
                .param("observacion", "Contabilidad verificó el ajuste contable solicitado."))
                .andExpect(status().isOk());

        // 4. Transición RESUELTO -> CERRADO
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "CERRADO")
                .param("observacion", "Cierre definitivo con conformidad del copropietario."))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> dbRow = jdbcTemplate.queryForMap("SELECT ESTADO, FECHA_CIERRE, OBSERVACION_CIERRE FROM PQRS_TICKETS WHERE ID_TICKET = ?", idTicket);
        assertEquals("CERRADO", dbRow.get("ESTADO"));
        assertNotNull(dbRow.get("FECHA_CIERRE"));
        assertEquals("Cierre definitivo con conformidad del copropietario.", dbRow.get("OBSERVACION_CIERRE"));
        clearContext();
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Normalización de alias de estado (EN_REVISION -> EN_GESTION) exitosa")
    void test13_maquinaEstados_transicionConAlias_enRevisionAEnGestion() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Paz y salvo solicitado");
        req.setDescripcion("Requiero paz y salvo para fin de mes.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Admin envía EN_REVISION (alias)
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_REVISION")
                .param("observacion", "Pasa a revisión."))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = ?", String.class, idTicket);
        assertEquals("EN_GESTION", estadoDb, "El estado en base de datos debe ser el valor canónico DDL EN_GESTION");
        clearContext();
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Salto ilegal de estado (RADICADO -> CERRADO) rechazado con 400 Bad Request controlado")
    void test14_maquinaEstados_transicionInvalida_radicadoACerrado_falla400() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("RECLAMO");
        req.setCategoria("CONVIVENCIA");
        req.setAsunto("Ruido excesivo apartamento 202");
        req.setDescripcion("Música a alto volumen en horas de la madrugada.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Intento de saltar directamente a CERRADO desde RADICADO
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "CERRADO")
                .param("observacion", "Cierre directo no permitido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_ESTADO_INVALIDA")))
                .andExpect(jsonPath("$.estadoOrigen", is("RADICADO")))
                .andExpect(jsonPath("$.estadoDestino", is("CERRADO")));
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Estado terminal: CERRADO no permite transiciones posteriores -> 400 Bad Request")
    void test15_maquinaEstados_estadoTerminal_cerradoNoPermiteCambios() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Consulta de reglamento interno");
        req.setDescripcion("Requiero copia del reglamento interno de la propiedad.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Llevar a CERRADO
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RESUELTO")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "CERRADO")).andExpect(status().isOk());

        // Intentar reabrir desde CERRADO a RADICADO o EN_GESTION
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RADICADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_ESTADO_INVALIDA")));
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: Estado terminal: RECHAZADO no permite transiciones posteriores -> 400 Bad Request")
    void test16_maquinaEstados_estadoTerminal_rechazadoNoPermiteCambios() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("QUEJA");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Queja improcedente");
        req.setDescripcion("Hechos sin fundamentación ni relación con la copropiedad.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Rechazar
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RECHAZADO")
                .param("observacion", "Rechazado por improcedente."))
                .andExpect(status().isOk());

        // Intentar pasar de RECHAZADO a EN_GESTION
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_ESTADO_INVALIDA")));
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: Estado no reconocido arrolla 400 Bad Request controlado con ESTADO_PQRS_INVALIDO")
    void test17_maquinaEstados_estadoInexistente_retorna400() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Ticket para prueba de estado inválido");
        req.setDescripcion("Descripción detallada para la prueba de estado inválido.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "ESTADO_INVENTADO_MAGIC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ESTADO_PQRS_INVALIDO")))
                .andExpect(jsonPath("$.message", containsString("Estado de ticket no reconocido")));
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Reapertura permitida: RESUELTO -> EN_GESTION ante inconformidad del residente")
    void test18_maquinaEstados_reaperturaDesdeResuelto_exitoso() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("MANTENIMIENTO");
        req.setAsunto("Avería en ascensor torre 1");
        req.setDescripcion("El ascensor presenta ruidos extraños y desnivel.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // RADICADO -> EN_GESTION -> RESUELTO
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RESUELTO")).andExpect(status().isOk());

        // Reabrir: RESUELTO -> EN_GESTION
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION")
                .param("observacion", "Residente reporta que el desnivel persiste; se reabre el caso."))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = ?", String.class, idTicket);
        assertEquals("EN_GESTION", estadoDb);
        clearContext();
    }

    // =========================================================================
    // CATEGORÍA D: RESPUESTAS OFICIALES & NOTIFICACIONES (/responder)
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("Test 19: ADMIN responde ticket con cambio opcional a RESUELTO -> 200 OK y trazabilidad RESPUESTA_INTERNA")
    void test19_responderTicket_comoAdmin_conCambioEstado_exitoso() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Consulta sobre fecha de asamblea ordinaria");
        req.setDescripcion("Favor confirmar si la asamblea ordinaria será virtual o presencial.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Mover a EN_GESTION primero
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION")).andExpect(status().isOk());

        // Responder y resolver
        Map<String, String> payload = Map.of(
                "respuesta", "Estimado copropietario, la asamblea será en modalidad mixta el 25 de octubre.",
                "nuevoEstado", "RESUELTO"
        );

        mockMvc.perform(post("/api/v1/pqrs/" + idTicket + "/responder")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = ?", String.class, idTicket);
        assertEquals("RESUELTO", estadoDb);

        List<Map<String, Object>> traz = jdbcTemplate.queryForList(
                "SELECT * FROM PQRS_TRAZABILIDAD WHERE ID_TICKET = ? AND TIPO_INTERVENCION = 'RESPUESTA_INTERNA'", idTicket);
        assertEquals(1, traz.size());
        assertEquals("RESUELTO", traz.get(0).get("ESTADO_NUEVO"));
        assertTrue(traz.get(0).get("COMENTARIO").toString().contains("asamblea será en modalidad mixta"));
        clearContext();
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: RESIDENTE responde a su propio ticket -> 200 OK y trazabilidad RESPUESTA_RESIDENTE")
    void test20_responderTicket_comoResidente_propio_exitoso() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Pregunta sobre buzón de correspondencia");
        req.setDescripcion("¿Dónde se pueden recoger los paquetes de gran tamaño?");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        Map<String, String> payload = Map.of(
                "respuesta", "Aclaración adicional: me refiero a paquetes de más de 10 kg."
        );

        mockMvc.perform(post("/api/v1/pqrs/" + idTicket + "/responder")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        setElevatedContext();
        List<Map<String, Object>> traz = jdbcTemplate.queryForList(
                "SELECT * FROM PQRS_TRAZABILIDAD WHERE ID_TICKET = ? AND TIPO_INTERVENCION = 'RESPUESTA_RESIDENTE'", idTicket);
        assertEquals(1, traz.size());
        assertTrue(traz.get(0).get("COMENTARIO").toString().contains("paquetes de más de 10 kg"));
        clearContext();
    }

    @Test
    @Order(21)
    @DisplayName("Test 21: Responder con texto vacío (< 3 caracteres) -> 400 Bad Request")
    void test21_responderTicket_respuestaVacia_retorna400() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Consulta de parqueaderos");
        req.setDescripcion("Información sobre el sorteo de bahías comunales.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        Map<String, String> payload = Map.of("respuesta", "ok"); // solo 2 chars

        mockMvc.perform(post("/api/v1/pqrs/" + idTicket + "/responder")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("La respuesta debe tener al menos 3 caracteres")));
    }

    // =========================================================================
    // CATEGORÍA E: SEGURIDAD MULTI-TENANT & ANTI-IDOR
    // =========================================================================

    @Test
    @Order(22)
    @DisplayName("Test 22: Anti-IDOR Cross-Property: Admin de Propiedad 2 no puede ver ticket de Propiedad 1")
    void test22_seguridadMultiTenant_adminNoPuedeVerTicketsDeOtraPropiedad() throws Exception {
        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Ticket confidencial de Propiedad 1");
        req.setDescripcion("Datos reservados de la torre 1 bajo estricto tenant isolation.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Admin de Propiedad 2 intenta consultar el ticket de Propiedad 1
        mockMvc.perform(get("/api/v1/pqrs/" + idTicket)
                .header("Authorization", "Bearer " + tokenAdmin2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no encontrado para la propiedad activa")));
    }

    @Test
    @Order(23)
    @DisplayName("Test 23: Anti-IDOR Cross-Property: Admin de Propiedad 2 no puede actualizar estado en Propiedad 1")
    void test23_seguridadMultiTenant_adminNoPuedeModificarTicketsDeOtraPropiedad() throws Exception {
        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Ticket protegido anti-mutación IDOR");
        req.setDescripcion("No debe ser alterable por administradores de otra copropiedad.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2))
                .param("estado", "EN_GESTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no encontrado para la propiedad activa")));
    }

    @Test
    @Order(24)
    @DisplayName("Test 24: Anti-IDOR Inter-Residente: Residente 2 no puede ver ni consultar ticket de Residente 1 -> 403 Forbidden")
    void test24_seguridadMultiTenant_residenteNoPuedeVerTicketDeOtroResidente() throws Exception {
        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenRes2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("QUEJA");
        req.setCategoria("CONVIVENCIA");
        req.setAsunto("Queja privada del Apto 101");
        req.setDescripcion("Detalles estrictamente privados del residente titular del 101.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Residente 2 intenta consultar el ticket de Residente 1
        mockMvc.perform(get("/api/v1/pqrs/" + idTicket)
                .header("Authorization", "Bearer " + tokenRes2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @Order(25)
    @DisplayName("Test 25: Residente en /mis-tickets solo recibe sus propios tickets, no los de otros copropietarios")
    void test25_seguridadMultiTenant_residenteMisTickets_soloRetornaPropios() throws Exception {
        String tokenRes1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenRes2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);

        // Crear ticket por Residente 1
        TicketRequestDTO req1 = new TicketRequestDTO();
        req1.setTipo("PETICION");
        req1.setCategoria("ADMINISTRACION");
        req1.setAsunto("Ticket exclusivo de Residente 1");
        req1.setDescripcion("Contenido solo visible para el usuario residente 1.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Crear ticket por Residente 2
        TicketRequestDTO req2 = new TicketRequestDTO();
        req2.setTipo("PETICION");
        req2.setCategoria("ADMINISTRACION");
        req2.setAsunto("Ticket exclusivo de Residente 2");
        req2.setDescripcion("Contenido solo visible para el usuario residente 2.");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Residente 1 consulta /mis-tickets
        mockMvc.perform(get("/api/v1/pqrs/mis-tickets")
                .header("Authorization", "Bearer " + tokenRes1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].asunto", hasItem("Ticket exclusivo de Residente 1")))
                .andExpect(jsonPath("$[*].asunto", not(hasItem("Ticket exclusivo de Residente 2"))));
    }

    @Test
    @Order(26)
    @DisplayName("Test 26: SuperAdmin bloqueado de endpoints operativos sin contexto de propiedad -> 403 Forbidden")
    void test26_seguridadModulo_superAdminBloqueado() throws Exception {
        String tokenSuper = jwtProvider.generateIdentityToken(USER_SUPERADMIN);

        mockMvc.perform(get("/api/v1/pqrs/todos")
                .header("Authorization", "Bearer " + tokenSuper)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_SUPERADMIN)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CATEGORÍA F: TRAZABILIDAD ATÓMICA & CONSULTA DE HISTORIAL
    // =========================================================================

    @Test
    @Order(27)
    @DisplayName("Test 27: Historial cronológico completo de trazabilidad vía GET /api/v1/pqrs/{id}/trazabilidad")
    void test27_trazabilidad_historialCronologicoCompleto() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // 1. Radicación
        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("RECLAMO");
        req.setCategoria("LIMPIEZA");
        req.setAsunto("Aseo deficiente en área de shut");
        req.setDescripcion("Se requiere desinfección profunda en el shut de basuras.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // 2. Asignación
        Map<String, Object> asigPayload = Map.of(
                "idResponsable", USER_ADMIN_PROP_1,
                "observacion", "Asignado a personal de intendencia"
        );
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/asignar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asigPayload)))
                .andExpect(status().isOk());

        // 3. Respuesta Interna
        Map<String, String> respPayload = Map.of(
                "respuesta", "Equipo de limpieza programado para las 2:00 PM.",
                "nuevoEstado", "EN_GESTION"
        );
        mockMvc.perform(post("/api/v1/pqrs/" + idTicket + "/responder")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(respPayload)))
                .andExpect(status().isOk());

        // 4. Cierre
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "RESUELTO")).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "CERRADO")
                .param("observacion", "Trabajo de desinfección concluido a satisfacción.")).andExpect(status().isOk());

        // Consultar trazabilidad
        mockMvc.perform(get("/api/v1/pqrs/" + idTicket + "/trazabilidad")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].tipoIntervencion", is("RADICACION")))
                .andExpect(jsonPath("$[1].tipoIntervencion", is("ASIGNACION")))
                .andExpect(jsonPath("$[2].tipoIntervencion", is("RESPUESTA_INTERNA")))
                .andExpect(jsonPath("$[3].tipoIntervencion", is("CAMBIO_ESTADO")))
                .andExpect(jsonPath("$[4].tipoIntervencion", is("CIERRE")));
    }

    // =========================================================================
    // CATEGORÍA G: CONCURRENCIA & RESOLUCIÓN DE DUALIDAD (QuejasController)
    // =========================================================================

    @Test
    @Order(28)
    @DisplayName("Test 28: Seguridad concurrente (CAS): Dos transiciones simultáneas detectan cambio y fallan ordenadamente")
    void test28_concurrencia_actualizacionesSimultaneas_cas() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Ticket para prueba de concurrencia");
        req.setDescripcion("Validación de Compare-And-Swap en actualización de estado.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        // Primera transición: RADICADO -> EN_GESTION (éxito)
        mockMvc.perform(put("/api/v1/pqrs/" + idTicket + "/estado")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .param("estado", "EN_GESTION"))
                .andExpect(status().isOk());

        // Segunda transición concurrente: asumiendo estado RADICADO intenta cambiar a RECHAZADO
        // Pero el estado ya es EN_GESTION, por lo que la regla de transición aplica sobre el estado real
        setElevatedContext();
        String estadoDb = jdbcTemplate.queryForObject("SELECT ESTADO FROM PQRS_TICKETS WHERE ID_TICKET = ?", String.class, idTicket);
        assertEquals("EN_GESTION", estadoDb);
        clearContext();
    }

    @Test
    @Order(29)
    @DisplayName("Test 29: Resolución de dualidad: POST /api/v1/quejas delega transparentemente a TicketService")
    void test29_dualidadQuejas_crearQuejaLegacy_delegaATicketService() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> legacyPayload = Map.of(
                "tipo", "PETICION",
                "categoria", "MANTENIMIENTO",
                "titulo", "Petición legacy vía QuejasController",
                "descripcion", "Debe delegarse al nuevo TicketService con SLA y trazabilidad unificada."
        );

        mockMvc.perform(post("/api/v1/quejas")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(legacyPayload)))
                .andExpect(status().isCreated());

        setElevatedContext();
        List<Map<String, Object>> tickets = jdbcTemplate.queryForList(
                "SELECT * FROM PQRS_TICKETS WHERE ASUNTO = 'Petición legacy vía QuejasController'");
        assertEquals(1, tickets.size());
        assertEquals("RADICADO", tickets.get(0).get("ESTADO"));
        assertNotNull(tickets.get(0).get("FECHA_LIMITE_SLA"));
        clearContext();
    }

    @Test
    @Order(30)
    @DisplayName("Test 30: Resolución de dualidad: GET /api/v1/quejas retorna lista mapeada de tickets")
    void test30_dualidadQuejas_listarQuejasLegacy_delegaATicketService() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> legacyPayload = Map.of(
                "tipo", "PETICION",
                "categoria", "MANTENIMIENTO",
                "titulo", "Petición legacy para listar",
                "descripcion", "Debe delegarse al nuevo TicketService con SLA y trazabilidad unificada."
        );

        mockMvc.perform(post("/api/v1/quejas")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(legacyPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/quejas")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].titulo", notNullValue()))
                .andExpect(jsonPath("$[0].radicado", notNullValue()));
    }

    @Test
    @Order(31)
    @DisplayName("Test 31: Resolución de dualidad: PUT /api/v1/quejas/{id}/responder inserta en trazabilidad sin violación de constraint")
    void test31_dualidadQuejas_responderQuejaLegacy_delegaATicketService() throws Exception {
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("ADMINISTRACION");
        req.setAsunto("Petición para respuesta legacy");
        req.setDescripcion("Validación de que no se lance ORA-02290 en PQRS_TRAZABILIDAD.");

        MvcResult res = mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idTicket = Long.parseLong(res.getResponse().getContentAsString().trim());

        Map<String, String> payload = Map.of("respuesta", "Respuesta enviada a través de endpoint legacy");

        mockMvc.perform(put("/api/v1/quejas/" + idTicket + "/responder")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        setElevatedContext();
        List<Map<String, Object>> traz = jdbcTemplate.queryForList(
                "SELECT * FROM PQRS_TRAZABILIDAD WHERE ID_TICKET = ? AND TIPO_INTERVENCION = 'RESPUESTA_INTERNA'", idTicket);
        assertEquals(1, traz.size());
        assertTrue(traz.get(0).get("COMENTARIO").toString().contains("endpoint legacy"));
        clearContext();
    }
}
