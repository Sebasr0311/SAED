package com.saed.backend.pqrs;

import org.junit.jupiter.api.DisplayName;
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
public class PhaseDPqrsAdversarialTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar tickets PQRS de su copropiedad")
    @WithMockUser(username = "admin_prop1", authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void adminPropiedad_debeVerPqrsDeSuCopropiedad() throws Exception {
        mockMvc.perform(get("/api/v1/pqrs/todos"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERADMIN denegado en tickets PQRS operativos de copropiedad")
    @WithMockUser(username = "superadmin_user", authorities = {"SCOPE_SUPERADMIN"})
    public void superAdmin_noDebeVerPqrsDeCopropiedad() throws Exception {
        mockMvc.perform(get("/api/v1/pqrs/todos"))
                .andExpect(status().isForbidden());
    }
}
