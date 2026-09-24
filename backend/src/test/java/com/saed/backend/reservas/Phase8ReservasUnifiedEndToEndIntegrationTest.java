package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Integral y Unificada de Extremo a Extremo — GAP-F8-06.
 *
 * Demuestra la interacción integrada y real de:
 * Usuario -> JWT/Security -> SaedContextHolder -> Unidad -> Propiedad ->
 * Zona Común -> Estado Zona -> Estado Financiero (Paz y Salvo / Mora) ->
 * Horario -> Solapamiento -> Persistencia y Concurrencia en Oracle 23ai / XE.
 *
 * Utiliza implementaciones 100% reales contra Oracle (sin mocks de negocio):
 * - PazYSalvoService REAL
 * - FinanzasRepository REAL
 * - ReservasRepository REAL
 * - ZonaComunRepository REAL
 * - AssignmentService REAL (alimentado por tablas canónicas Oracle)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase8ReservasUnifiedEndToEndIntegrationTest {

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

    @Autowired
    private FinanzasRepository finanzasRepository;

    @Autowired
    private ReservasRepository reservasRepository;

    @Autowired
    private AssignmentService assignmentService;

    // Constantes de Identidad y Roles
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_RESIDENTE_1 = 4L; // Residente moroso (Unidad 1)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Residente al día (Unidad 2)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_CONVIVIENTE_1 = 6L; // Conviviente (Unidad 2)
    private static final long ASSIGN_CONVIVIENTE_1 = 106L;

    private static final long USER_RESIDENTE_3 = 7L; // Residente al día (Unidad 3)
    private static final long ASSIGN_RESIDENTE_3 = 107L;

    // Organizaciones, Propiedades y Unidades
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;
    private static final long UNIT_3_ID = 701L;

    private String tokenAdminProp1;
    private String tokenResidente1;
    private String tokenResidente2;
    private String tokenConviviente1;
    private String tokenResidente3;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza de reservas y estados financieros previos
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE '%INTEGRAL%' OR NOMBRE LIKE '%CONCURRENTE%');
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE '%INTEGRAL%' OR NOMBRE LIKE '%CONCURRENTE%';
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 701));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 701);
                END;
            """);

            // Asegurar entidades base en Oracle
            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");

            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");
            ensureUnidad(UNIT_3_ID, PROP_1_ID, "Apto 701");

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin", "admin@saed.com");

            ensurePersona(USER_RESIDENTE_1, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(USER_RESIDENTE_1, USER_RESIDENTE_1, "carlos_m", "camartinez@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "ana_g", "anagomez@saed.com");

            ensurePersona(USER_CONVIVIENTE_1, "1000000006", "Pedro", "Perez", "pperez@saed.com");
            ensureUsuario(USER_CONVIVIENTE_1, USER_CONVIVIENTE_1, "pedro_p", "pperez@saed.com");

            ensurePersona(USER_RESIDENTE_3, "1000000007", "Maria", "Rodriguez", "mrodriguez@saed.com");
            ensureUsuario(USER_RESIDENTE_3, USER_RESIDENTE_3, "maria_r", "mrodriguez@saed.com");

            // Asignaciones en base de datos real para AssignmentService
            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_1_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);
            ensureAsignacion(ASSIGN_CONVIVIENTE_1, USER_CONVIVIENTE_1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_3, USER_RESIDENTE_3, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_3_ID);

            ensureResidenteUnidad(UNIT_1_ID, USER_RESIDENTE_1, "PROPIETARIO");
            ensureResidenteUnidad(UNIT_2_ID, USER_RESIDENTE_2, "PROPIETARIO");
            ensureResidenteUnidad(UNIT_2_ID, USER_CONVIVIENTE_1, "CONVIVIENTE");
            ensureResidenteUnidad(UNIT_3_ID, USER_RESIDENTE_3, "PROPIETARIO");

            // Recalcular carteras al día por defecto para unidades 2 y 3
            finanzasRepository.recalcularCarteraUnidad(UNIT_2_ID);
            finanzasRepository.recalcularCarteraUnidad(UNIT_3_ID);

            // Generar JWTs reales
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenConviviente1 = jwtProvider.generateIdentityToken(USER_CONVIVIENTE_1);
            tokenResidente3 = jwtProvider.generateIdentityToken(USER_RESIDENTE_3);

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
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE '%INTEGRAL%' OR NOMBRE LIKE '%CONCURRENTE%');
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE '%INTEGRAL%' OR NOMBRE LIKE '%CONCURRENTE%';
                    DELETE FROM RESERVAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 701));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 701);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 701);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    // =========================================================================
    // ESCENARIO INTEGRAL DE EXTREMO A EXTREMO (STEPS 1 AL 12)
    // =========================================================================

    @Test
    @DisplayName("GAP-F8-06 — Pipeline Integral E2E: Ciclo de vida completo de Zonas Comunes y Reservas")
    void testPipelineIntegralCompleto_Pasos1al12() throws Exception {
        LocalDate fechaObjetivo = LocalDate.now().plusDays(5);

        // ---------------------------------------------------------------------
        // STEP 1 — ADMIN_PROPIEDAD crea zona común activa en Oracle
        // ---------------------------------------------------------------------
        CreateZonaComunDTO createZonaDTO = new CreateZonaComunDTO(
                "SALON_COMUNAL_INTEGRAL_E2E",
                "SALON_SOCIAL",
                60,
                "S",
                new BigDecimal("15000.00"),
                "ACTIVA"
        );

        MvcResult resultCrearZona = mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createZonaDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idZona", notNullValue()))
                .andExpect(jsonPath("$.nombre", is("SALON_COMUNAL_INTEGRAL_E2E")))
                .andExpect(jsonPath("$.estado", is("ACTIVA")))
                .andReturn();

        ZonaComunDTO zonaCreada = objectMapper.readValue(resultCrearZona.getResponse().getContentAsString(), ZonaComunDTO.class);
        Long idZona = zonaCreada.getIdZona();
        assertNotNull(idZona, "El ID de la zona creada debe ser retornado");

        // Verificación directa en Oracle
        setElevatedContext();
        try {
            Integer countZonaOracle = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ZONAS_COMUNES WHERE ID_ZONA = ? AND ESTADO = 'ACTIVA' AND ID_PROPIEDAD = ?",
                    Integer.class, idZona, PROP_1_ID);
            assertEquals(1, countZonaOracle, "La zona común debe estar persistida en Oracle con estado ACTIVA");
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 2 — RESIDENTE EN MORA intenta reservar -> 422 UNIDAD_EN_MORA
        // ---------------------------------------------------------------------
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
                VALUES (1, 1, '2026-05', 350000, 350000, TRUNC(SYSDATE) - 12, 'VENCIDA')
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_1_ID);
        } finally {
            clearContext();
        }

        Map<String, Object> reqMora = Map.of(
                "idZona", idZona,
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00",
                "cantidadAsistentes", 10,
                "costoTotal", 15000.00
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqMora)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNIDAD_EN_MORA")));

        // Verificación Oracle: no existe ninguna reserva
        setElevatedContext();
        try {
            Integer countReservas = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND ID_UNIDAD = 1", Integer.class, idZona);
            assertEquals(0, countReservas, "No debe haberse insertado ninguna reserva en Oracle para unidad morosa");
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 3 — ADMIN_PROPIEDAD cambia estado de zona a MANTENIMIENTO
        // ---------------------------------------------------------------------
        UpdateZonaComunDTO updateMaint = new UpdateZonaComunDTO(
                "SALON_COMUNAL_INTEGRAL_E2E",
                "SALON_SOCIAL",
                60,
                "S",
                new BigDecimal("15000.00"),
                "MANTENIMIENTO"
        );

        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateMaint)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("MANTENIMIENTO")));

        // Verificación Oracle
        setElevatedContext();
        try {
            String estadoZona = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZona);
            assertEquals("MANTENIMIENTO", estadoZona);
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 4 — RESIDENTE AL DÍA intenta reservar zona en mantenimiento -> 400 ZONA_NO_DISPONIBLE
        // ---------------------------------------------------------------------
        Map<String, Object> reqAlDiaMaint = Map.of(
                "idZona", idZona,
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqAlDiaMaint)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ZONA_NO_DISPONIBLE")));

        // Verificación Oracle: no hay reservas
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZona);
            assertEquals(0, count);
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 5 — REACTIVAR ZONA a ACTIVA por ADMIN_PROPIEDAD
        // ---------------------------------------------------------------------
        UpdateZonaComunDTO updateActiva = new UpdateZonaComunDTO(
                "SALON_COMUNAL_INTEGRAL_E2E",
                "SALON_SOCIAL",
                60,
                "S",
                new BigDecimal("15000.00"),
                "ACTIVA"
        );

        mockMvc.perform(put("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateActiva)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACTIVA")));

        // Verificación Oracle
        setElevatedContext();
        try {
            String estadoZona = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZona);
            assertEquals("ACTIVA", estadoZona);
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 6 — RESIDENTE 2 RESERVA 10:00-12:00 -> 201 Created y PENDIENTE
        // ---------------------------------------------------------------------
        Map<String, Object> reqReserva2 = Map.of(
                "idZona", idZona,
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 12,
                "costoTotal", 15000.00,
                "observaciones", "Reunión familiar"
        );

        MvcResult resultReserva2 = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqReserva2)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva2 = Long.parseLong(resultReserva2.getResponse().getContentAsString().trim());
        assertTrue(idReserva2 > 0);

        // Verificación Oracle
        setElevatedContext();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT ID_ZONA, ID_UNIDAD, ESTADO, HORA_INICIO, HORA_FIN FROM RESERVAS WHERE ID_RESERVA = ?",
                    idReserva2);
            assertEquals(idZona, ((Number) row.get("ID_ZONA")).longValue());
            assertEquals(UNIT_2_ID, ((Number) row.get("ID_UNIDAD")).longValue());
            assertEquals("PENDIENTE", row.get("ESTADO"));
            assertEquals("10:00", row.get("HORA_INICIO"));
            assertEquals("12:00", row.get("HORA_FIN"));
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 7 — RESIDENTE 3 INTENTA SOLAPAR (11:00-13:00) -> 409 RESERVA_SOLAPADA
        // ---------------------------------------------------------------------
        Map<String, Object> reqSolapada = Map.of(
                "idZona", idZona,
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "11:00",
                "horaFin", "13:00",
                "cantidadAsistentes", 5
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqSolapada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESERVA_SOLAPADA")));

        // Verificación Oracle: total de reservas en la zona sigue siendo exactamente 1
        setElevatedContext();
        try {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZona);
            assertEquals(1, total, "Solo debe existir la reserva legítima previa de 10:00 a 12:00");
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 8 — ANTI-IDOR: Residente 3 intenta especificar Unidad 2 en body -> 403 Forbidden
        // ---------------------------------------------------------------------
        Map<String, Object> reqIdor = Map.of(
                "idZona", idZona,
                "idUnidad", UNIT_2_ID, // Intento de suplantar la Unidad 2
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "16:00",
                "horaFin", "18:00"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqIdor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", containsString("No tiene permisos para realizar reservas en nombre de otra unidad")));

        // Verificación Oracle: total de reservas permanece en 1
        setElevatedContext();
        try {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZona);
            assertEquals(1, total);
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 9 — CONVIVIENTE reserva franja adyacente (12:00-14:00) -> 201 Created
        // ---------------------------------------------------------------------
        Map<String, Object> reqConviviente = Map.of(
                "idZona", idZona,
                "fechaReserva", fechaObjetivo.toString(),
                "horaInicio", "12:00", // Adyacente a la de 10:00-12:00
                "horaFin", "14:00",
                "cantidadAsistentes", 8,
                "costoTotal", 15000.00
        );

        MvcResult resultConv = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenConviviente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqConviviente)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReservaConv = Long.parseLong(resultConv.getResponse().getContentAsString().trim());
        assertTrue(idReservaConv > 0);

        // Verificación Oracle: unidad derivada del contexto del conviviente (Unidad 2)
        setElevatedContext();
        try {
            Map<String, Object> rowConv = jdbcTemplate.queryForMap(
                    "SELECT ID_ZONA, ID_UNIDAD, ID_PERSONA_SOLICITA, ESTADO, HORA_INICIO, HORA_FIN FROM RESERVAS WHERE ID_RESERVA = ?",
                    idReservaConv);
            assertEquals(idZona, ((Number) rowConv.get("ID_ZONA")).longValue());
            assertEquals(UNIT_2_ID, ((Number) rowConv.get("ID_UNIDAD")).longValue(), "Debe heredar la Unidad 2 del conviviente");
            assertEquals(USER_CONVIVIENTE_1, ((Number) rowConv.get("ID_PERSONA_SOLICITA")).longValue());
            assertEquals("PENDIENTE", rowConv.get("ESTADO"));
            assertEquals("12:00", rowConv.get("HORA_INICIO"));
            assertEquals("14:00", rowConv.get("HORA_FIN"));
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 10 — APROBACIÓN: ADMIN_PROPIEDAD aprueba la reserva del Residente 2
        // ---------------------------------------------------------------------
        mockMvc.perform(put("/api/v1/reservas/" + idReserva2 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        // Verificación Oracle: estado actualizado a APROBADA y auditoría de aprobación
        setElevatedContext();
        try {
            Map<String, Object> rowAprobada = jdbcTemplate.queryForMap(
                    "SELECT ESTADO, APROBADO_POR FROM RESERVAS WHERE ID_RESERVA = ?", idReserva2);
            assertEquals("APROBADA", rowAprobada.get("ESTADO"));
            assertEquals(USER_ADMIN_PROP_1, ((Number) rowAprobada.get("APROBADO_POR")).longValue());
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 11 — BAJA LÓGICA DE ZONA por ADMIN_PROPIEDAD -> 204 No Content
        // ---------------------------------------------------------------------
        setElevatedContext();
        int reservasAntesDeBaja;
        try {
            reservasAntesDeBaja = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZona);
            assertEquals(2, reservasAntesDeBaja, "Existen exactamente 2 reservas activas (Reserva 2 y Reserva Conviviente)");
        } finally {
            clearContext();
        }

        mockMvc.perform(delete("/api/v1/zonas-comunes/" + idZona)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNoContent());

        // Verificación Oracle: estado = INACTIVA (baja lógica, no DELETE físico)
        setElevatedContext();
        try {
            String estadoZonaEliminada = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ZONAS_COMUNES WHERE ID_ZONA = ?", String.class, idZona);
            assertEquals("INACTIVA", estadoZonaEliminada);
        } finally {
            clearContext();
        }

        // ---------------------------------------------------------------------
        // STEP 12 — PRESERVACIÓN HISTÓRICA: Reservas permanecen intactas en Oracle
        // ---------------------------------------------------------------------
        setElevatedContext();
        try {
            int reservasDespuesDeBaja = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ?", Integer.class, idZona);
            assertEquals(reservasAntesDeBaja, reservasDespuesDeBaja, "El conteo de reservas históricas debe ser idéntico tras la baja lógica");

            // Comprobar que los IDs y estados históricos persisten
            Integer countR2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ESTADO = 'APROBADA'", Integer.class, idReserva2);
            assertEquals(1, countR2);

            Integer countRConv = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_RESERVA = ? AND ESTADO = 'PENDIENTE'", Integer.class, idReservaConv);
            assertEquals(1, countRConv);
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // PRUEBA DE CONCURRENCIA REAL MULTI-HILO ENTRE UNIDADES DISTINTAS
    // =========================================================================

    @Test
    @DisplayName("GAP-F8-06 — Concurrencia Real Multi-Hilo: Dos unidades distintas compiten simultáneamente por el mismo slot")
    void testConcurrenciaRealMultiUnidad_MismoHorarioYEntornoAislado() throws Exception {
        LocalDate fechaConcurrencia = LocalDate.now().plusDays(10);
        String horaInicio = "18:00";
        String horaFin = "20:00";

        // Crear zona común dedicada para la prueba concurrente
        CreateZonaComunDTO zonaConcurrenteDTO = new CreateZonaComunDTO(
                "CANCHA_SINTETICA_CONCURRENTE",
                "DEPORTES",
                20,
                "S",
                new BigDecimal("20000.00"),
                "ACTIVA"
        );

        MvcResult resultZona = mockMvc.perform(post("/api/v1/zonas-comunes")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zonaConcurrenteDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        ZonaComunDTO zonaDTO = objectMapper.readValue(resultZona.getResponse().getContentAsString(), ZonaComunDTO.class);
        Long idZonaConcurrente = zonaDTO.getIdZona();

        Map<String, Object> reqUnidad2 = Map.of(
                "idZona", idZonaConcurrente,
                "fechaReserva", fechaConcurrencia.toString(),
                "horaInicio", horaInicio,
                "horaFin", horaFin,
                "cantidadAsistentes", 10,
                "costoTotal", 20000.00
        );

        Map<String, Object> reqUnidad3 = Map.of(
                "idZona", idZonaConcurrente,
                "fechaReserva", fechaConcurrencia.toString(),
                "horaInicio", horaInicio,
                "horaFin", horaFin,
                "cantidadAsistentes", 10,
                "costoTotal", 20000.00
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger statusUnidad2 = new AtomicInteger(0);
        AtomicInteger statusUnidad3 = new AtomicInteger(0);

        // Hilo 1: Residente Unidad 2
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente2)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnidad2)))
                        .andReturn();
                statusUnidad2.set(res.getResponse().getStatus());
            } catch (Exception e) {
                statusUnidad2.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        // Hilo 2: Residente Unidad 3
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenResidente3)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnidad3)))
                        .andReturn();
                statusUnidad3.set(res.getResponse().getStatus());
            } catch (Exception e) {
                statusUnidad3.set(500);
            } finally {
                doneLatch.countDown();
            }
        });

        // Disparo concurrente simultáneo
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "La ejecución concurrente debió terminar dentro de los 15 segundos");

        int s2 = statusUnidad2.get();
        int s3 = statusUnidad3.get();

        // Exactamente un ganador (201 Created) y un perdedor por conflicto (409 Conflict)
        boolean exactWinnerAndConflict = (s2 == 201 && s3 == 409) || (s2 == 409 && s3 == 201);
        assertTrue(exactWinnerAndConflict,
                "Bajo concurrencia de dos unidades distintas, una debe ganar con 201 y la otra fallar con 409. Obtenidos: s2=" + s2 + ", s3=" + s3);

        // Verificación directa en base de datos Oracle: exactamente 1 reserva persistida
        setElevatedContext();
        try {
            Integer totalPersistidas = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESERVAS WHERE ID_ZONA = ? AND FECHA_RESERVA = ? AND HORA_INICIO = ?",
                    Integer.class, idZonaConcurrente, Date.valueOf(fechaConcurrencia), horaInicio);
            assertEquals(1, totalPersistidas, "Debe existir exactamente 1 reserva persistida en Oracle para ese horario");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PARA SETUP DE ENTIDADES ORACLE REALES
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
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'ACTIVA')", id, idProp, identificador);
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
                UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = ?, ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA'
                WHERE ID_ASIGNACION = ?
            """, idUsuario, idRol, idOrg, idProp, idUnidad, idAsignacion);
        }
    }

    private void ensureResidenteUnidad(Long idUnidad, Long idPersona, String tipoResidente) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?", Integer.class, idUnidad, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO)
                VALUES (?, ?, ?, TRUNC(SYSDATE), 'ACTIVO')
            """, idUnidad, idPersona, tipoResidente);
        } else {
            jdbcTemplate.update("""
                UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = ?, ESTADO = 'ACTIVO'
                WHERE ID_UNIDAD = ? AND ID_PERSONA = ?
            """, tipoResidente, idUnidad, idPersona);
        }
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

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }
}
