package com.saed.backend.contratos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.service.ContratoParticipanteService;
import com.saed.backend.finanzas.service.FinanzasService;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Pruebas de Integración para GAP-F5-03: Coarrendatarios y Tutores Legales.
 *
 * Certifica:
 * 1. Ciclo de vida multi-participante: Principal + Coarrendatarios en CONTRATO_RESIDENTE.
 * 2. Restricción y validación estricta de menores de edad (< 18 años) que requieren tutor activo en TUTORES y CONTRATOS.ID_TUTOR.
 * 3. Sincronización de habitabilidad física en RESIDENTES_UNIDAD con respeto al límite de ConvivienteQuotaService.
 * 4. Control de acceso multi-tenant y aislamiento anti-IDOR en endpoints de participantes.
 * 5. Resolución de variables documentales (${nombreTutor}, ${coarrendatarios.firmas_html}, etc.) en el snapshot HTML y PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ContractParticipantsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinanzasService finanzasService;

    @Autowired
    private ContratoParticipanteService contratoParticipanteService;

    @MockBean
    private AssignmentService assignmentService;

    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 301L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long USER_ADMIN_ORG_2 = 98L;
    private static final long ASSIGN_ADMIN_ORG_2 = 981L;

    private static final long USER_RESIDENTE_1 = 4L;
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    // Personas
    private static final long PERSONA_ADULTO_1 = 4L; // Carlos Martinez (adulto)
    private static final long PERSONA_COARRENDATARIO_1 = 20L; // Pedro Picapiedra
    private static final long PERSONA_COARRENDATARIO_2 = 21L; // Pablo Marmol
    private static final long PERSONA_MENOR_1 = 22L; // Pepito Perez (menor de edad, 16 años)
    private static final long PERSONA_TUTOR_1 = 23L; // Maria Perez (tutora legal de Pepito)

    private Long plantillaParticipantesOrg1Id;

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Limpiar registros previos de prueba
        cleanTestTables();

        // Asegurar entidades multi-tenant
        ensureOrganizacion(1L, "Organización Central", "900000001-1", "org1@test.com");
        ensurePropiedad(1L, 1L, "Edificio Residencial SAED");
        ensureUnidad(1L, 1L, "Apto 101");
        ensureUnidad(2L, 1L, "Apto 102");

        ensureOrganizacion(9992L, "Organización Foránea", "900009992-9", "org9992@test.com");
        ensurePropiedad(9992L, 9992L, "Propiedad 9992 Test");
        ensureUnidad(9992L, 9992L, "Apto 9992");

        // Asegurar Personas con fechas de nacimiento
        ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com", LocalDate.now().minusYears(35));
        ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

        ensurePersona(2L, "1000000002", "Admin", "Propiedad", "admin@saed.com", LocalDate.now().minusYears(38));
        ensureUsuario(2L, 2L, "admin", "admin@saed.com");

        ensurePersona(4L, "1000000004", "Carlos", "Martinez", "camartinez@saed.com", LocalDate.now().minusYears(30));
        ensureUsuario(4L, 4L, "carlos_m", "camartinez@saed.com");

        ensurePersona(8L, "1000000008", "AdminOrg1", "SAED", "admin_org1@saed.com", LocalDate.now().minusYears(40));
        ensureUsuario(8L, 8L, "admin_org1", "admin_org1@saed.com");

        ensurePersona(98L, "1000000098", "AdminOrg2", "SAED", "admin_org2@saed.com", LocalDate.now().minusYears(42));
        ensureUsuario(98L, 98L, "admin_org2", "admin_org2@saed.com");

        ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com", LocalDate.now().minusYears(39));
        ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

        // Coarrendatarios y Tutores
        ensurePersona(PERSONA_COARRENDATARIO_1, "1000000020", "Pedro", "Picapiedra", "pedro@saed.com", LocalDate.now().minusYears(28));
        ensurePersona(PERSONA_COARRENDATARIO_2, "1000000021", "Pablo", "Marmol", "pablo@saed.com", LocalDate.now().minusYears(27));
        ensurePersona(PERSONA_MENOR_1, "1000000022", "Pepito", "Perez", "pepito@saed.com", LocalDate.now().minusYears(16)); // Menor de 16 años
        ensurePersona(PERSONA_TUTOR_1, "1000000023", "Maria", "Perez", "maria.perez@saed.com", LocalDate.now().minusYears(45)); // Tutora

        // Relación de tutoría en TUTORES
        ensureTutorRelation(PERSONA_MENOR_1, PERSONA_TUTOR_1, "MADRE");

        // Membresías activas
        seedMemberships();

        // Plantilla con variables de tutores y coarrendatarios
        seedPlantilla();

        // Asignaciones de roles
        seedAsignaciones();
        setupMockAssignments();

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void tearDown() {
        cleanTestTables();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void cleanTestTables() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO IN (SELECT ID_CONTRATO FROM CONTRATOS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992)) OR ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD IN (1, 2, 9992) AND ID_PERSONA IN (20, 21, 22, 23);
                END;
            """);
        } catch (Exception e) {
            System.err.println("CLEAN TEST TABLES NOTICE: " + e.getMessage());
        }
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea");

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

        AssignmentResponseDTO adminOrg2Assign = new AssignmentResponseDTO();
        adminOrg2Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_2);
        adminOrg2Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg2Assign.setOrganizacion(org2);

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

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN))
                .thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1))
                .thenReturn(Optional.of(adminOrg1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1))
                .thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_2, USER_ADMIN_ORG_2))
                .thenReturn(Optional.of(adminOrg2Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2))
                .thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1))
                .thenReturn(Optional.of(res1Assign));
    }

    @Test
    @DisplayName("GAP-F5-03.1: Creación de contrato con múltiples coarrendatarios persiste en CONTRATO_RESIDENTE")
    public void testCreateContrato_WithMultipleCoarrendatarios_PersistsInContratoResidente() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        List<CoarrendatarioCreateDTO> coarrendatarios = List.of(
                new CoarrendatarioCreateDTO(null, PERSONA_COARRENDATARIO_1, "COARRENDATARIO", "ACTIVO", false),
                new CoarrendatarioCreateDTO(null, PERSONA_COARRENDATARIO_2, "COARRENDATARIO", "ACTIVO", false)
        );

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_ADULTO_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("2200000.00"),
                plantillaParticipantesOrg1Id,
                null, // idTutor (adulto no requiere)
                coarrendatarios,
                false
        );

        MvcResult result = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> respMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long contratoId = ((Number) respMap.get("id")).longValue();
        assertNotNull(contratoId);

        // Validar en DB CONTRATO_RESIDENTE: Debe haber 3 participantes (1 Principal + 2 Coarrendatarios)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer countPrincipal = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO = ? AND ID_PERSONA = ? AND TIPO_VINCULO = 'ARRENDATARIO_PRINCIPAL'",
                Integer.class, contratoId, PERSONA_ADULTO_1
        );
        assertEquals(1, countPrincipal, "Debe existir un registro de ARRENDATARIO_PRINCIPAL");

        Integer countCoarrendatarios = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO = ? AND TIPO_VINCULO = 'COARRENDATARIO'",
                Integer.class, contratoId
        );
        assertEquals(2, countCoarrendatarios, "Deben existir 2 coarrendatarios en CONTRATO_RESIDENTE");

        // Consultar detalle vía API
        MvcResult detalleResult = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        ContratoDetalleDTO detalle = objectMapper.readValue(detalleResult.getResponse().getContentAsString(), ContratoDetalleDTO.class);
        assertNotNull(detalle);
        assertNotNull(detalle.getCoarrendatarios());
        assertEquals(2, detalle.getCoarrendatarios().size());

        CoarrendatarioDTO co1 = detalle.getCoarrendatarios().stream()
                .filter(c -> c.idPersona().equals(PERSONA_COARRENDATARIO_1)).findFirst().orElse(null);
        assertNotNull(co1, "Pedro Picapiedra debe estar en los coarrendatarios");
        assertEquals("Pedro Picapiedra", co1.nombrePersona());
        assertEquals("1000000020", co1.numeroDocumento());
        assertEquals("pedro@saed.com", co1.email());
    }

    @Test
    @DisplayName("GAP-F5-03.2: Arrendatario menor de edad sin tutor legal es rechazado")
    public void testCreateContrato_MinorResidentWithoutTutor_ThrowsValidation() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Persona 22 es menor (16 años), request sin idTutor
        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_MENOR_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("1500000.00"),
                plantillaParticipantesOrg1Id,
                null, // idTutor nulo
                null,
                false
        );

        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GAP-F5-03.3: Arrendatario menor de edad con tutor legal activo se persiste exitosamente")
    public void testCreateContrato_MinorResidentWithValidTutor_Success() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_MENOR_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("1600000.00"),
                plantillaParticipantesOrg1Id,
                PERSONA_TUTOR_1, // Maria Perez (tutora legal activa)
                null,
                false
        );

        MvcResult result = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> respMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long contratoId = ((Number) respMap.get("id")).longValue();
        assertNotNull(contratoId);

        // Validar persistencia en CONTRATOS.ID_TUTOR
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Long idTutorDb = jdbcTemplate.queryForObject(
                "SELECT ID_TUTOR FROM CONTRATOS WHERE ID_CONTRATO = ?", Long.class, contratoId);
        assertEquals(PERSONA_TUTOR_1, idTutorDb);

        // Consultar detalle y validar datos del tutor
        MvcResult detalleResult = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        ContratoDetalleDTO detalle = objectMapper.readValue(detalleResult.getResponse().getContentAsString(), ContratoDetalleDTO.class);
        assertEquals(PERSONA_TUTOR_1, detalle.getIdTutor());
        assertEquals("Maria Perez", detalle.getNombreTutor());
        assertEquals("1000000023", detalle.getCedulaTutor());
        assertEquals("MADRE", detalle.getParentescoTutor());
    }

    @Test
    @DisplayName("GAP-F5-03.4: Tutor legal no puede ser la misma persona que el residente principal")
    public void testCreateContrato_TutorCannotBeSameAsPrincipal() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_ADULTO_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("1700000.00"),
                plantillaParticipantesOrg1Id,
                PERSONA_ADULTO_1, // Mismo ID
                null,
                false
        );

        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GAP-F5-03.5: Sincronización de habitabilidad crea habitante físico y valida quota de convivientes")
    public void testCoarrendatarioHabitabilitySync_CreatesResidenteUnidad() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        List<CoarrendatarioCreateDTO> coarrendatarios = List.of(
                new CoarrendatarioCreateDTO(null, PERSONA_COARRENDATARIO_1, "COARRENDATARIO", "ACTIVO", true)
        );

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_ADULTO_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("1900000.00"),
                plantillaParticipantesOrg1Id,
                null,
                coarrendatarios,
                true // Sincronizar habitabilidad
        );

        MvcResult result = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        // Validar que en RESIDENTES_UNIDAD existe Pedro como CONVIVIENTE
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer countHabitante = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND TIPO_RESIDENTE = 'CONVIVIENTE' AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_1_ID, PERSONA_COARRENDATARIO_1
        );
        assertEquals(1, countHabitante, "El coarrendatario sincronizado debe estar registrado como CONVIVIENTE en RESIDENTES_UNIDAD");
    }

    @Test
    @DisplayName("GAP-F5-03.6: Endpoints de ContratosAdminController permiten agregar, listar y eliminar coarrendatarios")
    public void testContratosAdmin_AddAndListAndRemoveCoarrendatario_Authorized() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Crear contrato base simple
        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_ADULTO_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("2100000.00"),
                plantillaParticipantesOrg1Id
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Long contratoId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // 1. Agregar coarrendatario vía ContratosAdminController
        CoarrendatarioCreateDTO addDto = new CoarrendatarioCreateDTO(
                contratoId,
                PERSONA_COARRENDATARIO_1,
                "COARRENDATARIO",
                "ACTIVO",
                false
        );

        MvcResult addRes = mockMvc.perform(post("/api/v1/contratos-admin/coarrendatarios")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addDto)))
                .andExpect(status().isCreated())
                .andReturn();

        // 2. Listar coarrendatarios
        MvcResult listRes = mockMvc.perform(get("/api/v1/contratos-admin/coarrendatarios/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        String body = listRes.getResponse().getContentAsString();
        assertTrue(body.contains("Pedro Picapiedra"));

        // 3. Eliminar coarrendatario
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Long idCoarrendatario = jdbcTemplate.queryForObject(
                "SELECT ID_CONTRATO_RESIDENTE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO = ? AND ID_PERSONA = ?",
                Long.class, contratoId, PERSONA_COARRENDATARIO_1
        );
        assertNotNull(idCoarrendatario);

        mockMvc.perform(delete("/api/v1/contratos-admin/coarrendatarios/" + idCoarrendatario)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar que ya no existe
        Integer countPostDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO_RESIDENTE = ?",
                Integer.class, idCoarrendatario
        );
        assertEquals(0, countPostDelete);
    }

    @Test
    @DisplayName("GAP-F5-03.7: Control Anti-IDOR bloquea administradores de otra propiedad/organización")
    public void testContratosAdmin_AntiIdor_AdminProp2CannotAccessContractOfProp1() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        String tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);

        // Crear contrato en Propiedad 1
        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_ADULTO_1,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("2100000.00"),
                plantillaParticipantesOrg1Id
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Long contratoId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Admin Prop 2 intenta listar coarrendatarios del contrato de Prop 1 -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/contratos-admin/coarrendatarios/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isForbidden());

        // Admin Prop 2 intenta agregar coarrendatario al contrato de Prop 1 -> 403 FORBIDDEN
        CoarrendatarioCreateDTO addDto = new CoarrendatarioCreateDTO(
                contratoId,
                PERSONA_COARRENDATARIO_1,
                "COARRENDATARIO",
                "ACTIVO",
                false
        );

        mockMvc.perform(post("/api/v1/contratos-admin/coarrendatarios")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-F5-03.8: VariableResolverService compila variables de tutores y firmas de coarrendatarios en HTML y PDF")
    public void testVariableResolver_ResolvesTutorAndCoarrendatarios() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        List<CoarrendatarioCreateDTO> coarrendatarios = List.of(
                new CoarrendatarioCreateDTO(null, PERSONA_COARRENDATARIO_1, "COARRENDATARIO", "ACTIVO", false),
                new CoarrendatarioCreateDTO(null, PERSONA_COARRENDATARIO_2, "COARRENDATARIO", "ACTIVO", false)
        );

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                PERSONA_MENOR_1, // Menor
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("2300000.00"),
                plantillaParticipantesOrg1Id,
                PERSONA_TUTOR_1, // Tutora Maria Perez
                coarrendatarios,
                false
        );

        MvcResult result = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Long contratoId = ((Number) objectMapper.readValue(result.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        MvcResult detalleRes = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        ContratoDetalleDTO detalle = objectMapper.readValue(detalleRes.getResponse().getContentAsString(), ContratoDetalleDTO.class);
        String html = detalle.getHtmlCongelado();
        assertNotNull(html, "El snapshot HTML no debe ser nulo");

        // Validar resolución de variables de tutor
        assertTrue(html.contains("Maria Perez"), "Debe contener el nombre del tutor");
        assertTrue(html.contains("1000000023"), "Debe contener el documento del tutor");
        assertTrue(html.contains("MADRE"), "Debe contener la relación del tutor");

        // Validar resolución de variables de coarrendatarios
        assertTrue(html.contains("Total Coarrendatarios: 2"), "Debe contener el total de coarrendatarios");
        assertTrue(html.contains("Pedro Picapiedra"), "Debe contener a Pedro en firmas o lista");
        assertTrue(html.contains("Pablo Marmol"), "Debe contener a Pablo en firmas o lista");
        assertTrue(html.contains("firmas-coarrendatarios") && html.contains("COARRENDATARIO:"), "Debe contener el bloque XHTML de firmas generado");

        // Validar documento PDF generado
        assertNotNull(detalle.getDocumentoUrl(), "Debe existir URL del PDF generado");
        assertNotNull(detalle.getDocumentoHash(), "Debe existir SHA-256 del PDF");
        assertTrue(detalle.getDocumentoTamanoBytes() > 0, "El PDF debe tener contenido binario válido");
    }

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (?, ?, ?, ?)",
                    id, nombre, nit, email);
        } else {
            jdbcTemplate.update(
                    "UPDATE ORGANIZACIONES SET NOMBRE = ?, IDENTIFICACION_FISCAL = ?, EMAIL_CONTACTO = ? WHERE ID_ORGANIZACION = ?",
                    nombre, nit, email, id);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'Carrera 99', 'Medellin', 'Colombia', 'MIXTA', 'ACTIVA')",
                    id, orgId, nombre);
        } else {
            jdbcTemplate.update(
                    "UPDATE PROPIEDADES SET ID_ORGANIZACION = ?, NOMBRE = ?, ESTADO = 'ACTIVA' WHERE ID_PROPIEDAD = ?",
                    orgId, nombre, id);
        }
    }

    private void ensureUnidad(long id, long propId, String num) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    id, propId, num);
        } else {
            jdbcTemplate.update(
                    "UPDATE UNIDADES SET ID_PROPIEDAD = ?, IDENTIFICADOR = ?, ESTADO = 'ACTIVA' WHERE ID_UNIDAD = ?",
                    propId, num, id);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email, LocalDate fechaNacimiento) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        java.sql.Date sqlBirth = fechaNacimiento != null ? java.sql.Date.valueOf(fechaNacimiento) : null;
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, FECHA_NACIMIENTO) " +
                    "VALUES (?, 1, ?, 'NATURAL', ?, ?, ?, ?)",
                    id, doc, nombre, apellido, email, sqlBirth);
        } else {
            jdbcTemplate.update(
                    "UPDATE PERSONAS SET NUMERO_DOCUMENTO = ?, PRIMER_NOMBRE = ?, PRIMER_APELLIDO = ?, EMAIL = ?, FECHA_NACIMIENTO = ? WHERE ID_PERSONA = ?",
                    doc, nombre, apellido, email, sqlBirth, id);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')",
                    id, personaId, username, email);
        } else {
            jdbcTemplate.update(
                    "UPDATE USUARIOS SET ID_PERSONA = ?, NOMBRE_USUARIO = ?, EMAIL = ?, ESTADO = 'ACTIVO' WHERE ID_USUARIO = ?",
                    personaId, username, email, id);
        }
    }

    private void ensureTutorRelation(long menorId, long tutorId, String parentesco) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TUTORES WHERE ID_PERSONA_MENOR = ? AND ID_PERSONA_TUTOR = ?",
                Integer.class, menorId, tutorId);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO TUTORES (ID_PERSONA_MENOR, ID_PERSONA_TUTOR, PARENTESCO, ESTADO) " +
                    "VALUES (?, ?, ?, 'ACTIVO')",
                    menorId, tutorId, parentesco);
        } else {
            jdbcTemplate.update(
                    "UPDATE TUTORES SET PARENTESCO = ?, ESTADO = 'ACTIVO' WHERE ID_PERSONA_MENOR = ? AND ID_PERSONA_TUTOR = ?",
                    parentesco, menorId, tutorId);
        }
    }

    private void seedMemberships() {
        try {
            Long defaultPlanId = jdbcTemplate.queryForObject(
                    "SELECT ID_PLAN FROM PLANES WHERE (LIMITE_USUARIOS IS NULL OR LIMITE_USUARIOS = 0 OR LIMITE_USUARIOS >= 50) AND ROWNUM = 1",
                    Long.class
            );
            if (defaultPlanId != null) {
                Integer count1 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = 1", Integer.class);
                if (count1 == null || count1 == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                            "VALUES (1, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')",
                            defaultPlanId);
                } else {
                    jdbcTemplate.update(
                            "UPDATE MEMBRESIAS SET ID_PLAN = ?, ESTADO = 'ACTIVA', FECHA_FIN = TRUNC(SYSDATE) + 365 WHERE ID_ORGANIZACION = 1",
                            defaultPlanId);
                }

                Integer count2 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = 9992", Integer.class);
                if (count2 == null || count2 == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                            "VALUES (9992, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')",
                            defaultPlanId);
                }
            }
        } catch (Exception e) {
            System.err.println("SEED MEMBERSHIPS NOTICE: " + e.getMessage());
        }
    }

    private void seedPlantilla() {
        try {
            jdbcTemplate.update("DELETE FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_PARTICIPANTS_TEST'");
            jdbcTemplate.update(
                    "INSERT INTO PLANTILLAS_CONTRATOS (ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) " +
                    "VALUES (1, 'TPL_PARTICIPANTS_TEST', 'Plantilla Participantes Test', 'INICIAL', " +
                    "'<html><head><style>body { font-family: sans-serif; } table { width: 100%; }</style></head><body>" +
                    "<h1>CONTRATO DE ARRENDAMIENTO CON TUTOR Y COARRENDATARIOS</h1>" +
                    "<p>Propiedad: ${propiedad.nombre}</p>" +
                    "<p>Arrendatario: ${residente.nombreCompleto}</p>" +
                    "<p>Tutor Legal: ${nombreTutor}</p>" +
                    "<p>Documento Tutor: ${cedulaTutor}</p>" +
                    "<p>Parentesco Tutor: ${relacionTutor}</p>" +
                    "<p>Total Coarrendatarios: ${coarrendatarios.total}</p>" +
                    "<p>Coarrendatarios: ${coarrendatarios.nombres}</p>" +
                    "<div>Firmas Coarrendatarios:</div>" +
                    "${coarrendatarios.firmas_html}" +
                    "</body></html>', 'ACTIVA', 1, 1)"
            );

            plantillaParticipantesOrg1Id = jdbcTemplate.queryForObject(
                    "SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_PARTICIPANTS_TEST'", Long.class);
        } catch (Exception e) {
            System.err.println("SEED PLANTILLA NOTICE: " + e.getMessage());
        }
    }

    private void seedAsignaciones() {
        jdbcTemplate.execute("""
            DECLARE
                v_rol_super NUMBER;
                v_rol_org   NUMBER;
                v_rol_prop  NUMBER;
                v_rol_res   NUMBER;
            BEGIN
                SELECT ID_ROL INTO v_rol_super FROM ROLES WHERE CODIGO = 'SUPERADMIN';
                SELECT ID_ROL INTO v_rol_org   FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION';
                SELECT ID_ROL INTO v_rol_prop  FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD';
                SELECT ID_ROL INTO v_rol_res   FROM ROLES WHERE CODIGO = 'RESIDENTE';

                DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (101, 102, 301, 981, 991, 104)
                    OR (ID_USUARIO = 1 AND ID_ROL = v_rol_super)
                    OR (ID_USUARIO = 2 AND ID_ROL = v_rol_prop AND NVL(ID_ORGANIZACION, -1) = 1 AND NVL(ID_PROPIEDAD, -1) = 1)
                    OR (ID_USUARIO = 8 AND ID_ROL = v_rol_org AND NVL(ID_ORGANIZACION, -1) = 1)
                    OR (ID_USUARIO = 98 AND ID_ROL = v_rol_org AND NVL(ID_ORGANIZACION, -1) = 9992)
                    OR (ID_USUARIO = 99 AND ID_ROL = v_rol_prop AND NVL(ID_ORGANIZACION, -1) = 9992 AND NVL(ID_PROPIEDAD, -1) = 9992)
                    OR (ID_USUARIO = 4 AND ID_ROL = v_rol_res AND NVL(ID_UNIDAD, -1) = 1);

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (101, 1, v_rol_super, NULL, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (102, 2, v_rol_prop, 1, 1, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (301, 8, v_rol_org, 1, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (981, 98, v_rol_org, 9992, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (991, 99, v_rol_prop, 9992, 9992, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (104, 4, v_rol_res, 1, 1, 1, 'ACTIVA', TRUNC(SYSDATE));
            END;
        """);
    }
}
