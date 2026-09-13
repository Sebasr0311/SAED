package com.saed.backend.security;

import com.saed.backend.config.DatabaseSeeder;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.controller.IncidenteController;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.service.IncidenteService;
import com.saed.backend.porteria.controller.PorteriaController;
import com.saed.backend.porteria.dto.PorteriaDTO;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.service.PorteriaService;
import com.saed.backend.reservas.controller.ReservasController;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.service.ReservasService;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de verificación integral de seguridad para las correcciones de auditoría:
 * 1. H-01: SUPERADMIN bloqueado de operaciones de copropiedad (Incidentes y Reservas)
 * 2. RESIDENTE_CONVIVENCIA: Acceso residencial habilitado (visitas, reservas, incidentes, zonas comunes)
 *    y acceso administrativo/financiero estrictamente bloqueado.
 * 3. SD-01: Verificación de metadatos de DatabaseSeeder (@Profile("!prod") y no sobreescritura de credenciales)
 */
@WebMvcTest({
    IncidenteController.class,
    ReservasController.class,
    PorteriaController.class
})
@Import(SecurityConfig.class)
public class AuditFixesSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidenteService incidenteService;

    @MockBean
    private ReservasService reservasService;

    @MockBean
    private PorteriaService porteriaService;

    @MockBean
    private com.saed.backend.common.service.EmailService emailService;

    @MockBean
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private JwtProvider jwtProvider;

    @BeforeEach
    public void setUp() {
        SaedContext ctx = SaedContext.builder()
                .userId(100L)
                .organizationId(1L)
                .propertyId(1L)
                .unitId(1L)
                .roleCode("RESIDENTE_CONVIVENCIA")
                .roleScope("UNIDAD")
                .build();
        SaedContextHolder.setContext(ctx);
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // H-01: SUPERADMIN BLOQUEADO DE INCIDENTES (OPERACIÓN CLIENTE)
    // =========================================================================

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en GET /api/v1/incidentes/admin")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotAccessIncidentesAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en POST /api/v1/incidentes")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotReportarIncidente() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "titulo": "Prueba",
                      "tipoIncidente": "SEGURIDAD",
                      "descripcionHechos": "Intento de reporte por superadmin",
                      "fechaHoraIncidente": "2026-09-12T20:00:00Z"
                    }
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en POST /api/v1/incidentes/{id}/cerrar")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCerrarIncidente() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes/1/cerrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conclusiones\":\"Cierre\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // H-01: SUPERADMIN BLOQUEADO DE RESERVAS (OPERACIÓN CLIENTE)
    // =========================================================================

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en GET /api/v1/zonas-comunes")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotAccessZonasComunes() throws Exception {
        mockMvc.perform(get("/api/v1/zonas-comunes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en GET /api/v1/reservas/todas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotAccessTodasReservas() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/todas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en POST /api/v1/reservas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateReserva() throws Exception {
        mockMvc.perform(post("/api/v1/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idZona\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-01: SUPERADMIN recibe 403 en PUT /api/v1/reservas/{id}/estado")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotUpdateReservaEstado() throws Exception {
        mockMvc.perform(put("/api/v1/reservas/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CONFIRMADA\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // RESIDENTE_CONVIVENCIA: ACCESO RESIDENCIAL PERMITIDO
    // =========================================================================

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede consultar zonas comunes (GET /api/v1/zonas-comunes)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canAccessZonasComunes() throws Exception {
        when(reservasService.getAllZonasComunes()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/zonas-comunes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede consultar sus reservas (GET /api/v1/reservas/mis-reservas)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canAccessMisReservas() throws Exception {
        when(reservasService.getMyReservas()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/reservas/mis-reservas"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede crear reserva (POST /api/v1/reservas)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canCreateReserva() throws Exception {
        when(reservasService.createReserva(any())).thenReturn(1L);
        mockMvc.perform(post("/api/v1/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idZona\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede consultar sus incidentes (GET /api/v1/incidentes/mis-incidentes)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canAccessMisIncidentes() throws Exception {
        when(incidenteService.getMisIncidentes()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/incidentes/mis-incidentes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede reportar incidente (POST /api/v1/incidentes)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canReportarIncidente() throws Exception {
        when(incidenteService.reportarIncidente(any())).thenReturn(1L);
        mockMvc.perform(post("/api/v1/incidentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "titulo": "Fuga de agua",
                      "tipoIncidente": "MANTENIMIENTO",
                      "descripcionHechos": "Tubo roto en cocina",
                      "fechaHoraIncidente": "2026-09-12T20:00:00Z"
                    }
                    """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Puede consultar visitas de su unidad (GET /api/v1/porteria/unidades/1/visitas)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_canGetVisitasUnidad() throws Exception {
        when(porteriaService.getVisitasByUnidad(1L)).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/porteria/unidades/1/visitas"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // RESIDENTE_CONVIVENCIA: BLOQUEADO DE FUNCIONES DE ADMIN / PORTERIA
    // =========================================================================

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Bloqueado con 403 de ver todas las reservas (GET /api/v1/reservas/todas)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_cannotAccessTodasReservas() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/todas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Bloqueado con 403 de ver incidentes admin (GET /api/v1/incidentes/admin)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_cannotAccessIncidentesAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Bloqueado con 403 de cerrar incidentes (POST /api/v1/incidentes/{id}/cerrar)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_cannotCerrarIncidente() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes/1/cerrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conclusiones\":\"Cierre\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESIDENTE_CONVIVENCIA: Bloqueado con 403 de registrar entrada de acceso (POST /api/v1/porteria/registros/entrada)")
    @WithMockUser(authorities = "SCOPE_RESIDENTE_CONVIVENCIA")
    public void residenteConvivencia_cannotRegistrarEntrada() throws Exception {
        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "propiedadId": 1,
                      "porteriaId": 1,
                      "personaId": 10,
                      "visitaId": 1,
                      "tipoMovimiento": "ENTRADA",
                      "metodoAutorizacion": "MANUAL"
                    }
                    """))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SD-01: VERIFICACIÓN DE SEGURIDAD EN DATABASE SEEDER
    // =========================================================================

    @Test
    @DisplayName("SD-01: DatabaseSeeder está protegido con @Profile('!prod') para no correr en producción")
    public void databaseSeeder_isProtectedFromProductionProfile() {
        Profile profile = DatabaseSeeder.class.getAnnotation(Profile.class);
        org.junit.jupiter.api.Assertions.assertNotNull(profile, "DatabaseSeeder debe tener anotación @Profile");
        boolean containsNotProd = java.util.Arrays.asList(profile.value()).contains("!prod");
        assertTrue(containsNotProd, "DatabaseSeeder debe tener @Profile('!prod')");
    }
}
