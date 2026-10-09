package com.saed.backend.porteria.controller;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.porteria.service.PorteriaService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C-06: only gate roles (portero / property admin) may look up visitors by document.
 */
@WebMvcTest(PorteriaController.class)
@Import(SecurityConfig.class)
class PorteriaVisitorLookupAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PorteriaService porteriaService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("C-06: RESIDENTE gets 403 on visitor lookup and no query runs")
    @WithMockUser(authorities = "SCOPE_RESIDENTE")
    void residentIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/porteria/visitas/buscar").param("documento", "123456"))
                .andExpect(status().isForbidden());
        verify(namedParameterJdbcTemplate, never()).queryForList(anyString(), anyMap());
        verify(namedParameterJdbcTemplate, never()).getJdbcOperations();
    }

    @Test
    @DisplayName("C-06: RESIDENTE_CONVIVENCIA gets 403 on visitor lookup")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    void cohabitantIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/porteria/visitas/buscar").param("documento", "123456"))
                .andExpect(status().isForbidden());
        verify(namedParameterJdbcTemplate, never()).queryForList(anyString(), anyMap());
    }

    @Test
    @DisplayName("C-06: PORTERO is allowed to call visitor lookup")
    @WithMockUser(authorities = "SCOPE_PORTERO")
    void porteroIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/porteria/visitas/buscar").param("documento", "123456"))
                .andExpect(status().isOk());
    }
}
