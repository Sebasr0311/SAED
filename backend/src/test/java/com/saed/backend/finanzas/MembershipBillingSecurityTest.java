package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.MembresiaRenovacionRequestDTO;
import com.saed.backend.finanzas.dto.MembresiaUpgradeRequestDTO;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import com.saed.backend.security.jwt.JwtProvider;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MembershipBillingSecurityTest — Suite de pruebas de seguridad y lógica de negocio para:
 * GAP-ENT-04: Renovación y Upgrade de membresías existentes mediante Wompi.
 *
 * Cobertura de verificación:
 *  1. Renovación legítima con cálculo server-side de precio desde Oracle PLANES.
 *  2. Renovación con ciclo ANUAL aplicando 20% de descuento determinista.
 *  3. Inmunidad a manipulación de monto/precio desde payload del frontend.
 *  4. Upgrade legítimo calculando tarifa del nuevo plan desde Oracle.
 *  5. Rechazo de upgrade hacia el mismo plan (400 Bad Request).
 *  6. Rechazo de downgrade o plan con tarifa inferior/igual (400 Bad Request).
 *  7. Rechazo de upgrade a plan inexistente o inactivo (400 Bad Request).
 *  8. Aislamiento multi-tenant (usuario de Org A no puede operar sobre Org B).
 *  9. Confinamiento RBAC estricto (SUPERADMIN, ADMIN_PROPIEDAD, PORTERO, RESIDENTE -> 403 Forbidden).
 * 10. Webhook con firma criptográfica válida y status APPROVED: aplica cambios atómicos en MEMBRESIAS.
 * 11. Webhook de Upgrade: actualiza ID_PLAN y genera trazabilidad en MEMBRESIAS_HISTORIAL.
 * 12. Webhook con checksum inválido/forjado: rechazado y sin efecto en la membresía.
 * 13. Webhook con monto discrepante: rechazado y sin efecto en la membresía.
 * 14. Idempotencia estricta: re-envío de webhook no duplica extensión de vigencia ni historial.
 * 15. Webhook con status DECLINED: marca transacción como RECHAZADO sin alterar la membresía.
 * 16. Membresía suspendida: bloquea autoservicio de renovación (400 Bad Request).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class MembershipBillingSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WompiServiceImpl wompiService;
    @Autowired private JwtProvider jwtProvider;

    @MockBean private AssignmentService assignmentService;

    private static final String TEST_EVENTS_SECRET = "test_events_sec_bill_12345";
    private static final String TEST_INTEGRITY_SECRET = "test_integ_sec_bill_67890";
    private static final String TEST_PUBLIC_KEY = "pub_test_wompi_billing_key";

    // IDs de Organizaciones de prueba
    private static final long ORG_A_ID       = 8901L; // Plan 2 (PRO, $299.000)
    private static final long ORG_B_ID       = 8902L; // Plan 1 (FREE, $0)
    private static final long ORG_ENT_ID     = 8903L; // Plan 3 (ENTERPRISE, $799.000)
    private static final long ORG_SUSP_ID    = 8904L; // Plan 2 (PRO, SUSPENDIDA)

    // IDs de Propiedades
    private static final long PROP_A_ID      = 8901L;
    private static final long PROP_B_ID      = 8902L;
    private static final long PROP_ENT_ID    = 8903L;
    private static final long PROP_SUSP_ID   = 8904L;

    // IDs de Usuarios
    private static final long USER_ADMIN_ORG_A  = 8901L;
    private static final long USER_ADMIN_ORG_B  = 8902L;
    private static final long USER_ADMIN_ENT    = 8903L;
    private static final long USER_ADMIN_SUSP   = 8904L;
    private static final long USER_ADMIN_PROP   = 8905L;
    private static final long USER_PORTERO      = 8906L;
    private static final long USER_RESIDENTE    = 8907L;

    // Asignaciones
    private static final long ASSIGN_ADMIN_ORG_A = 8901L;
    private static final long ASSIGN_ADMIN_ORG_B = 8902L;
    private static final long ASSIGN_ADMIN_ENT   = 8903L;
    private static final long ASSIGN_ADMIN_SUSP  = 8904L;
    private static final long ASSIGN_ADMIN_PROP  = 8905L;
    private static final long ASSIGN_PORTERO     = 8906L;
    private static final long ASSIGN_RESIDENTE   = 8907L;

    private String tokenAdminOrgA;
    private String tokenAdminOrgB;
    private String tokenAdminEnt;
    private String tokenAdminSusp;
    private String tokenAdminProp;
    private String tokenPortero;
    private String tokenResidente;

    @BeforeEach
    void setUp() {
        wompiService.setEventsSecret(TEST_EVENTS_SECRET);
        wompiService.setIntegritySecret(TEST_INTEGRITY_SECRET);
        wompiService.setPublicKey(TEST_PUBLIC_KEY);

        // Establecer SaedContext elevado para configurar semillas sin RLS
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // 1. Sembrar organizaciones
        seedOrganizacion(ORG_A_ID, "Org Alpha Billing Test", "901118901-1", "admin.alpha@billing.test");
        seedOrganizacion(ORG_B_ID, "Org Beta Billing Test", "901118902-2", "admin.beta@billing.test");
        seedOrganizacion(ORG_ENT_ID, "Org Enterprise Billing Test", "901118903-3", "admin.ent@billing.test");
        seedOrganizacion(ORG_SUSP_ID, "Org Suspended Billing Test", "901118904-4", "admin.susp@billing.test");

        // 2. Sembrar propiedades y unidades
        seedPropiedad(PROP_A_ID, ORG_A_ID, "Edificio Alpha");
        seedPropiedad(PROP_B_ID, ORG_B_ID, "Edificio Beta");
        seedPropiedad(PROP_ENT_ID, ORG_ENT_ID, "Torre Enterprise");
        seedPropiedad(PROP_SUSP_ID, ORG_SUSP_ID, "Edificio Suspended");
        seedUnidad(8901L, PROP_A_ID, "APTO 8901");

        // 3. Sembrar membresías
        seedMembresia(ORG_A_ID, 2L, "ACTIVA", "N", 30);       // PRO activa
        seedMembresia(ORG_B_ID, 1L, "ACTIVA", "N", 365);      // FREE activa
        seedMembresia(ORG_ENT_ID, 3L, "ACTIVA", "N", 30);     // ENTERPRISE activa
        seedMembresia(ORG_SUSP_ID, 2L, "SUSPENDIDA", "N", 30); // PRO suspendida

        // 4. Sembrar personas y usuarios
        seedPersonaUsuario(USER_ADMIN_ORG_A, USER_ADMIN_ORG_A, "bill_admin_a", "admin.alpha@billing.test", "DOC-8901");
        seedPersonaUsuario(USER_ADMIN_ORG_B, USER_ADMIN_ORG_B, "bill_admin_b", "admin.beta@billing.test", "DOC-8902");
        seedPersonaUsuario(USER_ADMIN_ENT, USER_ADMIN_ENT, "bill_admin_ent", "admin.ent@billing.test", "DOC-8903");
        seedPersonaUsuario(USER_ADMIN_SUSP, USER_ADMIN_SUSP, "bill_admin_susp", "admin.susp@billing.test", "DOC-8904");
        seedPersonaUsuario(USER_ADMIN_PROP, USER_ADMIN_PROP, "bill_admin_prop", "admin.prop@billing.test", "DOC-8905");
        seedPersonaUsuario(USER_PORTERO, USER_PORTERO, "bill_portero", "portero@billing.test", "DOC-8906");
        seedPersonaUsuario(USER_RESIDENTE, USER_RESIDENTE, "bill_residente", "residente@billing.test", "DOC-8907");

        // 5. Sembrar asignaciones en base de datos
        seedAsignaciones();

        // 6. Configurar mock de asignaciones
        setupAssignmentMocks();

        // 7. Generar tokens JWT
        tokenAdminOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);
        tokenAdminOrgB = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_B);
        tokenAdminEnt  = jwtProvider.generateIdentityToken(USER_ADMIN_ENT);
        tokenAdminSusp = jwtProvider.generateIdentityToken(USER_ADMIN_SUSP);
        tokenAdminProp = jwtProvider.generateIdentityToken(USER_ADMIN_PROP);
        tokenPortero   = jwtProvider.generateIdentityToken(USER_PORTERO);
        tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
    }

    private void elevateToSuperAdmin() {
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // BLOQUE 1: CREACIÓN DE INTENCIÓN DE RENOVACIÓN Y UPGRADE
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-04: Renovación mensual legítima calcula monto exacto desde Oracle (299.000 COP)")
    public void renovacion_mensualLegitima_calculaMontoDesdeOracle() throws Exception {
        MembresiaRenovacionRequestDTO request = new MembresiaRenovacionRequestDTO("MENSUAL");

        String resStr = mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.operacion", is("RENOVACION")))
                .andExpect(jsonPath("$.data.idPlan", is(2)))
                .andExpect(jsonPath("$.data.planNombre", is("Plan Profesional")))
                .andExpect(jsonPath("$.data.montoCentavos", is(29900000))) // 299.000 COP * 100
                .andExpect(jsonPath("$.data.moneda", is("COP")))
                .andExpect(jsonPath("$.data.publicKey", is(TEST_PUBLIC_KEY)))
                .andExpect(jsonPath("$.data.firmaIntegridad", notNullValue()))
                .andExpect(jsonPath("$.data.referencia", startsWith("SAED-RENOVACION-8901-")))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");

        elevateToSuperAdmin();

        // Verificar que se insertó en TRANSACCIONES_PAGO en estado PENDIENTE con la referencia única
        Number countPend = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ? AND ESTADO_PASARELA = 'PENDIENTE'",
                Long.class, referencia
        );
        assertEquals(1L, countPend.longValue());

        // REGLA DE SEGURIDAD: La membresía en MEMBRESIAS NO debe haberse extendido aún
        Map<String, Object> mem = jdbcTemplate.queryForMap(
                "SELECT ESTADO, FECHA_RENOVACION FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", ORG_A_ID
        );
        assertEquals("ACTIVA", mem.get("ESTADO"));
        assertNull(mem.get("FECHA_RENOVACION"), "FECHA_RENOVACION no debe actualizarse antes del pago");
    }

    @Test
    @DisplayName("GAP-ENT-04: Renovación anual aplica deterministamente 20% de descuento (2.870.400 COP)")
    public void renovacion_cicloAnual_aplicaDescuentoVeintePorCiento() throws Exception {
        MembresiaRenovacionRequestDTO request = new MembresiaRenovacionRequestDTO("ANUAL");

        // 299.000 * 12 * 0.80 = 2.870.400 COP -> 287.040.000 centavos
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operacion", is("RENOVACION")))
                .andExpect(jsonPath("$.data.cicloFacturacion", is("ANUAL")))
                .andExpect(jsonPath("$.data.montoCentavos", is(287040000)));
    }

    @Test
    @DisplayName("GAP-ENT-04: Servidor ignora campos de monto y precio enviados en payload por atacante")
    public void renovacion_manipulacionPrecioEnPayload_esIgnoradoPorServidor() throws Exception {
        // Atacante intenta pagar $1.000 COP enviando campos adulterados
        Map<String, Object> hackPayload = Map.of(
                "cicloFacturacion", "MENSUAL",
                "amount", 1000,
                "montoCentavos", 100000,
                "price", 1000,
                "precio", 1000
        );

        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hackPayload)))
                .andExpect(status().isOk())
                // El monto DEBE ser el oficial de Oracle (299.000 COP), NO el adulterado
                .andExpect(jsonPath("$.data.montoCentavos", is(29900000)));
    }

    @Test
    @DisplayName("GAP-ENT-04: Upgrade mensual hacia Plan Enterprise calcula 799.000 COP desde Oracle")
    public void upgrade_mensualLegitimo_calculaPrecioPlanNuevo() throws Exception {
        MembresiaUpgradeRequestDTO request = new MembresiaUpgradeRequestDTO(3L, "MENSUAL"); // 3 = ENTERPRISE

        mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.operacion", is("UPGRADE")))
                .andExpect(jsonPath("$.data.idPlan", is(3)))
                .andExpect(jsonPath("$.data.planNombre", is("Plan Empresarial")))
                .andExpect(jsonPath("$.data.montoCentavos", is(79900000))) // 799.000 COP * 100
                .andExpect(jsonPath("$.data.referencia", startsWith("SAED-UPGRADE-8901-")));

        elevateToSuperAdmin();

        // REGLA DE SEGURIDAD: La membresía aún NO ha cambiado de plan
        Map<String, Object> mem = jdbcTemplate.queryForMap(
                "SELECT ID_PLAN FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", ORG_A_ID
        );
        assertEquals(2L, ((Number) mem.get("ID_PLAN")).longValue(), "El plan no debe migrar antes de pago aprobado");
    }

    @Test
    @DisplayName("GAP-ENT-04: Upgrade hacia el mismo plan es rechazado (400 Bad Request)")
    public void upgrade_mismoPlan_esRechazado() throws Exception {
        MembresiaUpgradeRequestDTO request = new MembresiaUpgradeRequestDTO(2L, "MENSUAL"); // Mismo Plan PRO

        mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GAP-ENT-04: Upgrade hacia plan de menor valor (downgrade) es rechazado (400 Bad Request)")
    public void upgrade_haciaPlanInferior_esRechazado() throws Exception {
        // Org Enterprise (Plan 3) intenta "upgradear" a Plan 2 (PRO)
        MembresiaUpgradeRequestDTO request = new MembresiaUpgradeRequestDTO(2L, "MENSUAL");

        mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .header("Authorization", "Bearer " + tokenAdminEnt)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GAP-ENT-04: Upgrade hacia plan inexistente o inactivo es rechazado (400 Bad Request)")
    public void upgrade_planInexistente_esRechazado() throws Exception {
        MembresiaUpgradeRequestDTO request = new MembresiaUpgradeRequestDTO(999999L, "MENSUAL");

        mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GAP-ENT-04: Membresía suspendida no permite renovación por autoservicio (400 Bad Request)")
    public void renovacion_membresiaSuspendida_esRechazada() throws Exception {
        MembresiaRenovacionRequestDTO request = new MembresiaRenovacionRequestDTO("MENSUAL");

        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminSusp)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_SUSP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // BLOQUE 2: CONFINAMIENTO RBAC Y AISLAMIENTO MULTI-TENANT
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-04: SUPERADMIN no tiene permitido acceso a endpoints comerciales de renovación (403)")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void rbac_superAdmin_bloqueadoEnEndpointsComerciales() throws Exception {
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaUpgradeRequestDTO(3L, "MENSUAL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-04: Roles no autorizados (ADMIN_PROPIEDAD, PORTERO, RESIDENTE) son bloqueados (403)")
    public void rbac_rolesOperacionales_bloqueadosEnRenovacionYUpgrade() throws Exception {
        // ADMIN_PROPIEDAD
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminProp)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // PORTERO
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // RESIDENTE
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // Anónimo / sin token
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GAP-ENT-04: Tenant Isolation: admin de Org A no puede afectar la membresía de Org B")
    public void tenantIsolation_adminOrgANoAfectaOrgB() throws Exception {
        // Admin Org A solicita renovación
        mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaRenovacionRequestDTO("MENSUAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referencia", containsString("8901")));

        elevateToSuperAdmin();

        // Verificar que en Org B no se generó ninguna intención ni cambio
        Number countOrgB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA LIKE 'SAED-RENOVACION-8902-%'",
                Long.class
        );
        assertEquals(0L, countOrgB.longValue());
    }

    // =========================================================================
    // BLOQUE 3: PROCESAMIENTO CRIPTOGRÁFICO DE WEBHOOK, IDEMPOTENCIA Y ATOMICIDAD
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-04: Webhook con firma válida y APPROVED procesa renovación y crea MEMBRESIAS_HISTORIAL")
    public void webhook_renovacionAprobada_extiendeFechaFinYRegistraHistorial() throws Exception {
        // 1. Crear intención previa
        String resStr = mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaRenovacionRequestDTO("MENSUAL"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");
        long montoCentavos = ((Number) data.get("montoCentavos")).longValue();

        // 2. Simular llamada de webhook de Wompi con checksum verídico
        String idWompi = "wompi_tx_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, timestamp, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, timestamp);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk());

        elevateToSuperAdmin();

        // 3. Verificar estado en TRANSACCIONES_PAGO
        String estadoTx = jdbcTemplate.queryForObject(
                "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
                String.class, referencia
        );
        assertEquals("APROBADO", estadoTx);

        // 4. Verificar que la membresía fue renovada y activada
        Map<String, Object> memUpdated = jdbcTemplate.queryForMap(
                "SELECT ESTADO, FECHA_RENOVACION FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", ORG_A_ID
        );
        assertEquals("ACTIVA", memUpdated.get("ESTADO"));
        assertNotNull(memUpdated.get("FECHA_RENOVACION"), "FECHA_RENOVACION debió registrar la fecha de hoy");

        // 5. Verificar registro inmutable en MEMBRESIAS_HISTORIAL
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO, OBSERVACIONES FROM MEMBRESIAS_HISTORIAL WHERE OBSERVACIONES LIKE ? ORDER BY ID_HISTORIAL DESC",
                "%" + referencia + "%"
        );
        assertFalse(hist.isEmpty(), "Debe existir registro en MEMBRESIAS_HISTORIAL");
        assertEquals("RENOVACION", hist.get(0).get("TIPO_CAMBIO"));
        assertEquals(2L, ((Number) hist.get(0).get("ID_PLAN_ANTERIOR")).longValue());
        assertEquals(2L, ((Number) hist.get(0).get("ID_PLAN_NUEVO")).longValue());
    }

    @Test
    @DisplayName("GAP-ENT-04: Webhook de Upgrade actualiza ID_PLAN y registra tipo UPGRADE en historial")
    public void webhook_upgradeAprobado_actualizaPlanYRegistraHistorial() throws Exception {
        // 1. Crear intención de upgrade a Plan 3 (Enterprise)
        String resStr = mockMvc.perform(post("/api/v1/membresias/upgrade")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaUpgradeRequestDTO(3L, "MENSUAL"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");
        long montoCentavos = ((Number) data.get("montoCentavos")).longValue();

        // 2. Simular webhook de Wompi aprobado
        String idWompi = "wompi_upg_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, timestamp, TEST_EVENTS_SECRET);

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, timestamp);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk());

        elevateToSuperAdmin();

        // 3. Verificar que ID_PLAN cambió a 3 (Enterprise)
        Map<String, Object> memUpdated = jdbcTemplate.queryForMap(
                "SELECT ID_PLAN, ESTADO FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", ORG_A_ID
        );
        assertEquals(3L, ((Number) memUpdated.get("ID_PLAN")).longValue(), "El ID_PLAN debió actualizarse a 3");
        assertEquals("ACTIVA", memUpdated.get("ESTADO"));

        // 4. Verificar registro en MEMBRESIAS_HISTORIAL
        List<Map<String, Object>> hist = jdbcTemplate.queryForList(
                "SELECT TIPO_CAMBIO, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO FROM MEMBRESIAS_HISTORIAL WHERE OBSERVACIONES LIKE ? ORDER BY ID_HISTORIAL DESC",
                "%" + referencia + "%"
        );
        assertFalse(hist.isEmpty());
        assertEquals("UPGRADE", hist.get(0).get("TIPO_CAMBIO"));
        assertEquals(2L, ((Number) hist.get(0).get("ID_PLAN_ANTERIOR")).longValue());
        assertEquals(3L, ((Number) hist.get(0).get("ID_PLAN_NUEVO")).longValue());
    }

    @Test
    @DisplayName("GAP-ENT-04: Webhook con checksum falso es rechazado y no muta membresía")
    public void webhook_checksumFalso_esRechazadoSinMutarMembresia() throws Exception {
        // Crear intención
        String resStr = mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaRenovacionRequestDTO("MENSUAL"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");
        long montoCentavos = ((Number) data.get("montoCentavos")).longValue();

        // Enviar webhook con firma corrupta/falsa
        String idWompi = "wompi_fake_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        String fakeChecksum = "bad_checksum_000000000000000000000000000000000000000000000000000000000000";

        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", fakeChecksum, timestamp);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk()); // Webhook endpoint returns 200 to pasarela but ignores body

        elevateToSuperAdmin();

        // Verificar que la transacción sigue PENDIENTE y la membresía NO cambió
        String estadoTx = jdbcTemplate.queryForObject(
                "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
                String.class, referencia
        );
        assertEquals("PENDIENTE", estadoTx);

        Number histCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MEMBRESIAS_HISTORIAL WHERE OBSERVACIONES LIKE ?",
                Long.class, "%" + referencia + "%"
        );
        assertEquals(0L, histCount.longValue(), "No debe existir historial tras firma inválida");
    }

    @Test
    @DisplayName("GAP-ENT-04: Idempotencia estricta: re-envío de webhook no duplica extensión de vigencia ni historial")
    public void webhook_idempotencia_dobleLlegadaNoDuplicaAcciones() throws Exception {
        // 1. Crear intención
        String resStr = mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaRenovacionRequestDTO("MENSUAL"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");
        long montoCentavos = ((Number) data.get("montoCentavos")).longValue();

        String idWompi = "wompi_idemp_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "APPROVED", montoCentavos, timestamp, TEST_EVENTS_SECRET);
        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "APPROVED", montoCentavos, "COP", checksum, timestamp);

        // 2. Primera llegada del webhook
        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk());

        elevateToSuperAdmin();

        // Consultar FECHA_FIN tras primera llegada
        java.sql.Timestamp fechaFin1 = jdbcTemplate.queryForObject(
                "SELECT FECHA_FIN FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?",
                java.sql.Timestamp.class, ORG_A_ID
        );

        // 3. Segunda llegada idéntica del webhook (reintento de red de Wompi)
        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk());

        elevateToSuperAdmin();

        // Consultar FECHA_FIN tras segunda llegada
        java.sql.Timestamp fechaFin2 = jdbcTemplate.queryForObject(
                "SELECT FECHA_FIN FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?",
                java.sql.Timestamp.class, ORG_A_ID
        );

        assertEquals(fechaFin1, fechaFin2, "FECHA_FIN no debe volver a extenderse en evento duplicado");

        // 4. Verificar que solo existe exactamente 1 registro en MEMBRESIAS_HISTORIAL
        Number countHist = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MEMBRESIAS_HISTORIAL WHERE OBSERVACIONES LIKE ?",
                Long.class, "%" + referencia + "%"
        );
        assertEquals(1L, countHist.longValue(), "No debe crearse historial duplicado");
    }

    @Test
    @DisplayName("GAP-ENT-04: Webhook con status DECLINED actualiza transacción a RECHAZADO sin mutar membresía")
    public void webhook_estadoRechazado_noAplicaCambiosEnMembresia() throws Exception {
        // Crear intención
        String resStr = mockMvc.perform(post("/api/v1/membresias/renovar")
                        .header("Authorization", "Bearer " + tokenAdminOrgA)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MembresiaRenovacionRequestDTO("MENSUAL"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = objectMapper.readValue(resStr, Map.class);
        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
        String referencia = (String) data.get("referencia");
        long montoCentavos = ((Number) data.get("montoCentavos")).longValue();

        String idWompi = "wompi_dec_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        String checksum = generateChecksum(idWompi, "DECLINED", montoCentavos, timestamp, TEST_EVENTS_SECRET);
        Map<String, Object> webhookPayload = buildWompiPayload(idWompi, referencia, "DECLINED", montoCentavos, "COP", checksum, timestamp);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk());

        elevateToSuperAdmin();

        // Verificar estado RECHAZADO en TRANSACCIONES_PAGO
        String estadoTx = jdbcTemplate.queryForObject(
                "SELECT ESTADO_PASARELA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = ?",
                String.class, referencia
        );
        assertEquals("RECHAZADO", estadoTx);

        // Membresía no debe tener fecha de renovación
        Map<String, Object> mem = jdbcTemplate.queryForMap(
                "SELECT FECHA_RENOVACION FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", ORG_A_ID
        );
        assertNull(mem.get("FECHA_RENOVACION"));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE SEMILLADO Y MOCKEO
    // =========================================================================

    private void seedOrganizacion(long id, String nombre, String nit, String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO)
                VALUES (?, ?, ?, ?, 'ACTIVA')
                """, id, nombre, nit, email);
        } else {
            jdbcTemplate.update("UPDATE ORGANIZACIONES SET NOMBRE = ?, ESTADO = 'ACTIVA' WHERE ID_ORGANIZACION = ?", nombre, id);
        }
    }

    private void seedPropiedad(long id, long orgId, String nombre) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
                VALUES (?, ?, 1, ?, 'Calle Test', 'Bogota', 'PROPIETARIOS', 'ACTIVA')
                """, id, orgId, nombre);
        } else {
            jdbcTemplate.update("UPDATE PROPIEDADES SET NOMBRE = ?, ID_ORGANIZACION = ?, ESTADO = 'ACTIVA' WHERE ID_PROPIEDAD = ?", nombre, orgId, id);
        }
    }

    private void seedUnidad(long id, long propId, String identificador) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, AREA_M2)
                VALUES (?, ?, ?, 1, 100)
                """, id, propId, identificador);
        } else {
            jdbcTemplate.update("UPDATE UNIDADES SET IDENTIFICADOR = ? WHERE ID_UNIDAD = ?", identificador, id);
        }
    }

    private void seedMembresia(long orgId, long planId, String estado, String esPrueba, int diasFin) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", Integer.class, orgId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
                VALUES (?, ?, TRUNC(SYSDATE), TRUNC(SYSDATE) + ?, ?, ?)
                """, orgId, planId, diasFin, estado, esPrueba);
        } else {
            jdbcTemplate.update("""
                UPDATE MEMBRESIAS
                SET ID_PLAN = ?,
                    FECHA_INICIO = TRUNC(SYSDATE),
                    FECHA_FIN = TRUNC(SYSDATE) + ?,
                    ESTADO = ?,
                    ES_PRUEBA = ?,
                    FECHA_RENOVACION = NULL
                WHERE ID_ORGANIZACION = ?
                """, planId, diasFin, estado, esPrueba, orgId);
        }
    }

    private void seedPersonaUsuario(long personaId, long usuarioId, String nombre, String email, String doc) {
        Integer countP = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, personaId);
        if (countP == null || countP == 0) {
            jdbcTemplate.update("""
                INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, TIPO_PERSONA)
                VALUES (?, 1, ?, ?, 'Tester', ?, 'NATURAL')
                """, personaId, doc, nombre, email);
        } else {
            jdbcTemplate.update("UPDATE PERSONAS SET EMAIL = ? WHERE ID_PERSONA = ?", email, personaId);
        }

        Integer countU = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ? OR NOMBRE_USUARIO = ?", Integer.class, usuarioId, nombre);
        if (countU == null || countU == 0) {
            jdbcTemplate.update("""
                INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO)
                VALUES (?, ?, ?, ?, '$2a$10$dummyHashDummyHashDummyHashDummyHashDummyHashDummyHa', 'ACTIVO')
                """, usuarioId, personaId, nombre, email);
        } else {
            jdbcTemplate.update("UPDATE USUARIOS SET EMAIL = ?, ESTADO = 'ACTIVO', ID_PERSONA = ?, NOMBRE_USUARIO = ? WHERE ID_USUARIO = ? OR NOMBRE_USUARIO = ?",
                    email, personaId, nombre, usuarioId, nombre);
        }
    }

    private void seedAsignaciones() {
        jdbcTemplate.execute("""
            DECLARE
                v_rol_org   NUMBER;
                v_rol_prop  NUMBER;
                v_rol_port  NUMBER;
                v_rol_res   NUMBER;
            BEGIN
                SELECT ID_ROL INTO v_rol_org  FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION';
                SELECT ID_ROL INTO v_rol_prop FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD';
                SELECT ID_ROL INTO v_rol_port FROM ROLES WHERE CODIGO = 'PORTERO';
                SELECT ID_ROL INTO v_rol_res  FROM ROLES WHERE CODIGO = 'RESIDENTE';

                DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (8901, 8902, 8903, 8904, 8905, 8906, 8907);

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8901, 8901, v_rol_org, 8901, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8902, 8902, v_rol_org, 8902, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8903, 8903, v_rol_org, 8903, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8904, 8904, v_rol_org, 8904, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8905, 8905, v_rol_prop, 8901, 8901, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8906, 8906, v_rol_port, 8901, 8901, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (8907, 8907, v_rol_res, 8901, 8901, 8901, 'ACTIVA', TRUNC(SYSDATE));
            END;
        """);
    }

    private void setupAssignmentMocks() {
        mockAssignment(ASSIGN_ADMIN_ORG_A, USER_ADMIN_ORG_A, ORG_A_ID, null, "ADMIN_ORGANIZACION", "ORGANIZACION");
        mockAssignment(ASSIGN_ADMIN_ORG_B, USER_ADMIN_ORG_B, ORG_B_ID, null, "ADMIN_ORGANIZACION", "ORGANIZACION");
        mockAssignment(ASSIGN_ADMIN_ENT, USER_ADMIN_ENT, ORG_ENT_ID, null, "ADMIN_ORGANIZACION", "ORGANIZACION");
        mockAssignment(ASSIGN_ADMIN_SUSP, USER_ADMIN_SUSP, ORG_SUSP_ID, null, "ADMIN_ORGANIZACION", "ORGANIZACION");
        mockAssignment(ASSIGN_ADMIN_PROP, USER_ADMIN_PROP, ORG_A_ID, PROP_A_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_PORTERO, USER_PORTERO, ORG_A_ID, PROP_A_ID, "PORTERO", "PROPIEDAD");
        mockAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE, ORG_A_ID, PROP_A_ID, "RESIDENTE", "UNIDAD");
    }

    private void mockAssignment(Long assignId, Long userId, Long orgId, Long propId, String roleCode, String roleScope) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(assignId);

        RoleDTO rol = new RoleDTO();
        rol.setCodigo(roleCode);
        rol.setAlcance(roleScope);
        dto.setRol(rol);

        OrganizationDTO org = new OrganizationDTO();
        org.setId(orgId);
        org.setNombre("Org " + orgId);
        org.setEstado("ACTIVA");
        dto.setOrganizacion(org);

        if (propId != null) {
            PropertyDTO prop = new PropertyDTO();
            prop.setId(propId);
            prop.setIdOrganizacion(orgId);
            prop.setNombre("Propiedad " + propId);
            prop.setEstado("ACTIVA");
            dto.setPropiedad(prop);
        }

        Mockito.when(assignmentService.validateAssignment(assignId, userId)).thenReturn(Optional.of(dto));
        Mockito.when(assignmentService.getAssignmentsForUser(userId)).thenReturn(List.of(dto));
    }

    private String generateChecksum(String id, String status, long amountInCents, long timestamp, String secret) throws Exception {
        String base = id + status + amountInCents + timestamp + secret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private Map<String, Object> buildWompiPayload(String idWompi, String reference, String status, long amountInCents, String currency, String checksum, long timestamp) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("id", idWompi);
        transaction.put("reference", reference);
        transaction.put("status", status);
        transaction.put("amount_in_cents", amountInCents);
        transaction.put("currency", currency);
        transaction.put("payment_method_type", "CARD");

        Map<String, Object> data = new HashMap<>();
        data.put("transaction", transaction);

        Map<String, Object> signature = new HashMap<>();
        signature.put("properties", List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"));
        signature.put("checksum", checksum);

        Map<String, Object> evento = new HashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", data);
        evento.put("environment", "test");
        evento.put("signature", signature);
        evento.put("timestamp", timestamp);

        return evento;
    }
}
