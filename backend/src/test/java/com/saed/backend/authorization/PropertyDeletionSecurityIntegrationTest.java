package com.saed.backend.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.authorization.service.PropertyDeletionChallengeService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PropertyDeletionSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PropertyDeletionChallengeService challengeService;

    @MockBean
    private AssignmentService assignmentService;

    private final String orgAdminAssignment = "999981";
    private final String propAdminAssignment = "999982";
    private final String residentAssignment = "999983";
    private final String otherOrgAdminAssignment = "999984";

    private final Long testOrgId = 999981L;
    private final Long otherOrgId = 999984L;
    private final Long testPropId = 999981L;
    private final Long otherPropId = 999984L;

    @BeforeEach
    public void setupDataAndMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Limpiar datos previos si existen
        try { jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (999981, 999982, 999983, 999984)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD IN (999981, 999984, 999985)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION IN (999981, 999984)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO IN (999981, 999982, 999983, 999984)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA IN (999981, 999982, 999983, 999984)"); } catch (Exception ignored) {}

        // Seed Org A y Propiedad A
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 999981 AS id, 'Org Test A' AS n, 'NIT999981' AS if, 'adminA@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999981 AS id, 999981 AS o, 1 AS t, 'Propiedad A Eliminar' AS n, 'Calle 123' AS dir, 'Bogota' AS ciu, 'Colombia' AS pais, 'MIXTA' AS oc, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pais, s.oc, s.st) WHEN MATCHED THEN UPDATE SET pr.ESTADO = 'ACTIVA'"); } catch (Exception ignored) {}

        // Seed Org B y Propiedad B (para test cross-tenant)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 999984 AS id, 'Org Test B' AS n, 'NIT999984' AS if, 'adminB@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999984 AS id, 999984 AS o, 1 AS t, 'Propiedad B Ajena' AS n, 'Carrera 45' AS dir, 'Medellin' AS ciu, 'Colombia' AS pais, 'MIXTA' AS oc, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pais, s.oc, s.st) WHEN MATCHED THEN UPDATE SET pr.ESTADO = 'ACTIVA'"); } catch (Exception ignored) {}

        // Seed Usuarios & Personas
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999981 AS id, 1 AS td, 'DOC999981' AS doc, 'NATURAL' AS tp, 'adminA@test.com' AS em, 'Admin' AS n, 'OrgA' AS a FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 999981 AS id, 999981 AS p, 'admin_org_a' AS u, 'adminA@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999982 AS id, 1 AS td, 'DOC999982' AS doc, 'NATURAL' AS tp, 'propadminA@test.com' AS em, 'Prop' AS n, 'Admin' AS a FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 999982 AS id, 999982 AS p, 'prop_admin_a' AS u, 'propadminA@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999983 AS id, 1 AS td, 'DOC999983' AS doc, 'NATURAL' AS tp, 'resA@test.com' AS em, 'Res' AS n, 'A' AS a FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 999983 AS id, 999983 AS p, 'residente_a' AS u, 'resA@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999984 AS id, 1 AS td, 'DOC999984' AS doc, 'NATURAL' AS tp, 'adminB@test.com' AS em, 'Admin' AS n, 'OrgB' AS a FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 999984 AS id, 999984 AS p, 'admin_org_b' AS u, 'adminB@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)"); } catch (Exception ignored) {}

        // Insertar asignaciones reales en Oracle para que PKG_SAED_SESSION.SET_CONTEXT no falle
        Long idRolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
        Long idRolProp = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);

        try { jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (999981, 999982, 999984)"); } catch (Exception ignored) {}
        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (999981, 999981, ?, 999981, 'ACTIVA')", idRolOrg);
        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (999982, 999982, ?, 999981, 999981, 'ACTIVA')", idRolProp);
        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (999984, 999984, ?, 999984, 'ACTIVA')", idRolOrg);

        // Mockito Asignaciones
        AssignmentResponseDTO orgAdmin = new AssignmentResponseDTO();
        orgAdmin.setIdAsignacion(999981L);
        orgAdmin.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgAdmin.setOrganizacion(new OrganizationDTO(testOrgId, "Org Test A"));
        Mockito.when(assignmentService.validateAssignment(999981L, 999981L)).thenReturn(Optional.of(orgAdmin));

        AssignmentResponseDTO propAdmin = new AssignmentResponseDTO();
        propAdmin.setIdAsignacion(999982L);
        propAdmin.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdmin.setOrganizacion(new OrganizationDTO(testOrgId, "Org Test A"));
        PropertyDTO pDto = new PropertyDTO();
        pDto.setId(testPropId);
        pDto.setIdOrganizacion(testOrgId);
        propAdmin.setPropiedad(pDto);
        Mockito.when(assignmentService.validateAssignment(999982L, 999982L)).thenReturn(Optional.of(propAdmin));

        AssignmentResponseDTO resident = new AssignmentResponseDTO();
        resident.setIdAsignacion(999983L);
        resident.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resident.setOrganizacion(new OrganizationDTO(testOrgId, "Org Test A"));
        resident.setPropiedad(pDto);
        Mockito.when(assignmentService.validateAssignment(999983L, 999983L)).thenReturn(Optional.of(resident));

        AssignmentResponseDTO otherOrgAdmin = new AssignmentResponseDTO();
        otherOrgAdmin.setIdAsignacion(999984L);
        otherOrgAdmin.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        otherOrgAdmin.setOrganizacion(new OrganizationDTO(otherOrgId, "Org Test B"));
        Mockito.when(assignmentService.validateAssignment(999984L, 999984L)).thenReturn(Optional.of(otherOrgAdmin));

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // 1. REGLAS DE AUTORIZACIÓN (TEST 01 a 06)
    // =========================================================================

    @Test
    @DisplayName("TEST 01: ADMIN_ORGANIZACION puede solicitar eliminación de su propia propiedad")
    public void test01_adminOrg_canRequestDeletion() throws Exception {
        String token = jwtProvider.generateIdentityToken(999981L);

        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.maskedEmail").isNotEmpty());
    }

    @Test
    @DisplayName("TEST 02: ADMIN_PROPIEDAD es rechazado con 403 Forbidden")
    public void test02_adminPropiedad_cannotRequestDeletion() throws Exception {
        String token = jwtProvider.generateIdentityToken(999982L);

        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 04: RESIDENTE es rechazado con 403 Forbidden")
    public void test04_residente_cannotRequestDeletion() throws Exception {
        String token = jwtProvider.generateIdentityToken(999983L);

        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", residentAssignment)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 05: SUPERADMIN es rechazado con 403 Forbidden (no opera en contexto de organización)")
    public void test05_superadmin_cannotRequestDeletion() throws Exception {
        String token = jwtProvider.generateIdentityToken(1L);

        // Sin asignación de ADMIN_ORGANIZACION
        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 06: Ataque Cross-Tenant (Org B intenta eliminar Propiedad de Org A) es bloqueado")
    public void test06_crossTenant_blocked() throws Exception {
        String token = jwtProvider.generateIdentityToken(999984L);

        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", otherOrgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 404, "Debe retornar 403 Forbidden o 404 Not Found por aislamiento multi-tenant, pero fue: " + status);
                });
    }

    // =========================================================================
    // 2. CICLO COMPLETO DE OTP Y DOBLE CONFIRMACIÓN (TEST 07 a 16)
    // =========================================================================

    @Test
    @DisplayName("TEST 07-10: Flujo de generación, verificación de código erróneo y verificación exitosa")
    public void test07_otpVerificationLifecycle() throws Exception {
        String token = jwtProvider.generateIdentityToken(999981L);

        // 1. Solicitar OTP
        MvcResult reqResult = mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/request")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        PropertyDeletionDTOs.RequestResponse reqResponse = objectMapper.readValue(
                reqResult.getResponse().getContentAsString(),
                PropertyDeletionDTOs.RequestResponse.class
        );
        String challengeId = reqResponse.challengeId();
        assertNotNull(challengeId);

        // 2. TEST 08: Verificar con código erróneo -> Rechazado
        PropertyDeletionDTOs.VerifyRequest wrongReq = new PropertyDeletionDTOs.VerifyRequest(challengeId, "000000");
        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/verify")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        // 3. TEST 10: Obtener desafío y verificar con código correcto -> VERIFIED
        var challengeOpt = challengeService.getChallenge(challengeId);
        assertTrue(challengeOpt.isPresent());

        // Simulamos la verificación exitosa directamente o creando el challenge con código conocido
        // Para probar el endpoint con el código exacto:
        var gen = challengeService.createChallenge(testPropId, testOrgId, 999981L, "adminA@test.com", "Propiedad A");
        PropertyDeletionDTOs.VerifyRequest correctReq = new PropertyDeletionDTOs.VerifyRequest(gen.challengeId(), gen.rawOtp());

        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/verify")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        assertEquals(PropertyDeletionChallengeService.ChallengeState.VERIFIED, gen.challenge().getState());
    }

    @Test
    @DisplayName("TEST 13: Fuerza bruta bloquea el desafío tras superar intentos máximos")
    public void test13_bruteForce_blocksChallenge() throws Exception {
        String token = jwtProvider.generateIdentityToken(999981L);

        var gen = challengeService.createChallenge(testPropId, testOrgId, 999981L, "adminA@test.com", "Propiedad A");

        // Realizar 5 intentos fallidos
        for (int i = 0; i < 5; i++) {
            PropertyDeletionDTOs.VerifyRequest badReq = new PropertyDeletionDTOs.VerifyRequest(gen.challengeId(), "99999" + i);
            mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/verify")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Assignment-Id", orgAdminAssignment)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verified").value(false));
        }

        // El 6to intento debe fallar con error de estado bloqueado (Conflict 409)
        PropertyDeletionDTOs.VerifyRequest sixthReq = new PropertyDeletionDTOs.VerifyRequest(gen.challengeId(), "123456");
        mockMvc.perform(post("/api/v1/properties/" + testPropId + "/deletion/verify")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sixthReq)))
                .andExpect(status().isConflict());

        assertEquals(PropertyDeletionChallengeService.ChallengeState.BLOCKED, gen.challenge().getState());
    }

    @Test
    @DisplayName("TEST 14-16: Doble confirmación obligatoria, ejecución destructiva e idempotencia")
    public void test14_16_confirmAndExecuteDeletion_withDoubleConfirmationAndIdempotency() throws Exception {
        String token = jwtProvider.generateIdentityToken(999981L);

        // Crear propiedad temporal dedicada para borrado real
        Long propToDeleteId = 999985L;
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (" + propToDeleteId + ", 999981, 1, 'Prop Temp Delete', 'Calle Temp', 'Cali', 'Colombia', 'MIXTA', 'ACTIVA')");
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        var gen = challengeService.createChallenge(propToDeleteId, testOrgId, 999981L, "adminA@test.com", "Prop Temp Delete");

        // TEST 14: Intentar confirmar SIN verificar OTP -> Falla con Conflict (409)
        PropertyDeletionDTOs.ConfirmRequest unverifiedConfirm = new PropertyDeletionDTOs.ConfirmRequest(gen.challengeId(), true);
        mockMvc.perform(post("/api/v1/properties/" + propToDeleteId + "/deletion/confirm")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unverifiedConfirm)))
                .andExpect(status().isConflict());

        // Verificar OTP legítimo
        challengeService.verifyOtp(gen.challengeId(), propToDeleteId, testOrgId, 999981L, gen.rawOtp());

        // TEST 15: Con OTP verificado + Confirmación = true -> Eliminación exitosa
        PropertyDeletionDTOs.ConfirmRequest validConfirm = new PropertyDeletionDTOs.ConfirmRequest(gen.challengeId(), true);
        mockMvc.perform(post("/api/v1/properties/" + propToDeleteId + "/deletion/confirm")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validConfirm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.idPropiedadEliminada").value(propToDeleteId));

        // TEST 17: Verificación de persistencia real en Oracle ATP -> La propiedad ya no existe
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?",
                Integer.class,
                propToDeleteId
        );
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        assertEquals(0, count, "La propiedad debió ser eliminada de Oracle ATP");

        // TEST AUDIT SURVIVAL: Verificar que AUDITORIA_LOG conserva el evento y NO fue eliminado
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA_LOG WHERE ENTIDAD = 'PROPIEDAD' AND ID_ENTIDAD_AFECTADA = ? AND ACCION = 'DELETE'",
                Integer.class,
                propToDeleteId
        );
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1, "AUDITORIA_LOG debe conservar el registro de la eliminación de la propiedad");

        // TEST 16: Idempotencia -> Segundo intento de confirmación cuando ya fue eliminada es rechazado
        mockMvc.perform(post("/api/v1/properties/" + propToDeleteId + "/deletion/confirm")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validConfirm)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 404 || status == 409, "Debe retornar 404 (no existe) o 409 (conflicto), pero fue: " + status);
                });
    }
}
