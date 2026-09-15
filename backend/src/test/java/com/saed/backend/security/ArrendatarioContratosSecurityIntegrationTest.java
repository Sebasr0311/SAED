package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Pruebas de Seguridad y Autorizacion para ARRENDATARIO y CONTRATOS (Fase 6).
 * Valida de forma rigurosa y adversarial:
 * 1. Registro valido de PROPIETARIO sin contrato (200 OK).
 * 2. Rechazo de ARRENDATARIO sin contrato activo (400 Bad Request).
 * 3. Registro atomico de ARRENDATARIO con contrato y plantilla valida (200 OK).
 * 4. Rechazo de uso de plantilla de otra organizacion (403 Forbidden).
 * 5. Rechazo de plantilla inactiva o borrador (400 Bad Request).
 * 6. Rechazo de creacion de contrato cruzado por ADMIN_PROPIEDAD (403 Forbidden).
 * 7. Bloqueo de creacion de contratos para roles no administrativos (403 Forbidden).
 * 8. Rechazo al cambiar PROPIETARIO a ARRENDATARIO sin contrato (400 Bad Request).
 * 9. Rechazo en POST /units/{id}/residents para ARRENDATARIO sin contrato (400 Bad Request).
 * 10. Desactivacion de residente ARRENDATARIO al cancelar el contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ArrendatarioContratosSecurityIntegrationTest {

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

    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 1L;

    private static final long USER_ADMIN_ORG = 8L;
    private static final long ASSIGN_ADMIN_ORG = 301L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 101L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long USER_PORTERO = 3L;
    private static final long ASSIGN_PORTERO = 103L;

    private static final long USER_RESIDENTE = 4L;
    private static final long ASSIGN_RESIDENTE = 201L;

    private static final long USER_CONVIVIENTE = 6L;
    private static final long ASSIGN_CONVIVIENTE = 206L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_2_ID = 9992L;

    private long personaPropietarioId = 10L;
    private long personaArrendatarioId = 11L;

    private long plantillaActivaOrg1Id = 101L;
    private long plantillaBorradorOrg1Id = 102L;
    private long plantillaActivaOrg2Id = 201L;

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {
        }

        // Restaurar estado canonico de Unidad 2 en Propiedad 1
        try { jdbcTemplate.update("UPDATE UNIDADES SET ID_PROPIEDAD = 1 WHERE ID_UNIDAD = 2"); } catch (Exception ignored) {}

        // Limpiar datos previos de pruebas en unidades de test
        try { jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (?, ?)", UNIT_1_ID, UNIT_2_ID); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (?, ?)", UNIT_1_ID, UNIT_2_ID); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD IN (?, ?)", UNIT_1_ID, UNIT_2_ID); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD IN (?, ?)", UNIT_1_ID, UNIT_2_ID); } catch (Exception ignored) {}

        // Asegurar Organizacion 9992
        try {
            jdbcTemplate.execute("MERGE INTO ORGANIZACIONES o "
                    + "USING (SELECT 9992 AS id, 'Org 9992 Test' AS nom, '900009992-2' AS nit, "
                    + "'org9992@test.com' AS em FROM DUAL) s "
                    + "ON (o.ID_ORGANIZACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) "
                    + "VALUES (s.id, s.nom, s.nit, s.em)");
        } catch (Exception ignored) {
        }

        // Asegurar Propiedad 9992
        try {
            jdbcTemplate.execute("MERGE INTO PROPIEDADES p "
                    + "USING (SELECT 9992 AS id, 9992 AS org, 'Propiedad 9992 Test' AS nom FROM DUAL) s "
                    + "ON (p.ID_PROPIEDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, "
                    + "NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) "
                    + "VALUES (s.id, s.org, 1, s.nom, 'Calle 9992', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA')");
        } catch (Exception ignored) {
        }

        // Asegurar Unidad 9992
        try {
            jdbcTemplate.execute("MERGE INTO UNIDADES u "
                    + "USING (SELECT 9992 AS id, 9992 AS prop, 'Apto 9992' AS num FROM DUAL) s "
                    + "ON (u.ID_UNIDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) "
                    + "VALUES (s.id, s.prop, 1, s.num, 'ACTIVA')");
        } catch (Exception ignored) {
        }

        // Asegurar Personas de prueba: reutilizar si ya existen, insertar si no
        List<Long> p10List = jdbcTemplate.queryForList(
                "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = '1000000010'", Long.class);
        if (!p10List.isEmpty()) {
            personaPropietarioId = p10List.get(0);
        } else {
            org.springframework.jdbc.support.KeyHolder kh10 = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(con -> con.prepareStatement(
                "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, TIPO_PERSONA, EMAIL) " +
                "VALUES (1, '1000000010', 'Juan', 'Perez', 'NATURAL', 'juan.perez@test.com')",
                new String[]{"ID_PERSONA"}
            ), kh10);
            if (kh10.getKey() != null) {
                personaPropietarioId = kh10.getKey().longValue();
            }
        }

        List<Long> p11List = jdbcTemplate.queryForList(
                "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = '1000000011'", Long.class);
        if (!p11List.isEmpty()) {
            personaArrendatarioId = p11List.get(0);
        } else {
            org.springframework.jdbc.support.KeyHolder kh11 = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(con -> con.prepareStatement(
                "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, TIPO_PERSONA, EMAIL) " +
                "VALUES (1, '1000000011', 'Maria', 'Gomez', 'NATURAL', 'maria.gomez@test.com')",
                new String[]{"ID_PERSONA"}
            ), kh11);
            if (kh11.getKey() != null) {
                personaArrendatarioId = kh11.getKey().longValue();
            }
        }

        // Asegurar Plantillas de contrato
        try {
            jdbcTemplate.update("DELETE FROM PLANTILLAS_CONTRATOS WHERE CODIGO IN ('TPL_ORG1_ACTIVA', 'TPL_ORG1_BORRADOR', 'TPL_ORG2_ACTIVA')");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.update("INSERT INTO PLANTILLAS_CONTRATOS (ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) " +
                    "VALUES (1, 'TPL_ORG1_ACTIVA', 'Plantilla Activa Org 1', 'INICIAL', '<p>Test</p>', 'ACTIVA', 1, 1)");
            jdbcTemplate.update("INSERT INTO PLANTILLAS_CONTRATOS (ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) " +
                    "VALUES (1, 'TPL_ORG1_BORRADOR', 'Plantilla Borrador Org 1', 'INICIAL', '<p>Test</p>', 'BORRADOR', 1, 1)");
            jdbcTemplate.update("INSERT INTO PLANTILLAS_CONTRATOS (ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) " +
                    "VALUES (" + ORG_2_ID + ", 'TPL_ORG2_ACTIVA', 'Plantilla Activa Org 2', 'INICIAL', '<p>Test</p>', 'ACTIVA', 1, 1)");

            plantillaActivaOrg1Id = jdbcTemplate.queryForObject("SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_ORG1_ACTIVA'", Long.class);
            plantillaBorradorOrg1Id = jdbcTemplate.queryForObject("SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_ORG1_BORRADOR'", Long.class);
            plantillaActivaOrg2Id = jdbcTemplate.queryForObject("SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_ORG2_ACTIVA'", Long.class);
        } catch (Exception ignored) {}

        setupMockAssignments();

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();
    }

    private <T> T queryWithElevatedContext(String sql, Class<T> requiredType, Object... args) {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            return jdbcTemplate.queryForObject(sql, requiredType, args);
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Org 1");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Propiedad 1");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Org 2");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Propiedad 2");

        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

        AssignmentResponseDTO adminProp2Assign = new AssignmentResponseDTO();
        adminProp2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        adminProp2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp2Assign.setOrganizacion(org2);
        adminProp2Assign.setPropiedad(prop2);

        AssignmentResponseDTO adminOrgAssign = new AssignmentResponseDTO();
        adminOrgAssign.setIdAsignacion(ASSIGN_ADMIN_ORG);
        adminOrgAssign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrgAssign.setOrganizacion(org1);

        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        AssignmentResponseDTO portAssign = new AssignmentResponseDTO();
        portAssign.setIdAsignacion(ASSIGN_PORTERO);
        portAssign.setRol(new RoleDTO("PORTERO", "PORTERIA"));
        portAssign.setOrganizacion(org1);
        portAssign.setPropiedad(prop1);

        AssignmentResponseDTO resAssign = new AssignmentResponseDTO();
        resAssign.setIdAsignacion(ASSIGN_RESIDENTE);
        resAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resAssign.setOrganizacion(org1);
        resAssign.setPropiedad(prop1);
        resAssign.setUnidad(unit1);

        AssignmentResponseDTO convAssign = new AssignmentResponseDTO();
        convAssign.setIdAsignacion(ASSIGN_CONVIVIENTE);
        convAssign.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        convAssign.setOrganizacion(org1);
        convAssign.setPropiedad(prop1);
        convAssign.setUnidad(unit1);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN))
                .thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG, USER_ADMIN_ORG))
                .thenReturn(Optional.of(adminOrgAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1))
                .thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2))
                .thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO))
                .thenReturn(Optional.of(portAssign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE))
                .thenReturn(Optional.of(resAssign));
        when(assignmentService.validateAssignment(ASSIGN_CONVIVIENTE, USER_CONVIVIENTE))
                .thenReturn(Optional.of(convAssign));
    }

    @AfterEach
    public void tearDown() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();
    }

    @Test
    @DisplayName("SEC-F6-01: Propietario valid registration without contract succeeds (200 OK)")
    public void testSecF601_PropietarioRegistrationWithoutContract_Succeeds() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("tipoRelacion", "PROPIETARIO_RESIDENTE");

        mockMvc.perform(post("/api/v1/residentes/" + personaPropietarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Integer countProp = queryWithElevatedContext(
                "SELECT COUNT(1) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_1_ID, personaPropietarioId);
        assertEquals(1, countProp);

        Integer countRes = queryWithElevatedContext(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'PROPIETARIO'",
                Integer.class, UNIT_1_ID, personaPropietarioId);
        assertEquals(1, countRes);

        Integer countContrato = queryWithElevatedContext(
                "SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = ? AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_1_ID);
        assertEquals(0, countContrato);
    }

    @Test
    @DisplayName("SEC-F6-02: Arrendatario registration without contract is rejected (400 Bad Request)")
    public void testSecF602_ArrendatarioRegistrationWithoutContract_RejectedBadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");

        mockMvc.perform(post("/api/v1/residentes/" + personaArrendatarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());

        Integer countRes = queryWithElevatedContext(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?",
                Integer.class, UNIT_1_ID, personaArrendatarioId);
        assertEquals(0, countRes);
    }

    @Test
    @DisplayName("SEC-F6-03: Arrendatario atomic registration with valid contract and template succeeds (200 OK)")
    public void testSecF603_ArrendatarioAtomicRegistrationWithValidContractAndTemplate_Succeeds() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", 1500000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("idPlantilla", plantillaActivaOrg1Id);
        payload.put("tipoContrato", "INICIAL");

        mockMvc.perform(post("/api/v1/residentes/" + personaArrendatarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Integer countContrato = queryWithElevatedContext(
                "SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = ? AND ID_ARRENDATARIO_PRINCIPAL = ? AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_1_ID, personaArrendatarioId);
        assertEquals(1, countContrato);

        Integer countRes = queryWithElevatedContext(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                Integer.class, UNIT_1_ID, personaArrendatarioId);
        assertEquals(1, countRes);
    }

    @Test
    @DisplayName("SEC-F6-04: Cross-tenant template usage is rejected (403 Forbidden)")
    public void testSecF604_CrossTenantTemplateUsage_Forbidden() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("idResidente", personaArrendatarioId);
        payload.put("canonMensual", 1600000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("tipoContrato", "INICIAL");
        payload.put("idPlantilla", plantillaActivaOrg2Id);

        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F6-05: Inactive template usage is rejected (400 Bad Request)")
    public void testSecF605_InactiveTemplateUsage_RejectedBadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("idResidente", personaArrendatarioId);
        payload.put("canonMensual", 1700000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("tipoContrato", "INICIAL");
        payload.put("idPlantilla", plantillaBorradorOrg1Id);

        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-F6-06: Cross-property contract creation by ADMIN_PROPIEDAD is rejected (403 Forbidden)")
    public void testSecF606_CrossPropertyContractCreationByAdminPropiedad_Forbidden() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Intenta crear contrato en Unidad 2 (pertenece a Propiedad 2)
        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_2_ID);
        payload.put("idResidente", personaArrendatarioId);
        payload.put("canonMensual", 1800000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("tipoContrato", "INICIAL");
        payload.put("idPlantilla", plantillaActivaOrg1Id);

        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F6-07: Non-admin roles denied contract creation (403 Forbidden)")
    public void testSecF607_NonAdminRoles_ContractCreation_Forbidden() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("idResidente", personaArrendatarioId);
        payload.put("canonMensual", 1500000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("tipoContrato", "INICIAL");
        payload.put("idPlantilla", plantillaActivaOrg1Id);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        // 1. Residente Titular
        String tokenRes = jwtProvider.generateIdentityToken(USER_RESIDENTE);
        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenRes)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isForbidden());

        // 2. Conviviente
        String tokenConv = jwtProvider.generateIdentityToken(USER_CONVIVIENTE);
        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenConv)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isForbidden());

        // 3. Portero
        String tokenPort = jwtProvider.generateIdentityToken(USER_PORTERO);
        mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenPort)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_PORTERO))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F6-08: Switching PROPIETARIO to ARRENDATARIO without contract is rejected (400 Bad Request)")
    public void testSecF608_SwitchPropietarioToArrendatarioWithoutContract_RejectedBadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // 1. Primero registrar como PROPIETARIO
        Map<String, Object> propPayload = new HashMap<>();
        propPayload.put("idApartamento", UNIT_1_ID);
        propPayload.put("tipoRelacion", "PROPIETARIO_RESIDENTE");

        mockMvc.perform(post("/api/v1/residentes/" + personaPropietarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(propPayload)))
                .andExpect(status().isOk());

        // 2. Intentar cambiarlo a ARRENDATARIO sin enviar datos de contrato
        Map<String, Object> arrPayload = new HashMap<>();
        arrPayload.put("idApartamento", UNIT_1_ID);
        arrPayload.put("tipoRelacion", "ARRENDATARIO");

        mockMvc.perform(post("/api/v1/residentes/" + personaPropietarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(arrPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-F6-09: Direct POST /units/{unitId}/residents for ARRENDATARIO without contract is rejected (400 Bad Request)")
    public void testSecF609_DirectUnitResidentsPostForArrendatarioWithoutContract_RejectedBadRequest() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("personaId", personaArrendatarioId);
        payload.put("tipoResidente", "ARRENDATARIO");

        mockMvc.perform(post("/api/v1/units/" + UNIT_1_ID + "/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-F6-10: Contract cancellation deactivates the arrendatario in RESIDENTES_UNIDAD")
    public void testSecF610_ContractCancellation_DeactivatesArrendatarioInResidentesUnidad() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // 1. Crear contrato y asignar arrendatario
        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_1_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", 1500000);
        payload.put("fechaInicio", "2026-09-01");
        payload.put("idPlantilla", plantillaActivaOrg1Id);
        payload.put("tipoContrato", "INICIAL");

        mockMvc.perform(post("/api/v1/residentes/" + personaArrendatarioId + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Long idContrato = queryWithElevatedContext(
                "SELECT ID_CONTRATO FROM CONTRATOS WHERE ID_UNIDAD = ? AND ID_ARRENDATARIO_PRINCIPAL = ? AND ESTADO = 'ACTIVO'",
                Long.class, UNIT_1_ID, personaArrendatarioId);
        assertNotNull(idContrato);

        // 2. Cancelar el contrato
        mockMvc.perform(post("/api/v1/contratos/" + idContrato + "/cancelar")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        String estadoContrato = queryWithElevatedContext(
                "SELECT ESTADO FROM CONTRATOS WHERE ID_CONTRATO = ?",
                String.class, idContrato);
        assertEquals("CANCELADO", estadoContrato);

        String estadoResidente = queryWithElevatedContext(
                "SELECT ESTADO FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?",
                String.class, UNIT_1_ID, personaArrendatarioId);
        assertEquals("INACTIVO", estadoResidente);
    }
}
