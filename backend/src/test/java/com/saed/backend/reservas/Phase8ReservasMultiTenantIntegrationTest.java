package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.reservas.dto.ReservaDTO;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Rigurosa para GAP-F8-04:
 * Aislamiento Multi-Tenant y Contexto de Unidad en Reservas de Zonas Comunes.
 *
 * 21 Escenarios de Validación:
 *  1. Unidad Correcta (Residente): 201 Created y asignación estricta de unidad desde contexto.
 *  2. Unidad Manipulada (Anti-IDOR): Residente intenta reservar para otra unidad -> 403 Forbidden.
 *  3. Unidad Inexistente: Payload con unidad inexistente -> 403 Forbidden.
 *  4. Unidad de Otra Propiedad: Admin/Usuario intenta usar unidad de otra copropiedad -> 403 Forbidden.
 *  5. Unidad de Otra Organización: Intento de usar unidad foránea de otra org -> 403 Forbidden.
 *  6. Zona Propia: Operación permitida en zona de la misma copropiedad -> 201 Created.
 *  7. Zona de Otra Propiedad: Intento de reservar zona de copropiedad distinta -> 403 Forbidden.
 *  8. Zona de Otra Organización: Intento de reservar zona de otra organización -> 403 Forbidden.
 *  9. Anti-IDOR Lectura: Residente no puede leer reserva de otra unidad -> 403 Forbidden.
 * 10. Anti-IDOR Modificación: Admin de Propiedad 1 no puede modificar reserva de Propiedad 2 -> 403 Forbidden.
 * 11. Lectura Reserva Propia: Residente lee su propia reserva -> 200 OK con payload íntegro.
 * 12. Lectura Reserva Inexistente: GET a ID no existente -> 404 Not Found.
 * 13. Rol Residente: Asigna automáticamente unidad del token aún si el payload omite idUnidad -> 201 Created.
 * 14. Rol Residente Conviviente: Hereda unidad asignada de su contexto -> 201 Created.
 * 15. Rol Admin Propiedad: Solo lista y visualiza reservas de su propia copropiedad (Cross-Property filter).
 * 16. Rol Admin Organización: Sin acceso a reservas operativas -> 403 Forbidden.
 * 17. Rol Superadmin: Acceso global y lectura entre propiedades autorizada -> 200 OK.
 * 18. Concurrencia Multi-Unidad: Dos unidades de la misma propiedad compitiendo por la misma zona y horario -> 1 gana (201), 1 rechazada (409), exactamente 1 en Oracle.
 * 19. Verificación de Restauración de Contexto: hasOverlappingReserva restaura íntegramente el contexto original tras elevación.
 * 20. Preservación GAP-F8-02: Mora financiera bloquea con 422 y Anti-IDOR previene evasión con 403.
 * 21. Preservación GAP-F8-03: Detección de solapamiento retorna 409 y adyacencia coexiste con 201.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase8ReservasMultiTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinanzasRepository finanzasRepository;

    @Autowired
    private ReservasRepository reservasRepository;

    @MockBean
    private AssignmentService assignmentService;

    // Constantes de Identidad y Roles
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_PROP_2 = 3L;
    private static final long ASSIGN_ADMIN_PROP_2 = 103L;

    private static final long USER_RESIDENTE_1 = 4L; // Carlos Martinez (Apto 101 / Unit 1 / Prop 1)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Ana Gomez (Apto 102 / Unit 2 / Prop 1)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_CONVIVIENTE_1 = 6L; // Pedro Perez (Apto 101 / Unit 1 / Conviviente)
    private static final long ASSIGN_CONVIVIENTE_1 = 106L;

    private static final long USER_RESIDENTE_PROP_2 = 7L; // Maria Lopez (Apto 201 / Unit 3 / Prop 2)
    private static final long ASSIGN_RESIDENTE_PROP_2 = 107L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 108L;

    private static final long USER_RESIDENTE_ORG_2 = 9L; // Juan Foraneo (Casa 501 / Unit 5 / Prop 4 / Org 2)
    private static final long ASSIGN_RESIDENTE_ORG_2 = 109L;

    // Organizaciones y Copropiedades
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 2L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 2L;
    private static final long PROP_4_ID = 4L; // Pertenece a Org 2

    // Unidades
    private static final long UNIT_1_ID = 1L; // Prop 1
    private static final long UNIT_2_ID = 2L; // Prop 1
    private static final long UNIT_3_ID = 3L; // Prop 2
    private static final long UNIT_5_ID = 5L; // Prop 4 (Org 2)

    // Zonas Comunes
    private static final long ZONA_1_ID = 1L; // Salón Social Central (Prop 1)
    private static final long ZONA_2_ID = 2L; // Zona BBQ Principal (Prop 1)
    private static final long ZONA_3_ID = 3L; // Piscina Recreativa (Prop 2)
    private static final long ZONA_4_ID = 4L; // Cancha de Tenis (Prop 4 / Org 2)

    // Tokens JWT
    private String tokenSuperAdmin;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenAdminOrg1;
    private String tokenResidente1;
    private String tokenResidente2;
    private String tokenConviviente1;
    private String tokenResidenteProp2;
    private String tokenResidenteOrg2;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza integral de datos residuales de reservas y deudas
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                END;
            """);

            // Organizaciones
            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "900000002-2", "org2@saed.com");

            // Propiedades
            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_2_ID, ORG_1_ID, "Torres del Parque II");
            ensurePropiedad(PROP_4_ID, ORG_2_ID, "Condominio Campestre Norte");

            // Unidades
            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");
            ensureUnidad(UNIT_3_ID, PROP_2_ID, "Apto 201");
            ensureUnidad(UNIT_5_ID, PROP_4_ID, "Casa 501");

            // Personas y Usuarios
            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Propiedad1", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_PROP_2, "1000000003", "Admin", "Propiedad2", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2", "adminprop2@saed.com");

            ensurePersona(USER_RESIDENTE_1, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(USER_RESIDENTE_1, USER_RESIDENTE_1, "carlos_m", "camartinez@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "ana_g", "anagomez@saed.com");

            ensurePersona(USER_CONVIVIENTE_1, "1000000006", "Pedro", "Perez", "pedroperez@saed.com");
            ensureUsuario(USER_CONVIVIENTE_1, USER_CONVIVIENTE_1, "pedro_p", "pedroperez@saed.com");

            ensurePersona(USER_RESIDENTE_PROP_2, "1000000007", "Maria", "Lopez", "marialopez@saed.com");
            ensureUsuario(USER_RESIDENTE_PROP_2, USER_RESIDENTE_PROP_2, "maria_l", "marialopez@saed.com");

            ensurePersona(USER_ADMIN_ORG_1, "1000000008", "Admin", "Org1", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1", "adminorg1@saed.com");

            ensurePersona(USER_RESIDENTE_ORG_2, "1000000009", "Juan", "Foraneo", "juanforaneo@saed.com");
            ensureUsuario(USER_RESIDENTE_ORG_2, USER_RESIDENTE_ORG_2, "juan_f", "juanforaneo@saed.com");

            // Zonas Comunes
            ensureZonaComun(ZONA_1_ID, PROP_1_ID, "Salón Social Central", 50, "S", new BigDecimal("100000.00"));
            ensureZonaComun(ZONA_2_ID, PROP_1_ID, "Zona BBQ Principal", 25, "S", new BigDecimal("30000.00"));
            ensureZonaComun(ZONA_3_ID, PROP_2_ID, "Piscina Recreativa", 30, "S", new BigDecimal("20000.00"));
            ensureZonaComun(ZONA_4_ID, PROP_4_ID, "Cancha de Tenis Campestre", 10, "S", new BigDecimal("50000.00"));

            setupMockAssignments();

            // Generación de tokens JWT
            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenConviviente1 = jwtProvider.generateIdentityToken(USER_CONVIVIENTE_1);
            tokenResidenteProp2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_PROP_2);
            tokenResidenteOrg2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_ORG_2);

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
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 3, 5, 9992);
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
        UnitDTO unit3 = new UnitDTO(UNIT_3_ID, "Apto 201");
        UnitDTO unit5 = new UnitDTO(UNIT_5_ID, "Casa 501");

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

        // Residente 1 (Unidad 1 / Prop 1)
        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));

        // Residente 2 (Unidad 2 / Prop 1)
        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));

        // Conviviente 1 (Unidad 1 / Prop 1)
        AssignmentResponseDTO conv1Assign = new AssignmentResponseDTO();
        conv1Assign.setIdAsignacion(ASSIGN_CONVIVIENTE_1);
        conv1Assign.setOrganizacion(org1);
        conv1Assign.setPropiedad(prop1);
        conv1Assign.setUnidad(unit1);
        conv1Assign.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_CONVIVIENTE_1, USER_CONVIVIENTE_1)).thenReturn(Optional.of(conv1Assign));

        // Residente Prop 2 (Unidad 3 / Prop 2)
        AssignmentResponseDTO resProp2Assign = new AssignmentResponseDTO();
        resProp2Assign.setIdAsignacion(ASSIGN_RESIDENTE_PROP_2);
        resProp2Assign.setOrganizacion(org1);
        resProp2Assign.setPropiedad(prop2);
        resProp2Assign.setUnidad(unit3);
        resProp2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_PROP_2, USER_RESIDENTE_PROP_2)).thenReturn(Optional.of(resProp2Assign));

        // Residente Org 2 (Unidad 5 / Prop 4 / Org 2)
        AssignmentResponseDTO resOrg2Assign = new AssignmentResponseDTO();
        resOrg2Assign.setIdAsignacion(ASSIGN_RESIDENTE_ORG_2);
        resOrg2Assign.setOrganizacion(org2);
        resOrg2Assign.setPropiedad(prop4);
        resOrg2Assign.setUnidad(unit5);
        resOrg2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_ORG_2, USER_RESIDENTE_ORG_2)).thenReturn(Optional.of(resOrg2Assign));
    }

    // =========================================================================
    // PRUEBAS DE AISLAMIENTO MULTI-TENANT Y CONTEXTO DE UNIDAD (21 CASOS)
    // =========================================================================

    @Test
    @DisplayName("01 — Unidad Correcta: Permite reserva 201 y persiste con id_unidad del contexto")
    void test01_UnidadCorrecta_PermiteReserva201() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "08:00",
                "horaFin", "09:00",
                "cantidadAsistentes", 5,
                "observaciones", "Reserva propia correcta"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.valueOf(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ID_UNIDAD = 1",
                    Integer.class, idReserva);
            assertEquals(1, count, "La reserva debe estar asignada estrictamente a la Unidad 1");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("02 — Unidad Manipulada (Anti-IDOR): Residente no puede reservar a nombre de otra unidad -> 403")
    void test02_UnidadManipulada_AntiIdorRechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", UNIT_2_ID, // Manipulación intencional de ID de unidad
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:00",
                "horaFin", "10:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_UNIDAD = 2 AND HORA_INICIO = '09:00'",
                    Integer.class);
            assertEquals(0, count, "No debe haberse creado ninguna reserva para la unidad 2");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("03 — Unidad Inexistente: Rechaza con 403 Forbidden por no pertenecer a la copropiedad")
    void test03_UnidadInexistente_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", 999999L,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        // Admin intenta asignar unidad inexistente
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("04 — Unidad de Otra Propiedad: Admin de Propiedad 1 intenta usar unidad de Propiedad 2 -> 403")
    void test04_UnidadDeOtraPropiedad_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", UNIT_3_ID, // Unidad 3 pertenece a Propiedad 2
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("05 — Unidad de Otra Organización: Intento de usar unidad foránea de Org 2 -> 403")
    void test05_UnidadDeOtraOrganizacion_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", UNIT_5_ID, // Unidad 5 pertenece a Propiedad 4 en Organización 2
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("06 — Zona Propia: Operación permitida en zona de la misma copropiedad -> 201")
    void test06_ZonaPropia_OperacionPermitida() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(6);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_2_ID, // BBQ en Propiedad 1
                "fechaReserva", fecha.toString(),
                "horaInicio", "11:00",
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
    @DisplayName("07 — Zona de Otra Propiedad: Residente de Propiedad 1 intenta reservar zona de Propiedad 2 -> 403")
    void test07_ZonaDeOtraPropiedad_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(6);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_3_ID, // Zona 3 pertenece a Propiedad 2
                "fechaReserva", fecha.toString(),
                "horaInicio", "11:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("08 — Zona de Otra Organización: Residente de Org 1 intenta reservar zona de Org 2 -> 403")
    void test08_ZonaDeOtraOrganizacion_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(6);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_4_ID, // Zona 4 pertenece a Propiedad 4 en Org 2
                "fechaReserva", fecha.toString(),
                "horaInicio", "11:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("09 — Anti-IDOR Lectura: Residente 1 no puede leer reserva de Residente 2 -> 403")
    void test09_UsuarioNoPuedeLeerReservaAjena_Rechaza403() throws Exception {
        // Crear reserva para Residente 2
        Long idReserva2 = crearReservaDirecta(ZONA_1_ID, UNIT_2_ID, USER_RESIDENTE_2,
                LocalDate.now().plusDays(7), "10:00", "11:00", "PENDIENTE");

        // Residente 1 intenta leerla por ID
        mockMvc.perform(get("/api/v1/reservas/" + idReserva2)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10 — Anti-IDOR Modificación: Admin de Propiedad 1 no puede modificar reserva de Propiedad 2 -> 403")
    void test10_UsuarioNoPuedeModificarReservaAjena_Rechaza403() throws Exception {
        // Crear reserva en Propiedad 2
        Long idReservaProp2 = crearReservaDirecta(ZONA_3_ID, UNIT_3_ID, USER_RESIDENTE_PROP_2,
                LocalDate.now().plusDays(7), "10:00", "11:00", "PENDIENTE");

        // Admin de Propiedad 1 intenta modificar su estado
        mockMvc.perform(put("/api/v1/reservas/" + idReservaProp2 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11 — Lectura Reserva Propia: Residente lee su reserva -> 200 OK con payload íntegro")
    void test11_LecturaReservaPropia_Permite200() throws Exception {
        Long idReserva1 = crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, USER_RESIDENTE_1,
                LocalDate.now().plusDays(7), "12:00", "13:00", "PENDIENTE");

        mockMvc.perform(get("/api/v1/reservas/" + idReserva1)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva", is(idReserva1.intValue())))
                .andExpect(jsonPath("$.idUnidad", is(1)))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")));
    }

    @Test
    @DisplayName("12 — Lectura Reserva Inexistente: Retorna 404 Not Found")
    void test12_LecturaReservaInexistente_Retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/999999")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("13 — Rol Residente: Asigna automáticamente la unidad autorizada del contexto aun sin enviarla")
    void test13_RolResidente_UsaUnidadAutorizadaYRespetaTenant() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(8);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "14:00",
                "horaFin", "15:00"
                // idUnidad omitida intencionalmente
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.valueOf(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            Long unitInDb = jdbcTemplate.queryForObject(
                    "SELECT ID_UNIDAD FROM RESERVAS WHERE ID_RESERVA = ?",
                    Long.class, idReserva);
            assertEquals(UNIT_1_ID, unitInDb, "La reserva debe heredar estrictamente la Unidad 1 del contexto");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("14 — Rol Residente Convivencia: Hereda unidad del contexto y crea reserva exitosamente")
    void test14_RolResidenteConvivencia_HeredaUnidadYRespetaTenant() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(8);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "16:00",
                "horaFin", "17:00"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenConviviente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.valueOf(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            Long unitInDb = jdbcTemplate.queryForObject(
                    "SELECT ID_UNIDAD FROM RESERVAS WHERE ID_RESERVA = ?",
                    Long.class, idReserva);
            assertEquals(UNIT_1_ID, unitInDb, "Conviviente debe heredar la Unidad 1");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("15 — Rol Admin Propiedad: Solo ve y lista reservas de su propia propiedad")
    void test15_RolAdminPropiedad_SoloVeYOperaSuPropiedad() throws Exception {
        // Reserva en Propiedad 1
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, USER_RESIDENTE_1,
                LocalDate.now().plusDays(9), "10:00", "11:00", "PENDIENTE");

        // Reserva en Propiedad 2
        crearReservaDirecta(ZONA_3_ID, UNIT_3_ID, USER_RESIDENTE_PROP_2,
                LocalDate.now().plusDays(9), "10:00", "11:00", "PENDIENTE");

        // Admin Propiedad 1 consulta todas
        mockMvc.perform(get("/api/v1/reservas/todas")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idZona", everyItem(isOneOf(1, 2))))
                .andExpect(jsonPath("$[*].idZona", not(hasItem(3))));
    }

    @Test
    @DisplayName("16 — Rol Admin Organización: Sin acceso a reservas operativas -> 403 Forbidden")
    void test16_RolAdminOrganizacion_SinAccesoAReservasOperativas_Rechaza403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(10);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", UNIT_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        // POST reservas
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        // GET reservas/todas
        mockMvc.perform(get("/api/v1/reservas/todas")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("17 — Rol Superadmin: Acceso global y lectura de reservas entre distintas copropiedades")
    void test17_RolSuperadmin_AccesoGlobalAutorizado() throws Exception {
        Long rProp1 = crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, USER_RESIDENTE_1,
                LocalDate.now().plusDays(11), "09:00", "10:00", "PENDIENTE");
        Long rProp2 = crearReservaDirecta(ZONA_3_ID, UNIT_3_ID, USER_RESIDENTE_PROP_2,
                LocalDate.now().plusDays(11), "09:00", "10:00", "PENDIENTE");

        mockMvc.perform(get("/api/v1/reservas/" + rProp1)
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_SUPERADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva", is(rProp1.intValue())));

        mockMvc.perform(get("/api/v1/reservas/" + rProp2)
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_SUPERADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva", is(rProp2.intValue())));
    }

    @Test
    @DisplayName("18 — Concurrencia Multi-Unidad: Dos unidades de la misma propiedad compiten por el mismo slot -> 1 gana (201), 1 rechazada (409), exactamente 1 en DB")
    void test18_DosUnidadesMismaPropiedad_MismaZonaYHorario_UnoGana201_OtroBloquea409_ExactamenteUnaPersistida() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(12);
        String horaInicio = "15:00";
        String horaFin = "17:00";

        Map<String, Object> body1 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", horaInicio,
                "horaFin", horaFin,
                "cantidadAsistentes", 10,
                "observaciones", "Competencia Unidad 1"
        );

        Map<String, Object> body2 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", horaInicio,
                "horaFin", horaFin,
                "cantidadAsistentes", 15,
                "observaciones", "Competencia Unidad 2"
        );

        int nThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        AtomicInteger status1 = new AtomicInteger(0);
        AtomicInteger status2 = new AtomicInteger(0);

        // Hilo 1: Residente 1 (Unidad 1)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                        .andReturn();
                int st = res.getResponse().getStatus();
                status1.set(st);
                if (st != 201 && st != 409) {
                    System.err.println("THREAD 1 UNEXPECTED: " + st + " BODY: " + res.getResponse().getContentAsString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                status1.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        // Hilo 2: Residente 2 (Unidad 2)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                        .andReturn();
                int st = res.getResponse().getStatus();
                status2.set(st);
                if (st != 201 && st != 409) {
                    System.err.println("THREAD 2 UNEXPECTED: " + st + " BODY: " + res.getResponse().getContentAsString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                status2.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "La ejecución concurrente debió terminar dentro del tiempo límite");
        int s1 = status1.get();
        int s2 = status2.get();
        boolean winnerAndConflict = (s1 == 201 && s2 == 409) || (s1 == 409 && s2 == 201);
        assertTrue(winnerAndConflict, "Bajo concurrencia de dos unidades, una debe obtener 201 y la otra 409. Obtenidos: s1=" + s1 + ", s2=" + s2);

        // Verificación de integridad en Oracle Database
        setElevatedContext();
        try {
            Integer totalPersistidas = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ? AND HORA_INICIO = ?",
                    Integer.class, ZONA_1_ID, Date.valueOf(fecha), horaInicio);
            assertEquals(1, totalPersistidas, "Debe existir exactamente 1 reserva persistida en Oracle para ese horario");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("19 — Restauración de Contexto: hasOverlappingReserva restaura íntegramente el contexto original tras elevación")
    void test19_VerificacionRestauracionContexto_DespuesDeElevacion() {
        SaedContext originalCtx = SaedContext.builder()
                .userId(USER_RESIDENTE_1)
                .organizationId(ORG_1_ID)
                .propertyId(PROP_1_ID)
                .unitId(UNIT_1_ID)
                .roleCode("RESIDENTE")
                .roleScope("UNIDAD")
                .build();

        SaedContextHolder.setContext(originalCtx);
        try {
            boolean hasOverlap = reservasRepository.hasOverlappingReserva(
                    ZONA_1_ID,
                    LocalDate.now().plusDays(20),
                    "10:00",
                    "11:00"
            );
            assertFalse(hasOverlap);

            SaedContext currentCtx = SaedContextHolder.getContext();
            assertNotNull(currentCtx, "El contexto SaedContext no debe ser nulo");
            assertEquals(USER_RESIDENTE_1, currentCtx.getUserId(), "El userId debe seguir siendo el del residente");
            assertEquals(UNIT_1_ID, currentCtx.getUnitId(), "El unitId debe seguir siendo el de la unidad original");
            assertEquals("RESIDENTE", currentCtx.getRoleCode(), "El rol debe mantenerse en RESIDENTE y no en SUPERADMIN");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("20 — Preservación GAP-F8-02: Unidad morosa bloqueada con 422 y Anti-IDOR previene evasión con 403")
    void test20_PreservacionGAPF802_MoraFinancieraYAntiIdor() throws Exception {
        // Poner en mora a la Unidad 1
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-06', 300000, 300000, TRUNC(SYSDATE) - 10, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        LocalDate fecha = LocalDate.now().plusDays(13);

        // 1. Intento normal por parte del residente moroso -> 422 UNIDAD_EN_MORA
        Map<String, Object> bodyPropia = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyPropia)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")));

        // 2. Intento de evasión cambiando a Unidad 2 en el payload -> 403 Anti-IDOR (precede a mora)
        Map<String, Object> bodyEvasion = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", UNIT_2_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyEvasion)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("21 — Preservación GAP-F8-03: Solapamiento horario retorna 409 y adyacencia coexiste con 201")
    void test21_PreservacionGAPF803_SolapamientoYAdyacencia() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(14);

        // 1. Reserva base: 18:00 a 19:00 -> 201 Created
        Map<String, Object> base = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "18:00",
                "horaFin", "19:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(base)))
                .andExpect(status().isCreated());

        // 2. Solapamiento parcial: 18:30 a 19:30 -> 409 Conflict
        Map<String, Object> solapada = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "18:30",
                "horaFin", "19:30"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solapada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESERVA_SOLAPADA")));

        // 3. Adyacencia exacta: 19:00 a 20:00 -> 201 Created
        Map<String, Object> adyacente = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "19:00",
                "horaFin", "20:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adyacente)))
                .andExpect(status().isCreated());

        // Verificar que ambas reservas coexisten en Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ? AND ESTADO = 'PENDIENTE'",
                    Integer.class, ZONA_1_ID, Date.valueOf(fecha));
            assertEquals(2, count, "Deben existir exactamente 2 reservas adyacentes confirmadas");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE INFRAESTRUCTURA DE PRUEBAS
    // =========================================================================

    private Long crearReservaDirecta(Long idZona, Long idUnidad, Long idPersona, LocalDate fecha, String horaInicio, String horaFin, String estado) {
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO RESERVAS (ID_ZONA, ID_UNIDAD, ID_PERSONA_SOLICITA, FECHA_RESERVA, HORA_INICIO, HORA_FIN, CANTIDAD_ASISTENTES, COSTO_TOTAL, ESTADO)
                VALUES (?, ?, ?, ?, ?, ?, 4, 0, ?)
            """, idZona, idUnidad, idPersona, Date.valueOf(fecha), horaInicio, horaFin, estado);
            return jdbcTemplate.queryForObject("SELECT MAX(ID_RESERVA) FROM RESERVAS", Long.class);
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
        }
    }

    private void ensureUnidad(Long id, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'ACTIVA')", id, idProp, identificador);
        } else {
            jdbcTemplate.update("UPDATE UNIDADES SET ID_PROPIEDAD = ?, IDENTIFICADOR = ?, ESTADO = 'ACTIVA' WHERE ID_UNIDAD = ?",
                    idProp, identificador, id);
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

    private void ensureZonaComun(Long idZona, Long idProp, String nombre, int aforo, String reqRes, BigDecimal costo) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ZONAS_COMUNES WHERE ID_ZONA = ?", Integer.class, idZona);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ZONAS_COMUNES (ID_ZONA, ID_PROPIEDAD, NOMBRE, TIPO, AFORO_MAXIMO, REQUIERE_RESERVA, COSTO_RESERVA, ESTADO) " +
                    "VALUES (?, ?, ?, 'SOCIAL', ?, ?, ?, 'ACTIVA')", idZona, idProp, nombre, aforo, reqRes, costo);
        } else {
            jdbcTemplate.update("UPDATE ZONAS_COMUNES SET ID_PROPIEDAD = ?, NOMBRE = ?, TIPO = 'SOCIAL', AFORO_MAXIMO = ?, REQUIERE_RESERVA = ?, COSTO_RESERVA = ?, ESTADO = 'ACTIVA' WHERE ID_ZONA = ?",
                    idProp, nombre, aforo, reqRes, costo, idZona);
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
