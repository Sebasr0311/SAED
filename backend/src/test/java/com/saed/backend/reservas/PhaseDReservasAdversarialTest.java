package com.saed.backend.reservas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class PhaseDReservasAdversarialTest {

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    public void tearDown() {
        // Limpiar contexto manual si fue alterado, manejado por los filtros
    }

    @Test
    @WithMockUser(username = "admin_org1", authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_NoDebeVerReservasDeNingunaOrganizacion() throws Exception {
        // DEC-02 / c074514: SUPERADMIN has no operational data access, so reservations of every organization are denied
        // (previously the call returned an empty list for the platform administrator).
        mockMvc.perform(get("/api/v1/reservas/todas")
                .header("X-Organization-Id", "1"))
                .andExpect(status().isForbidden());
    }
}
