package com.saed.backend.security;

import com.saed.backend.asambleas.controller.AsambleasController;
import com.saed.backend.asambleas.service.AsambleaService;
import com.saed.backend.config.SecurityConfig;
import com.saed.backend.consumos.controller.ConsumosController;
import com.saed.backend.consumos.service.MedicionConsumoService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.controller.ReportesController;
import com.saed.backend.emergencias.controller.EmergenciasController;
import com.saed.backend.emergencias.service.EmergenciasService;
import com.saed.backend.obras.controller.ObraController;
import com.saed.backend.obras.service.ObraService;
import com.saed.backend.person.controller.PersonaController;
import com.saed.backend.person.service.PersonaService;
import com.saed.backend.sanciones.controller.SancionesController;
import com.saed.backend.sanciones.service.SancionService;
import com.saed.backend.security.jwt.JwtProvider;
import com.saed.backend.seguros.controller.PolizaSeguroController;
import com.saed.backend.seguros.service.PolizaSeguroService;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-04: Verificación exhaustiva de la eliminación de privilegios residuales SCOPE_SUPERADMIN
 * en controladores operativos de copropiedades y organizaciones cliente.
 *
 * Módulos auditados:
 * 1. PersonaController
 * 2. ReportesController
 * 3. PolizaSeguroController
 * 4. ConsumosController
 * 5. EmergenciasController
 * 6. ObraController
 * 7. SancionesController
 * 8. AsambleasController
 *
 * También valida H-02: PORTERO bloqueado de POST /api/v1/personas.
 */
@WebMvcTest({
    PersonaController.class,
    ReportesController.class,
    PolizaSeguroController.class,
    ConsumosController.class,
    EmergenciasController.class,
    ObraController.class,
    SancionesController.class,
    AsambleasController.class
})
@Import(SecurityConfig.class)
public class H04SuperAdminResidualOperationalRestrictionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean
    private PolizaSeguroService polizaSeguroService;

    @MockBean
    private MedicionConsumoService medicionConsumoService;

    @MockBean
    private EmergenciasService emergenciasService;

    @MockBean
    private ObraService obraService;

    @MockBean
    private com.saed.backend.trabajadores.service.TrabajadorService trabajadorService;

    @MockBean
    private SancionService sancionService;

    @MockBean
    private AsambleaService asambleaService;

    @MockBean
    private JwtProvider jwtTokenProvider;

    private static final String VALID_PERSONA_JSON = """
        {
            "tipoDocumentoId": 1,
            "numeroDocumento": "123456",
            "tipoPersona": "NATURAL",
            "primerNombre": "Juan",
            "primerApellido": "Perez"
        }
    """;

    private static final String VALID_CONSUMO_JSON = """
        {
            "tipoServicio": "AGUA",
            "numeroMedidor": "M-101",
            "periodo": "2026-09",
            "lecturaAnterior": 100,
            "lecturaActual": 150
        }
    """;

    private static final String VALID_PLAN_EMERGENCIA_JSON = """
        {
            "titulo": "Plan Incendio",
            "tipoContingencia": "INCENDIO",
            "puntosEncuentro": "Parque Central",
            "rutasEvacuacionDesc": "Escalera A"
        }
    """;

    private static final String VALID_CONTACTO_EMERGENCIA_JSON = """
        {
            "entidad": "Bomberos",
            "tipoServicio": "EMERGENCIA",
            "telefonoPrincipal": "119"
        }
    """;

    private static final String VALID_SANCION_JSON = """
        {
            "idUnidad": 1,
            "idPersonaImputada": 2,
            "tipoFalta": "RUIDO",
            "descripcionHechos": "Musica alta"
        }
    """;

    private static final String VALID_ASAMBLEA_JSON = """
        {
            "tipo": "ORDINARIA",
            "modalidad": "PRESENCIAL",
            "titulo": "Asamblea Anual",
            "fechaHoraPrimeraConv": "2026-10-01T08:00:00",
            "lugarOEnlace": "Salon Comunal",
            "ordenDelDia": "1. Quorum"
        }
    """;

    private static final String VALID_ESTADO_ASAMBLEA_JSON = """
        {
            "nuevoEstado": "EN_CURSO"
        }
    """;

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
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // 1. PERSONA CONTROLLER (/api/v1/personas) - H-04 & H-02
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/personas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPersonas() throws Exception {
        mockMvc.perform(get("/api/v1/personas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/personas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreatePersona() throws Exception {
        mockMvc.perform(post("/api/v1/personas")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_PERSONA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-02: PORTERO recibe 403 en POST /api/v1/personas")
    @WithMockUser(authorities = "SCOPE_PORTERO")
    public void portero_cannotCreatePersona() throws Exception {
        mockMvc.perform(post("/api/v1/personas")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_PERSONA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/personas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPersonaById() throws Exception {
        mockMvc.perform(get("/api/v1/personas/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en PUT /api/v1/personas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotUpdatePersona() throws Exception {
        mockMvc.perform(put("/api/v1/personas/1")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_PERSONA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/personas/importar")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotImportarPersonas() throws Exception {
        mockMvc.perform(post("/api/v1/personas/importar")
               .contentType(MediaType.APPLICATION_JSON)
               .content("[]"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en DELETE /api/v1/personas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotDeletePersona() throws Exception {
        mockMvc.perform(delete("/api/v1/personas/1"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. REPORTES CONTROLLER (/api/v1/reportes) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/reportes/cartera-morosa")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetReporteCarteraMorosa() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/reportes/ejecucion-cuotas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetReporteEjecucionCuotas() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-cuotas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/reportes/pagos-recientes")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetReportePagosRecientes() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/pagos-recientes"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. POLIZA SEGURO CONTROLLER (/api/v1/seguros/polizas) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/seguros/polizas/resumen")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPolizasResumen() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas/resumen"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/seguros/polizas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetAllPolizas() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/seguros/polizas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPolizaById() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/seguros/polizas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreatePoliza() throws Exception {
        mockMvc.perform(post("/api/v1/seguros/polizas")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"numeroPoliza\":\"POL-01\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en PUT /api/v1/seguros/polizas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotUpdatePoliza() throws Exception {
        mockMvc.perform(put("/api/v1/seguros/polizas/1")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"numeroPoliza\":\"POL-01-REV\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en DELETE /api/v1/seguros/polizas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotDeletePoliza() throws Exception {
        mockMvc.perform(delete("/api/v1/seguros/polizas/1"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. CONSUMOS CONTROLLER (/api/v1/consumos) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/consumos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetAllConsumos() throws Exception {
        mockMvc.perform(get("/api/v1/consumos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/consumos/resumen")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetConsumosResumen() throws Exception {
        mockMvc.perform(get("/api/v1/consumos/resumen"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/consumos/tendencias")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetConsumosTendencias() throws Exception {
        mockMvc.perform(get("/api/v1/consumos/tendencias"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/consumos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetMedicionConsumoById() throws Exception {
        mockMvc.perform(get("/api/v1/consumos/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/consumos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateMedicionConsumo() throws Exception {
        mockMvc.perform(post("/api/v1/consumos")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_CONSUMO_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en PUT /api/v1/consumos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotUpdateMedicionConsumo() throws Exception {
        mockMvc.perform(put("/api/v1/consumos/1")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_CONSUMO_JSON))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. EMERGENCIAS CONTROLLER (/api/v1/emergencias) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/emergencias/resumen")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetEmergenciasResumen() throws Exception {
        mockMvc.perform(get("/api/v1/emergencias/resumen"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/emergencias/planes")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPlanesEmergencia() throws Exception {
        mockMvc.perform(get("/api/v1/emergencias/planes"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/emergencias/planes/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetPlanEmergenciaById() throws Exception {
        mockMvc.perform(get("/api/v1/emergencias/planes/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/emergencias/planes")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreatePlanEmergencia() throws Exception {
        mockMvc.perform(post("/api/v1/emergencias/planes")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_PLAN_EMERGENCIA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/emergencias/contactos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetContactosEmergencia() throws Exception {
        mockMvc.perform(get("/api/v1/emergencias/contactos"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/emergencias/contactos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetContactoEmergenciaById() throws Exception {
        mockMvc.perform(get("/api/v1/emergencias/contactos/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/emergencias/contactos")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateContactoEmergencia() throws Exception {
        mockMvc.perform(post("/api/v1/emergencias/contactos")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_CONTACTO_EMERGENCIA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en DELETE /api/v1/emergencias/contactos/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotDeleteContactoEmergencia() throws Exception {
        mockMvc.perform(delete("/api/v1/emergencias/contactos/1"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. OBRA CONTROLLER (/api/v1/obras) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/obras/admin")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetObrasAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/obras/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetObraById() throws Exception {
        mockMvc.perform(get("/api/v1/obras/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/obras")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCreateObra() throws Exception {
        mockMvc.perform(post("/api/v1/obras")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"titulo\":\"Remodelacion\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/obras/{id}/aprobar")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotAprobarObra() throws Exception {
        mockMvc.perform(post("/api/v1/obras/1/aprobar"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/obras/{id}/rechazar")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotRechazarObra() throws Exception {
        mockMvc.perform(post("/api/v1/obras/1/rechazar"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/obras/{id}/finalizar")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotFinalizarObra() throws Exception {
        mockMvc.perform(post("/api/v1/obras/1/finalizar"))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. SANCIONES CONTROLLER (/api/v1/sanciones) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/sanciones/todas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotGetAllSanciones() throws Exception {
        mockMvc.perform(get("/api/v1/sanciones/todas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/sanciones")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotCrearPliegoSancion() throws Exception {
        mockMvc.perform(post("/api/v1/sanciones")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_SANCION_JSON))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. ASAMBLEAS CONTROLLER (/api/v1/asambleas) - H-04
    // =========================================================================

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/asambleas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotListarAsambleas() throws Exception {
        mockMvc.perform(get("/api/v1/asambleas"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en GET /api/v1/asambleas/{id}")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotObtenerDetalleAsamblea() throws Exception {
        mockMvc.perform(get("/api/v1/asambleas/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en POST /api/v1/asambleas")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotConvocarAsamblea() throws Exception {
        mockMvc.perform(post("/api/v1/asambleas")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_ASAMBLEA_JSON))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H-04: SUPERADMIN recibe 403 en PUT /api/v1/asambleas/{id}/estado")
    @WithMockUser(authorities = "SCOPE_SUPERADMIN")
    public void superAdmin_cannotActualizarEstadoAsamblea() throws Exception {
        mockMvc.perform(put("/api/v1/asambleas/1/estado")
               .contentType(MediaType.APPLICATION_JSON)
               .content(VALID_ESTADO_ASAMBLEA_JSON))
               .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 9. COMPROBACIÓN POSITIVA: ADMIN_ORGANIZACION CONSERVA ACCESO
    // =========================================================================

    @Test
    @DisplayName("H-04 Positivo: ADMIN_ORGANIZACION tiene acceso a GET /api/v1/personas")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrganizacion_canGetPersonas() throws Exception {
        mockMvc.perform(get("/api/v1/personas"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H-04 Positivo: ADMIN_ORGANIZACION tiene acceso a GET /api/v1/reportes/cartera-morosa")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrganizacion_canGetReportes() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H-04 Positivo: ADMIN_PROPIEDAD tiene acceso a GET /api/v1/seguros/polizas")
    @WithMockUser(authorities = "SCOPE_ADMIN_PROPIEDAD")
    public void adminPropiedad_canGetPolizas() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H-04 Positivo: ADMIN_ORGANIZACION tiene acceso a GET /api/v1/asambleas")
    @WithMockUser(authorities = "SCOPE_ADMIN_ORGANIZACION")
    public void adminOrganizacion_canGetAsambleas() throws Exception {
        mockMvc.perform(get("/api/v1/asambleas"))
               .andExpect(status().isOk());
    }
}
