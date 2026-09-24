package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.reservas.dto.ReservaDTO;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Pruebas de Integración y Seguridad para Reservas — GAP-F8-02.
 * Certifica el Bloqueo Automático por Mora Financiera y Aislamiento Multi-Tenant:
 *
 * 1. Unidad al día: HTTP 201 Created y persistencia exitosa en Oracle.
 * 2. Unidad morosa: HTTP 422 Unprocessable Entity, código UNIDAD_EN_MORA, reserva NO persistida.
 * 3. Mora después de pago: Bloqueo en mora, desbloqueo inmediato tras saldar la deuda en F6.
 * 4. Manipulación de unidad (Anti-IDOR): Residente no puede reservar a nombre de otra unidad para evadir mora.
 * 5. Cross-property: Rechazo al intentar reservar zonas de otra copropiedad.
 * 6. Conviviente: Rol RESIDENTE_CONVIVENCIA hereda las reglas financieras de su unidad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase8ReservasSecurityIntegrationTest {

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
    private PazYSalvoService pazYSalvoService;

    @Autowired
    private FinanzasRepository finanzasRepository;

    @MockBean
    private AssignmentService assignmentService;

    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_RESIDENTE_1 = 4L; // Carlos Martinez (Apto 101 / Unit 1)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Ana Gomez (Apto 102 / Unit 2)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_CONVIVIENTE_1 = 6L; // Pedro Perez (Apto 101 / Conviviente)
    private static final long ASSIGN_CONVIVIENTE_1 = 106L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    private static final long ZONA_1_ID = 1L;
    private static final long ZONA_FORANEA_ID = 9992L;

    private String tokenResidente1;
    private String tokenResidente2;
    private String tokenConviviente1;
    private String tokenAdminProp1;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza de datos de reservas y financiero en unidades de prueba
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);

            ensureOrganizacion(1L, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(9992L, "Organización Foránea", "900009992-9", "org9992@test.com");

            ensurePropiedad(1L, 1L, "Edificio Residencial SAED");
            ensurePropiedad(9992L, 9992L, "Propiedad 9992 Test");

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

            ensurePersona(6L, "1000000006", "Pedro", "Perez", "pperez@saed.com");
            ensureUsuario(6L, 6L, "pedro_p", "pperez@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            ensureZonaComun(ZONA_1_ID, PROP_1_ID, "Salón Social Central", 50, "S", new BigDecimal("100000.00"));
            ensureZonaComun(ZONA_FORANEA_ID, PROP_2_ID, "Piscina Foránea", 30, "S", new BigDecimal("50000.00"));

            setupMockAssignments();

            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenConviviente1 = jwtProvider.generateIdentityToken(USER_CONVIVIENTE_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
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
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 9992);
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
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Propiedad 9992 Test");
        UnitDTO unitOrg2 = new UnitDTO(UNIT_ORG2_ID, "Apto 9992");

        // Residente 1 (Unidad 1)
        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));

        // Residente 2 (Unidad 2)
        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));

        // Conviviente 1 (Unidad 1)
        AssignmentResponseDTO conv1Assign = new AssignmentResponseDTO();
        conv1Assign.setIdAsignacion(ASSIGN_CONVIVIENTE_1);
        conv1Assign.setOrganizacion(org1);
        conv1Assign.setPropiedad(prop1);
        conv1Assign.setUnidad(unit1);
        conv1Assign.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_CONVIVIENTE_1, USER_CONVIVIENTE_1)).thenReturn(Optional.of(conv1Assign));

        // Admin Propiedad 1
        AssignmentResponseDTO admin1Assign = new AssignmentResponseDTO();
        admin1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        admin1Assign.setOrganizacion(org1);
        admin1Assign.setPropiedad(prop1);
        admin1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(admin1Assign));
    }

    // =========================================================================
    // CASOS DE PRUEBA OBLIGATORIOS GAP-F8-02
    // =========================================================================

    @Test
    @DisplayName("Prueba 1 — Unidad al día: HTTP 201 Created y persistencia exitosa en Oracle")
    void test01_UnidadAlDia_PermiteCrearReservaYPersisteEnOracle() throws Exception {
        // La Unidad 1 está al día (sin cuotas pendientes ni en mora)
        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_1_ID);
        request.setFechaReserva(LocalDate.now().plusDays(5));
        request.setHoraInicio("14:00");
        request.setHoraFin("18:00");
        request.setCantidadAsistentes(15);
        request.setCostoTotal(new BigDecimal("100000.00"));
        request.setObservaciones("Celebración familiar al día");

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Long idReserva = Long.parseLong(responseBody.trim());
        assertTrue(idReserva > 0, "El ID de la reserva creada debe ser positivo");

        // Comprobación directa en base de datos Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ID_UNIDAD = 1 AND ESTADO = 'PENDIENTE'",
                    Integer.class, idReserva);
            assertEquals(1, count, "La reserva debe estar persistida en la tabla RESERVAS de Oracle");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("Prueba 2 — Unidad morosa: Bloqueo automático HTTP 422, código UNIDAD_EN_MORA y reserva NO persistida")
    void test02_UnidadEnMora_BloqueoAutomatico422_NoPersisteReserva() throws Exception {
        // Insertar cuota vencida (mora > 0) para la Unidad 1
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-05', 250000, 250000, TRUNC(SYSDATE) - 15, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_1_ID);
        request.setFechaReserva(LocalDate.now().plusDays(3));
        request.setHoraInicio("10:00");
        request.setHoraFin("13:00");
        request.setCantidadAsistentes(10);
        request.setCostoTotal(new BigDecimal("100000.00"));
        request.setObservaciones("Intento de reserva por moroso");

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("obligaciones financieras pendientes")));

        // Comprobación directa de persistencia: NO debe existir ninguna reserva para la unidad
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_UNIDAD = 1", Integer.class);
            assertEquals(0, count, "No debe haberse insertado ninguna reserva en Oracle para la unidad morosa");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("Prueba 3 — Mora después de pago: Bloqueo en mora y desbloqueo inmediato tras normalizar cartera")
    void test03_MoraDespuesDePago_PermiteReservarTrasPonerseAlDia() throws Exception {
        // 1. Crear escenario moroso
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-05', 300000, 300000, TRUNC(SYSDATE) - 10, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_1_ID);
        request.setFechaReserva(LocalDate.now().plusDays(7));
        request.setHoraInicio("09:00");
        request.setHoraFin("12:00");
        request.setCantidadAsistentes(8);

        // 2. Comprobar bloqueo con HTTP 422
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")));

        // 3. Simular normalización financiera (pago aplicado y cartera recalculada en F6)
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE CUOTAS SET SALDO_PENDIENTE = 0, ESTADO = 'PAGADA' WHERE ID_UNIDAD = 1");
            jdbcTemplate.update("DELETE FROM CARTERA WHERE ID_UNIDAD = 1");
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        // 4. Intentar reservar nuevamente: ahora sí debe continuar exitosamente
        MvcResult okResult = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(okResult.getResponse().getContentAsString().trim());
        assertTrue(idReserva > 0);

        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ?", Integer.class, idReserva);
            assertEquals(1, count, "La reserva debe estar persistida una vez la unidad esté al día");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("Prueba 4 — Manipulación de unidad (Anti-IDOR): Residente no puede reservar a nombre de otra unidad")
    void test04_ManipulacionUnidad_AntiIdor_RechazaIntentoSuplantacion() throws Exception {
        // Unidad 1 está en mora, Unidad 2 está al día
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-05', 200000, 200000, TRUNC(SYSDATE) - 5, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        // Residente 1 envía en el payload idUnidad = 2 (intentando usar la unidad al día de Ana Gomez)
        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_1_ID);
        request.setIdUnidad(UNIT_2_ID); // Suplantación
        request.setFechaReserva(LocalDate.now().plusDays(4));
        request.setHoraInicio("15:00");
        request.setHoraFin("19:00");
        request.setCantidadAsistentes(12);

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        // Verificar que no se creó reserva en ninguna de las dos unidades
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_UNIDAD IN (1, 2)", Integer.class);
            assertEquals(0, count, "No debe existir ninguna reserva persistida tras intento de IDOR");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("Prueba 5 — Cross-Property: Rechazo estricto al intentar reservar una zona de otra propiedad")
    void test05_CrossProperty_ZonaForanea_RechazoSeguridad() throws Exception {
        // Residente 1 de Propiedad 1 intenta reservar la Zona 9992 (Propiedad 9992)
        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_FORANEA_ID); // Zona de otra propiedad
        request.setFechaReserva(LocalDate.now().plusDays(2));
        request.setHoraInicio("11:00");
        request.setHoraFin("13:00");
        request.setCantidadAsistentes(5);

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Comprobar que no hay reserva en BD
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, ZONA_FORANEA_ID);
            assertEquals(0, count, "No debe haberse insertado ninguna reserva para la zona foránea");
        } finally {
            clearContext();
        }
    }

    @Test
    @DisplayName("Prueba 6 — Conviviente: Rol RESIDENTE_CONVIVENCIA hereda el bloqueo por mora de su unidad")
    void test06_Conviviente_HeredaBloqueoPorMoraDeUnidad() throws Exception {
        // 1. Unidad 1 en mora
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-05', 180000, 180000, TRUNC(SYSDATE) - 8, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        ReservaDTO request = new ReservaDTO();
        request.setIdZona(ZONA_1_ID);
        request.setFechaReserva(LocalDate.now().plusDays(6));
        request.setHoraInicio("16:00");
        request.setHoraFin("18:00");
        request.setCantidadAsistentes(4);

        // 2. Conviviente intenta reservar -> Bloqueado con HTTP 422 a nivel de unidad
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenConviviente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")))
                .andExpect(jsonPath("$.message", containsString("obligaciones financieras pendientes")));

        // 3. Normalizar unidad
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE CUOTAS SET SALDO_PENDIENTE = 0, ESTADO = 'PAGADA' WHERE ID_UNIDAD = 1");
            jdbcTemplate.update("DELETE FROM CARTERA WHERE ID_UNIDAD = 1");
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        // 4. Conviviente intenta reservar con unidad al día -> HTTP 201 Created exitoso
        MvcResult okResult = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenConviviente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(okResult.getResponse().getContentAsString().trim());
        assertTrue(idReserva > 0);

        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ID_PERSONA_SOLICITA = 6",
                    Integer.class, idReserva);
            assertEquals(1, count, "La reserva del conviviente debe quedar persistida con su propio ID_PERSONA");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PARA SETUP DE ENTIDADES ORACLE
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
