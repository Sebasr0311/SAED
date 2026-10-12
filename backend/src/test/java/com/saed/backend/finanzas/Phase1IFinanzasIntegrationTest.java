package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class Phase1IFinanzasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        SaedContext ctx = SaedContext.builder()
            .userId(1L)
            .organizationId(1L)
            .propertyId(1L)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(ctx);
        // The test runs inside one transaction, so the Oracle session keeps the context set here for the module
        // entitlement lookup (MEMBRESIAS is under RLS); same setup as PhaseBFinanzasAdversarialTest.
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username="2")
    void adminCannotCreateContratoWithoutActiveTemplate() throws Exception {
        ContratoRequestDTO request = new ContratoRequestDTO(
            1L, 1L, LocalDate.now(), LocalDate.now().plusYears(1), "INICIAL", new BigDecimal("1200000")
        );

        mockMvc.perform(post("/api/v1/contratos")
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // DEC-09: no contract template is seeded for a demo organization, and an organization without an active
                // template cannot create contracts (400); the lenient 201/403/500 expectation hid this rule.
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("plantilla de contrato activa")));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username="4")
    void residenteCannotCreateContrato() throws Exception {
        ContratoRequestDTO request = new ContratoRequestDTO(
            1L, 1L, LocalDate.now(), LocalDate.now().plusYears(1), "INICIAL", new BigDecimal("1200000")
        );

        mockMvc.perform(post("/api/v1/contratos")
                .header("X-Assignment-Id", "104")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username="2")
    void adminCanGetCuotas() throws Exception {
        mockMvc.perform(get("/api/v1/cuotas?pendientes=true")
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk());
    }
}
