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

import java.util.List;
import java.util.Map;
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

        // Localizar o preparar datos mínimos para FKs
        try {
            List<Map<String, Object>> props = jdbcTemplate.queryForList("SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ROWNUM = 1");
            if (!props.isEmpty()) {
                testPropiedadId = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
            }
            List<Map<String, Object>> ports = jdbcTemplate.queryForList("SELECT ID_PORTERIA FROM PORTERIAS WHERE ROWNUM = 1");
            if (!ports.isEmpty()) {
                testPorteriaId = ((Number) ports.get(0).get("ID_PORTERIA")).longValue();
            }
            List<Map<String, Object>> pers = jdbcTemplate.queryForList("SELECT ID_PERSONA FROM PERSONAS WHERE ROWNUM = 1");
            if (!pers.isEmpty()) {
                testPersonaId = ((Number) pers.get(0).get("ID_PERSONA")).longValue();
            }
            List<Map<String, Object>> units = jdbcTemplate.queryForList("SELECT ID_UNIDAD FROM UNIDADES WHERE ROWNUM = 1");
            if (!units.isEmpty()) {
                testUnidadId = ((Number) units.get(0).get("ID_UNIDAD")).longValue();
            }
        } catch (Exception ignored) {}

        // Asegurar existencia de visitante
        Long testVisitanteId = 1L;
        try {
            List<Map<String, Object>> vis = jdbcTemplate.queryForList("SELECT ID_VISITANTE FROM VISITANTES WHERE ROWNUM = 1");
            if (!vis.isEmpty()) {
                testVisitanteId = ((Number) vis.get(0).get("ID_VISITANTE")).longValue();
            }
        } catch (Exception ignored) {}

        // Insertar visita de prueba con estado PROGRAMADA
        try {
            jdbcTemplate.update(
                    "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, ESTADO) " +
                    "VALUES (?, ?, 'PEATONAL', 'Prueba entrada manual GAP-F7-01', 1, 'PROGRAMADA')",
                    testUnidadId, testVisitanteId
            );
            testVisitaId = jdbcTemplate.queryForObject("SELECT MAX(ID_VISITA) FROM VISITAS WHERE MOTIVO = 'Prueba entrada manual GAP-F7-01'", Long.class);
        } catch (Exception e) {
            List<Map<String, Object>> existingVis = jdbcTemplate.queryForList("SELECT ID_VISITA FROM VISITAS WHERE ROWNUM = 1");
            if (!existingVis.isEmpty()) {
                testVisitaId = ((Number) existingVis.get(0).get("ID_VISITA")).longValue();
                jdbcTemplate.update("UPDATE VISITAS SET ESTADO = 'PROGRAMADA' WHERE ID_VISITA = ?", testVisitaId);
            }
        }
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
}
