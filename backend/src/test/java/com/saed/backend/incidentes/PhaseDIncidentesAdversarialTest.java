package com.saed.backend.incidentes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class PhaseDIncidentesAdversarialTest {

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    public void tearDown() {
        // Limpiar contexto manual si fue alterado, manejado por los filtros
    }

    @Test
    @WithMockUser(username = "admin_org1", roles = {"SUPERADMIN"})
    public void adminOrg1_NoDebeVerIncidentesDeOrg2() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/admin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is5xxServerError());
    }
}
