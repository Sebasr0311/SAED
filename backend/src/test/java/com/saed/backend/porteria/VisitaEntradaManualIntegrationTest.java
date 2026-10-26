package com.saed.backend.porteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.RegistroAccesoDTO;
import com.saed.backend.porteria.dto.RegistroAccesoRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class VisitaEntradaManualIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PorteriaService porteriaService;

    @MockBean
    private AssignmentService assignmentService;

    private Long testVisitaId;
    private Long testPropiedadId = 1L;
    private Long testPorteriaId = 1L;
    private Long testPersonaId = 1L;
    private Long testUnidadId = 1L;
    private final Long testPorteroUserId = 3L;
    private final Long testAssignmentId = 103L;

    @BeforeEach
    void setUp() {
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Mock para AssignmentFilter en endpoints HTTP protegidos
        AssignmentResponseDTO porteroAssignment = new AssignmentResponseDTO();
        porteroAssignment.setIdAsignacion(testAssignmentId);
        porteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignment.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(testPropiedadId);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Propiedad Test");
        porteroAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentId, testPorteroUserId))
                .thenReturn(Optional.of(porteroAssignment));

        // Deterministic fixture owned by this class. The porter acts on property 1 (mocked assignment 103), and the
        // VISITAS update runs under RLS, so the visit must belong to a unit of property 1; picking "any row"
        // (ROWNUM = 1) selected another tenant's property/unit on a fresh database and the check-in then
        // updated 0 rows.
        testPropiedadId = 1L;
        testPorteriaId = jdbcTemplate.queryForObject(
                "SELECT MIN(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = ?", Long.class, testPropiedadId);
        if (testPorteriaId == null) {
            jdbcTemplate.update("INSERT INTO PORTERIAS (ID_PROPIEDAD, NOMBRE, UBICACION, ESTADO) "
                    + "VALUES (?, 'Porteria Entrada Manual', 'Acceso principal', 'ACTIVO')", testPropiedadId);
            testPorteriaId = jdbcTemplate.queryForObject(
                    "SELECT MIN(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = ?", Long.class, testPropiedadId);
        }
        testUnidadId = jdbcTemplate.queryForObject(
                "SELECT MIN(ID_UNIDAD) FROM UNIDADES WHERE ID_PROPIEDAD = ?", Long.class, testPropiedadId);
        assertNotNull(testUnidadId, "Property 1 must have at least one unit");

        testPersonaId = 20102L;
        jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO) "
                + "SELECT 20102, 1, 'DOC-ENTRADA-MANUAL', 'NATURAL', 'Visitante', 'EntradaManual' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 20102)");
        jdbcTemplate.update("INSERT INTO VISITANTES (ID_PERSONA, ES_FRECUENTE, ESTADO, ID_PROPIEDAD) "
                + "SELECT 20102, 'N', 'ACTIVO', ? FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM VISITANTES WHERE ID_PERSONA = 20102)", testPropiedadId);
        Long testVisitanteId = jdbcTemplate.queryForObject(
                "SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 20102", Long.class);

        // Insertar visita de prueba con estado PROGRAMADA
        jdbcTemplate.update(
                "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, ESTADO) " +
                "VALUES (?, ?, 'PREAUTORIZADA', 'Prueba entrada manual GAP-F7-01', 1, 'PROGRAMADA')",
                testUnidadId, testVisitanteId
        );
        testVisitaId = jdbcTemplate.queryForObject("SELECT MAX(ID_VISITA) FROM VISITAS WHERE MOTIVO = 'Prueba entrada manual GAP-F7-01'", Long.class);
    }

    @AfterEach
    void tearDown() {
        if (testVisitaId != null) {
            try {
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", testVisitaId);
                jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ? AND MOTIVO = 'Prueba entrada manual GAP-F7-01'", testVisitaId);
            } catch (Exception ignored) {}
        }
        SaedContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("GAP-F7-01: Registro de entrada manual debe actualizar VISITAS.ESTADO a EN_CURSO y rechazar ACTIVA")
    void registrarEntradaManual_ActualizaVisitaAEstadoEnCurso_Exitoso() throws Exception {
        assertNotNull(testVisitaId, "Debe existir una visita de prueba para ejecutar el test");

        // Estado inicial de la visita en DB
        String estadoInicial = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?",
                String.class,
                testVisitaId
        );
        assertEquals("PROGRAMADA", estadoInicial, "El estado inicial de la visita debe ser PROGRAMADA");

        RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                testPropiedadId,
                testPorteriaId,
                null,
                testVisitaId,
                testPersonaId,
                testUnidadId,
                null,
                "ENTRADA",
                "MANUAL_PORTERO",
                testPorteroUserId,
                null,
                "Entrada registrada manualmente por portero"
        );

        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                        .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Restaurar contexto en SaedContextHolder para que SaedDataSourceProxy aplique SUPERADMIN
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        // Verificar en Oracle 23ai que el estado canónico resultante es EN_CURSO y nunca ACTIVA
        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?",
                String.class,
                testVisitaId
        );

        assertEquals("EN_CURSO", estadoFinal, "El estado de la visita después del check-in manual debe ser EN_CURSO");
        assertNotEquals("ACTIVA", estadoFinal, "El estado ACTIVA es inválido en Oracle CK_VISITAS_ESTADO y no debe persistirse");
    }

    @Test
    @DisplayName("GAP-F7-01: PorteriaService.registrarEntrada actualiza visita a EN_CURSO respetando Oracle 23ai")
    void registrarEntradaDirecta_ServicioActualizaVisitaAEstadoEnCurso() {
        assertNotNull(testVisitaId, "Debe existir una visita de prueba para ejecutar el test");

        RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                testPropiedadId,
                testPorteriaId,
                null,
                testVisitaId,
                testPersonaId,
                testUnidadId,
                null,
                "ENTRADA",
                "MANUAL_PORTERO",
                testPorteroUserId,
                null,
                "Prueba unitaria directa del servicio"
        );

        RegistroAccesoDTO resultado = porteriaService.registrarEntrada(request);
        assertNotNull(resultado);
        assertNotNull(resultado.idRegistroAcceso());

        String estadoActualizado = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?",
                String.class,
                testVisitaId
        );
        assertEquals("EN_CURSO", estadoActualizado);
    }

    @Test
    @DisplayName("GAP-F7-01: Demostrar que CK_VISITAS_ESTADO rechaza ACTIVA y acepta EN_CURSO")
    void verificarConstraintOracle_RechazaEstadoInvalidoActiva() {
        assertNotNull(testVisitaId, "Debe existir una visita de prueba");

        // 1. Demostrar que 'ACTIVA' es rechazado por el constraint de Oracle (ORA-02290)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("UPDATE VISITAS SET ESTADO = 'ACTIVA' WHERE ID_VISITA = ?", testVisitaId);
        }, "Oracle CK_VISITAS_ESTADO debe violarse con ORA-02290 si se intenta persistir 'ACTIVA'");

        // 2. Demostrar que 'EN_CURSO' es aceptado por el constraint de Oracle
        int updated = jdbcTemplate.update("UPDATE VISITAS SET ESTADO = 'EN_CURSO' WHERE ID_VISITA = ?", testVisitaId);
        assertEquals(1, updated);

        String estado = jdbcTemplate.queryForObject("SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?", String.class, testVisitaId);
        assertEquals("EN_CURSO", estado);
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("GAP-F7-01: entrada manual sobre una visita de otra propiedad -> 404, la visita no cambia y no se registra acceso")
    void registrarEntradaManual_VisitaDeOtraPropiedad_Rechazada() throws Exception {
        // A visit of property 20110 is invisible to the porter of property 1 (RLS), so the state UPDATE affects 0 rows.
        // Before the fix the access record was still created (201) and the visit silently stayed PROGRAMADA.
        jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) "
                + "SELECT 20110, 1, 1, 'Propiedad entrada manual ajena', 'Calle 1', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM PROPIEDADES WHERE ID_PROPIEDAD = 20110)");
        jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) "
                + "SELECT 20110, 20110, 1, 'Ajena-20110', 'ACTIVA' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM UNIDADES WHERE ID_UNIDAD = 20110)");
        Long visitanteId = jdbcTemplate.queryForObject(
                "SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 20102", Long.class);
        jdbcTemplate.update("INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, ESTADO) "
                + "VALUES (20110, ?, 'PREAUTORIZADA', 'Entrada manual propiedad ajena GAP-F7-01', 1, 'PROGRAMADA')", visitanteId);
        Long foreignVisit = jdbcTemplate.queryForObject(
                "SELECT MAX(ID_VISITA) FROM VISITAS WHERE MOTIVO = 'Entrada manual propiedad ajena GAP-F7-01'", Long.class);

        try {
            RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                    testPropiedadId, testPorteriaId, null, foreignVisit, testPersonaId, 20110L, null,
                    "ENTRADA", "MANUAL_PORTERO", testPorteroUserId, null, "Entrada sobre visita ajena");

            mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                            .header("X-Assignment-Id", String.valueOf(testAssignmentId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(testPropiedadId)
                    .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            assertEquals("PROGRAMADA", jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?", String.class, foreignVisit));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", Integer.class, foreignVisit),
                    "A rejected movement must not leave an access record behind");
        } finally {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(testPropiedadId)
                    .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", foreignVisit);
            jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ?", foreignVisit);
            jdbcTemplate.update("DELETE FROM UNIDADES WHERE ID_UNIDAD = 20110");
            jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = 20110");
        }
    }
}
