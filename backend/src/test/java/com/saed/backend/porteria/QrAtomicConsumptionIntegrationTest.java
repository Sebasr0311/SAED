package com.saed.backend.porteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class QrAtomicConsumptionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PorteriaService porteriaService;

    @MockBean
    private AssignmentService assignmentService;

    private Long testVisitaId;
    private Long testPropiedadId = 1L;
    private Long testPorteriaId = 1L;
    private Long testUnidadId = 1L;
    private Long testVisitanteId = 1L;
    private Long testOtraPropiedadId = 2L;
    private Long testOtraUnidadId = 2L;
    private final Long testPorteroUserId = 3L;
    private final Long testAssignmentId = 103L;
    private final Long testAssignmentIdOtraProp = 105L;

    private final List<String> generatedTokens = new ArrayList<>();

    private void setElevatedContext() {
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        setElevatedContext();

        // 1. Resolver infraestructura coherente para la propiedad principal
        try {
            testPropiedadId = 1L;
            List<Map<String, Object>> units = jdbcTemplate.queryForList("SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId);
            if (!units.isEmpty()) {
                testUnidadId = ((Number) units.get(0).get("ID_UNIDAD")).longValue();
            }
            List<Map<String, Object>> ports = jdbcTemplate.queryForList("SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId);
            if (!ports.isEmpty()) {
                testPorteriaId = ((Number) ports.get(0).get("ID_PORTERIA")).longValue();
            } else {
                jdbcTemplate.update(
                    "INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO) " +
                    "VALUES (?, 'Porteria Principal Test QR', 'Acceso Principal', '3001234567', 'ACTIVA')",
                    testPropiedadId
                );
                testPorteriaId = jdbcTemplate.queryForObject(
                    "SELECT MAX(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = ?", Long.class, testPropiedadId
                );
            }
            List<Map<String, Object>> vis = jdbcTemplate.queryForList("SELECT ID_VISITANTE FROM VISITANTES WHERE ROWNUM = 1");
            if (!vis.isEmpty()) {
                testVisitanteId = ((Number) vis.get(0).get("ID_VISITANTE")).longValue();
            }
        } catch (Exception e) {
            System.err.println("ERROR SETUP INFRA: " + e.getMessage());
        }

        // 2. Mock para AssignmentFilter (Portero en testPropiedadId = 1)
        AssignmentResponseDTO porteroAssignment = new AssignmentResponseDTO();
        porteroAssignment.setIdAsignacion(testAssignmentId);
        porteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignment.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(testPropiedadId);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Propiedad Test QR");
        porteroAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentId, testPorteroUserId))
                .thenReturn(Optional.of(porteroAssignment));

        // Mock para segunda portería/asignación (Portería B de la misma propiedad)
        final Long testAssignmentIdB = 104L;
        final Long testPorteroUserIdB = 4L;
        AssignmentResponseDTO porteroAssignmentB = new AssignmentResponseDTO();
        porteroAssignmentB.setIdAsignacion(testAssignmentIdB);
        porteroAssignmentB.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignmentB.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        porteroAssignmentB.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentIdB, testPorteroUserIdB))
                .thenReturn(Optional.of(porteroAssignmentB));

        // 3. Mock para portero asignado a OTRA propiedad (Property 999994) para Test 5 (Multi-tenant)
        testOtraPropiedadId = 999994L;
        try {
            List<Map<String, Object>> portsOtra = jdbcTemplate.queryForList("SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testOtraPropiedadId);
            if (portsOtra.isEmpty()) {
                jdbcTemplate.update(
                    "INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, TELEFONO_CONTACTO, ESTADO) " +
                    "VALUES (?, 'Porteria Prop 999994', 'Acceso Norte', '3009999999', 'ACTIVA')",
                    testOtraPropiedadId
                );
            }
        } catch (Exception ignored) {}

        AssignmentResponseDTO porteroOtraProp = new AssignmentResponseDTO();
        porteroOtraProp.setIdAsignacion(testAssignmentIdOtraProp);
        porteroOtraProp.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroOtraProp.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propOtraDTO = new PropertyDTO();
        propOtraDTO.setId(testOtraPropiedadId);
        propOtraDTO.setIdOrganizacion(1L);
        propOtraDTO.setNombre("Prop Test 999994");
        porteroOtraProp.setPropiedad(propOtraDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentIdOtraProp, testPorteroUserId))
                .thenReturn(Optional.of(porteroOtraProp));

        // 4. Crear visita de prueba PROGRAMADA en propiedad principal
        try {
            jdbcTemplate.update(
                    "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, ESTADO) " +
                    "VALUES (?, ?, 'CODIGO_QR', 'Visita automatizada prueba QR GAP-F7-02', 1, 'PROGRAMADA')",
                    testUnidadId, testVisitanteId
            );
            testVisitaId = jdbcTemplate.queryForObject(
                    "SELECT MAX(ID_VISITA) FROM VISITAS WHERE MOTIVO = 'Visita automatizada prueba QR GAP-F7-02'", Long.class);
        } catch (Exception e) {
            testVisitaId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITA) FROM VISITAS", Long.class);
        }
    }

    @AfterEach
    void tearDown() {
        setElevatedContext();
        try {
            for (String token : generatedTokens) {
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_QR IN (SELECT ID_QR FROM QR_ACCESOS WHERE TOKEN_QR = ?)", token);
                jdbcTemplate.update("DELETE FROM QR_ACCESOS WHERE TOKEN_QR = ?", token);
            }
            if (testVisitaId != null) {
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", testVisitaId);
                jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ? AND MOTIVO = 'Visita automatizada prueba QR GAP-F7-02'", testVisitaId);
            }
        } catch (Exception ignored) {}
    }

    private String insertTestQr(int usosPermitidos, int usosConsumidos, String estado, boolean expirado) {
        setElevatedContext();
        String token = "TEST-QR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        generatedTokens.add(token);

        String fechaSql = expirado
                ? "FROM_TZ(CAST(SYSTIMESTAMP - INTERVAL '2' HOUR AS TIMESTAMP), 'America/Bogota')"
                : "FROM_TZ(CAST(SYSTIMESTAMP + INTERVAL '1' DAY AS TIMESTAMP), 'America/Bogota')";

        jdbcTemplate.update(
                "INSERT INTO QR_ACCESOS (ID_VISITA, TOKEN_QR, FECHA_EXPIRACION, USOS_PERMITIDOS, USOS_CONSUMIDOS, ESTADO, GENERADO_POR) " +
                "VALUES (?, ?, " + fechaSql + ", ?, ?, ?, 1)",
                testVisitaId, token, usosPermitidos, usosConsumidos, estado
        );
        return token;
    }

    @Test
    @DisplayName("Test 0: Verificación SQL de que SP_VALIDAR_CONSUMIR_QR existe y está VALID en Oracle")
    void test0_verificarObjetoOracleValid() {
        setElevatedContext();
        String status = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'SP_VALIDAR_CONSUMIR_QR' AND OBJECT_TYPE = 'PROCEDURE'",
                String.class
        );
        assertNotNull(status, "El objeto SP_VALIDAR_CONSUMIR_QR debe existir en Oracle");
        assertEquals("VALID", status, "El objeto SP_VALIDAR_CONSUMIR_QR debe estar en estado VALID");
    }

    @Test
    @DisplayName("Test 1 — QR válido: Éxito (HTTP 200), USOS_CONSUMIDOS incrementado, REGISTROS_ACCESO creado, VISITA EN_CURSO")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test1_qrValido_debeConsumirAtomoYActualizarVisita() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", false);

        Map<String, String> body = Map.of(
                "codigoQr", token,
                "medioTransporte", "PEATONAL",
                "descripcion", "Ingreso peatonal normal"
        );

        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.idVisita").value(testVisitaId));

        setElevatedContext();

        // 1. Verificar que USOS_CONSUMIDOS se incrementó a 1 y ESTADO pasó a USADO
        Map<String, Object> qrDb = jdbcTemplate.queryForMap(
                "SELECT USOS_CONSUMIDOS, ESTADO FROM QR_ACCESOS WHERE TOKEN_QR = ?", token);
        assertEquals(1, ((Number) qrDb.get("USOS_CONSUMIDOS")).intValue());
        assertEquals("USADO", qrDb.get("ESTADO"));

        // 2. Verificar que VISITAS pasó a EN_CURSO
        String estadoVisita = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?", String.class, testVisitaId);
        assertEquals("EN_CURSO", estadoVisita, "La visita debe haber pasado canónicamente a EN_CURSO");

        // 3. Verificar que se insertó exactamente 1 registro en REGISTROS_ACCESO
        Integer regCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM REGISTROS_ACCESO WHERE ID_VISITA = ? AND TIPO_MOVIMIENTO = 'ENTRADA'",
                Integer.class, testVisitaId);
        assertEquals(1, regCount, "Debe existir exactamente 1 registro de acceso");
    }

    @Test
    @DisplayName("Test 2 — QR expirado: Rechazo controlado (HTTP 409), no incrementa usos")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test2_qrExpirado_debeRechazarSinIncrementar() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", true);

        Map<String, String> body = Map.of("codigoQr", token);

        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("expirado")));

        setElevatedContext();

        // Verificar que USOS_CONSUMIDOS sigue en 0
        Integer usos = jdbcTemplate.queryForObject(
                "SELECT USOS_CONSUMIDOS FROM QR_ACCESOS WHERE TOKEN_QR = ?", Integer.class, token);
        assertEquals(0, usos, "No debe haber consumido usos para un QR expirado");
    }

    @Test
    @DisplayName("Test 3 — QR revocado: Rechazo controlado (HTTP 409), no incrementa usos")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test3_qrRevocado_debeRechazarSinIncrementar() throws Exception {
        String token = insertTestQr(1, 0, "REVOCADO", false);

        Map<String, String> body = Map.of("codigoQr", token);

        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        setElevatedContext();

        Integer usos = jdbcTemplate.queryForObject(
                "SELECT USOS_CONSUMIDOS FROM QR_ACCESOS WHERE TOKEN_QR = ?", Integer.class, token);
        assertEquals(0, usos, "No debe incrementar usos en QR revocado");
    }

    @Test
    @DisplayName("Test 4 — QR agotado (MAX_USOS=1, USOS=1): Rechazo controlado (HTTP 409), NO HTTP 500")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test4_qrAgotado_debeRechazarCon409() throws Exception {
        String token = insertTestQr(1, 1, "USADO", false);

        Map<String, String> body = Map.of("codigoQr", token);

        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("activo")));
    }

    @Test
    @DisplayName("Test 5 — QR de otra propiedad: Rechazo por aislamiento multi-tenant (HTTP 403 Forbidden)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test5_qrDeOtraPropiedad_esRechazadoCon403() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", false);

        Map<String, String> body = Map.of("codigoQr", token);

        // El portero asignado a testOtraPropiedadId (999994) intenta escanear un QR de testPropiedadId (1)
        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentIdOtraProp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("QR_PROPERTY_MISMATCH"));

        setElevatedContext();

        // Verificar que no se consumió el QR
        Integer usos = jdbcTemplate.queryForObject(
                "SELECT USOS_CONSUMIDOS FROM QR_ACCESOS WHERE TOKEN_QR = ?", Integer.class, token);
        assertEquals(0, usos, "No debe consumir usos si la portería pertenece a otra propiedad");
    }

    @Test
    @DisplayName("Test 6 — Token inexistente: Rechazo controlado (HTTP 404), sin excepción Oracle filtrada")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test6_tokenInexistente_debeRechazarCon404() throws Exception {
        Map<String, String> body = Map.of("codigoQr", "TOKEN-FANTASMA-TOTALMENTE-INEXISTENTE-999");

        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Test 7 (CRÍTICO) — Concurrencia MAX_USOS=1: Dos peticiones simultáneas resultan en exactamente 1 éxito y 1 rechazo 409 (0 errores 500)")
    void test7_dosConsumosConcurrentes_unSoloUsoDisponible_unExitoUnRechazo() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneGun = new CountDownLatch(2);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger conflictos = new AtomicInteger(0);
        AtomicInteger serverErrors = new AtomicInteger(0);
        AtomicInteger otros = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    SaedContext ctx = SaedContext.builder()
                            .userId(testPorteroUserId)
                            .organizationId(1L)
                            .propertyId(testPropiedadId)
                            .roleCode("PORTERO")
                            .roleScope("PROPIEDAD")
                            .build();
                    SaedContextHolder.setContext(ctx);

                    startGun.await(); // Disparo sincronizado simultáneo
                    Map<String, Object> resp = porteriaService.registrarEntradaQr(token, "PEATONAL", null, "Concurrencia test");
                    if (Boolean.TRUE.equals(resp.get("success"))) {
                        exitos.incrementAndGet();
                    } else {
                        conflictos.incrementAndGet();
                    }
                } catch (com.saed.backend.porteria.exception.QrAccessException e) {
                    if (e.getStatus() == org.springframework.http.HttpStatus.CONFLICT) {
                        conflictos.incrementAndGet();
                    } else {
                        otros.incrementAndGet();
                    }
                } catch (Exception e) {
                    serverErrors.incrementAndGet();
                } finally {
                    doneGun.countDown();
                }
            });
        }

        // Fuego!
        startGun.countDown();
        boolean completed = doneGun.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Las dos peticiones concurrentes debieron completar dentro del timeout");
        assertEquals(1, exitos.get(), "Debe haber EXACTAMENTE 1 éxito para un QR con MAX_USOS=1");
        assertEquals(1, conflictos.get(), "Debe haber EXACTAMENTE 1 rechazo controlado por conflicto");
        assertEquals(0, serverErrors.get(), "NUNCA debe ocurrir HTTP 500 ni ORA-02290");
        assertEquals(0, otros.get(), "No debe haber otros errores inesperados");

        setElevatedContext();

        // Validar estado final en DB
        Map<String, Object> qrDb = jdbcTemplate.queryForMap(
                "SELECT USOS_CONSUMIDOS, ESTADO FROM QR_ACCESOS WHERE TOKEN_QR = ?", token);
        assertEquals(1, ((Number) qrDb.get("USOS_CONSUMIDOS")).intValue(), "USOS_CONSUMIDOS debe ser exactamente 1");
        assertEquals("USADO", qrDb.get("ESTADO"), "ESTADO debe ser USADO");
    }

    @Test
    @DisplayName("Test 8 — Concurrencia MAX_USOS=2: Dos peticiones simultáneas resultan en 2 éxitos y USOS_CONSUMIDOS=2")
    void test8_nConsumosConcurrentes_paraQrConNUsos_exactamenteNExitos() throws Exception {
        String token = insertTestQr(2, 0, "ACTIVO", false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneGun = new CountDownLatch(2);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger serverErrors = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    SaedContext ctx = SaedContext.builder()
                            .userId(testPorteroUserId)
                            .organizationId(1L)
                            .propertyId(testPropiedadId)
                            .roleCode("PORTERO")
                            .roleScope("PROPIEDAD")
                            .build();
                    SaedContextHolder.setContext(ctx);

                    startGun.await();
                    Map<String, Object> resp = porteriaService.registrarEntradaQr(token, "PEATONAL", null, "Concurrencia test usos=2");
                    if (Boolean.TRUE.equals(resp.get("success"))) {
                        exitos.incrementAndGet();
                    }
                } catch (Exception e) {
                    serverErrors.incrementAndGet();
                } finally {
                    doneGun.countDown();
                }
            });
        }

        startGun.countDown();
        boolean completed = doneGun.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Las dos peticiones debieron finalizar");
        assertEquals(2, exitos.get(), "Ambas peticiones deben tener éxito con MAX_USOS=2");
        assertEquals(0, serverErrors.get(), "No debe haber errores");

        setElevatedContext();

        // Validar estado final en DB
        Map<String, Object> qrDb = jdbcTemplate.queryForMap(
                "SELECT USOS_CONSUMIDOS, ESTADO FROM QR_ACCESOS WHERE TOKEN_QR = ?", token);
        assertEquals(2, ((Number) qrDb.get("USOS_CONSUMIDOS")).intValue(), "USOS_CONSUMIDOS debe ser exactamente 2");
        assertEquals("USADO", qrDb.get("ESTADO"), "ESTADO debe ser USADO al alcanzar el límite");
    }

    @Test
    @DisplayName("Test 9 — N+1 intento tras agotar usos: Rechazo con HTTP 409 sin error 500 ni violación ORA-02290")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test9_nMasUnoIntentoDespuesDeAgotarUsos_debeRechazarse() throws Exception {
        int n = 2;
        String token = insertTestQr(n, 0, "ACTIVO", false);

        Map<String, String> body = Map.of("codigoQr", token);

        // Consumir N veces con éxito
        for (int i = 0; i < n; i++) {
            mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                    .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // Intento N + 1
        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        setElevatedContext();
        Integer usos = jdbcTemplate.queryForObject(
                "SELECT USOS_CONSUMIDOS FROM QR_ACCESOS WHERE TOKEN_QR = ?", Integer.class, token);
        assertEquals(n, usos, "USOS_CONSUMIDOS no debe exceder N");
    }

    @Test
    @DisplayName("Test 10 — Repetición secuencial de consumo tras ser consumido: Rechazo HTTP 409")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void test10_repeticionDeConsumoMismoQr_despuesDeConsumido_esRechazado() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", false);
        Map<String, String> body = Map.of("codigoQr", token);

        // Primer consumo: OK
        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Segundo consumo repetido del mismo QR: CONFLICT
        mockMvc.perform(post("/api/v1/porteria/qr/entrada")
                .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Test 11 — Concurrencia desde dos porterías (Portería Norte vs Portería Sur): Atomicidad de Oracle preservada")
    void test11_concurrenciaDosPorterias_atomicidadPreservada() throws Exception {
        String token = insertTestQr(1, 0, "ACTIVO", false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneGun = new CountDownLatch(2);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger conflictos = new AtomicInteger(0);
        AtomicInteger serverErrors = new AtomicInteger(0);

        // Hilo 1: Simula Portería 1 (Portero 3)
        executor.submit(() -> {
            try {
                SaedContext ctx1 = SaedContext.builder()
                        .userId(3L)
                        .organizationId(1L)
                        .propertyId(testPropiedadId)
                        .roleCode("PORTERO")
                        .roleScope("PROPIEDAD")
                        .build();
                SaedContextHolder.setContext(ctx1);

                startGun.await();
                Map<String, Object> resp = porteriaService.registrarEntradaQr(token, "PEATONAL", null, "Porteria 1");
                if (Boolean.TRUE.equals(resp.get("success"))) exitos.incrementAndGet();
            } catch (com.saed.backend.porteria.exception.QrAccessException e) {
                conflictos.incrementAndGet();
            } catch (Throwable e) {
                System.err.println("TEST11 HILO 1 ERROR: " + e.getClass().getName() + " : " + e.getMessage());
                serverErrors.incrementAndGet();
            } finally {
                doneGun.countDown();
            }
        });

        // Hilo 2: Simula Portería 2
        executor.submit(() -> {
            try {
                SaedContext ctx2 = SaedContext.builder()
                        .userId(testPorteroUserId)
                        .organizationId(1L)
                        .propertyId(testPropiedadId)
                        .roleCode("PORTERO")
                        .roleScope("PROPIEDAD")
                        .build();
                SaedContextHolder.setContext(ctx2);

                startGun.await();
                Map<String, Object> resp = porteriaService.registrarEntradaQr(token, "CARRO", "XYZ987", "Porteria 2");
                if (Boolean.TRUE.equals(resp.get("success"))) exitos.incrementAndGet();
            } catch (com.saed.backend.porteria.exception.QrAccessException e) {
                conflictos.incrementAndGet();
            } catch (Throwable e) {
                System.err.println("TEST11 HILO 2 ERROR: " + e.getClass().getName() + " : " + e.getMessage());
                serverErrors.incrementAndGet();
            } finally {
                doneGun.countDown();
            }
        });

        startGun.countDown();
        boolean completed = doneGun.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Ambas solicitudes debieron responder");
        assertEquals(1, exitos.get(), "Exactamente 1 portería debe obtener el acceso");
        assertEquals(1, conflictos.get(), "La segunda portería debe recibir rechazo controlado");
        assertEquals(0, serverErrors.get(), "Cero errores de servidor");
    }
}
