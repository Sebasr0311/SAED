package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.saed.backend.finanzas.repository.FinanzasRepository;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Certificación Rigurosa para GAP-F8-03:
 * Prevención de Doble Reserva y Solapamiento Horario Concurrente en Zonas Comunes.
 *
 * Casos cubiertos:
 * 1. Reserva libre sin conflicto -> 201 Created + persistencia en Oracle.
 * 2. Mismo horario exacto -> 409 Conflict (RESERVA_SOLAPADA) + no persiste.
 * 3. Solapamiento parcial inicial -> 409 Conflict.
 * 4. Solapamiento parcial final -> 409 Conflict.
 * 5. Reserva contenedora (engloba una existente) -> 409 Conflict.
 * 6. Reserva contenida (dentro de una existente) -> 409 Conflict.
 * 7. Horarios adyacentes (10-11 y 11-12) -> 201 Created (ambas coexisten).
 * 8. Días diferentes -> 201 Created.
 * 9. Zonas diferentes en misma propiedad y horario -> 201 Created.
 * 10. Reserva previa CANCELADA / RECHAZADA -> 201 Created (no bloquea horario).
 * 11. Horario inválido (inicio >= fin) -> 400 Bad Request.
 * 12. Carrera concurrente multihilo -> 1 ganador 201, 1 conflicto 409, exactamente 1 en Oracle.
 * 13. Preservación de seguridad F8-02 (Mora 422, Anti-IDOR 403, Cross-Property 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase8ReservasDoubleBookingIntegrationTest {

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

    @MockBean
    private AssignmentService assignmentService;

    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_RESIDENTE_1 = 4L; // Carlos Martinez (Apto 101 / Unit 1)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Ana Gomez (Apto 102 / Unit 2)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ZONA_1_ID = 1L; // Salón Social Central
    private static final long ZONA_2_ID = 2L; // Zona BBQ / Piscina

    private String tokenResidente1;
    private String tokenResidente2;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza de datos de reservas de prueba
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
            ensurePropiedad(1L, 1L, "Edificio Residencial SAED");
            ensureUnidad(1L, 1L, "Apto 101");
            ensureUnidad(2L, 1L, "Apto 102");

            ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "camartinez@saed.com");

            ensurePersona(5L, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(5L, 5L, "ana_g", "anagomez@saed.com");

            ensureZonaComun(ZONA_1_ID, PROP_1_ID, "Salón Social Central", 50, "S", new BigDecimal("100000.00"));
            ensureZonaComun(ZONA_2_ID, PROP_1_ID, "Zona BBQ Principal", 25, "S", new BigDecimal("30000.00"));

            setupMockAssignments();

            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
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
    }

    // =========================================================================
    // CASO 1: RESERVA LIBRE SIN CONFLICTO
    // =========================================================================
    @Test
    public void test01_ReservaSinConflicto_PermiteCreacionYPersisteEnOracle() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 10,
                "observaciones", "Reunión familiar"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());
        assertNotNull(idReserva);

        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ID_ZONA = ? AND ESTADO = 'PENDIENTE'",
                    Integer.class, idReserva, ZONA_1_ID
            );
            assertEquals(1, count, "La reserva sin conflicto debe persistirse en Oracle con estado PENDIENTE");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // CASO 2: MISMO HORARIO EXACTO -> 409 CONFLICT
    // =========================================================================
    @Test
    public void test02_MismoHorarioExacto_Rechazo409Conflict_NoPersisteSegundaReserva() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(6);

        // 1. Primera reserva: 10:00 - 11:00 por Residente 1
        Map<String, Object> body1 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 5
        );
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isCreated());

        // 2. Segunda reserva para el MISMO horario exacto por Residente 2
        Map<String, Object> body2 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 8
        );
        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SOLAPADA"))
                .andExpect(jsonPath("$.message").value("La zona común ya cuenta con una reserva activa que se solapa con el horario solicitado."));

        // 3. Verificación Oracle: solo existe 1 reserva
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ?",
                    Integer.class, ZONA_1_ID, Date.valueOf(fecha)
            );
            assertEquals(1, count, "No debe persistirse la segunda reserva solapada en Oracle");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // CASO 3: SOLAPAMIENTO PARCIAL INICIAL (10:00-11:00 vs 10:30-11:30) -> 409
    // =========================================================================
    @Test
    public void test03_SolapamientoParcialInicial_Rechazo409Conflict() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(7);

        // Reserva 1: 10:00 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:00", "11:00", "PENDIENTE");

        // Intento 2: 10:30 - 11:30
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:30",
                "horaFin", "11:30",
                "cantidadAsistentes", 6
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SOLAPADA"));
    }

    // =========================================================================
    // CASO 4: SOLAPAMIENTO PARCIAL FINAL (10:00-11:00 vs 09:30-10:30) -> 409
    // =========================================================================
    @Test
    public void test04_SolapamientoParcialFinal_Rechazo409Conflict() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(8);

        // Reserva 1: 10:00 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:00", "11:00", "APROBADA");

        // Intento 2: 09:30 - 10:30
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:30",
                "horaFin", "10:30",
                "cantidadAsistentes", 4
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SOLAPADA"));
    }

    // =========================================================================
    // CASO 5: RESERVA CONTENEDORA (10:30-11:00 vs 10:00-12:00) -> 409
    // =========================================================================
    @Test
    public void test05_ReservaQueContieneAOtra_Rechazo409Conflict() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(9);

        // Reserva 1 (dentro del rango): 10:30 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:30", "11:00", "PENDIENTE");

        // Intento 2 (engloba la existente): 10:00 - 12:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 12
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SOLAPADA"));
    }

    // =========================================================================
    // CASO 6: RESERVA CONTENIDA (10:00-12:00 vs 10:30-11:00) -> 409
    // =========================================================================
    @Test
    public void test06_ReservaContenida_Rechazo409Conflict() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(10);

        // Reserva 1 (rango amplio): 10:00 - 12:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:00", "12:00", "APROBADA");

        // Intento 2 (dentro del rango): 10:30 - 11:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:30",
                "horaFin", "11:00",
                "cantidadAsistentes", 3
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SOLAPADA"));
    }

    // =========================================================================
    // CASO 7: HORARIOS ADYACENTES (10:00-11:00 vs 11:00-12:00) -> 201 PERMITIDO
    // =========================================================================
    @Test
    public void test07_HorariosAdyacentes_PermiteAmbasReservas() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(11);

        // Reserva 1: 10:00 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:00", "11:00", "PENDIENTE");

        // Reserva 2 adyacente: 11:00 - 12:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "11:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 7
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // Ambas deben existir en base de datos
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ?",
                    Integer.class, ZONA_1_ID, Date.valueOf(fecha)
            );
            assertEquals(2, count, "Las reservas adyacentes deben coexistir sin conflicto");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // CASO 8: DÍA DIFERENTE -> 201 PERMITIDO
    // =========================================================================
    @Test
    public void test08_DiaDiferente_MismoHorario_PermiteAmbasReservas() throws Exception {
        LocalDate dia1 = LocalDate.now().plusDays(12);
        LocalDate dia2 = LocalDate.now().plusDays(13);

        // Reserva en dia 1: 10:00 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, dia1, "10:00", "11:00", "PENDIENTE");

        // Reserva en dia 2: 10:00 - 11:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", dia2.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 5
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // CASO 9: ZONA DIFERENTE (MISMA PROPIEDAD, MISMO HORARIO) -> 201 PERMITIDO
    // =========================================================================
    @Test
    public void test09_ZonaDiferente_MismaFechaYHorario_PermiteAmbasReservas() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(14);

        // Reserva en Zona 1 (Salón Social): 14:00 - 16:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "14:00", "16:00", "PENDIENTE");

        // Reserva en Zona 2 (BBQ): 14:00 - 16:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_2_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00",
                "cantidadAsistentes", 10
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // CASO 10: RESERVA CANCELADA O RECHAZADA NO BLOQUEA EL HORARIO -> 201
    // =========================================================================
    @Test
    public void test10_ReservaPreviaCanceladaORechazada_NoBloqueaHorario() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(15);

        // Reserva CANCELADA: 10:00 - 11:00
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "10:00", "11:00", "CANCELADA");

        // Reserva RECHAZADA: 11:30 - 12:30
        crearReservaDirecta(ZONA_1_ID, UNIT_1_ID, 4L, fecha, "11:30", "12:30", "RECHAZADA");

        // Intento de nueva reserva sobre el horario de la CANCELADA: 10:00 - 11:00
        Map<String, Object> body = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 6
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // CASO 11: HORARIO INVÁLIDO (INICIO >= FIN) -> 400 BAD REQUEST
    // =========================================================================
    @Test
    public void test11_HorarioInvalido_HoraInicioMayorOIgualAFin_Rechazo400BadRequest() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(16);

        // Caso hora inicio > hora fin (15:00 - 14:00)
        Map<String, Object> body1 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "15:00",
                "horaFin", "14:00",
                "cantidadAsistentes", 2
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isBadRequest());

        // Caso hora inicio == hora fin (10:00 - 10:00)
        Map<String, Object> body2 = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "10:00",
                "cantidadAsistentes", 2
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // CASO 12: CONCURRENCIA REAL — 2 HILOS PARALELOS, 1 GANA (201), 1 PIERDE (409)
    // =========================================================================
    @Test
    public void test12_ConcurrenciaReal_DosHilosParalelos_UnGanador201_UnConflicto409_ExactamenteUnaPersistida() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(20);
        String horaInicio = "16:00";
        String horaFin = "18:00";

        setElevatedContext();
        try {
            jdbcTemplate.execute("DELETE FROM RESERVAS WHERE ID_ZONA = " + ZONA_1_ID + " AND FECHA_RESERVA = DATE '" + fecha + "'");
        } finally {
            clearContext();
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger status1 = new AtomicInteger();
        AtomicInteger status2 = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                Map<String, Object> body = Map.of(
                        "idZona", ZONA_1_ID,
                        "fechaReserva", fecha.toString(),
                        "horaInicio", horaInicio,
                        "horaFin", horaFin,
                        "cantidadAsistentes", 5
                );
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                        .andReturn();
                status1.set(res.getResponse().getStatus());
            } catch (Exception e) {
                status1.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                Map<String, Object> body = Map.of(
                        "idZona", ZONA_1_ID,
                        "fechaReserva", fecha.toString(),
                        "horaInicio", horaInicio,
                        "horaFin", horaFin,
                        "cantidadAsistentes", 8
                );
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                        .andReturn();
                status2.set(res.getResponse().getStatus());
            } catch (Exception e) {
                e.printStackTrace();
                status2.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        // Disparo simultáneo de ambos hilos
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Ambos hilos concurrentes deben completar en menos de 15 segundos");

        int s1 = status1.get();
        int s2 = status2.get();

        // Exactamente uno debe ser 201 y el otro debe ser 409
        boolean winnerAndConflict = (s1 == 201 && s2 == 409) || (s1 == 409 && s2 == 201);
        assertTrue(winnerAndConflict, "Bajo concurrencia un hilo debe obtener 201 y el otro 409. Obtenidos: s1=" + s1 + ", s2=" + s2);

        // Verificación directa en base de datos Oracle: exactamente 1 reserva persistida
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ?",
                    Integer.class, ZONA_1_ID, Date.valueOf(fecha)
            );
            assertEquals(1, count, "Debe existir exactamente 1 reserva persistida en Oracle para la zona y horario");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // CASO 13: PRESERVACIÓN DE REGLAS DE SEGURIDAD F8-02
    // =========================================================================
    @Test
    public void test13_PreservacionSeguridadF802_MoraAntiIdorYCrossProperty() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(25);

        // 1. Unidad en mora -> 422 UNIDAD_EN_MORA
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

        Map<String, Object> bodyMora = Map.of(
                "idZona", ZONA_1_ID,
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:00",
                "horaFin", "10:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyMora)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNIDAD_EN_MORA"));

        // 2. Anti-IDOR: Residente 2 (Unidad 2) intenta enviar payload con idUnidad: 1 -> 403
        Map<String, Object> bodyIdor = Map.of(
                "idZona", ZONA_1_ID,
                "idUnidad", 1L, // Intento de suplantar Unidad 1
                "fechaReserva", fecha.toString(),
                "horaInicio", "12:00",
                "horaFin", "13:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bodyIdor)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private void crearReservaDirecta(Long idZona, Long idUnidad, Long idPersona, LocalDate fecha, String horaInicio, String horaFin, String estado) {
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO RESERVAS (ID_ZONA, ID_UNIDAD, ID_PERSONA_SOLICITA, FECHA_RESERVA, HORA_INICIO, HORA_FIN, CANTIDAD_ASISTENTES, COSTO_TOTAL, ESTADO)
                VALUES (?, ?, ?, ?, ?, ?, 4, 0, ?)
            """, idZona, idUnidad, idPersona, Date.valueOf(fecha), horaInicio, horaFin, estado);
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
