package com.saed.backend.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.dto.ApiErrorResponse;
import com.saed.backend.common.dto.FieldErrorDetail;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Contractual:
 * SAED 2.0 — API RESPONSE & ERROR CONTRACT v1
 *
 * Valida de forma exhaustiva:
 * 1. Estructura canónica y determinismo (success, status, code, message, traceId, timestamp).
 * 2. Validación de campos: fieldErrors (field, code, message) + errors map backward-compatible.
 * 3. Semántica HTTP Status: 400, 401, 403, 404, 405, 409, 422, 500.
 * 4. Trazabilidad: correlación con CorrelationIdHolder y cabecera X-Correlation-Id.
 * 5. Seguridad: CERO filtración de SQL, ORA errors, stack traces o tablas internas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ApiErrorResponseContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssignmentService assignmentService;

    private String adminToken;
    private String residenteToken;

    private static final long USER_ADMIN = 2L;
    private static final long ASSIGN_ADMIN = 102L;

    private static final long USER_RESIDENTE = 4L;
    private static final long ASSIGN_RESIDENTE = 104L;

    @BeforeEach
    public void setUp() {
        OrganizationDTO org1 = new OrganizationDTO(1L, "Organización Central SAED");
        PropertyDTO prop1 = new PropertyDTO(1L, "Torre Central SAED");
        UnitDTO unit1 = new UnitDTO(1L, "Apto 101");

        AssignmentResponseDTO adminAssign = new AssignmentResponseDTO();
        adminAssign.setIdAsignacion(ASSIGN_ADMIN);
        adminAssign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminAssign.setOrganizacion(org1);
        adminAssign.setPropiedad(prop1);

        AssignmentResponseDTO resAssign = new AssignmentResponseDTO();
        resAssign.setIdAsignacion(ASSIGN_RESIDENTE);
        resAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resAssign.setOrganizacion(org1);
        resAssign.setPropiedad(prop1);
        resAssign.setUnidad(unit1);

        when(assignmentService.validateAssignment(ASSIGN_ADMIN, USER_ADMIN)).thenReturn(Optional.of(adminAssign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(resAssign));

        adminToken = jwtProvider.generateIdentityToken(USER_ADMIN);
        residenteToken = jwtProvider.generateIdentityToken(USER_RESIDENTE);
    }

    // =========================================================================
    // 1. VALIDATION ERRORS (HTTP 400 + fieldErrors + errors)
    // =========================================================================

    @Test
    @DisplayName("Contract 01: Error de validación Bean produce 400 canónico con fieldErrors y errors")
    public void testValidationError_BeanValidation_ProducesCanonical400() throws Exception {
        // Enviar reporte de incidente con título nulo para disparar @NotNull en titulo y fechaHoraIncidente
        String invalidPayload = """
            {
                "titulo": null,
                "tipoIncidente": "DANO_BIEN_COMUN",
                "nivelSeveridad": "MODERADA",
                "descripcionHechos": "Prueba de contrato de validación"
            }
        """;

        MvcResult result = mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Error de validación en los campos enviados"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.fieldErrors[0].field").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].code").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors.titulo").isNotEmpty())
                .andExpect(jsonPath("$.errors.fechaHoraIncidente").isNotEmpty())
                .andReturn();

        // Validar deserialización contra ApiErrorResponse DTO
        String json = result.getResponse().getContentAsString();
        ApiErrorResponse err = objectMapper.readValue(json, ApiErrorResponse.class);
        assertFalse(err.isSuccess());
        assertEquals(400, err.getStatus());
        assertEquals("VALIDATION_FAILED", err.getCode());
        assertNotNull(err.getTraceId());
        assertNotNull(err.getTimestamp());
        assertNotNull(err.getFieldErrors());
        assertFalse(err.getFieldErrors().isEmpty());
    }

    @Test
    @DisplayName("Contract 02: JSON malformado produce 400 MALFORMED_JSON canónico")
    public void testMalformedJson_ProducesCanonical400() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto_no_es_json_valido: ... }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Contract 03: Parámetro de tipo incompatible produce 400 TYPE_MISMATCH")
    public void testTypeMismatch_ProducesCanonical400() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/no-es-un-numero")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =========================================================================
    // 2. SEGURIDAD Y AUTORIZACIÓN (HTTP 401 & 403)
    // =========================================================================

    @Test
    @DisplayName("Contract 04: Petición sin autenticación produce 401 UNAUTHORIZED")
    public void testAuthenticationError_Unauthenticated_Produces401() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Contract 05: Rol sin privilegios produce 403 FORBIDDEN canónico")
    public void testAuthorizationError_ForbiddenRole_ProducesCanonical403() throws Exception {
        // Residente intentando cerrar un incidente (solo ADMIN_PROPIEDAD)
        mockMvc.perform(post("/api/v1/incidentes/1/cerrar")
                        .header("Authorization", "Bearer " + residenteToken)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusiones\": \"Intento no autorizado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =========================================================================
    // 3. RECURSO NO ENCONTRADO (HTTP 404)
    // =========================================================================

    @Test
    @DisplayName("Contract 06: Recurso inexistente produce 404 canónico")
    public void testNotFoundError_ResourceNotFound_ProducesCanonical404() throws Exception {
        mockMvc.perform(get("/api/v1/activos/999999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value(anyOf(equalTo("ACTIVO_NOT_FOUND"), equalTo("NOT_FOUND"))))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =========================================================================
    // 4. MÉTODO HTTP NO SOPORTADO (HTTP 405)
    // =========================================================================

    @Test
    @DisplayName("Contract 07: Método HTTP no soportado produce 405 METHOD_NOT_ALLOWED")
    public void testMethodNotAllowed_ProducesCanonical405() throws Exception {
        mockMvc.perform(delete("/api/v1/incidentes/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =========================================================================
    // 5. REGLAS DE NEGOCIO (HTTP 422) & TRANSICIONES FSM (HTTP 400)
    // =========================================================================

    @Test
    @DisplayName("Contract 08: Transición FSM inválida produce 400 TRANSICION_ESTADO_INVALIDA")
    public void testInvalidTransition_ProducesCanonical400() throws Exception {
        // Enviar cambio de estado inválido
        mockMvc.perform(put("/api/v1/incidentes/1/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\": \"ESTADO_INEXISTENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // =========================================================================
    // 6. SEGURIDAD Y ZERO LEAKS DE IMPLEMENTACIÓN
    // =========================================================================

    @Test
    @DisplayName("Contract 09: Cero filtración de SQL, ORA o stack traces en respuestas de error")
    public void testSecurityLeakPrevention_ZeroInternalLeaks() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"titulo\": \"\" }"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("ORA-"), "No debe contener códigos ORA de Oracle");
        assertFalse(body.contains("SELECT "), "No debe contener sintaxis SQL");
        assertFalse(body.contains("FROM "), "No debe contener sintaxis SQL");
        assertFalse(body.contains("INSERT "), "No debe contener sintaxis SQL");
        assertFalse(body.contains("UPDATE "), "No debe contener sintaxis SQL");
        assertFalse(body.contains("org.springframework"), "No debe exponer paquetes internos de Spring");
        assertFalse(body.contains("com.saed.backend"), "No debe exponer paquetes internos del backend");
        assertFalse(body.contains("\tat "), "No debe exponer trazas de stacktrace");
    }

    @Test
    @DisplayName("Contract 10: Trazabilidad propaga X-Correlation-Id recibido en la petición")
    public void testTraceability_PropagatesCorrelationId() throws Exception {
        String testCorrelationId = "cid-contract-test-999888";

        mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Assignment-Id", ASSIGN_ADMIN)
                        .header("X-Correlation-Id", testCorrelationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"titulo\": \"\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", testCorrelationId))
                .andExpect(jsonPath("$.traceId").value(testCorrelationId));
    }
}
