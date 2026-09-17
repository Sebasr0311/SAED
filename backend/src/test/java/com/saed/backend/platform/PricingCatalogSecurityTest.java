package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PricingCatalogSecurityTest — Suite de pruebas exhaustiva para GAP-ENT-07:
 * Pricing / Catálogo Comercial Dinámico en SAED 2.0.
 *
 * Cobertura de certificación:
 * 1. Acceso público sin autenticación a GET /api/v1/planes y /api/v1/planes/catalogo (200 OK).
 * 2. Filtrado estricto de planes inactivos para solicitudes no autenticadas / públicas.
 * 3. Visibilidad de planes inactivos solo para SUPERADMIN vía solo_activos=false.
 * 4. Precisión matemática canónica de precios mensuales y anuales (20% de descuento).
 * 5. Concordancia canónica de límites de Modelo C y Cuotas de Storage (GAP-ENT-06).
 * 6. Entitlements modulares dinámicos desde PLAN_MODULOS y MODULOS.
 * 7. Blindaje estricto de /api/v1/platform/plans (401 para anónimos, 403 para tenant admins / residentes).
 * 8. Catálogo público de onboarding (/api/v1/auth/onboarding/planes) consistente con /planes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PricingCatalogSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private Long testInactivePlanId = null;

    @BeforeEach
    public void setUp() {
        cleanTestData();
    }

    @AfterEach
    public void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        try {
            jdbcTemplate.update("DELETE FROM PLAN_MODULOS WHERE ID_PLAN IN (SELECT ID_PLAN FROM PLANES WHERE CODIGO LIKE 'TEST_INACTIVO%')", new MapSqlParameterSource());
            jdbcTemplate.update("DELETE FROM PLANES WHERE CODIGO LIKE 'TEST_INACTIVO%'", new MapSqlParameterSource());
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 1. ACCESO PÚBLICO ANÓNIMO (PERMITALL) AL CATÁLOGO
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Solicitud anónima a GET /api/v1/planes retorna 200 OK y catálogo activo")
    public void anonymous_canAccessPublicCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/planes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].codigo", hasItems("FREE", "PRO", "ENTERPRISE")));
    }

    @Test
    @DisplayName("GAP-ENT-07: Solicitud anónima a GET /api/v1/planes/catalogo retorna 200 OK")
    public void anonymous_canAccessPublicSimplifiedCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/planes/catalogo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("GAP-ENT-07: Solicitud anónima a detalle de plan activo retorna 200 OK")
    public void anonymous_canAccessActivePlanDetail() throws Exception {
        // Consultar el plan PRO
        Integer idPro = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM PLANES WHERE CODIGO = 'PRO' AND ESTADO = 'ACTIVO'",
                new MapSqlParameterSource(),
                Integer.class
        );
        assertNotNull(idPro, "El plan PRO debe existir en el catálogo");

        mockMvc.perform(get("/api/v1/planes/" + idPro)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.codigo").value("PRO"))
                .andExpect(jsonPath("$.data.precioMensual").value(299000))
                .andExpect(jsonPath("$.data.precioAnual").value(2870400))
                .andExpect(jsonPath("$.data.descuentoAnual").value(20));
    }

    // =========================================================================
    // 2. FILTRADO ESTRICTO DE PLANES INACTIVOS
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Anónimo no recibe planes inactivos aunque envíe solo_activos=false")
    public void anonymous_cannotSeeInactivePlans() throws Exception {
        // Insertar plan inactivo de prueba
        jdbcTemplate.update("""
            INSERT INTO PLANES (CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB, ESTADO)
            VALUES ('TEST_INACTIVO_01', 'Plan Oculto', 'Plan de prueba inactivo', 99000, 1, 10, 5, 2, 'INACTIVO')
            """, new MapSqlParameterSource());

        mockMvc.perform(get("/api/v1/planes?solo_activos=false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[*].codigo", not(hasItem("TEST_INACTIVO_01"))));
    }

    @Test
    @DisplayName("GAP-ENT-07: SUPERADMIN puede ver todos los planes (incluyendo inactivos) con solo_activos=false")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canSeeAllPlansIncludingInactive() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO PLANES (CODIGO, NOMBRE, DESCRIPCION, PRECIO_MENSUAL, LIMITE_PROPIEDADES, LIMITE_UNIDADES, LIMITE_USUARIOS, LIMITE_ALMACENAMIENTO_GB, ESTADO)
            VALUES ('TEST_INACTIVO_02', 'Plan Oculto Admin', 'Plan inactivo para admin', 99000, 1, 10, 5, 2, 'INACTIVO')
            """, new MapSqlParameterSource());

        mockMvc.perform(get("/api/v1/planes?solo_activos=false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[*].codigo", hasItem("TEST_INACTIVO_02")));
    }

    // =========================================================================
    // 3. EXACTITUD MATEMÁTICA Y LÍMITES MODELO C (ORACLE SINGLE SOURCE OF TRUTH)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Verificación canónica de precios, ciclo anual (-20%) y límites Modelo C")
    public void catalog_oraclePricesAndModelCLimitsMatch() throws Exception {
        mockMvc.perform(get("/api/v1/planes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                // FREE: $0, 1 prop, 10 unidades, 5 usuarios, 1 GB storage
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].precioMensual").value(hasItem(0)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].precioAnual").value(hasItem(0)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].limitePropiedades").value(hasItem(1)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].limiteUnidades").value(hasItem(10)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].limiteUsuarios").value(hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'FREE')].limiteAlmacenamientoGb").value(hasItem(1)))
                // PRO: $299.000 COP/mes, $2.870.400 COP/año, 5 props, 100 unidades, 50 usuarios, 10 GB storage
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].precioMensual").value(hasItem(299000)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].precioAnual").value(hasItem(2870400)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].descuentoAnual").value(hasItem(20)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].limitePropiedades").value(hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].limiteUnidades").value(hasItem(100)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].limiteUsuarios").value(hasItem(50)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].limiteAlmacenamientoGb").value(hasItem(10)))
                // ENTERPRISE: $799.000 COP/mes, $7.670.400 COP/año, 999 props, 9999 unidades, 999 usuarios, 100 GB storage
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].precioMensual").value(hasItem(799000)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].precioAnual").value(hasItem(7670400)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].descuentoAnual").value(hasItem(20)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].limitePropiedades").value(hasItem(999)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].limiteUnidades").value(hasItem(9999)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].limiteUsuarios").value(hasItem(999)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].limiteAlmacenamientoGb").value(hasItem(100)));
    }

    // =========================================================================
    // 4. ENTITLEMENTS MODULARES DINÁMICOS DESDE PLAN_MODULOS
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Módulos y features dinámicas concuerdan con PLAN_MODULOS")
    public void catalog_modulesAndEntitlementsMatchPlanModulos() throws Exception {
        var mvcResult = mockMvc.perform(get("/api/v1/planes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        var root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        var dataNode = root.get("data");
        assertTrue(dataNode.isArray());

        com.fasterxml.jackson.databind.JsonNode proNode = null;
        com.fasterxml.jackson.databind.JsonNode entNode = null;

        for (var plan : dataNode) {
            String codigo = plan.path("codigo").asText();
            if ("PRO".equalsIgnoreCase(codigo)) proNode = plan;
            if ("ENTERPRISE".equalsIgnoreCase(codigo)) entNode = plan;
        }

        assertNotNull(proNode, "El plan PRO debe estar en el catálogo");
        assertNotNull(entNode, "El plan ENTERPRISE debe estar en el catálogo");

        List<String> proMods = new java.util.ArrayList<>();
        for (var m : proNode.path("modulosCodigos")) {
            proMods.add(m.asText());
        }

        List<String> entMods = new java.util.ArrayList<>();
        for (var m : entNode.path("modulosCodigos")) {
            entMods.add(m.asText());
        }

        // PRO: tiene módulos operativos habilitados y NO tiene ASAMBLEAS
        assertTrue(proMods.containsAll(List.of("OBRAS", "POLIZAS", "RESERVAS", "PAQUETES", "PARQUEADEROS", "PQRS", "FINANZAS")),
                "PRO debe incluir los 7 módulos operativos: " + proMods);
        assertFalse(proMods.contains("ASAMBLEAS"), "PRO NO debe tener ASAMBLEAS habilitado");

        // ENTERPRISE: tiene los 8 módulos incluyendo ASAMBLEAS
        assertTrue(entMods.containsAll(List.of("ASAMBLEAS", "OBRAS", "POLIZAS", "RESERVAS", "PAQUETES", "PARQUEADEROS", "PQRS", "FINANZAS")),
                "ENTERPRISE debe incluir los 8 módulos incluyendo ASAMBLEAS: " + entMods);
    }

    // =========================================================================
    // 5. PROTECCIÓN ESTRICTA DE ENDPOINTS ADMINISTRATIVOS (PLATFORM/PLANS)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Anónimo no puede acceder a /api/v1/platform/plans (401 Unauthorized)")
    public void anonymous_cannotAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GAP-ENT-07: ADMIN_ORGANIZACION no puede acceder a /api/v1/platform/plans (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void tenantAdmin_cannotAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-07: RESIDENTE no puede acceder a /api/v1/platform/plans (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"})
    public void residente_cannotAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-07: SUPERADMIN puede acceder a /api/v1/platform/plans (200 OK)")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // =========================================================================
    // 6. CATÁLOGO DE ONBOARDING (/auth/onboarding/planes)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: Endpoint público /auth/onboarding/planes concuerda con cálculo canónico")
    public void publicOnboarding_planesEndpoint_matchesCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/auth/onboarding/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].precioAnual").value(hasItem(2870400)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'PRO')].descuentoAnual").value(hasItem(20)))
                .andExpect(jsonPath("$.data[?(@.codigo == 'ENTERPRISE')].precioAnual").value(hasItem(7670400)));
    }

    // =========================================================================
    // 7. POLÍTICA CENTRALIZADA DE PRECIOS Y FACTURACIÓN (PLAN PRICING POLICY)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-07: PlanPricingPolicy es la fuente única de verdad para catálogo, onboarding y Wompi")
    public void pricingPolicy_isSingleSourceOfTruth() throws Exception {
        // PRO: 299000 mensual -> 2870400 anual
        long proMensual = 299000L;
        long proAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularPrecioAnual(proMensual);
        assertEquals(2870400L, proAnual);
        assertEquals(20, com.saed.backend.finanzas.service.PlanPricingPolicy.DESCUENTO_ANUAL_PORCENTAJE);

        // Validar que el monto de Wompi en centavos coincide exactamente
        long centavosAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(proMensual, "ANUAL");
        assertEquals(287040000L, centavosAnual);

        long centavosMensual = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(proMensual, "MENSUAL");
        assertEquals(29900000L, centavosMensual);

        // ENTERPRISE: 799000 mensual -> 7670400 anual
        long entMensual = 799000L;
        long entAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularPrecioAnual(entMensual);
        assertEquals(7670400L, entAnual);
        assertEquals(767040000L, com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(entMensual, "ANUAL"));

        // FREE: 0 mensual -> 0 anual
        assertEquals(0L, com.saed.backend.finanzas.service.PlanPricingPolicy.calcularPrecioAnual(0L));
        assertEquals(0L, com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(0L, "ANUAL"));
    }
}
