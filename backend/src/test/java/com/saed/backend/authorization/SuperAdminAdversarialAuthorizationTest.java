package com.saed.backend.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SuperAdminAdversarialAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    // ==========================================
    // 1. SUPERADMIN DENEGADO EN OPERACIÓN DE PROPIEDAD (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("SUPERADMIN no puede consultar multas operativas de copropiedad")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessMultas() throws Exception {
        mockMvc.perform(get("/api/v1/multas/todas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar quejas operativas de copropiedad")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessQuejas() throws Exception {
        mockMvc.perform(get("/api/v1/quejas/todas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar visitas de unidades")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessVisitasPorUnidad() throws Exception {
        mockMvc.perform(get("/api/v1/porteria/unidades/1/visitas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar historial de visitas operativas")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessVisitasHistorial() throws Exception {
        mockMvc.perform(get("/api/v1/porteria/visitas/historial")
                        .param("fechaInicio", "2026-01-01")
                        .param("fechaFin", "2026-12-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar cuotas operativas de copropiedad")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessCuotas() throws Exception {
        mockMvc.perform(get("/api/v1/cuotas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar residentes ni personas operativas")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessPersonas() throws Exception {
        mockMvc.perform(get("/api/v1/personas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar habitantes de unidades")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessUnitResidents() throws Exception {
        mockMvc.perform(get("/api/v1/units/1/residents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN no puede consultar reportes de cartera morosa de copropiedad")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_cannotAccessReportesCartera() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 2. SUPERADMIN PERMITIDO EN PLATAFORMA SAAS (200 OK)
    // ==========================================

    @Test
    @DisplayName("SUPERADMIN puede consultar dashboard de plataforma")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessPlatformDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/platform/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERADMIN puede consultar planes SaaS")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERADMIN puede consultar membresías SaaS")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessPlatformMemberships() throws Exception {
        mockMvc.perform(get("/api/v1/platform/memberships"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERADMIN puede consultar administradores de plataforma")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessPlatformAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/platform/admins"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERADMIN puede consultar organizaciones SaaS")
    @WithMockUser(authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_canAccessOrganizations() throws Exception {
        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 3. ADMIN_PROPIEDAD DENEGADO EN PLATAFORMA (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar dashboard de plataforma")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotAccessPlatformDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/platform/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar planes SaaS de plataforma")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotAccessPlatformPlans() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar membresías SaaS de organizaciones")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotAccessPlatformMemberships() throws Exception {
        mockMvc.perform(get("/api/v1/platform/memberships"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar operadores ni admins de plataforma")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_cannotAccessPlatformAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/platform/admins"))
                .andExpect(status().isForbidden());
    }
}
