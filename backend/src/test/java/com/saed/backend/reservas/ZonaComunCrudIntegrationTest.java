package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.repository.ReservasRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Rigurosa para GAP-F8-05: CRUD de Zonas Comunes.
 * 22 Escenarios Deterministas de Validación de Ciclo de Vida, Seguridad y Regresión.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ZonaComunCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservasRepository reservasRepository;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private PazYSalvoService pazYSalvoService;

    // Constantes de Identidad y Roles
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_PROP_2 = 3L;
    private static final long ASSIGN_ADMIN_PROP_2 = 103L;

    private static final long USER_RESIDENTE_1 = 4L;
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L;
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_CONVIVIENTE_1 = 6L;
    private static final long ASSIGN_CONVIVIENTE_1 = 106L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 108L;

    // Organizaciones y Propiedades
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 2L;
    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 2L;
    private static final long PROP_4_ID = 4L; // Org 2

    // Unidades
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    // Tokens JWT
    private String tokenSuperAdmin;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenAdminOrg1;
    private String tokenResidente1;
    private String tokenResidente2;
    private String tokenConviviente1;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza de zonas de prueba creadas durante tests
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM BLOQUEOS_ZONA WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%';
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "900000002-2", "org2@saed.com");
            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_2_ID, ORG_1_ID, "Torres del Parque II");
            ensurePropiedad(PROP_4_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");

            ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Prop1", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_PROP_2, "1000000003", "Admin", "Prop2", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2", "adminprop2@saed.com");
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_2_ID, null);

            ensurePersona(USER_RESIDENTE_1, "1000000004", "Carlos", "Martinez", "carlosmartinez@saed.com");
            ensureUsuario(USER_RESIDENTE_1, USER_RESIDENTE_1, "carlos_m", "carlosmartinez@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "ana_g", "anagomez@saed.com");

            ensurePersona(USER_CONVIVIENTE_1, "1000000006", "Pedro", "Perez", "pedroperez@saed.com");
            ensureUsuario(USER_CONVIVIENTE_1, USER_CONVIVIENTE_1, "pedro_p", "pedroperez@saed.com");

            ensurePersona(USER_ADMIN_ORG_1, "1000000008", "Admin", "Org1", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1", "adminorg1@saed.com");

            setupMockAssignments();

            // Configurar Paz y Salvo default: Habilitado (F8-02)
            when(pazYSalvoService.verificarEstadoFinanciero(anyLong()))
                    .thenReturn(new PazYSalvoEstadoFinancieroDTO(1L, "Apto 101", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, java.util.List.of()));

            // Generación de tokens JWT
            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenConviviente1 = jwtProvider.generateIdentityToken(USER_CONVIVIENTE_1);

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
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM BLOQUEOS_ZONA WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%';
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");

        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torres del Parque II");
        PropertyDTO prop4 = new PropertyDTO(PROP_4_ID, "Condominio Campestre Norte");

        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        // Superadmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setOrganizacion(org1);
        saAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(saAssign));

        // Admin Propiedad 1
        AssignmentResponseDTO admin1Assign = new AssignmentResponseDTO();
        admin1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        admin1Assign.setOrganizacion(org1);
        admin1Assign.setPropiedad(prop1);
        admin1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(admin1Assign));

        // Admin Propiedad 2
        AssignmentResponseDTO admin2Assign = new AssignmentResponseDTO();
        admin2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        admin2Assign.setOrganizacion(org1);
        admin2Assign.setPropiedad(prop2);
        admin2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(admin2Assign));

        // Admin Organización 1
        AssignmentResponseDTO adminOrg1Assign = new AssignmentResponseDTO();
        adminOrg1Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        adminOrg1Assign.setOrganizacion(org1);
        adminOrg1Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(adminOrg1Assign));

        // Residente 1
        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));

        // Residente 2
        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));

        // Conviviente 1
        AssignmentResponseDTO conv1Assign = new AssignmentResponseDTO();
        conv1Assign.setIdAsignacion(ASSIGN_CONVIVIENTE_1);
        conv1Assign.setOrganizacion(org1);
        conv1Assign.setPropiedad(prop1);
        conv1Assign.setUnidad(unit1);
        conv1Assign.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_CONVIVIENTE_1, USER_CONVIVIENTE_1)).thenReturn(Optional.of(conv1Assign));
    }

    // =========================================================================
    // CASOS DE PRUEBA MANDATORIOS (TEST 1 - TEST 22)
    // =========================================================================

    @Test
    @DisplayName("01 — Create: ADMIN_PROPIEDAD crea zona válida -> 201 Created y persistencia en Oracle")
    void test01_CreateZona_AdminPropiedad_Exitoso() throws Exception {
        CreateZonaComunDTO dto = new CreateZonaComunDTO("TEST_PISCINA_CLIMATIZADA", "ACUATICA", 40, true, new BigDecimal("25000.00"), "ACTIVA");

        MvcResult result = mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idZona", notNullValue()))
                .andExpect(jsonPath("$.nombre", is("TEST_PISCINA_CLIMATIZADA")))
                .andExpect(jsonPath("$.tipo", is("ACUATICA")))
                .andExpect(jsonPath("$.aforoMaximo", is(40)))
                .andExpect(jsonPath("$.requiereReserva", is("S")))
                .andExpect(jsonPath("$.costoReserva").value(25000))
                .andExpect(jsonPath("$.estado", is("ACTIVA")))
                .andReturn();

        // Verificación directa en base de datos Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE NOMBRE = 'TEST_PISCINA_CLIMATIZADA' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_1_ID);
            assertEquals(1, count, "La zona debe estar persistida en Oracle para la Propiedad 1");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("02 — Duplicate Name: Intento de crear zona con mismo nombre (case-insensitive) en misma propiedad -> 409 Conflict")
    void test02_DuplicateName_MismaPropiedad_Retorna409() throws Exception {
        CreateZonaComunDTO dto1 = new CreateZonaComunDTO("TEST_SALON_COMUNAL", "SOCIAL", 60, "S", BigDecimal.ZERO, "ACTIVA");
        CreateZonaComunDTO dto2 = new CreateZonaComunDTO("test_salon_comunal", "SOCIAL", 80, "S", BigDecimal.ZERO, "ACTIVA");

        // 1. Primera creación exitosa
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        // 2. Segunda creación con mismo nombre en minúsculas -> 409 Conflict
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ZONA_NOMBRE_DUPLICADO")));

        // Verificar que en Oracle solo existe exactamente 1 registro
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE UPPER(NOMBRE) = 'TEST_SALON_COMUNAL' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_1_ID);
            assertEquals(1, count, "No deben existir dos registros para el mismo nombre en la propiedad");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("03 — Same Name Different Property: Mismo nombre en Propiedad 1 y Propiedad 2 -> 201 en ambas")
    void test03_SameNameDifferentProperty_Permitido() throws Exception {
        CreateZonaComunDTO dtoProp1 = new CreateZonaComunDTO("TEST_ZONA_BBQ_CENTRAL", "BBQ", 20, "S", BigDecimal.ZERO, "ACTIVA");
        CreateZonaComunDTO dtoProp2 = new CreateZonaComunDTO("TEST_ZONA_BBQ_CENTRAL", "BBQ", 30, "S", BigDecimal.ZERO, "ACTIVA");

        // Crear en Propiedad 1
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoProp1)))
                .andExpect(status().isCreated());

        // Crear en Propiedad 2
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoProp2)))
                .andExpect(status().isCreated());

        // Verificar que ambas filas existen en Oracle independientemente
        setElevatedContext();
        try {
            Integer countProp1 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE NOMBRE = 'TEST_ZONA_BBQ_CENTRAL' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_1_ID);
            Integer countProp2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE NOMBRE = 'TEST_ZONA_BBQ_CENTRAL' AND ID_PROPIEDAD = ?",
                    Integer.class, PROP_2_ID);
            assertEquals(1, countProp1);
            assertEquals(1, countProp2);
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("04 — Get Detail: ADMIN_PROPIEDAD consulta su zona -> 200 OK con payload completo")
    void test04_GetDetail_AdminPropiedad_Exitoso() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_CANCHA_SINTETICA", "DEPORTIVA", 14, "S", new BigDecimal("15000.00"), "ACTIVA");

        mockMvc.perform(get("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idZona", is(idZona.intValue())))
                .andExpect(jsonPath("$.nombre", is("TEST_CANCHA_SINTETICA")))
                .andExpect(jsonPath("$.tipo", is("DEPORTIVA")))
                .andExpect(jsonPath("$.aforoMaximo", is(14)))
                .andExpect(jsonPath("$.costoReserva").value(15000))
                .andExpect(jsonPath("$.estado", is("ACTIVA")));
    }

    @Test
    @DisplayName("05 — Cross-Property Get: ADMIN_PROPIEDAD 1 intenta consultar zona de Propiedad 2 -> 404 Not Found")
    void test05_CrossPropertyGet_Retorna404() throws Exception {
        Long idZonaProp2 = crearZonaDirecta(PROP_2_ID, "TEST_ZONA_PROP_2", "DEPORTIVA", 10, "S", BigDecimal.ZERO, "ACTIVA");

        // Admin 1 intenta consultar zona de Propiedad 2
        mockMvc.perform(get("/api/v1/zonas-comunes/" + idZonaProp2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("06 — Update: ADMIN_PROPIEDAD actualiza su zona -> 200 OK y persistencia en Oracle")
    void test06_UpdateZona_AdminPropiedad_Exitoso() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_GIMNASIO_PRE", "FITNESS", 15, "S", BigDecimal.ZERO, "ACTIVA");

        UpdateZonaComunDTO updateDTO = new UpdateZonaComunDTO(
                "TEST_GIMNASIO_MODERNO", "FITNESS", 25, true, new BigDecimal("5000.00"), "MANTENIMIENTO"
        );

        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("TEST_GIMNASIO_MODERNO")))
                .andExpect(jsonPath("$.aforoMaximo", is(25)))
                .andExpect(jsonPath("$.costoReserva").value(5000))
                .andExpect(jsonPath("$.estado", is("MANTENIMIENTO")));

        // Verificación directa en base de datos
        setElevatedContext();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT NOMBRE, AFORO_MAXIMO, ESTADO, ID_PROPIEDAD FROM ZONAS_COMUNES WHERE ID_ZONA = ?", idZona);
            assertEquals("TEST_GIMNASIO_MODERNO", row.get("NOMBRE"));
            assertEquals(25, ((Number) row.get("AFORO_MAXIMO")).intValue());
            assertEquals("MANTENIMIENTO", row.get("ESTADO"));
            assertEquals(PROP_1_ID, ((Number) row.get("ID_PROPIEDAD")).longValue(), "La propiedad debe permanecer intacta");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("07 — Cross-Property Update: ADMIN_PROPIEDAD 1 intenta actualizar zona de Propiedad 2 -> 404 Not Found")
    void test07_CrossPropertyUpdate_Retorna404() throws Exception {
        Long idZonaProp2 = crearZonaDirecta(PROP_2_ID, "TEST_ZONA_INTACTA_P2", "FITNESS", 10, "S", BigDecimal.ZERO, "ACTIVA");

        UpdateZonaComunDTO updateDTO = new UpdateZonaComunDTO("TEST_HACK_PROP_2", "FITNESS", 99, "S", BigDecimal.ZERO, "INACTIVA");

        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZonaProp2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isNotFound());

        // Verificar que la zona de Propiedad 2 no fue modificada
        setElevatedContext();
        try {
            String nombreActual = jdbcTemplate.queryForObject(
                    "SELECT NOMBRE FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZonaProp2);
            assertEquals("TEST_ZONA_INTACTA_P2", nombreActual, "La zona foránea no debe sufrir modificaciones");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("08 — Property ID Injection: Inyección de idPropiedad en payload es ignorada y se usa contexto")
    void test08_PropertyIdInjection_UsaContextoAutenticado() throws Exception {
        CreateZonaComunDTO dto = new CreateZonaComunDTO("TEST_ZONA_INJECT_PROP", "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");
        dto.setIdPropiedad(999999L); // Intento malicioso de inyectar otra propiedad

        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Verificar que se persistió para PROP_1_ID y no para 999999
        setElevatedContext();
        try {
            Long propReal = jdbcTemplate.queryForObject(
                    "SELECT ID_PROPIEDAD FROM ZONAS_COMUNES WHERE NOMBRE = 'TEST_ZONA_INJECT_PROP'", Long.class);
            assertEquals(PROP_1_ID, propReal, "La zona debe haberse creado en la propiedad del contexto autenticado");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("09 — Resident Create: RESIDENTE intenta crear zona -> 403 Forbidden")
    void test09_ResidentCreate_Retorna403() throws Exception {
        CreateZonaComunDTO dto = new CreateZonaComunDTO("TEST_ZONA_RESIDENTE", "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");

        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10 — Resident Update: RESIDENTE intenta actualizar zona -> 403 Forbidden")
    void test10_ResidentUpdate_Retorna403() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_NO_EDITABLE_POR_RES", "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");
        UpdateZonaComunDTO updateDTO = new UpdateZonaComunDTO("TEST_HACK_POR_RES", "SOCIAL", 50, "S", BigDecimal.ZERO, "ACTIVA");

        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11 — Resident Delete: RESIDENTE intenta eliminar zona -> 403 Forbidden")
    void test11_ResidentDelete_Retorna403() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_NO_BORRABLE_POR_RES", "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");

        mockMvc.perform(delete("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12 — Soft Delete: ADMIN_PROPIEDAD ejecuta DELETE -> 204 No Content y ESTADO = 'INACTIVA' en Oracle")
    void test12_SoftDelete_AdminPropiedad_PasaAInactiva() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_A_DESACTIVAR", "SOCIAL", 20, "S", BigDecimal.ZERO, "ACTIVA");

        mockMvc.perform(delete("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNoContent());

        // Verificar que la fila no desapareció, sino que su estado es INACTIVA
        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZona);
            assertEquals("INACTIVA", estado, "El estado debe ser INACTIVA (Soft Delete)");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("13 — Historial Preservado: Desactivación de zona conserva reservas asociadas intactas en Oracle")
    void test13_HistorialPreservado_ConservaReservas() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_CON_HISTORIAL", "SOCIAL", 30, "S", BigDecimal.ZERO, "ACTIVA");

        // Crear una reserva asociada directamente en la base de datos
        setElevatedContext();
        Long idReserva;
        try {
            jdbcTemplate.update("""
                INSERT INTO RESERVAS (ID_ZONA, ID_UNIDAD, ID_PERSONA_SOLICITA, FECHA_RESERVA, HORA_INICIO, HORA_FIN, CANTIDAD_ASISTENTES, COSTO_TOTAL, ESTADO)
                VALUES (?, 1, 4, SYSDATE + 2, '10:00', '12:00', 4, 0, 'APROBADA')
            """, idZona);
            idReserva = jdbcTemplate.queryForObject("SELECT MAX(ID_RESERVA) FROM RESERVAS", Long.class);
        } finally {
            clearContext();
        }

        // Ejecutar Soft Delete de la zona
        mockMvc.perform(delete("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNoContent());

        // Verificar en Oracle: La zona existe con estado INACTIVA y la reserva SIGUE EXISTIENDO
        setElevatedContext();
        try {
            String estadoZona = jdbcTemplate.queryForObject("SELECT ESTADO FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZona);
            Integer countReserva = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ?", Integer.class, idReserva);

            assertEquals("INACTIVA", estadoZona);
            assertEquals(1, countReserva, "La reserva histórica debe permanecer intacta en Oracle sin ser afectada por CASCADE");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("14 — Resident Reserving Maintenance: Zona en MANTENIMIENTO bloquea creación de reserva -> 400 ZONA_NO_DISPONIBLE")
    void test14_ResidentReservingMaintenance_Retorna400() throws Exception {
        Long idZonaMaint = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_EN_OBRAS", "PISCINA", 25, "S", BigDecimal.ZERO, "MANTENIMIENTO");

        Map<String, Object> body = Map.of(
                "idZona", idZonaMaint,
                "fechaReserva", LocalDate.now().plusDays(3).toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ZONA_NO_DISPONIBLE")));

        // Verificar que no se persistió ninguna reserva
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZonaMaint);
            assertEquals(0, count, "No debe haberse registrado ninguna reserva para una zona en mantenimiento");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("15 — Resident Reserving Inactive: Zona INACTIVA bloquea creación de reserva -> 400 ZONA_NO_DISPONIBLE")
    void test15_ResidentReservingInactive_Retorna400() throws Exception {
        Long idZonaInactiva = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_CLAUSURADA", "SALON", 50, "S", BigDecimal.ZERO, "INACTIVA");

        Map<String, Object> body = Map.of(
                "idZona", idZonaInactiva,
                "fechaReserva", LocalDate.now().plusDays(4).toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ZONA_NO_DISPONIBLE")));
    }

    @Test
    @DisplayName("16 — Active Zone: Zona ACTIVA con unidad al día y sin solapamiento permite reserva -> 201 Created")
    void test16_ActiveZone_ReservaExitosa() throws Exception {
        Long idZonaActiva = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_OPERATIVA_TOTAL", "BBQ", 15, "S", BigDecimal.ZERO, "ACTIVA");

        Map<String, Object> body = Map.of(
                "idZona", idZonaActiva,
                "fechaReserva", LocalDate.now().plusDays(5).toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("17 — Cross-Organization: Admin de Org 1 no puede ver ni modificar zona de Org 2 -> 404")
    void test17_CrossOrganization_AislamientoTotal() throws Exception {
        Long idZonaOrg2 = crearZonaDirecta(PROP_4_ID, "TEST_ZONA_ORG_FORANEA", "CAMPESTRE", 50, "S", BigDecimal.ZERO, "ACTIVA");

        // Admin de Propiedad 1 (Org 1) intenta consultar zona de Propiedad 4 (Org 2)
        mockMvc.perform(get("/api/v1/zonas-comunes/" + idZonaOrg2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());

        // Admin de Propiedad 1 intenta actualizarla
        UpdateZonaComunDTO updateDTO = new UpdateZonaComunDTO("TEST_HACK_CROSS_ORG", "CAMPESTRE", 50, "S", BigDecimal.ZERO, "ACTIVA");
        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZonaOrg2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("18 — Unauthenticated: Peticiones sin JWT retornan 401 Unauthorized")
    void test18_Unauthenticated_Retorna401() throws Exception {
        CreateZonaComunDTO dto = new CreateZonaComunDTO("TEST_ZONA_NO_AUTH", "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");

        mockMvc.perform(post("/api/v1/zonas-comunes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/zonas-comunes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/zonas-comunes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("19 — Invalid Payload: Validaciones de Bean Validation rechazan payload defectuoso con 400")
    void test19_InvalidPayload_Retorna400() throws Exception {
        // 1. Nombre nulo
        CreateZonaComunDTO dtoNullName = new CreateZonaComunDTO(null, "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoNullName)))
                .andExpect(status().isBadRequest());

        // 2. Nombre > 100 caracteres
        String longName = "A".repeat(101);
        CreateZonaComunDTO dtoLongName = new CreateZonaComunDTO(longName, "SOCIAL", 10, "S", BigDecimal.ZERO, "ACTIVA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoLongName)))
                .andExpect(status().isBadRequest());

        // 3. Aforo máximo <= 0
        CreateZonaComunDTO dtoZeroAforo = new CreateZonaComunDTO("TEST_AFORO_CERO", "SOCIAL", 0, "S", BigDecimal.ZERO, "ACTIVA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoZeroAforo)))
                .andExpect(status().isBadRequest());

        CreateZonaComunDTO dtoNegAforo = new CreateZonaComunDTO("TEST_AFORO_NEG", "SOCIAL", -5, "S", BigDecimal.ZERO, "ACTIVA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoNegAforo)))
                .andExpect(status().isBadRequest());

        // 4. Costo reserva negativo
        CreateZonaComunDTO dtoNegCosto = new CreateZonaComunDTO("TEST_COSTO_NEG", "SOCIAL", 10, "S", new BigDecimal("-100.00"), "ACTIVA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoNegCosto)))
                .andExpect(status().isBadRequest());

        // 5. Estado inválido
        CreateZonaComunDTO dtoEstadoInv = new CreateZonaComunDTO("TEST_ESTADO_INV", "SOCIAL", 10, "S", BigDecimal.ZERO, "DESTRUIDA");
        mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtoEstadoInv)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("20 — Regresión GAP-F8-02: Unidad en mora es rechazada con 422 UNIDAD_EN_MORA al reservar zona ACTIVA")
    void test20_RegresionF802_MoraFinanciera_Bloquea422() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_REG_F8_02", "SOCIAL", 20, "S", BigDecimal.ZERO, "ACTIVA");

        // Simular que la unidad 1 entra en mora
        when(pazYSalvoService.verificarEstadoFinanciero(UNIT_1_ID))
                .thenReturn(new PazYSalvoEstadoFinancieroDTO(UNIT_1_ID, "Apto 101", new BigDecimal("450000.00"), BigDecimal.ZERO, new BigDecimal("450000.00"), false, java.util.List.of("Unidad con 2 cuotas de administración vencidas")));

        Map<String, Object> body = Map.of(
                "idZona", idZona,
                "fechaReserva", LocalDate.now().plusDays(6).toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")));
    }

    @Test
    @DisplayName("21 — Regresión GAP-F8-03: Prevención de doble reserva mantiene 409 RESERVA_SOLAPADA y serialización")
    void test21_RegresionF803_DobleReserva_Retorna409() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_REG_F8_03", "SOCIAL", 20, "S", BigDecimal.ZERO, "ACTIVA");
        LocalDate fecha = LocalDate.now().plusDays(7);

        // 1. Primera reserva exitosa: 14:00 - 16:00
        Map<String, Object> r1 = Map.of(
                "idZona", idZona,
                "fechaReserva", fecha.toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00"
        );
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r1)))
                .andExpect(status().isCreated());

        // 2. Segunda reserva solapada: 15:00 - 17:00
        Map<String, Object> r2 = Map.of(
                "idZona", idZona,
                "fechaReserva", fecha.toString(),
                "horaInicio", "15:00",
                "horaFin", "17:00"
        );
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESERVA_SOLAPADA")));
    }

    @Test
    @DisplayName("22 — Regresión GAP-F8-04: Conviviente hereda unidad y crea reserva en zona ACTIVA -> 201 Created")
    void test22_RegresionF804_Conviviente_HeredaUnidad_201() throws Exception {
        Long idZona = crearZonaDirecta(PROP_1_ID, "TEST_ZONA_REG_F8_04", "SOCIAL", 30, "S", BigDecimal.ZERO, "ACTIVA");

        Map<String, Object> body = Map.of(
                "idZona", idZona,
                "fechaReserva", LocalDate.now().plusDays(8).toString(),
                "horaInicio", "09:00",
                "horaFin", "11:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenConviviente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private Long crearZonaDirecta(Long idProp, String nombre, String tipo, int aforo, String reqRes, BigDecimal costo, String estado) {
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO ZONAS_COMUNES (ID_PROPIEDAD, NOMBRE, TIPO, AFORO_MAXIMO, REQUIERE_RESERVA, COSTO_RESERVA, ESTADO)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, idProp, nombre, tipo, aforo, reqRes, costo, estado);
            return jdbcTemplate.queryForObject("SELECT MAX(ID_ZONA) FROM ZONAS_COMUNES WHERE NOMBRE = ?", Long.class, nombre);
        } finally {
            clearContext();
        }
    }

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
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'Calle 123', 'Bogotá', 'Colombia', 'MIXTA', 'ACTIVA')", id, idOrg, nombre);
        } else {
            jdbcTemplate.update("UPDATE PROPIEDADES SET ID_ORGANIZACION = ?, NOMBRE = ?, ESTADO = 'ACTIVA' WHERE ID_PROPIEDAD = ?",
                    idOrg, nombre, id);
        }
    }

    private void ensureUnidad(Long id, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'ACTIVA')", id, idProp, identificador);
        }
    }

    private void ensurePersona(Long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, 'NATURAL', ?, ?, ?)", id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long id, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", id, idPersona, username, email);
        }
    }

    private void ensureAsignacion(Long idAsignacion, Long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, idAsignacion);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))
            """, idAsignacion, idUsuario, idRol, idOrg, idProp, idUnidad);
        } else {
            jdbcTemplate.update("""
                UPDATE USUARIO_ASIGNACIONES
                SET ID_USUARIO = ?, ID_ROL = ?, ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA'
                WHERE ID_ASIGNACION = ?
            """, idUsuario, idRol, idOrg, idProp, idUnidad, idAsignacion);
        }
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
}
