package com.saed.backend.comunicacion.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.context.annotation.Import;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.security.jwt.JwtProvider;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import java.util.Collections;

@WebMvcTest(AlertasController.class)
@Import(SecurityConfig.class)
public class AlertasControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @MockBean
    private JwtProvider jwtTokenProvider;

    @Test
    @WithMockUser(authorities = "SCOPE_ADMIN_PROPIEDAD")
    public void testGetAlertas() throws Exception {
        Mockito.when(jdbcTemplate.query(ArgumentMatchers.anyString(), ArgumentMatchers.any(RowMapper.class)))
               .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/alertas"))
               .andExpect(status().isOk());
    }
}
