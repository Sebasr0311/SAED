package com.saed.backend.porteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.*;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase1FPorteriaIntegrationTest — Suite Certificada de Integración y Seguridad para Portería.
 * GAP-F7-05: Endurecimiento riguroso con assertions deterministas, validación de contratos HTTP,
 * verificación de payload y estado en Oracle DB, aislamiento multi-tenant y eliminación de 
 * patrones permisivos (201 || 500 || 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1FPorteriaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AssignmentService assignmentService;

    // Fixture identifiers
    private final Long testOrgId = 1L;
    private final Long testPropiedadId = 1L;
    private Long testPorteriaId = 1L;
    private Long testUnidadId = 1L;
    private Long testPersonaVisitanteId = 2L;
    private Long testVisitanteId = 1L;
    private Long testVisitaId;

    // User & Assignment mocks
    private final Long adminPropiedadUserId = 2L;
    private final Long adminPropiedadAssignmentId = 102L;

    private final Long porteroUserId = 3L;
    private final Long porteroAssignmentId = 103L;

    private final Long residenteUserId = 4L;
    private final Long residenteAssignmentId = 104L;

    private final List<Long> visitasToClean = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. Establecer contexto SUPERADMIN para inicialización de fixtures en base de datos
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(testOrgId)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // 2. Configurar Mocks de Asignaciones para JwtAuthenticationFilter
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(testPropiedadId);
        propDTO.setIdOrganizacion(testOrgId);
        propDTO.setNombre("Edificio Residencial SAED");

        // Assignment Admin Propiedad (User 2, Assignment 102)
        AssignmentResponseDTO adminAssignment = new AssignmentResponseDTO();
        adminAssignment.setIdAsignacion(adminPropiedadAssignmentId);
        adminAssignment.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminAssignment.setOrganizacion(new OrganizationDTO(testOrgId, "SAED Global S.A.S."));
        adminAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(adminPropiedadAssignmentId, adminPropiedadUserId))
                .thenReturn(Optional.of(adminAssignment));

        // Assignment Portero (User 3, Assignment 103)
        AssignmentResponseDTO porteroAssignment = new AssignmentResponseDTO();
        porteroAssignment.setIdAsignacion(porteroAssignmentId);
        porteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignment.setOrganizacion(new OrganizationDTO(testOrgId, "SAED Global S.A.S."));
        porteroAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(porteroAssignmentId, porteroUserId))
                .thenReturn(Optional.of(porteroAssignment));

        // Assignment Residente (User 4, Assignment 104)
        AssignmentResponseDTO resAssignment = new AssignmentResponseDTO();
        resAssignment.setIdAsignacion(residenteAssignmentId);
        resAssignment.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resAssignment.setOrganizacion(new OrganizationDTO(testOrgId, "SAED Global S.A.S."));
        resAssignment.setPropiedad(propDTO);
        UnitDTO unitDTO = new UnitDTO(testUnidadId, "Apto 201");
        unitDTO.setIdPropiedad(testPropiedadId);
        resAssignment.setUnidad(unitDTO);
        Mockito.when(assignmentService.validateAssignment(residenteAssignmentId, residenteUserId))
                .thenReturn(Optional.of(resAssignment));

        // 3. Garantizar existencia de datos base (Porteria, Unidad, Persona, Visitante)
        try {
            List<Map<String, Object>> ports = jdbcTemplate.queryForList(
                    "SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId
            );
            if (ports.isEmpty()) {
                jdbcTemplate.update("INSERT INTO PORTERIAS (ID_PORTERIA, ID_PROPIEDAD, NOMBRE, UBICACION, ESTADO) VALUES (1, 1, 'Portería Principal', 'Entrada Principal', 'ACTIVO')");
                testPorteriaId = 1L;
            } else {
                testPorteriaId = ((Number) ports.get(0).get("ID_PORTERIA")).longValue();
            }

            List<Map<String, Object>> units = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND ROWNUM = 1", testPropiedadId
            );
            if (!units.isEmpty()) {
                testUnidadId = ((Number) units.get(0).get("ID_UNIDAD")).longValue();
            }

            // Asegurar persona y visitante válidos
            try {
                jdbcTemplate.update(
                        "MERGE INTO PERSONAS p USING (SELECT 20001 AS id, 1 AS td, 'DOC20001' AS nd, 'NATURAL' AS tp, 'Visitante' AS pn, 'Prueba' AS pa FROM DUAL) s " +
                        "ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO) " +
                        "VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa)"
                );
                jdbcTemplate.update(
                        "MERGE INTO VISITANTES vis USING (SELECT 20001 AS p FROM DUAL) s ON (vis.ID_PERSONA = s.p) " +
                        "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ES_FRECUENTE, ESTADO) VALUES (s.p, 'N', 'ACTIVO')"
                );
                testPersonaVisitanteId = 20001L;
                testVisitanteId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 20001", Long.class);
            } catch (Exception ignored) {
                List<Map<String, Object>> vis = jdbcTemplate.queryForList("SELECT ID_VISITANTE, ID_PERSONA FROM VISITANTES WHERE ROWNUM = 1");
                if (!vis.isEmpty()) {
                    testVisitanteId = ((Number) vis.get(0).get("ID_VISITANTE")).longValue();
                    testPersonaVisitanteId = ((Number) vis.get(0).get("ID_PERSONA")).longValue();
                }
            }

            // 4. Crear visita de prueba con estado PROGRAMADA
            jdbcTemplate.update(
                    "DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA IN (SELECT ID_VISITA FROM VISITAS WHERE MOTIVO = 'Visita fixture Phase1F')"
            );
            jdbcTemplate.update(
                    "DELETE FROM VEHICULOS_VISITA WHERE ID_VISITA IN (SELECT ID_VISITA FROM VISITAS WHERE MOTIVO = 'Visita fixture Phase1F')"
            );
            jdbcTemplate.update(
                    "DELETE FROM QR_ACCESOS WHERE ID_VISITA IN (SELECT ID_VISITA FROM VISITAS WHERE MOTIVO = 'Visita fixture Phase1F')"
            );
            jdbcTemplate.update(
                    "DELETE FROM VISITAS WHERE MOTIVO = 'Visita fixture Phase1F'"
            );

            jdbcTemplate.update(
                    "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, FECHA_PROGRAMADA, ESTADO) " +
                    "VALUES (?, ?, 'PREAUTORIZADA', 'Visita fixture Phase1F', 1, CURRENT_TIMESTAMP + INTERVAL '1' DAY, 'PROGRAMADA')",
                    testUnidadId, testVisitanteId
            );
            testVisitaId = jdbcTemplate.queryForObject(
                    "SELECT MAX(ID_VISITA) FROM VISITAS WHERE MOTIVO = 'Visita fixture Phase1F'", Long.class
            );
            if (testVisitaId != null) {
                visitasToClean.add(testVisitaId);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error al inicializar fixtures para Phase1FPorteriaIntegrationTest: " + e.getMessage(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // Restaurar contexto SUPERADMIN para limpieza
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(testOrgId)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        for (Long vId : visitasToClean) {
            try {
                jdbcTemplate.update("DELETE FROM VEHICULOS_VISITA WHERE ID_VISITA = ?", vId);
                jdbcTemplate.update("DELETE FROM QR_ACCESOS WHERE ID_VISITA = ?", vId);
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", vId);
                jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ?", vId);
            } catch (Exception ignored) {}
        }
        visitasToClean.clear();

        try {
            jdbcTemplate.update("DELETE FROM VEHICULOS_VISITA WHERE PLACA = 'XYZ-789'");
            jdbcTemplate.update("DELETE FROM QR_ACCESOS WHERE TOKEN_QR = 'token-seguro-phase1f'");
            jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE OBSERVACIONES LIKE '%Phase1F%'");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // 1. TESTS POSITIVOS DETERMINISTAS (201 CREATED + BODY + PERSISTENCIA EN DB)
    // =========================================================================

    @Test
    @WithMockUser(username = "2", authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    @DisplayName("POST /visitas — Admin Propiedad programa visita con datos válidos -> 201 Created y estado PROGRAMADA")
    void programarVisita_WithValidData_ShouldReturn201AndPersistProgramada() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", testUnidadId);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "PEATONAL");
        payload.put("motivo", "Visita general Phase1F");
        payload.put("estado", "PROGRAMADA");
        payload.put("autorizadoPor", adminPropiedadUserId);

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("X-Assignment-Id", String.valueOf(adminPropiedadAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVisita").isNumber())
                .andExpect(jsonPath("$.codigoQr").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.autorizadoPor").value(adminPropiedadUserId))
                .andReturn();

        // Extraer idVisita generado para validación de persistencia y limpieza posterior
        Map<?, ?> respMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long createdVisitaId = ((Number) respMap.get("idVisita")).longValue();
        visitasToClean.add(createdVisitaId);

        // Restaurar contexto SUPERADMIN para verificar persistencia en Oracle DB
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(testOrgId).propertyId(testPropiedadId)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        String estadoPersistido = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?", String.class, createdVisitaId
        );
        assertEquals("PROGRAMADA", estadoPersistido, "El estado canónico en VISITAS debe ser PROGRAMADA");
        assertNotEquals("ACTIVA", estadoPersistido, "El estado ACTIVA está prohibido en VISITAS");

        Integer qrCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM QR_ACCESOS WHERE ID_VISITA = ?", Integer.class, createdVisitaId
        );
        assertEquals(1, qrCount, "Se debe haber generado exactamente un registro de QR para la visita");
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("POST /registros/entrada — Portero registra entrada manual -> 201 Created y actualiza VISITAS a EN_CURSO")
    void registrarEntrada_WithValidData_ShouldReturn201AndTransitionToEnCurso() throws Exception {
        assertNotNull(testVisitaId, "Se requiere una visita de prueba activa");

        RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                testPropiedadId,
                testPorteriaId,
                null,
                testVisitaId,
                testPersonaVisitanteId,
                testUnidadId,
                null,
                "ENTRADA",
                "MANUAL_PORTERO",
                porteroUserId,
                null,
                "Sin novedad Phase1F"
        );

        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .header("X-Assignment-Id", String.valueOf(porteroAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idRegistroAcceso").isNumber())
                .andExpect(jsonPath("$.tipoMovimiento").value("ENTRADA"))
                .andExpect(jsonPath("$.metodoAutorizacion").value("MANUAL_PORTERO"))
                .andExpect(jsonPath("$.propiedadId").value(testPropiedadId))
                .andExpect(jsonPath("$.visitaId").value(testVisitaId));

        // Restaurar contexto SUPERADMIN para verificar persistencia en Oracle DB
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(testOrgId).propertyId(testPropiedadId)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        String estadoVisita = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VISITAS WHERE ID_VISITA = ?", String.class, testVisitaId
        );
        assertEquals("EN_CURSO", estadoVisita, "Al registrar entrada, el estado de VISITAS debe transicionar a EN_CURSO");
        assertNotEquals("ACTIVA", estadoVisita, "El estado ACTIVA está prohibido en VISITAS");

        Integer regCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM REGISTROS_ACCESO WHERE ID_VISITA = ? AND TIPO_MOVIMIENTO = 'ENTRADA'",
                Integer.class, testVisitaId
        );
        assertEquals(1, regCount, "Debe existir exactamente un registro de acceso con TIPO_MOVIMIENTO = ENTRADA");
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("POST /vehiculos — Portero registra ingreso vehicular de visita -> 201 Created y persiste en VEHICULOS_VISITA")
    void registrarIngresoVehiculo_WithValidData_ShouldReturn201AndPersistVehiculo() throws Exception {
        assertNotNull(testVisitaId, "Se requiere una visita de prueba activa");

        VehiculoVisitaRequestDTO request = new VehiculoVisitaRequestDTO(
                testVisitaId, null, "XYZ-789", "Automovil", "DENTRO"
        );

        mockMvc.perform(post("/api/v1/porteria/vehiculos")
                .header("X-Assignment-Id", String.valueOf(porteroAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVehiculoVisita").isNumber())
                .andExpect(jsonPath("$.visitaId").value(testVisitaId))
                .andExpect(jsonPath("$.placa").value("XYZ-789"))
                .andExpect(jsonPath("$.tipoVehiculo").value("Automovil"))
                .andExpect(jsonPath("$.estado").value("DENTRO"));

        // Restaurar contexto SUPERADMIN para verificar persistencia en Oracle DB
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(testOrgId).propertyId(testPropiedadId)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM VEHICULOS_VISITA WHERE ID_VISITA = ? AND PLACA = 'XYZ-789'",
                Integer.class, testVisitaId
        );
        assertEquals(1, count, "El vehículo debe quedar registrado en VEHICULOS_VISITA con la placa indicada");
    }

    @Test
    @WithMockUser(username = "4", authorities = {"SCOPE_RESIDENTE"})
    @DisplayName("POST /qr — Residente genera código QR de acceso -> 201 Created y estado ACTIVO en QR_ACCESOS")
    void generarQr_WithValidData_ShouldReturn201AndPersistQrActivo() throws Exception {
        assertNotNull(testVisitaId, "Se requiere una visita de prueba activa");

        QrAccesoRequestDTO request = new QrAccesoRequestDTO(
                testVisitaId, "token-seguro-phase1f", ZonedDateTime.now().plusHours(2), 1, "ACTIVO", residenteUserId
        );

        mockMvc.perform(post("/api/v1/porteria/qr")
                .header("X-Assignment-Id", String.valueOf(residenteAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idQr").isNumber())
                .andExpect(jsonPath("$.visitaId").value(testVisitaId))
                .andExpect(jsonPath("$.tokenQr").value("token-seguro-phase1f"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.usosPermitidos").value(1))
                .andExpect(jsonPath("$.usosConsumidos").value(0));

        // Restaurar contexto SUPERADMIN para verificar persistencia en Oracle DB
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(testOrgId).propertyId(testPropiedadId)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM QR_ACCESOS WHERE TOKEN_QR = 'token-seguro-phase1f' AND ESTADO = 'ACTIVO'",
                Integer.class
        );
        assertEquals(1, count, "El QR generado debe estar persistido en QR_ACCESOS con estado ACTIVO");
    }

    // =========================================================================
    // 2. TESTS NEGATIVOS DETERMINISTAS (AUTENTICACIÓN, AUTORIZACIÓN, IDOR, VALIDACIÓN)
    // =========================================================================

    @Test
    @DisplayName("POST /visitas — Sin autenticación -> 401 Unauthorized")
    void programarVisita_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/porteria/visitas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "999", authorities = {"SCOPE_INVITADO"})
    @DisplayName("POST /visitas — Rol no autorizado (SCOPE_INVITADO) -> 403 Forbidden")
    void programarVisita_UnauthorizedRole_Returns403() throws Exception {
        Map<String, Object> payload = Map.of(
                "unidadId", testUnidadId,
                "visitanteId", testVisitanteId,
                "motivo", "Intento no autorizado"
        );

        mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("X-Assignment-Id", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "2", authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    @DisplayName("POST /visitas — Intento de programar en unidad de otra propiedad -> 403 Forbidden (Aislamiento Multi-Tenant)")
    void programarVisita_UnitFromOtherProperty_Returns403() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 999999L); // Unidad inexistente o ajena a Propiedad 1
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "PEATONAL");
        payload.put("motivo", "Intento cross-property IDOR");
        payload.put("estado", "PROGRAMADA");

        mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("X-Assignment-Id", String.valueOf(adminPropiedadAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /registros/entrada — Sin autenticación -> 401 Unauthorized")
    void registrarEntrada_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "4", authorities = {"SCOPE_RESIDENTE"})
    @DisplayName("POST /registros/entrada — Residente intentando operar control de acceso de garita -> 403 Forbidden")
    void registrarEntrada_UnauthorizedResidenteRole_Returns403() throws Exception {
        RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                testPropiedadId, testPorteriaId, null, testVisitaId, testPersonaVisitanteId,
                testUnidadId, null, "ENTRADA", "MANUAL_PORTERO", residenteUserId, null, "Intento residente"
        );

        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .header("X-Assignment-Id", String.valueOf(residenteAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("POST /registros/entrada — Movimiento inválido (SALIDA enviado a endpoint de entrada) -> 400 Bad Request")
    void registrarEntrada_InvalidMovimiento_Returns400() throws Exception {
        RegistroAccesoRequestDTO request = new RegistroAccesoRequestDTO(
                testPropiedadId, testPorteriaId, null, testVisitaId, testPersonaVisitanteId,
                testUnidadId, null, "SALIDA", "MANUAL_PORTERO", porteroUserId, null, "Movimiento erróneo"
        );

        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .header("X-Assignment-Id", String.valueOf(porteroAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("POST /vehiculos — Sin autenticación -> 401 Unauthorized")
    void registrarIngresoVehiculo_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/porteria/vehiculos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "4", authorities = {"SCOPE_RESIDENTE"})
    @DisplayName("POST /vehiculos — Residente intentando registrar ingreso de vehículo en portería -> 403 Forbidden")
    void registrarIngresoVehiculo_UnauthorizedRole_Returns403() throws Exception {
        VehiculoVisitaRequestDTO request = new VehiculoVisitaRequestDTO(
                testVisitaId, null, "XYZ-789", "Automovil", "DENTRO"
        );

        mockMvc.perform(post("/api/v1/porteria/vehiculos")
                .header("X-Assignment-Id", String.valueOf(residenteAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("POST /vehiculos — Datos inválidos (placa nula viola @NotNull) -> 400 Bad Request")
    void registrarIngresoVehiculo_InvalidDataNullPlaca_Returns400() throws Exception {
        VehiculoVisitaRequestDTO request = new VehiculoVisitaRequestDTO(
                testVisitaId, null, null, "Automovil", "DENTRO"
        );

        mockMvc.perform(post("/api/v1/porteria/vehiculos")
                .header("X-Assignment-Id", String.valueOf(porteroAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /qr — Sin autenticación -> 401 Unauthorized")
    void generarQr_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/porteria/qr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "3", authorities = {"SCOPE_PORTERO"})
    @DisplayName("POST /qr — Portero intentando generar QR directamente vía endpoint de residentes -> 403 Forbidden")
    void generarQr_UnauthorizedPorteroRole_Returns403() throws Exception {
        QrAccesoRequestDTO request = new QrAccesoRequestDTO(
                testVisitaId, "token-portero-invalido", ZonedDateTime.now().plusHours(2), 1, "ACTIVO", porteroUserId
        );

        mockMvc.perform(post("/api/v1/porteria/qr")
                .header("X-Assignment-Id", String.valueOf(porteroAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "4", authorities = {"SCOPE_RESIDENTE"})
    @DisplayName("POST /qr — Datos inválidos (tokenQr nulo viola @NotNull) -> 400 Bad Request")
    void generarQr_InvalidDataNullToken_Returns400() throws Exception {
        QrAccesoRequestDTO request = new QrAccesoRequestDTO(
                testVisitaId, null, ZonedDateTime.now().plusHours(2), 1, "ACTIVO", residenteUserId
        );

        mockMvc.perform(post("/api/v1/porteria/qr")
                .header("X-Assignment-Id", String.valueOf(residenteAssignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
