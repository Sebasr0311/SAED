package com.saed.backend.domicilios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.service.DomicilioService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class DomicilioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DomicilioService domicilioService;

    @MockBean
    private AssignmentService assignmentService;

    private Long testPropiedadId = 1L;
    private Long testOtherPropiedadId = 2L;
    private Long testPorteriaId = 1L;
    private Long testUnidadId = 1L;
    private Long testOtherUnidadId = null;
    private final Long testPorteroUserId = 3L;
    private final Long testAssignmentId = 103L;
    private final Long testAssignmentOtherPropId = 203L;

    private final List<Long> createdDomicilios = new ArrayList<>();
    private final List<Long> createdVisitas = new ArrayList<>();

    private void setElevatedContext() {
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
    }

    @BeforeEach
    void setUp() {
        setElevatedContext();

        // 1. Resolver infraestructura real en Oracle
        try {
            List<Map<String, Object>> props = jdbcTemplate.queryForList("SELECT ID_PROPIEDAD FROM PROPIEDADES ORDER BY ID_PROPIEDAD ASC");
            if (!props.isEmpty()) {
                testPropiedadId = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
                if (props.size() > 1) {
                    testOtherPropiedadId = ((Number) props.get(1).get("ID_PROPIEDAD")).longValue();
                } else {
                    testOtherPropiedadId = 999998L;
                }
            }

            List<Map<String, Object>> ports = jdbcTemplate.queryForList(
                    "SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId);
            if (!ports.isEmpty()) {
                testPorteriaId = ((Number) ports.get(0).get("ID_PORTERIA")).longValue();
            }

            List<Map<String, Object>> units = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId);
            if (!units.isEmpty()) {
                testUnidadId = ((Number) units.get(0).get("ID_UNIDAD")).longValue();
            }

            // Buscar o crear unidad en otra propiedad para prueba Anti-IDOR
            List<Map<String, Object>> otherUnits = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD != ? AND ROWNUM = 1", testPropiedadId);
            if (!otherUnits.isEmpty()) {
                testOtherUnidadId = ((Number) otherUnits.get(0).get("ID_UNIDAD")).longValue();
            } else {
                // Insertar una unidad en otra propiedad temporalmente si no existe
                try {
                    testOtherUnidadId = 999991L;
                    jdbcTemplate.update("""
                        INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ESTADO)
                        VALUES (?, ?, 'APT-OTHER-99', 'ACTIVA')
                    """, testOtherUnidadId, testOtherPropiedadId);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Aviso en setUp: " + e.getMessage());
        }

        // 2. Mocks de asignación para portero en propiedad activa
        AssignmentResponseDTO porteroAssignment = new AssignmentResponseDTO();
        porteroAssignment.setIdAsignacion(testAssignmentId);
        porteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignment.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(testPropiedadId);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Propiedad Principal");
        porteroAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentId, testPorteroUserId))
                .thenReturn(Optional.of(porteroAssignment));

        // Mock de asignación para portero de OTRA propiedad
        AssignmentResponseDTO otherPorteroAssignment = new AssignmentResponseDTO();
        otherPorteroAssignment.setIdAsignacion(testAssignmentOtherPropId);
        otherPorteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        otherPorteroAssignment.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO otherPropDTO = new PropertyDTO();
        otherPropDTO.setId(testOtherPropiedadId);
        otherPropDTO.setIdOrganizacion(1L);
        otherPropDTO.setNombre("Propiedad Ajena");
        otherPorteroAssignment.setPropiedad(otherPropDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentOtherPropId, testPorteroUserId))
                .thenReturn(Optional.of(otherPorteroAssignment));
    }

    @AfterEach
    void tearDown() {
        setElevatedContext();
        for (Long idDom : createdDomicilios) {
            try {
                jdbcTemplate.update("DELETE FROM DOMICILIOS WHERE ID_DOMICILIO = ?", idDom);
            } catch (Exception ignored) {}
        }
        for (Long idVisita : createdVisitas) {
            try {
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", idVisita);
                jdbcTemplate.update("DELETE FROM QR_ACCESOS WHERE ID_VISITA = ?", idVisita);
                jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ?", idVisita);
            } catch (Exception ignored) {}
        }
        if (testOtherUnidadId != null && testOtherUnidadId == 999991L) {
            try {
                jdbcTemplate.update("DELETE FROM UNIDADES WHERE ID_UNIDAD = 999991");
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Test 1: Registro de domicilio exitoso en portería (201 Created)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void registrarDomicilio_Exitoso() throws Exception {
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testUnidadId,
                "Rappi",
                "Carlos Gomez",
                "1020304050",
                "3101234567",
                "COMIDA",
                "RAPPI-9988",
                "MOTO",
                "ABC-12D",
                "Pedido sin contacto en puerta",
                testPorteriaId,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDomicilio").exists())
                .andExpect(jsonPath("$.empresa").value("Rappi"))
                .andExpect(jsonPath("$.nombreDomiciliario").value("Carlos Gomez"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.fechaEntrada").exists())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long id = ((Number) body.get("idDomicilio")).longValue();
        createdDomicilios.add(id);
    }

    @Test
    @DisplayName("Test 2: Registro de domicilio para unidad inexistente (404 Not Found)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void registrarDomicilio_UnidadNoExiste() throws Exception {
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                999999L,
                "Servientrega",
                "Pedro Perez",
                null,
                null,
                "MENSAJERIA",
                null,
                "MOTO",
                null,
                null,
                testPorteriaId,
                null
        );

        mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test 3: Anti-IDOR — Registro de domicilio para unidad de otra propiedad (403 Forbidden)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void registrarDomicilio_UnidadOtraPropiedad() throws Exception {
        if (testOtherUnidadId == null) return;

        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testOtherUnidadId,
                "Farmatodo",
                "Juan Domicilios",
                null,
                null,
                "MEDICAMENTOS",
                null,
                "BICICLETA",
                null,
                null,
                testPorteriaId,
                null
        );

        mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 4: Finalización exitosa de domicilio (200 OK)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void finalizarDomicilio_Exitoso() throws Exception {
        // 1. Crear domicilio previo
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testUnidadId,
                "Didi Food",
                "Andres Castro",
                "11223344",
                "3009876543",
                "COMIDA",
                null,
                "MOTO",
                "XYZ-89C",
                "Entrega recibida por residente",
                testPorteriaId,
                null
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class);
        Long id = ((Number) body.get("idDomicilio")).longValue();
        createdDomicilios.add(id);

        // 2. Finalizar domicilio
        mockMvc.perform(patch("/api/v1/domicilios/{id}/finalizar", id)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDomicilio").value(id))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.fechaSalida").exists())
                .andExpect(jsonPath("$.finalizadoPor").exists());
    }

    @Test
    @DisplayName("Test 5: Conflicto por doble finalización de domicilio (409 Conflict)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void finalizarDomicilio_ConflictoDobleFinalizacion() throws Exception {
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testUnidadId,
                "MercadoLibre",
                "Santiago Ortiz",
                null,
                null,
                "PAQUETE_EXPRESS",
                "ML-554433",
                "CARRO",
                "AAA-111",
                null,
                testPorteriaId,
                null
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("idDomicilio")).longValue();
        createdDomicilios.add(id);

        // Primera finalización: OK
        mockMvc.perform(patch("/api/v1/domicilios/{id}/finalizar", id)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk());

        // Segunda finalización: 409 Conflict
        mockMvc.perform(patch("/api/v1/domicilios/{id}/finalizar", id)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Test 6: Cancelación exitosa y conflicto por doble cancelación (409 Conflict)")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void cancelarDomicilio_Exitoso_YDobleCancelacion() throws Exception {
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testUnidadId,
                "Éxito",
                "Martin Diaz",
                null,
                null,
                "SUPERMERCADO",
                null,
                "MOTO",
                null,
                "Cancelado por no encontrar destinatario",
                testPorteriaId,
                null
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("idDomicilio")).longValue();
        createdDomicilios.add(id);

        // Cancelar: 200 OK
        mockMvc.perform(patch("/api/v1/domicilios/{id}/cancelar", id)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        // Doble cancelar: 409 Conflict
        mockMvc.perform(patch("/api/v1/domicilios/{id}/cancelar", id)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Test 7: Verificación directa de persistencia y bitácora en Oracle 23ai")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void persistenciaOracle_VerificacionDirecta() throws Exception {
        DomicilioCreateDTO dto = new DomicilioCreateDTO(
                testUnidadId,
                "UberEats",
                "Mateo Silva",
                "99887766",
                "3115554433",
                "COMIDA",
                "UBER-7711",
                "MOTO",
                "KTM-500",
                "Verificación directa en base de datos",
                testPorteriaId,
                null
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("idDomicilio")).longValue();
        createdDomicilios.add(id);

        setElevatedContext();

        // 1. Verificar tabla DOMICILIOS
        Integer domCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM DOMICILIOS WHERE ID_DOMICILIO = ? AND EMPRESA = 'UberEats' AND ESTADO = 'EN_CURSO'",
                Integer.class, id
        );
        assertEquals(1, domCount, "El domicilio debe estar persistido en Oracle en estado EN_CURSO");

        // 2. Verificar REGISTROS_ACCESO
        Integer regCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM REGISTROS_ACCESO WHERE ID_UNIDAD = ? AND METODO_AUTORIZACION = 'MANUAL_PORTERO' AND TIPO_MOVIMIENTO = 'ENTRADA'",
                Integer.class, testUnidadId
        );
        assertTrue(regCount != null && regCount >= 1, "Debe existir al menos un registro de entrada de domicilio en REGISTROS_ACCESO");
    }

    @Test
    @DisplayName("Test 8: GAP-F7-03-D — /api/v1/visitas/rapida realiza persistencia real en Oracle")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void visitaRapida_PersistenciaRealOracle() throws Exception {
        setElevatedContext();

        Map<String, Object> payload = Map.of(
                "idUnidad", testUnidadId,
                "idPorteria", testPorteriaId,
                "nombreVisitante", "Visitante Rapido F7",
                "documento", "9876543210",
                "tipoDocumento", "CC",
                "tipoVisita", "PEATONAL",
                "motivo", "Entrega urgente de llaves"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/visitas/rapida")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVisita").exists())
                .andExpect(jsonPath("$.codigoQr").exists())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = ((Number) body.get("idVisita")).longValue();
        createdVisitas.add(idVisita);

        setElevatedContext();

        // Verificar que VISITAS existe realmente en Oracle
        Integer visitaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM VISITAS WHERE ID_VISITA = ?",
                Integer.class, idVisita
        );
        assertEquals(1, visitaCount, "La visita rápida debe estar persistida en la tabla VISITAS de Oracle");

        // Verificar que QR_ACCESOS existe realmente en Oracle
        Integer qrCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM QR_ACCESOS WHERE ID_VISITA = ?",
                Integer.class, idVisita
        );
        assertEquals(1, qrCount, "El código QR generado debe estar persistido en QR_ACCESOS");
    }

    @Test
    @DisplayName("Test 9: Listado de domicilios con filtrado por estado")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void listarDomicilios_FiltroPorEstado() throws Exception {
        // Crear 1 domicilio en curso
        DomicilioCreateDTO dto1 = new DomicilioCreateDTO(
                testUnidadId, "Empresa A", "Dom A", null, null, "COMIDA", null, "MOTO", null, null, testPorteriaId, null
        );
        MvcResult res1 = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated())
                .andReturn();
        Long id1 = ((Number) objectMapper.readValue(res1.getResponse().getContentAsString(), Map.class).get("idDomicilio")).longValue();
        createdDomicilios.add(id1);

        // Crear 1 domicilio y finalizarlo
        DomicilioCreateDTO dto2 = new DomicilioCreateDTO(
                testUnidadId, "Empresa B", "Dom B", null, null, "COMIDA", null, "MOTO", null, null, testPorteriaId, null
        );
        MvcResult res2 = mockMvc.perform(post("/api/v1/domicilios")
                        .header("X-Assignment-Id", testAssignmentId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isCreated())
                .andReturn();
        Long id2 = ((Number) objectMapper.readValue(res2.getResponse().getContentAsString(), Map.class).get("idDomicilio")).longValue();
        createdDomicilios.add(id2);

        mockMvc.perform(patch("/api/v1/domicilios/{id}/finalizar", id2)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk());

        // Listar EN_CURSO
        mockMvc.perform(get("/api/v1/domicilios?estado=EN_CURSO")
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Listar FINALIZADO
        mockMvc.perform(get("/api/v1/domicilios?estado=FINALIZADO")
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Test 10: Consulta de detalle por ID — 404 para ID inexistente")
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    void buscarPorId_Inexistente_Retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/domicilios/{id}", 999999L)
                        .header("X-Assignment-Id", testAssignmentId.toString()))
                .andExpect(status().isNotFound());
    }
}
