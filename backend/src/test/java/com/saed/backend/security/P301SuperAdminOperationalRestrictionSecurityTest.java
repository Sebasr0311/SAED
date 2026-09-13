package com.saed.backend.security;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.comunicacion.controller.AlertasController;
import com.saed.backend.comunicacion.controller.ComunicadosController;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.GastosController;
import com.saed.backend.org.controller.OrgGastosController;
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
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-01: Restricción de SUPERADMIN en rutas operativas de Organización.
 * Verifica que SUPERADMIN reciba HTTP 403 Forbidden al intentar acceder o mutar
 * gastos organizacionales y comunicaciones operativas de copropiedades,
 * mientras que ADMIN_ORGANIZACION y ADMIN_PROPIEDAD conservan sus permisos legítimos.
 */
@WebMvcTest({
    OrgGastosController.class,
    GastosController.class,
    ComunicadosController.class,
    AlertasController.class
})
@Import(SecurityConfig.class)
public class P301SuperAdminOperationalRestrictionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private JwtProvider jwtTokenProvider;

    @BeforeEach
    public void setupContext() {
        SaedContextHolder.setContext(
            SaedContext.builder()
                .userId(100L)
                .organizationId(1L)
                .propertyId(10L)
                .roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZACION")
                .build()
        );

        Mockito.when(jdbcTemplate.query(ArgumentMatchers.anyString(), ArgumentMatchers.any(SqlParameterSource.class), ArgumentMatchers.any(RowMapper.class)))
               .thenReturn(Collections.emptyList());
        Mockito.doNothing().when(jdbcTemplate).query(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(SqlParameterSource.class),
            ArgumentMatchers.any(RowCallbackHandler.class)
        );
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // 1. GASTOS ORGANIZACIONALES (/api/v1/org/gastos) - SUPERADMIN DENEGADO (403)
    // =========================================================================

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en GET /api/v1/org/gastos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetOrgGastosConsolidados() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en GET /api/v1/org/gastos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetOrgGastoDetalle() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos/10"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en GET /api/v1/org/gastos/{id}/soporte")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetOrgGastoSoporte() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos/10/soporte"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. GASTOS OPERATIVOS PROPIEDAD (/api/v1/gastos) - SUPERADMIN DENEGADO (403)
    // =========================================================================

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en GET /api/v1/gastos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotListGastos() throws Exception {
        mockMvc.perform(get("/api/v1/gastos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en POST /api/v1/gastos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateGasto() throws Exception {
        mockMvc.perform(post("/api/v1/gastos")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"monto\": 50000, \"categoria\": \"MANTENIMIENTO\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en PUT /api/v1/gastos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotUpdateGasto() throws Exception {
        mockMvc.perform(put("/api/v1/gastos/10")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"monto\": 60000}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en DELETE /api/v1/gastos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotDeleteGasto() throws Exception {
        mockMvc.perform(delete("/api/v1/gastos/10"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. COMUNICACIONES OPERATIVAS - SUPERADMIN DENEGADO (403)
    // =========================================================================

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en POST /api/v1/buzon/aviso")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotPostAviso() throws Exception {
        mockMvc.perform(post("/api/v1/buzon/aviso")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"titulo\": \"Aviso Global No Autorizado\", \"mensaje\": \"Test\", \"idPropiedad\": 10}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en DELETE /api/v1/buzon/aviso/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotArchivarAviso() throws Exception {
        mockMvc.perform(delete("/api/v1/buzon/aviso/10"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en GET /api/v1/alertas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetAlertas() throws Exception {
        mockMvc.perform(get("/api/v1/alertas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en POST /api/v1/alertas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateAlerta() throws Exception {
        mockMvc.perform(post("/api/v1/alertas")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"tipoAlerta\": \"MORA\", \"numeroApartamento\": \"101\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: SUPERADMIN recibe 403 en PUT /api/v1/alertas/{id}/leer")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotMarcarAlertaLeida() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/10/leer"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. AUSENCIA DE REGRESIÓN: ADMIN_ORGANIZACION PERMITIDO
    // =========================================================================

    @Test
    @DisplayName("P3-01: ADMIN_ORGANIZACION conserva acceso a GET /api/v1/org/gastos")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrg_canGetOrgGastosConsolidados() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3-01: ADMIN_ORGANIZACION conserva acceso a GET /api/v1/gastos")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrg_canListGastos() throws Exception {
        mockMvc.perform(get("/api/v1/gastos"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3-01: ADMIN_ORGANIZACION conserva acceso a POST /api/v1/buzon/aviso")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrg_canPostAviso() throws Exception {
        mockMvc.perform(post("/api/v1/buzon/aviso")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"titulo\": \"Aviso Directiva\", \"mensaje\": \"Comunicado oficial\", \"idPropiedad\": 10, \"enviarEmail\": false}"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3-01: ADMIN_ORGANIZACION conserva acceso a GET /api/v1/alertas")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrg_canGetAlertas() throws Exception {
        mockMvc.perform(get("/api/v1/alertas"))
               .andExpect(status().isOk());
    }

    // =========================================================================
    // 5. AUSENCIA DE REGRESIÓN: ADMIN_PROPIEDAD, PORTERO Y RESIDENTE
    // =========================================================================

    @Test
    @DisplayName("P3-01: ADMIN_PROPIEDAD no accede a nivel organizacional /api/v1/org/gastos (403)")
    @WithMockUser(authorities = "SCOPE_ADMIN_PROPIEDAD")
    public void adminProp_cannotAccessOrgLevelGastos() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: ADMIN_PROPIEDAD conserva acceso a su propiedad GET /api/v1/gastos (200)")
    @WithMockUser(authorities = "SCOPE_ADMIN_PROPIEDAD")
    public void adminProp_canListPropiedadGastos() throws Exception {
        mockMvc.perform(get("/api/v1/gastos"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3-01: ADMIN_PROPIEDAD conserva acceso a publicar avisos POST /api/v1/buzon/aviso (200)")
    @WithMockUser(authorities = "SCOPE_ADMIN_PROPIEDAD")
    public void adminProp_canPostAviso() throws Exception {
        mockMvc.perform(post("/api/v1/buzon/aviso")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"titulo\": \"Aviso Administracion\", \"mensaje\": \"Mantenimiento ascensor\", \"idPropiedad\": 10, \"enviarEmail\": false}"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3-01: PORTERO no puede acceder a /api/v1/org/gastos (403)")
    @WithMockUser(authorities = "SCOPE_PORTERO")
    public void portero_cannotAccessOrgGastos() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P3-01: RESIDENTE no puede acceder a /api/v1/org/gastos (403)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE")
    public void residente_cannotAccessOrgGastos() throws Exception {
        mockMvc.perform(get("/api/v1/org/gastos"))
               .andExpect(status().isForbidden());
    }
}
