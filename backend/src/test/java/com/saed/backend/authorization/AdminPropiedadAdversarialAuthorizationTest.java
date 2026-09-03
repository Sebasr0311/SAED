package com.saed.backend.authorization;

import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentManagementService;
import com.saed.backend.authorization.service.AssignmentService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AdminPropiedadAdversarialAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssignmentManagementService assignmentManagementService;

    @MockBean
    private AssignmentService assignmentService;

    private final String propAdminAssignment1 = "102";
    private final String propAdminAssignment2 = "105";
    private final String foreignPropAssignment = "888888";
    private final Long userId = 2L;

    private Long idRolSuperAdmin;
    private Long idRolOrgAdmin;
    private Long idRolPropAdmin;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // 1. Roles
        idRolSuperAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'SUPERADMIN'", Long.class);
        idRolOrgAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
        idRolPropAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);

        // 2. Extra Property 999994 for Org 1
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999994 AS id, 1 AS o, 1 AS t, 'Prop Test 999994' AS n, 'Calle 101 # 10-20' AS dir, 'Bogota' AS ciu, 'Colombia' AS pai, 'MIXTA' AS ocu, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pai, s.ocu, s.st)"); } catch (Exception ignored) {}

        // 3. Tenant B (888888)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 888888 AS id, 'Org Test 888888' AS n, 'NIT888888' AS if, 'org888888@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 888888 AS id, 888888 AS o, 1 AS t, 'Prop Test 888888' AS n, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 888888 AS id, 1 AS td, 'DOC888888' AS nd, 'NATURAL' AS tp, 'admin888888@test.com' AS em, 'AdminB' AS pn, 'PropB' AS pa FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.em, s.pn, s.pa)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 888888 AS id, 888888 AS ip, 'admin888' AS nu, 'admin888888@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st)"); } catch (Exception ignored) {}

        // 4. Assignments
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 102 AS id, 2 AS u, ? AS r, 1 AS o, 1 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPropAdmin); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 105 AS id, 2 AS u, ? AS r, 1 AS o, 999994 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPropAdmin); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 888888 AS id, 888888 AS u, ? AS r, 888888 AS o, 888888 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPropAdmin); } catch (Exception ignored) {}

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        // 5. Mockito expectations for assignments
        AssignmentResponseDTO propAdmin1 = new AssignmentResponseDTO();
        propAdmin1.setIdAsignacion(102L);
        propAdmin1.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdmin1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO1 = new PropertyDTO();
        propDTO1.setId(1L);
        propDTO1.setIdOrganizacion(1L);
        propDTO1.setNombre("Edificio Residencial SAED");
        propAdmin1.setPropiedad(propDTO1);
        Mockito.when(assignmentService.validateAssignment(102L, 2L)).thenReturn(Optional.of(propAdmin1));

        AssignmentResponseDTO propAdmin2 = new AssignmentResponseDTO();
        propAdmin2.setIdAsignacion(105L);
        propAdmin2.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdmin2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO2 = new PropertyDTO();
        propDTO2.setId(999994L);
        propDTO2.setIdOrganizacion(1L);
        propDTO2.setNombre("Prop Test 999994");
        propAdmin2.setPropiedad(propDTO2);
        Mockito.when(assignmentService.validateAssignment(105L, 2L)).thenReturn(Optional.of(propAdmin2));

        AssignmentResponseDTO propAdminB = new AssignmentResponseDTO();
        propAdminB.setIdAsignacion(888888L);
        propAdminB.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdminB.setOrganizacion(new OrganizationDTO(888888L, "Org Test 888888"));
        PropertyDTO propDTOB = new PropertyDTO();
        propDTOB.setId(888888L);
        propDTOB.setIdOrganizacion(888888L);
        propDTOB.setNombre("Prop Test 888888");
        propAdminB.setPropiedad(propDTOB);
        Mockito.when(assignmentService.validateAssignment(888888L, 888888L)).thenReturn(Optional.of(propAdminB));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    // ==========================================
    // 1. ADMIN_PROPIEDAD PERMITIDO EN OPERACIÓN DE PROPIEDAD ASIGNADA (200 OK)
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar unidades de su propiedad asignada")
    public void adminProp_canAccessUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar personas de su propiedad asignada")
    public void adminProp_canAccessPersonas() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/personas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar cuotas de su propiedad asignada")
    public void adminProp_canAccessCuotas() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/cuotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar multas de su propiedad asignada")
    public void adminProp_canAccessMultas() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/multas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar quejas de su propiedad asignada")
    public void adminProp_canAccessQuejas() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/quejas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar tickets PQRS de su propiedad asignada")
    public void adminProp_canAccessPqrs() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/pqrs/todos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar pólizas de seguro de su propiedad")
    public void adminProp_canAccessSeguros() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/seguros/polizas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar cartera de su propiedad")
    public void adminProp_canAccessCartera() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/cartera")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar resumen de cartera")
    public void adminProp_canAccessCarteraResumen() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/cartera/resumen")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar porterías de su propiedad")
    public void adminProp_canAccessPorteria() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/porteria")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar paquetes de su propiedad")
    public void adminProp_canAccessPaquetes() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/paquetes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar contratos de su propiedad")
    public void adminProp_canAccessContratos() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar parqueaderos de su propiedad")
    public void adminProp_canAccessParqueaderos() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/parqueaderos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar reportes de cartera morosa de su propiedad")
    public void adminProp_canAccessReportes() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD puede consultar pista de auditoría acotada a su contexto")
    public void adminProp_canAccessAudit() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/audit")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 2. ADMIN_PROPIEDAD DENEGADO EN PLATAFORMA SAAS (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede acceder al dashboard global de SUPERADMIN")
    public void adminProp_cannotAccessPlatformDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/platform/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar planes globales de SUPERADMIN")
    public void adminProp_cannotAccessPlatformPlans() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/platform/plans")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar administradores de plataforma de SUPERADMIN")
    public void adminProp_cannotAccessPlatformAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/platform/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar membresías globales de SUPERADMIN")
    public void adminProp_cannotAccessPlatformMemberships() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/platform/memberships")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 3. ADMIN_PROPIEDAD DENEGADO EN CONSOLA ORGANIZACIONAL (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar perfil institucional de organización")
    public void adminProp_cannotAccessOrgProfile() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/org/profile")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar dashboard de organización")
    public void adminProp_cannotAccessOrgDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar suscripción ni límites de organización")
    public void adminProp_cannotAccessOrgSubscription() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/org/subscription")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar administradores de organización")
    public void adminProp_cannotAccessOrgAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/org/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar módulo de organizaciones globales")
    public void adminProp_cannotAccessOrganizations() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        mockMvc.perform(get("/api/v1/organizations")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede crear nuevas propiedades (rol de ADMIN_ORGANIZACION)")
    public void adminProp_cannotCreateProperties() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        String body = """
            {
                "nombre": "Propiedad Ilegal",
                "idTipoPropiedad": 1,
                "direccion": "Calle 1 # 2-3",
                "ciudad": "Bogotá",
                "pais": "Colombia"
            }
        """;
        mockMvc.perform(post("/api/v1/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede mutar estado de propiedades")
    public void adminProp_cannotUpdatePropertyStatus() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);
        String body = """
            {
                "estado": "INACTIVA"
            }
        """;
        mockMvc.perform(patch("/api/v1/properties/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 4. ANTI-PRIVILEGE ESCALATION & IDOR DEFENSE
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede asignar rol SUPERADMIN (Anti-escalamiento)")
    public void adminProp_cannotAssignSuperAdmin() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(userId)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("ADMIN_PROPIEDAD")
                    .roleScope("PROPIEDAD")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(userId);
            req.setIdRol(idRolSuperAdmin);
            req.setIdOrganizacion(1L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede asignar rol ADMIN_ORGANIZACION (Anti-escalamiento)")
    public void adminProp_cannotAssignAdminOrg() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(userId)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("ADMIN_PROPIEDAD")
                    .roleScope("PROPIEDAD")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(userId);
            req.setIdRol(idRolOrgAdmin);
            req.setIdOrganizacion(1L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    // ==========================================
    // 5. MULTI-PROPERTY ISOLATION & CONTEXT SWITCHING
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD con múltiples propiedades: cambio de X-Assignment-Id aisla el contexto")
    public void adminProp_multiPropertySwitching_isolatesContext() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId);

        // Access with Assignment 1 (Property A1: 1)
        mockMvc.perform(get("/api/v1/properties")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment1))
                .andExpect(status().isOk());

        // Access with Assignment 2 (Property A2: 999994)
        mockMvc.perform(get("/api/v1/properties")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede usar assignment perteneciente a otro usuario")
    public void adminProp_cannotUseForeignAssignment() throws Exception {
        String token = jwtProvider.generateIdentityToken(userId); // User 2
        // Try to send assignment 888888 belonging to User 888888
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", foreignPropAssignment))
                .andExpect(status().isForbidden());
    }
}
