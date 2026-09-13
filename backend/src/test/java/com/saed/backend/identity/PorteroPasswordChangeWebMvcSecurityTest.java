package com.saed.backend.identity;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.controller.MeController;
import com.saed.backend.identity.service.ContextService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-02 Fast Isolated Security Tests:
 * Verifies RBAC matrix for /api/v1/me/change-password at the Spring Security Web Layer.
 * Ensures SCOPE_PORTERO is rejected with 403 Forbidden and produces ZERO mutation on USUARIOS,
 * while authorized scopes are permitted and proceed to credential mutation.
 */
@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
public class PorteroPasswordChangeWebMvcSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContextService contextService;

    @MockBean
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtProvider jwtTokenProvider;

    private static final String VALID_PAYLOAD = "{\"passwordActual\":\"PasswordActual123!\",\"nuevaPassword\":\"NuevaPassword456!\"}";

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(
                SaedContext.builder()
                        .userId(500L)
                        .organizationId(1L)
                        .propertyId(1L)
                        .roleCode("RESIDENTE")
                        .roleScope("UNIDAD")
                        .build()
        );

        Mockito.when(jdbcTemplate.query(contains("SELECT HASH_PASSWORD FROM USUARIOS"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("$2a$10$existingHash1234567890"));
        Mockito.when(passwordEncoder.matches("PasswordActual123!", "$2a$10$existingHash1234567890"))
                .thenReturn(true);
        Mockito.when(passwordEncoder.encode("NuevaPassword456!"))
                .thenReturn("$2a$10$newHash1234567890");
        Mockito.when(jdbcTemplate.update(contains("UPDATE USUARIOS SET HASH_PASSWORD"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    @DisplayName("P2-02 MATRIX: PORTERO is 403 Forbidden and ZERO mutation on credentials occurs")
    @WithMockUser(username = "portero_test", authorities = {"SCOPE_PORTERO"})
    public void tc01_portero_forbidden_and_no_credential_mutation() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden());

        // Probar que el cuerpo del método nunca ejecutó la actualización ni tocó el encoder
        Mockito.verify(jdbcTemplate, Mockito.never()).update(
                contains("UPDATE USUARIOS SET HASH_PASSWORD"),
                any(MapSqlParameterSource.class)
        );
        Mockito.verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("P2-02 MATRIX: RESIDENTE is authorized and mutates password")
    @WithMockUser(username = "residente_test", authorities = {"SCOPE_RESIDENTE"})
    public void tc02_residente_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(jdbcTemplate).update(
                eq("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 MATRIX: RESIDENTE_CONVIVENCIA is authorized and mutates password")
    @WithMockUser(username = "conviviente_test", authorities = {"SCOPE_RESIDENTE_CONVIVENCIA"})
    public void tc03_residente_convivencia_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(jdbcTemplate).update(
                eq("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 MATRIX: ADMIN_PROPIEDAD is authorized and mutates password")
    @WithMockUser(username = "admin_prop_test", authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    public void tc04_admin_propiedad_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(jdbcTemplate).update(
                eq("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 MATRIX: ADMIN_ORGANIZACION is authorized and mutates password")
    @WithMockUser(username = "admin_org_test", authorities = {"SCOPE_ADMIN_ORGANIZACION"})
    public void tc05_admin_organizacion_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(jdbcTemplate).update(
                eq("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 MATRIX: SUPERADMIN is authorized and mutates password")
    @WithMockUser(username = "superadmin_test", authorities = {"SCOPE_SUPERADMIN"})
    public void tc06_superadmin_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(jdbcTemplate).update(
                eq("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = :uid"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 MATRIX: Anonymous/No Token is rejected with 401 Unauthorized")
    public void tc07_anonymous_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized());

        Mockito.verify(jdbcTemplate, Mockito.never()).update(
                contains("UPDATE USUARIOS SET HASH_PASSWORD"),
                any(MapSqlParameterSource.class)
        );
        Mockito.verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("P2-02 NON-REGRESSION: RESIDENTE wrong current password returns 400 and NO UPDATE occurs")
    @WithMockUser(username = "residente_test", authorities = {"SCOPE_RESIDENTE"})
    public void tc08_residente_wrong_password_no_update() throws Exception {
        Mockito.when(passwordEncoder.matches("WrongPassword123!", "$2a$10$existingHash1234567890"))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"WrongPassword123!\",\"nuevaPassword\":\"NuevaPassword456!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("La contraseña actual ingresada es incorrecta"));

        Mockito.verify(jdbcTemplate, Mockito.never()).update(
                contains("UPDATE USUARIOS SET HASH_PASSWORD"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    @DisplayName("P2-02 NON-REGRESSION: RESIDENTE short new password returns 400 and NO UPDATE occurs")
    @WithMockUser(username = "residente_test", authorities = {"SCOPE_RESIDENTE"})
    public void tc09_residente_short_password_no_update() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"PasswordActual123!\",\"nuevaPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("La nueva contraseña debe tener al menos 6 caracteres"));

        Mockito.verify(jdbcTemplate, Mockito.never()).update(
                contains("UPDATE USUARIOS SET HASH_PASSWORD"),
                any(MapSqlParameterSource.class)
        );
    }
}
