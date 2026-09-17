package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MembershipHistorySecurityTest — Suite de pruebas exhaustiva para GAP-ENT-05:
 * Historial Completo del Ciclo de Vida de Membresías en SAED 2.0.
 *
 * Cobertura de verificación:
 *  1. Creación de membresía registra INICIO con ID_PLAN_ANTERIOR = null e ID_PLAN_NUEVO correcto.
 *  2. Asignación con plan superior registra UPGRADE con ID_PLAN_ANTERIOR y nuevo.
 *  3. Asignación con plan inferior registra DOWNGRADE con ID_PLAN_ANTERIOR y nuevo.
 *  4. Cambio de estado a SUSPENDIDA genera SUSPENSION en historial.
 *  5. Cambio de estado de SUSPENDIDA a ACTIVA genera REACTIVACION en historial.
 *  6. Cambio de estado a CANCELADA en platform genera CANCELACION en historial.
 *  7. Endpoint DELETE /api/v1/membresias/{id} genera CANCELACION en historial.
 *  8. Endpoint POST /api/v1/membresias (finanzas) genera INICIO en historial.
 *  9. Onboarding gratuito registra INICIO en historial atómicamente.
 * 10. Inmutabilidad en DB: Trigger TRG_MEMBHIST_IMMUTABLE rechaza UPDATE (ORA-20030 / append-only).
 * 11. Inmutabilidad en DB: Trigger TRG_MEMBHIST_IMMUTABLE rechaza DELETE (ORA-20030 / append-only).
 * 12. Validación de servicio: Rechazo de TIPO_CAMBIO no canónico (IllegalArgumentException).
 * 13. Integridad referencial: REALIZADO_POR con usuario inexistente se normaliza a null para proteger FK_MEMBHIST_USUARIO.
 * 14. Endpoint GET /api/v1/platform/memberships/{id}/historial accesible por SUPERADMIN con datos completos.
 * 15. RBAC estricto: GET /api/v1/platform/memberships/{id}/historial bloqueado para otros roles (403 Forbidden).
 * 16. Aislamiento multi-tenant: GET /api/v1/membresias/{id}/historial bloquea a ADMIN_ORGANIZACION de consultar otra org.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MembershipHistorySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private MembershipHistoryService membershipHistoryService;

    @Autowired
    private OnboardingService onboardingService;

    private Long testOrgId;
    private Long testOrgOtherId;
    private Long superAdminUserId;

    private void elevateToSuperAdmin() {
        superAdminUserId = 1L;
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(superAdminUserId)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        elevateToSuperAdmin();

        // Crear organizaciones de prueba deterministas
        testOrgId = createTestOrganization("Org Historial Test " + System.currentTimeMillis(), "990001-1");
        testOrgOtherId = createTestOrganization("Org Other Test " + System.currentTimeMillis(), "990002-2");
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
    }

    private Long createTestOrganization(String nombre, String nit) {
        String sql = """
            INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO)
            VALUES (:nombre, :nit, :email, 'ACTIVA')
            """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nombre", nombre)
                .addValue("nit", nit)
                .addValue("email", "test." + nit + "@saedtest.com"),
                kh, new String[]{"ID_ORGANIZACION"});
        Number key = kh.getKey();
        return key != null ? key.longValue() : 1L;
    }

    // =========================================================================
    // 1. INICIO — Creación inicial de membresía
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #1: Creación inicial de membresía registra evento INICIO con plan anterior nulo")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void test01_crearMembresia_registraInicioConPlanAnteriorNull() throws Exception {
        Map<String, Object> body = Map.of(
                "idOrganizacion", testOrgId,
                "idPlan", 1L,
                "estado", "ACTIVA"
        );

        String responseStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> resp = objectMapper.readValue(responseStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Long idMembresia = ((Number) data.get("id")).longValue();

        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO, OBSERVACIONES, REALIZADO_POR " +
                "FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMembresia));

        assertFalse(hist.isEmpty(), "Debe existir registro en MEMBRESIAS_HISTORIAL");
        Map<String, Object> entry = hist.get(0);
        assertEquals("INICIO", entry.get("TIPO_CAMBIO"));
        assertNull(entry.get("ID_PLAN_ANTERIOR"), "ID_PLAN_ANTERIOR debe ser null en creación inicial");
        assertEquals(1L, ((Number) entry.get("ID_PLAN_NUEVO")).longValue());
        assertEquals(superAdminUserId, ((Number) entry.get("REALIZADO_POR")).longValue());
    }

    // =========================================================================
    // 2. UPGRADE — Transición a plan superior
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #2: Asignación a plan superior registra evento UPGRADE con planes correctos")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test02_upgradeMembresia_registraUpgradeConPlanesCorrectos() throws Exception {
        elevateToSuperAdmin();
        // 1. Crear membresía inicial en Plan 1
        mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 1L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated());

        elevateToSuperAdmin();
        // 2. Asignar nuevo Plan 2 (Upgrade)
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 2L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> resp = objectMapper.readValue(respStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Long idNuevaMem = ((Number) data.get("id")).longValue();

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO " +
                "FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idNuevaMem));

        assertFalse(hist.isEmpty());
        Map<String, Object> entry = hist.get(0);
        assertEquals("UPGRADE", entry.get("TIPO_CAMBIO"));
        assertEquals(1L, ((Number) entry.get("ID_PLAN_ANTERIOR")).longValue());
        assertEquals(2L, ((Number) entry.get("ID_PLAN_NUEVO")).longValue());
    }

    // =========================================================================
    // 3. DOWNGRADE — Transición a plan inferior
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #3: Asignación a plan inferior registra evento DOWNGRADE con planes correctos")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test03_downgradeMembresia_registraDowngradeConPlanesCorrectos() throws Exception {
        elevateToSuperAdmin();
        // 1. Crear membresía inicial en Plan 3 (Enterprise)
        mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 3L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated());

        elevateToSuperAdmin();
        // 2. Asignar Plan 2 (Downgrade)
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 2L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> resp = objectMapper.readValue(respStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Long idNuevaMem = ((Number) data.get("id")).longValue();

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO " +
                "FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idNuevaMem));

        assertFalse(hist.isEmpty());
        Map<String, Object> entry = hist.get(0);
        assertEquals("DOWNGRADE", entry.get("TIPO_CAMBIO"));
        assertEquals(3L, ((Number) entry.get("ID_PLAN_ANTERIOR")).longValue());
        assertEquals(2L, ((Number) entry.get("ID_PLAN_NUEVO")).longValue());
    }

    // =========================================================================
    // 4. SUSPENSION — Cambio de estado a SUSPENDIDA
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #4: Cambio de estado a SUSPENDIDA genera SUSPENSION en historial")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test04_suspenderMembresia_registraSuspension() throws Exception {
        elevateToSuperAdmin();
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 2L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idMem = ((Number) ((Map<String, Object>) objectMapper.readValue(respStr, Map.class).get("data")).get("id")).longValue();

        elevateToSuperAdmin();
        mockMvc.perform(put("/api/v1/platform/memberships/" + idMem + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "SUSPENDIDA"))))
                .andExpect(status().isOk());

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO " +
                "FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMem));

        assertFalse(hist.isEmpty());
        assertEquals("SUSPENSION", hist.get(0).get("TIPO_CAMBIO"));
        assertEquals(2L, ((Number) hist.get(0).get("ID_PLAN_NUEVO")).longValue());
    }

    // =========================================================================
    // 5. REACTIVACION — Cambio de SUSPENDIDA a ACTIVA
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #5: Cambio de SUSPENDIDA a ACTIVA genera REACTIVACION en historial")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test05_reactivarMembresia_registraReactivacion() throws Exception {
        elevateToSuperAdmin();
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 2L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idMem = ((Number) ((Map<String, Object>) objectMapper.readValue(respStr, Map.class).get("data")).get("id")).longValue();

        elevateToSuperAdmin();
        // 1. Suspender
        mockMvc.perform(put("/api/v1/platform/memberships/" + idMem + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "SUSPENDIDA"))))
                .andExpect(status().isOk());

        elevateToSuperAdmin();
        // 2. Reactivar
        mockMvc.perform(put("/api/v1/platform/memberships/" + idMem + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "ACTIVA"))))
                .andExpect(status().isOk());

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMem));

        assertFalse(hist.isEmpty());
        assertEquals("REACTIVACION", hist.get(0).get("TIPO_CAMBIO"));
    }

    // =========================================================================
    // 6. CANCELACION — Platform Controller
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #6: Cancelación vía Platform genera CANCELACION en historial")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test06_cancelarMembresia_platform_registraCancelacion() throws Exception {
        elevateToSuperAdmin();
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 1L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idMem = ((Number) ((Map<String, Object>) objectMapper.readValue(respStr, Map.class).get("data")).get("id")).longValue();

        elevateToSuperAdmin();
        mockMvc.perform(put("/api/v1/platform/memberships/" + idMem + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "CANCELADA"))))
                .andExpect(status().isOk());

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMem));

        assertFalse(hist.isEmpty());
        assertEquals("CANCELACION", hist.get(0).get("TIPO_CAMBIO"));
    }

    // =========================================================================
    // 7. CANCELACION — Finanzas Controller DELETE
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #7: Cancelación vía DELETE /api/v1/membresias/{id} genera CANCELACION en historial")
    @WithMockUser(username = "1", authorities = {"SCOPE_SUPERADMIN"})
    public void test07_cancelarMembresia_finanzas_registraCancelacion() throws Exception {
        elevateToSuperAdmin();
        String respStr = mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idOrganizacion", testOrgId,
                                "idPlan", 2L,
                                "estado", "ACTIVA"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idMem = ((Number) ((Map<String, Object>) objectMapper.readValue(respStr, Map.class).get("data")).get("id")).longValue();

        elevateToSuperAdmin();
        mockMvc.perform(delete("/api/v1/membresias/" + idMem))
                .andExpect(status().isOk());

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMem));

        assertFalse(hist.isEmpty());
        assertEquals("CANCELACION", hist.get(0).get("TIPO_CAMBIO"));
    }

    // =========================================================================
    // 8. INICIO — Finanzas Controller POST /membresias
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #8: Creación vía POST /api/v1/membresias genera INICIO en historial")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void test08_membresiasController_crear_registraInicio() throws Exception {
        Map<String, Object> body = Map.of(
                "idOrganizacion", testOrgId,
                "idPlan", 2L,
                "estado", "PRUEBA",
                "esPrueba", true,
                "diasPrueba", 14
        );

        String respStr = mockMvc.perform(post("/api/v1/membresias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idMem = ((Number) ((Map<String, Object>) objectMapper.readValue(respStr, Map.class).get("data")).get("id")).longValue();

        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id",
                new MapSqlParameterSource("id", idMem));

        assertFalse(hist.isEmpty());
        assertEquals("INICIO", hist.get(0).get("TIPO_CAMBIO"));
        assertNull(hist.get(0).get("ID_PLAN_ANTERIOR"));
        assertEquals(2L, ((Number) hist.get(0).get("ID_PLAN_NUEVO")).longValue());
    }

    // =========================================================================
    // 9. INICIO — Onboarding Gratuito
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #9: Onboarding gratuito registra evento INICIO en historial de forma atómica")
    public void test09_onboarding_gratuito_registraInicio() throws Exception {
        elevateToSuperAdmin();
        OnboardingRegistroRequestDTO req = new OnboardingRegistroRequestDTO();
        String nit = "909" + (System.currentTimeMillis() % 1000000);
        req.setNombreOrganizacion("Conjunto Onboarding Test " + nit);
        req.setNit(nit);
        req.setEmailOrganizacion("onb." + nit + "@testsaed.com");
        req.setPrimerNombre("Carlos");
        req.setPrimerApellido("Perez");
        req.setNumeroDocumento("CC" + nit);
        req.setEmailAdmin("admin." + nit + "@testsaed.com");
        req.setAdminUsername("adm_" + nit);
        req.setIdPlan(1L); // Gratuito
        req.setEsPrueba(true);

        Map<String, Object> result = onboardingService.registrar(req);
        assertNotNull(result.get("idOrganizacion"));
        Long newOrgId = ((Number) result.get("idOrganizacion")).longValue();

        elevateToSuperAdmin();
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT h.TIPO_CAMBIO, h.ID_PLAN_ANTERIOR, h.ID_PLAN_NUEVO, h.OBSERVACIONES " +
                "FROM MEMBRESIAS_HISTORIAL h " +
                "JOIN MEMBRESIAS m ON h.ID_MEMBRESIA = m.ID_MEMBRESIA " +
                "WHERE m.ID_ORGANIZACION = :org ORDER BY h.ID_HISTORIAL DESC",
                new MapSqlParameterSource("org", newOrgId));

        assertFalse(hist.isEmpty(), "Onboarding debió registrar un evento de historial");
        assertEquals("INICIO", hist.get(0).get("TIPO_CAMBIO"));
        assertNull(hist.get(0).get("ID_PLAN_ANTERIOR"));
        assertEquals(1L, ((Number) hist.get(0).get("ID_PLAN_NUEVO")).longValue());
        assertTrue(hist.get(0).get("OBSERVACIONES").toString().contains("Onboarding"));
    }

    // =========================================================================
    // 10 & 11. INMUTABILIDAD EN BASE DE DATOS (Trigger TRG_MEMBHIST_IMMUTABLE)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #10: Trigger TRG_MEMBHIST_IMMUTABLE rechaza UPDATE sobre MEMBRESIAS_HISTORIAL (ORA-20030)")
    public void test10_trigger_inmutabilidad_bloqueaUpdate() {
        // 1. Insertar registro legítimo vía servicio
        Long idMem = seedMembresiaDirecta(testOrgId, 1L);
        membershipHistoryService.recordChange(idMem, null, 1L, "INICIO", "Prueba de trigger", superAdminUserId);

        Long idHist = jdbcTemplate.queryForObject(
                "SELECT ID_HISTORIAL FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id AND ROWNUM = 1",
                new MapSqlParameterSource("id", idMem),
                Long.class);
        assertNotNull(idHist);

        // 2. Intentar UPDATE ilícito directamente en base de datos
        DataAccessException ex = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                    "UPDATE MEMBRESIAS_HISTORIAL SET OBSERVACIONES = 'MODIFICADO_ILICITAMENTE' WHERE ID_HISTORIAL = :id",
                    new MapSqlParameterSource("id", idHist));
        });

        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        assertTrue(msg.contains("ORA-20030") || msg.contains("append-only"),
                "El trigger de inmutabilidad debe impedir UPDATE arrojando ORA-20030. Mensaje: " + msg);
    }

    @Test
    @DisplayName("GAP-ENT-05 #11: Trigger TRG_MEMBHIST_IMMUTABLE rechaza DELETE sobre MEMBRESIAS_HISTORIAL (ORA-20030)")
    public void test11_trigger_inmutabilidad_bloqueaDelete() {
        Long idMem = seedMembresiaDirecta(testOrgId, 1L);
        membershipHistoryService.recordChange(idMem, null, 1L, "INICIO", "Prueba de trigger delete", superAdminUserId);

        Long idHist = jdbcTemplate.queryForObject(
                "SELECT ID_HISTORIAL FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id AND ROWNUM = 1",
                new MapSqlParameterSource("id", idMem),
                Long.class);
        assertNotNull(idHist);

        DataAccessException ex = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                    "DELETE FROM MEMBRESIAS_HISTORIAL WHERE ID_HISTORIAL = :id",
                    new MapSqlParameterSource("id", idHist));
        });

        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        assertTrue(msg.contains("ORA-20030") || msg.contains("append-only"),
                "El trigger de inmutabilidad debe impedir DELETE arrojando ORA-20030. Mensaje: " + msg);
    }

    // =========================================================================
    // 12. VALIDACIÓN DE TIPOS DE CAMBIO CANÓNICOS (CK_MEMBHIST_TIPO)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #12: Servicio rechaza tipos de cambio no canónicos fuera de CK_MEMBHIST_TIPO")
    public void test12_service_validaTipoCambioCanonico() {
        Long idMem = seedMembresiaDirecta(testOrgId, 1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            membershipHistoryService.recordChange(idMem, 1L, 2L, "CAMBIO_ILEGAL", "Intento no válido", superAdminUserId);
        });

        assertTrue(ex.getMessage().contains("Tipo de cambio inválido"));
    }

    // =========================================================================
    // 13. RESILIENCIA Y PROTECCIÓN DE CLAVE FORÁNEA USUARIO
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #13: Usuario no existente se normaliza a null preservando FK referencial")
    public void test13_service_protegeClaveForaneaUsuarioInexistente() {
        Long idMem = seedMembresiaDirecta(testOrgId, 1L);

        // ID de usuario 999999999 no existe en la base de datos
        membershipHistoryService.recordChange(idMem, null, 1L, "INICIO", "Usuario sintético", 999999999L);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT REALIZADO_POR FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                new MapSqlParameterSource("id", idMem));

        assertFalse(rows.isEmpty());
        assertNull(rows.get(0).get("REALIZADO_POR"),
                "Si el usuario no existe en la DB, debe almacenarse como null para evitar fallo ORA-02291");
    }

    // =========================================================================
    // 14. ENDPOINT GET /platform/memberships/{id}/historial (SUPERADMIN)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #14: SUPERADMIN consulta historial de membresía vía GET /platform/memberships/{id}/historial")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void test14_endpoint_platform_getHistorial_accesoSuperAdmin() throws Exception {
        Long idMem = seedMembresiaDirecta(testOrgId, 2L);
        membershipHistoryService.recordChange(idMem, 1L, 2L, "UPGRADE", "Upgrade autorizado", superAdminUserId);

        mockMvc.perform(get("/api/v1/platform/memberships/" + idMem + "/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].tipoCambio").value("UPGRADE"))
                .andExpect(jsonPath("$.data[0].idPlanNuevo").value(2))
                .andExpect(jsonPath("$.data[0].planNuevoNombre").isNotEmpty());
    }

    // =========================================================================
    // 15. RBAC ESTRICTO EN CONSULTA DE HISTORIAL PLATFORM
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #15: Roles no privilegiados reciben 403 al consultar historial en endpoint de plataforma")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void test15_endpoint_platform_getHistorial_bloqueaOtrosRoles() throws Exception {
        mockMvc.perform(get("/api/v1/platform/memberships/1/historial"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 16. AISLAMIENTO MULTI-TENANT EN GET /api/v1/membresias/{id}/historial
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05 #16: ADMIN_ORGANIZACION puede consultar historial de su org pero no de otra")
    public void test16_endpoint_membresias_getHistorial_aislamientoTenant() throws Exception {
        Long idMemOrgA = seedMembresiaDirecta(testOrgId, 2L);
        Long idMemOrgB = seedMembresiaDirecta(testOrgOtherId, 2L);

        membershipHistoryService.recordChange(idMemOrgA, null, 2L, "INICIO", "Org A Hist", superAdminUserId);
        membershipHistoryService.recordChange(idMemOrgB, null, 2L, "INICIO", "Org B Hist", superAdminUserId);

        // 1. Simular contexto de ADMIN_ORGANIZACION para testOrgId
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(555L)
                .organizationId(testOrgId)
                .propertyId(1L)
                .roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZATION")
                .build());

        // Consulta sobre su propia membresía -> Éxito
        String respPropia = mockMvc.perform(get("/api/v1/membresias/" + idMemOrgA + "/historial")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin_org")
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(respPropia, Map.class);
        assertEquals("success", respMap.get("status"));
        List<?> items = (List<?>) respMap.get("data");
        assertFalse(items.isEmpty());

        // 2. Volver a simular contexto de ADMIN_ORGANIZACION para testOrgId para la consulta ajena
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(555L)
                .organizationId(testOrgId)
                .propertyId(1L)
                .roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZATION")
                .build());

        // Consulta sobre membresía de OTRA organización -> Bloqueado / Error
        String respAjena = mockMvc.perform(get("/api/v1/membresias/" + idMemOrgB + "/historial")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin_org")
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> mapAjena = objectMapper.readValue(respAjena, Map.class);
        assertEquals("error", mapAjena.get("status"), "Debe devolver status 'error' por acceso no autorizado a otra organización");
    }

    // =========================================================================
    // Helper para insertar membresías directas en pruebas unitarias
    // =========================================================================

    private Long seedMembresiaDirecta(Long idOrg, Long idPlan) {
        String sql = """
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
            VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 12), 'ACTIVA', 'N')
            """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("org", idOrg)
                .addValue("plan", idPlan),
                kh, new String[]{"ID_MEMBRESIA"});
        Number k = kh.getKey();
        return k != null ? k.longValue() : 1L;
    }
}
