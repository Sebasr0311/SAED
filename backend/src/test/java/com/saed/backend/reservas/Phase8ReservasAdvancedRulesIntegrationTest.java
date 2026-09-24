package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.reservas.repository.ReservasRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Certificación Rigurosa para GAP-F8-07:
 * Reglas Avanzadas de Reservas en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de 26 Aserciones Deterministas:
 * - GAP-F8-07-01: Validación de Capacidad / Aforo Máximo (tests 1 a 4).
 * - GAP-F8-07-02: Rango Semántico de Asistentes (tests 5 a 8).
 * - GAP-F8-07-03: Herencia y Autoridad de Tarifa de Reserva (tests 9 a 12).
 * - GAP-F8-07-04: Cancelación por Residente / Anti-IDOR (tests 13 a 19).
 * - GAP-F8-07-05: Máquina de Estados y Transiciones Válidas/Terminales (tests 20 a 26).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase8ReservasAdvancedRulesIntegrationTest {

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

    // Identidades y Roles
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_RESIDENTE_2 = 5L; // Residente Unidad 2 (Apto 102)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_RESIDENTE_3 = 7L; // Residente Unidad 3 (Apto 701)
    private static final long ASSIGN_RESIDENTE_3 = 107L;

    // Organizaciones, Propiedades y Unidades
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;
    private static final long UNIT_3_ID = 701L;

    // Zonas Comunes de Prueba para F8-07
    private static final long ZONA_AFORO_50 = 801L;
    private static final long ZONA_AFORO_NULL = 802L;
    private static final long ZONA_GRATUITA = 803L;
    private static final long ZONA_PAGA_50K = 804L;
    private static final long ZONA_OPERACIONES = 805L;

    private String tokenAdminProp1;
    private String tokenResidente2;
    private String tokenResidente3;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza de reservas y zonas de prueba F8-07
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (801, 802, 803, 804, 805) OR ID_UNIDAD IN (2, 701);
                    DELETE FROM ZONAS_COMUNES WHERE ID_ZONA IN (801, 802, 803, 804, 805);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (2, 701));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (2, 701);
                END;
            """);

            // Entidades base
            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");
            ensureUnidad(UNIT_3_ID, PROP_1_ID, "Apto 701");

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin", "admin@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "ana_g", "anagomez@saed.com");

            ensurePersona(USER_RESIDENTE_3, "1000000007", "Maria", "Rodriguez", "mrodriguez@saed.com");
            ensureUsuario(USER_RESIDENTE_3, USER_RESIDENTE_3, "maria_r", "mrodriguez@saed.com");

            // Asignaciones
            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_3, USER_RESIDENTE_3, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_3_ID);

            ensureResidenteUnidad(UNIT_2_ID, USER_RESIDENTE_2, "PROPIETARIO");
            ensureResidenteUnidad(UNIT_3_ID, USER_RESIDENTE_3, "PROPIETARIO");

            // Asegurar zonas comunes especializadas
            ensureZonaComun(ZONA_AFORO_50, PROP_1_ID, "TEST_F807_AFORO_50", 50, "S", BigDecimal.ZERO);
            ensureZonaComun(ZONA_AFORO_NULL, PROP_1_ID, "TEST_F807_AFORO_NULL", null, "S", BigDecimal.ZERO);
            ensureZonaComun(ZONA_GRATUITA, PROP_1_ID, "TEST_F807_GRATUITA", 30, "S", BigDecimal.ZERO);
            ensureZonaComun(ZONA_PAGA_50K, PROP_1_ID, "TEST_F807_PAGA_50K", 40, "S", new BigDecimal("50000.00"));
            ensureZonaComun(ZONA_OPERACIONES, PROP_1_ID, "TEST_F807_OPERACIONES", 25, "S", new BigDecimal("20000.00"));

            // Carteras al día (sin mora)
            finanzasRepository.recalcularCarteraUnidad(UNIT_2_ID);
            finanzasRepository.recalcularCarteraUnidad(UNIT_3_ID);

            // Generación de tokens JWT
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
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
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (801, 802, 803, 804, 805) OR ID_UNIDAD IN (2, 701);
                    DELETE FROM ZONAS_COMUNES WHERE ID_ZONA IN (801, 802, 803, 804, 805);
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (2, 701));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (2, 701);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    // =========================================================================
    // GAP-F8-07-01: VALIDACIÓN DE CAPACIDAD / AFORO MÁXIMO
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("F8-07-01 [1/26]: Reserva con asistentes menor que aforo máximo -> 201 Created")
    void test01_Aforo_AsistentesMenorQueAforo_Permitido() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(10);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 25,
                "observaciones", "Asistentes dentro del aforo"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    @DisplayName("F8-07-01 [2/26]: Reserva con asistentes exactamente igual al aforo máximo -> 201 Created")
    void test02_Aforo_AsistentesIgualQueAforo_Permitido() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(11);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 50,
                "observaciones", "Aforo exacto al límite"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    @DisplayName("F8-07-01 [3/26]: Reserva con asistentes mayor que aforo máximo -> 400 AFORO_EXCEDIDO")
    void test03_Aforo_AsistentesMayorQueAforo_RechazadoConAforoExcedido() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(12);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 51,
                "observaciones", "Aforo excedido por 1"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AFORO_EXCEDIDO"));
    }

    @Test
    @Order(4)
    @DisplayName("F8-07-01 [4/26]: Zona sin aforo definido (null) permite cualquier cantidad positiva -> 201 Created")
    void test04_Aforo_ZonaSinAforoDefinido_PermiteCualquierCantidadPositiva() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(13);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_NULL,
                "fechaReserva", fecha.toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00",
                "cantidadAsistentes", 120,
                "observaciones", "Zona abierta sin aforo máximo límite"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // GAP-F8-07-02: RANGO SEMÁNTICO DE ASISTENTES
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("F8-07-02 [5/26]: Reserva con cantidad mínima válida (1 asistente) -> 201 Created")
    void test05_Asistentes_CantidadMinimaUno_Permitido() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(14);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:00",
                "horaFin", "10:00",
                "cantidadAsistentes", 1,
                "observaciones", "Uso individual"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(6)
    @DisplayName("F8-07-02 [6/26]: Reserva con cantidad de asistentes igual a 0 -> 400 ASISTENTES_INVALIDOS")
    void test06_Asistentes_CantidadCero_RechazadoConAsistentesInvalidos() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(15);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:00",
                "horaFin", "10:00",
                "cantidadAsistentes", 0,
                "observaciones", "Asistentes cero"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASISTENTES_INVALIDOS"));
    }

    @Test
    @Order(7)
    @DisplayName("F8-07-02 [7/26]: Reserva con cantidad de asistentes negativa (-5) -> 400 ASISTENTES_INVALIDOS")
    void test07_Asistentes_CantidadNegativa_RechazadoConAsistentesInvalidos() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(16);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_AFORO_50,
                "fechaReserva", fecha.toString(),
                "horaInicio", "09:00",
                "horaFin", "10:00",
                "cantidadAsistentes", -5,
                "observaciones", "Asistentes negativos"
        );

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASISTENTES_INVALIDOS"));
    }

    @Test
    @Order(8)
    @DisplayName("F8-07-02 [8/26]: Reserva con cantidad de asistentes omitida/null -> default 1 y 201 Created")
    void test08_Asistentes_CantidadOmitidaNull_DefaultAUnoYPermitido() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(17);
        Map<String, Object> req = new HashMap<>();
        req.put("idZona", ZONA_AFORO_50);
        req.put("fechaReserva", fecha.toString());
        req.put("horaInicio", "08:00");
        req.put("horaFin", "09:00");
        req.put("observaciones", "Sin asistentes explícitos");

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            Integer asistentesPersistidos = jdbcTemplate.queryForObject(
                    "SELECT CANTIDAD_ASISTENTES FROM RESERVAS WHERE ID_RESERVA = ?",
                    Integer.class, idReserva
            );
            assertEquals(1, asistentesPersistidos, "Cuando no se envían asistentes, debe persistirse el valor por defecto 1");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // GAP-F8-07-03: HERENCIA Y AUTORIDAD DE TARIFA DE RESERVA
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("F8-07-03 [9/26]: Reserva en zona gratuita -> costoTotal = 0 en Oracle")
    void test09_Costo_ZonaGratuita_HeredaCostoCero() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(18);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_GRATUITA,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "11:00",
                "cantidadAsistentes", 5
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            BigDecimal costoPersistido = jdbcTemplate.queryForObject(
                    "SELECT COSTO_TOTAL FROM RESERVAS WHERE ID_RESERVA = ?",
                    BigDecimal.class, idReserva
            );
            assertNotNull(costoPersistido);
            assertEquals(0, BigDecimal.ZERO.compareTo(costoPersistido), "Zona gratuita debe heredar costo 0 en Oracle");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(10)
    @DisplayName("F8-07-03 [10/26]: Reserva en zona con costo -> costoTotal = 50000.00 exacto en Oracle")
    void test10_Costo_ZonaConCosto_HeredaCostoExactoDeZona() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(19);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_PAGA_50K,
                "fechaReserva", fecha.toString(),
                "horaInicio", "10:00",
                "horaFin", "12:00",
                "cantidadAsistentes", 15
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            BigDecimal costoPersistido = jdbcTemplate.queryForObject(
                    "SELECT COSTO_TOTAL FROM RESERVAS WHERE ID_RESERVA = ?",
                    BigDecimal.class, idReserva
            );
            assertNotNull(costoPersistido);
            assertEquals(0, new BigDecimal("50000.00").compareTo(costoPersistido),
                    "Zona con canon 50.000 debe heredar tarifa exacta en Oracle");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(11)
    @DisplayName("F8-07-03 [11/26]: Cliente envía costoTotal=0 en zona paga -> Servidor ignora input y persiste tarifa oficial (50.000)")
    void test11_Costo_ClienteEnviaCostoCero_ServidorIgnoraYPersisteTarifaReal() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(20);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_PAGA_50K,
                "fechaReserva", fecha.toString(),
                "horaInicio", "14:00",
                "horaFin", "16:00",
                "cantidadAsistentes", 10,
                "costoTotal", 0
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            BigDecimal costoPersistido = jdbcTemplate.queryForObject(
                    "SELECT COSTO_TOTAL FROM RESERVAS WHERE ID_RESERVA = ?",
                    BigDecimal.class, idReserva
            );
            assertEquals(0, new BigDecimal("50000.00").compareTo(costoPersistido),
                    "El servidor es la fuente autoritativa del precio: input manipulado del cliente debe ignorarse");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(12)
    @DisplayName("F8-07-03 [12/26]: Cliente envía costoTotal=999999 en zona paga -> Servidor ignora input y persiste 50.000")
    void test12_Costo_ClienteEnviaCostoArbitrario_ServidorIgnoraYPersisteTarifaReal() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(21);
        Map<String, Object> req = Map.of(
                "idZona", ZONA_PAGA_50K,
                "fechaReserva", fecha.toString(),
                "horaInicio", "17:00",
                "horaFin", "19:00",
                "cantidadAsistentes", 10,
                "costoTotal", 999999
        );

        MvcResult result = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idReserva = Long.parseLong(result.getResponse().getContentAsString().trim());

        setElevatedContext();
        try {
            BigDecimal costoPersistido = jdbcTemplate.queryForObject(
                    "SELECT COSTO_TOTAL FROM RESERVAS WHERE ID_RESERVA = ?",
                    BigDecimal.class, idReserva
            );
            assertEquals(0, new BigDecimal("50000.00").compareTo(costoPersistido),
                    "El servidor no permite alteración arbitraria de precios por parte del cliente");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // GAP-F8-07-04: CANCELACIÓN POR RESIDENTE Y ANTI-IDOR
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("F8-07-04 [13/26]: Residente cancela su propia reserva PENDIENTE -> 200 OK y estado CANCELADA en Oracle")
    void test13_Cancelacion_ResidenteCancelaSuPropiaReservaPendiente_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(22);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "08:00", "09:00", 5);

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("CANCELADA", estado, "La reserva debe figurar como CANCELADA en Oracle");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(14)
    @DisplayName("F8-07-04 [14/26]: Residente cancela su propia reserva APROBADA -> 200 OK y estado CANCELADA en Oracle")
    void test14_Cancelacion_ResidenteCancelaSuPropiaReservaAprobada_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(23);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "10:00", "11:00", 5);

        // Admin aprueba la reserva
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        // Residente cancela su reserva previamente aprobada
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("CANCELADA", estado, "Reserva aprobada debe pasar a CANCELADA en Oracle tras cancelación del residente");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(15)
    @DisplayName("F8-07-04 [15/26]: Residente intenta cancelar reserva de OTRA unidad -> 403 Forbidden (Anti-IDOR)")
    void test15_Cancelacion_ResidenteCancelaReservaDeOtraUnidad_RechazadoAntiIdor403() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(24);
        // Reserva creada por Residente 2 (Unidad 2)
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "12:00", "13:00", 5);

        // Residente 3 (Unidad 3) intenta cancelarla
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3)))
                .andExpect(status().isForbidden());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("PENDIENTE", estado, "La reserva de la Unidad 2 no debe haber sido modificada");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(16)
    @DisplayName("F8-07-04 [16/26]: Intento de cancelar reserva ya CANCELADA -> 400 TRANSICION_ESTADO_INVALIDA")
    void test16_Cancelacion_ResidenteCancelaReservaYaCancelada_RechazadoTransicionInvalida400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(25);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "14:00", "15:00", 5);

        // Primera cancelación exitosa
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isOk());

        // Segunda cancelación debe fallar
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));
    }

    @Test
    @Order(17)
    @DisplayName("F8-07-04 [17/26]: Intento de cancelar reserva RECHAZADA -> 400 TRANSICION_ESTADO_INVALIDA")
    void test17_Cancelacion_ResidenteCancelaReservaRechazada_RechazadoTransicionInvalida400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(26);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "16:00", "17:00", 5);

        // Admin rechaza la reserva
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "RECHAZADA"))))
                .andExpect(status().isOk());

        // Residente intenta cancelarla
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));
    }

    @Test
    @Order(18)
    @DisplayName("F8-07-04 [18/26]: Intento de cancelar reserva COMPLETADA -> 400 TRANSICION_ESTADO_INVALIDA")
    void test18_Cancelacion_ResidenteCancelaReservaCompletada_RechazadoTransicionInvalida400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(27);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "18:00", "19:00", 5);

        // PENDIENTE -> APROBADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        // APROBADA -> COMPLETADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "COMPLETADA"))))
                .andExpect(status().isOk());

        // Residente intenta cancelar reserva ya completada
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));
    }

    @Test
    @Order(19)
    @DisplayName("F8-07-04 [19/26]: Admin de Propiedad cancela reserva de su propiedad -> 200 OK y CANCELADA en Oracle")
    void test19_Cancelacion_AdminPropiedadCancelaReservaDeSuPropiedad_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(28);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "20:00", "21:00", 5);

        // Admin cancela la reserva vía endpoint /cancelar
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("CANCELADA", estado, "Admin debe poder cancelar la reserva");
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // GAP-F8-07-05: MÁQUINA DE ESTADOS Y TRANSICIONES VÁLIDAS / TERMINALES
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("F8-07-05 [20/26]: Admin cambia estado PENDIENTE -> APROBADA -> 200 OK")
    void test20_MaquinaEstados_AdminTransicionaPendienteAAprobada_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(29);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "08:00", "09:00", 2);

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("APROBADA", estado);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(21)
    @DisplayName("F8-07-05 [21/26]: Admin cambia estado PENDIENTE -> RECHAZADA -> 200 OK")
    void test21_MaquinaEstados_AdminTransicionaPendienteARechazada_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(30);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "10:00", "11:00", 2);

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "RECHAZADA"))))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("RECHAZADA", estado);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(22)
    @DisplayName("F8-07-05 [22/26]: Admin cambia estado PENDIENTE -> CANCELADA -> 200 OK")
    void test22_MaquinaEstados_AdminTransicionaPendienteACancelada_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(31);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "12:00", "13:00", 2);

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "CANCELADA"))))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("CANCELADA", estado);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(23)
    @DisplayName("F8-07-05 [23/26]: Admin cambia estado APROBADA -> COMPLETADA -> 200 OK")
    void test23_MaquinaEstados_AdminTransicionaAprobadaACompletada_Exitoso() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(32);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "14:00", "15:00", 2);

        // PENDIENTE -> APROBADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        // APROBADA -> COMPLETADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "COMPLETADA"))))
                .andExpect(status().isOk());

        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM RESERVAS WHERE ID_RESERVA = ?", String.class, idReserva);
            assertEquals("COMPLETADA", estado);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(24)
    @DisplayName("F8-07-05 [24/26]: Transición inválida desde estado terminal CANCELADA -> APROBADA rechazada con 400")
    void test24_MaquinaEstados_AdminTransicionInvalidaDesdeEstadoTerminal_Rechazado400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(33);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "16:00", "17:00", 2);

        // PENDIENTE -> CANCELADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "CANCELADA"))))
                .andExpect(status().isOk());

        // Intento de revivir estado terminal: CANCELADA -> APROBADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));
    }

    @Test
    @Order(25)
    @DisplayName("F8-07-05 [25/26]: Transición inválida desde estado terminal COMPLETADA -> CANCELADA rechazada con 400")
    void test25_MaquinaEstados_AdminTransicionInvalidaDesdeCompletadaACancelada_Rechazado400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(34);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "18:00", "19:00", 2);

        // PENDIENTE -> APROBADA -> COMPLETADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "APROBADA"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "COMPLETADA"))))
                .andExpect(status().isOk());

        // Intento inválido: COMPLETADA -> CANCELADA
        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "CANCELADA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSICION_ESTADO_INVALIDA"));
    }

    @Test
    @Order(26)
    @DisplayName("F8-07-05 [26/26]: Envío de estado semánticamente inexistente -> 400 ESTADO_RESERVA_INVALIDO")
    void test26_MaquinaEstados_AdminEnviaEstadoInvalido_Rechazado400() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(35);
        Long idReserva = crearReservaDirecta(tokenResidente2, ASSIGN_RESIDENTE_2, ZONA_OPERACIONES, fecha, "20:00", "21:00", 2);

        mockMvc.perform(put("/api/v1/reservas/" + idReserva + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "ESTADO_INVENTADO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESTADO_RESERVA_INVALIDO"));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private Long crearReservaDirecta(String token, Long assignId, Long idZona, LocalDate fecha, String horaIni, String horaFin, int asistentes) throws Exception {
        Map<String, Object> req = Map.of(
                "idZona", idZona,
                "fechaReserva", fecha.toString(),
                "horaInicio", horaIni,
                "horaFin", horaFin,
                "cantidadAsistentes", asistentes,
                "observaciones", "Reserva helper"
        );

        MvcResult res = mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(assignId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return Long.parseLong(res.getResponse().getContentAsString().trim());
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

    private void ensureZonaComun(Long idZona, Long idPropiedad, String nombre, Integer aforo, String requiereReserva, BigDecimal costo) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ZONAS_COMUNES WHERE ID_ZONA = ?", Integer.class, idZona);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO ZONAS_COMUNES (ID_ZONA, ID_PROPIEDAD, NOMBRE, TIPO, AFORO_MAXIMO, REQUIERE_RESERVA, COSTO_RESERVA, ESTADO)
                VALUES (?, ?, ?, 'SALON_SOCIAL', ?, ?, ?, 'ACTIVA')
            """, idZona, idPropiedad, nombre, aforo, requiereReserva, costo);
        } else {
            jdbcTemplate.update("""
                UPDATE ZONAS_COMUNES SET ID_PROPIEDAD = ?, NOMBRE = ?, AFORO_MAXIMO = ?, REQUIERE_RESERVA = ?, COSTO_RESERVA = ?, ESTADO = 'ACTIVA'
                WHERE ID_ZONA = ?
            """, idPropiedad, nombre, aforo, requiereReserva, costo, idZona);
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
