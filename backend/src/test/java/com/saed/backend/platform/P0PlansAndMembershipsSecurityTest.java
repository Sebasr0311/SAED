package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0PlansAndMembershipsSecurityTest — Suite de verificación P0 para:
 * - GAP-ENT-01: PlanesController sin mutaciones y PlatformPlansController restringido a SUPERADMIN.
 * - GAP-ENT-02: Blindaje contra auto-upgrade y alteración de membresías sin pago.
 * - GAP-ENT-05: Trazabilidad e integridad en MEMBRESIAS_HISTORIAL respetando restricciones y tipos de cambio Oracle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class P0PlansAndMembershipsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // =========================================================================
    // 1. GAP-ENT-01: PLATFORM PLANS EXCLUSIVO DE SUPERADMIN (403 FORBIDDEN)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-01: ADMIN_PROPIEDAD no puede crear planes en plataforma (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotCreatePlatformPlan() throws Exception {
        Map<String, Object> body = Map.of(
                "nombre", "Plan Hack",
                "codigo", "HACK_01",
                "precioMensual", 0
        );
        mockMvc.perform(post("/api/v1/platform/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-01: ADMIN_ORGANIZACION no puede actualizar planes en plataforma (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void adminOrg_cannotUpdatePlatformPlan() throws Exception {
        Map<String, Object> body = Map.of(
                "nombre", "Plan Modificado",
                "precioMensual", 10000
        );
        mockMvc.perform(put("/api/v1/platform/plans/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-01: ADMIN_PROPIEDAD no puede cambiar estado de planes en plataforma (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotTogglePlatformPlanStatus() throws Exception {
        Map<String, Object> body = Map.of("activo", false);
        mockMvc.perform(patch("/api/v1/platform/plans/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-01: RESIDENTE no puede consultar ni mutar planes de plataforma (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"})
    public void residente_cannotAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/platform/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. GAP-ENT-01: PLANES CONTROLLER ES ESTRICTAMENTE DE LECTURA (404/405)
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-01: POST /api/v1/planes ya no existe (405 Method Not Allowed)")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void planesController_postNoLongerExists() throws Exception {
        Map<String, Object> body = Map.of(
                "nombre", "Plan Inválido",
                "codigo", "INVALID_01"
        );
        mockMvc.perform(post("/api/v1/planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GAP-ENT-01: PUT /api/v1/planes/{id} ya no existe (405 Method Not Allowed)")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void planesController_putNoLongerExists() throws Exception {
        Map<String, Object> body = Map.of("nombre", "Plan Inválido");
        mockMvc.perform(put("/api/v1/planes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GAP-ENT-01: PATCH /api/v1/planes/{id}/status ya no existe (404 Not Found)")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void planesController_patchStatusNoLongerExists() throws Exception {
        Map<String, Object> body = Map.of("activo", true);
        mockMvc.perform(patch("/api/v1/planes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GAP-ENT-01: GET /api/v1/planes es permitido para roles administrativos")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void planesController_readCatalogAllowedForTenantAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/api/v1/planes/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // =========================================================================
    // 3. GAP-ENT-02: BLINDAJE CONTRA BYPASS Y CAMBIO DE PLAN SIN PAGO
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-02: Endpoint espurio /api/v1/membresias/cambiar-plan no existe (405 Method Not Allowed)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void tenantAdmin_cannotInvokeSelfUpgradeEndpoint() throws Exception {
        Map<String, Object> body = Map.of("idPlan", 3L);
        mockMvc.perform(post("/api/v1/membresias/cambiar-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GAP-ENT-02: ADMIN_ORGANIZACION no puede mutar membresías vía MembresiasController (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void adminOrg_cannotMutateMembershipViaMembresiasController() throws Exception {
        Map<String, Object> body = Map.of(
                "idOrganizacion", 1L,
                "idPlan", 3L,
                "estado", "ACTIVA"
        );
        // POST crear
        mockMvc.perform(post("/api/v1/membresias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        // PATCH status
        mockMvc.perform(patch("/api/v1/membresias/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "ACTIVA"))))
                .andExpect(status().isForbidden());

        // DELETE cancelar
        mockMvc.perform(delete("/api/v1/membresias/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GAP-ENT-02: ADMIN_PROPIEDAD no puede mutar membresías de plataforma (403 Forbidden)")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotMutatePlatformMemberships() throws Exception {
        Map<String, Object> body = Map.of(
                "idOrganizacion", 1L,
                "idPlan", 3L,
                "estado", "ACTIVA"
        );
        mockMvc.perform(post("/api/v1/platform/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/platform/memberships/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "SUSPENDIDA"))))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. GAP-ENT-05: INTEGRIDAD Y ATOMICIDAD EN MEMBRESIAS_HISTORIAL
    // =========================================================================

    @Test
    @DisplayName("GAP-ENT-05: SUPERADMIN actualiza estado de membresía y genera registro válido en MEMBRESIAS_HISTORIAL")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_updateStatusGeneratesValidHistorialRecord() throws Exception {
        // Asegurar que existe al menos una membresía en base de datos para la prueba
        List<Map<String, Object>> mems = jdbcTemplate.queryForList(
                "SELECT ID_MEMBRESIA, ESTADO, ID_PLAN FROM MEMBRESIAS WHERE ROWNUM = 1",
                new MapSqlParameterSource());

        if (!mems.isEmpty()) {
            Long idMembresia = ((Number) mems.get(0).get("ID_MEMBRESIA")).longValue();

            // Suspender membresía
            mockMvc.perform(put("/api/v1/platform/memberships/" + idMembresia + "/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("estado", "SUSPENDIDA"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));

            // Verificar inserción atómica en MEMBRESIAS_HISTORIAL con tipo SUSPENSION
            List<Map<String, Object>> historial = jdbcTemplate.queryForList(
                    "SELECT TIPO_CAMBIO, ID_PLAN_NUEVO, OBSERVACIONES, REALIZADO_POR " +
                    "FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                    new MapSqlParameterSource("id", idMembresia));

            assertFalse(historial.isEmpty(), "Debe existir un registro de historial generado");
            Map<String, Object> ultimo = historial.get(0);
            assertEquals("SUSPENSION", ultimo.get("TIPO_CAMBIO"), "El tipo de cambio debe ser SUSPENSION conforme a CK_MEMBHIST_TIPO");
            assertNotNull(ultimo.get("ID_PLAN_NUEVO"), "ID_PLAN_NUEVO no debe ser nulo");
        }
    }

    @Test
    @DisplayName("GAP-ENT-05: SUPERADMIN reactiva membresía suspendida y genera REACTIVACION en historial")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_reactivateGeneratesReactivacionHistorial() throws Exception {
        List<Map<String, Object>> mems = jdbcTemplate.queryForList(
                "SELECT ID_MEMBRESIA FROM MEMBRESIAS WHERE ROWNUM = 1",
                new MapSqlParameterSource());

        if (!mems.isEmpty()) {
            Long idMembresia = ((Number) mems.get(0).get("ID_MEMBRESIA")).longValue();

            // Suspender primero
            jdbcTemplate.update("UPDATE MEMBRESIAS SET ESTADO = 'SUSPENDIDA' WHERE ID_MEMBRESIA = :id",
                    new MapSqlParameterSource("id", idMembresia));

            // Reactivar mediante endpoint
            mockMvc.perform(put("/api/v1/platform/memberships/" + idMembresia + "/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("estado", "ACTIVA"))))
                    .andExpect(status().isOk());

            List<Map<String, Object>> historial = jdbcTemplate.queryForList(
                    "SELECT TIPO_CAMBIO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                    new MapSqlParameterSource("id", idMembresia));

            assertFalse(historial.isEmpty());
            assertEquals("REACTIVACION", historial.get(0).get("TIPO_CAMBIO"), "Debe registrarse como REACTIVACION");
        }
    }

    @Test
    @DisplayName("GAP-ENT-05: SUPERADMIN cancela membresía y genera CANCELACION en historial")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cancelGeneratesCancelacionHistorial() throws Exception {
        List<Map<String, Object>> mems = jdbcTemplate.queryForList(
                "SELECT ID_MEMBRESIA FROM MEMBRESIAS WHERE ROWNUM = 1",
                new MapSqlParameterSource());

        if (!mems.isEmpty()) {
            Long idMembresia = ((Number) mems.get(0).get("ID_MEMBRESIA")).longValue();

            mockMvc.perform(put("/api/v1/platform/memberships/" + idMembresia + "/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("estado", "CANCELADA"))))
                    .andExpect(status().isOk());

            List<Map<String, Object>> historial = jdbcTemplate.queryForList(
                    "SELECT TIPO_CAMBIO FROM MEMBRESIAS_HISTORIAL WHERE ID_MEMBRESIA = :id ORDER BY ID_HISTORIAL DESC",
                    new MapSqlParameterSource("id", idMembresia));

            assertFalse(historial.isEmpty());
            assertEquals("CANCELACION", historial.get(0).get("TIPO_CAMBIO"), "Debe registrarse como CANCELACION");
        }
    }
}
