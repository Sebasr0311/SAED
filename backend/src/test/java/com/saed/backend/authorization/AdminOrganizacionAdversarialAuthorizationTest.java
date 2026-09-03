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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AdminOrganizacionAdversarialAuthorizationTest {

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

    private final String orgAdminAssignment = "999993";
    private final String propAdminAssignment = "999992";
    private final String residentAssignment = "999991";
    private Long idRolProp;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // 1. Seed Tenant A (999993)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 999993 AS id, 'Org Test 999993' AS n, 'NIT999993' AS if, 'org999993@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999993 AS id, 999993 AS o, 1 AS t, 'Prop Test 999993' AS n, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.st)"); } catch (Exception ignored) {}

        // 2. Seed Tenant B (888888)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 888888 AS id, 'Org Test 888888' AS n, 'NIT888888' AS if, 'org888888@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 888888 AS id, 888888 AS o, 1 AS t, 'Prop Test 888888' AS n, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.st)"); } catch (Exception ignored) {}

        // 3. Seed Users & Personas
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999993, 1, 'DOC999993', 'NATURAL', 'admin2@test.com', 'N2', 'A2')"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999993, 999993, 'orgadmin', 'admin2@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999992, 1, 'DOC999992', 'NATURAL', 'propadmin@test.com', 'N3', 'A3')"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999992, 999992, 'propadmin', 'propadmin@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999991, 1, 'DOC999991', 'NATURAL', 'resident@test.com', 'N4', 'A4')"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999991, 999991, 'resident', 'resident@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (888888, 1, 'DOC888888', 'NATURAL', 'admin888888@test.com', 'N8', 'A8')"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (888888, 888888, 'admin888', 'admin888888@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')"); } catch (Exception ignored) {}

        // 4. Seed Assignments
        Long idRolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
        idRolProp = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
        Long idRolRes = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);

        try { jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (999993, 999993, ?, 999993, 'ACTIVA')", idRolOrg); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (999992, 999992, ?, 999993, 999993, 'ACTIVA')", idRolProp); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (999991, 999991, ?, 999993, 999993, 1, 'ACTIVA')", idRolRes); } catch (Exception ignored) {}
        try { jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (888888, 888888, ?, 888888, 888888, 'ACTIVA')", idRolProp); } catch (Exception ignored) {}

        // 5. Seed Membership (Plan 1 has limite_propiedades = 1)
        try { jdbcTemplate.update("MERGE INTO MEMBRESIAS m USING (SELECT 999993 AS id, 999993 AS o, 1 AS p, 'ACTIVA' AS st, TRUNC(SYSDATE) AS fi, TRUNC(SYSDATE)+365 AS ff, 'N' AS ep FROM DUAL) s ON (m.ID_MEMBRESIA = s.id) WHEN NOT MATCHED THEN INSERT (ID_MEMBRESIA, ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) VALUES (s.id, s.o, s.p, s.st, s.fi, s.ff, s.ep)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO MEMBRESIAS m USING (SELECT 888888 AS id, 888888 AS o, 1 AS p, 'ACTIVA' AS st, TRUNC(SYSDATE) AS fi, TRUNC(SYSDATE)+365 AS ff, 'N' AS ep FROM DUAL) s ON (m.ID_MEMBRESIA = s.id) WHEN NOT MATCHED THEN INSERT (ID_MEMBRESIA, ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) VALUES (s.id, s.o, s.p, s.st, s.fi, s.ff, s.ep)"); } catch (Exception ignored) {}

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        // 6. Setup Mockito expectations
        AssignmentResponseDTO orgAdmin = new AssignmentResponseDTO();
        orgAdmin.setIdAsignacion(999993L);
        orgAdmin.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgAdmin.setOrganizacion(new OrganizationDTO(999993L, "Org Test 999993"));
        Mockito.when(assignmentService.validateAssignment(999993L, 999993L)).thenReturn(Optional.of(orgAdmin));

        AssignmentResponseDTO propAdmin = new AssignmentResponseDTO();
        propAdmin.setIdAsignacion(999992L);
        propAdmin.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdmin.setOrganizacion(new OrganizationDTO(999993L, "Org Test 999993"));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(999993L);
        propDTO.setIdOrganizacion(999993L);
        propDTO.setNombre("Prop Test 999993");
        propAdmin.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(999992L, 999992L)).thenReturn(Optional.of(propAdmin));

        AssignmentResponseDTO resident = new AssignmentResponseDTO();
        resident.setIdAsignacion(999991L);
        resident.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resident.setOrganizacion(new OrganizationDTO(999993L, "Org Test 999993"));
        resident.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(999991L, 999991L)).thenReturn(Optional.of(resident));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    // ==========================================
    // 1. ADMIN_ORGANIZACION PERMITIDO EN /api/v1/org/* Y /properties
    // ==========================================

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar perfil institucional de su organización")
    public void adminOrg_canAccessOrgProfile() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/org/profile")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idOrganizacion").value(999993L));
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar dashboard de su organización")
    public void adminOrg_canAccessOrgDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar suscripción y límites de su organización")
    public void adminOrg_canAccessOrgSubscription() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/org/subscription")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCodigo").exists());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar administradores de su organización")
    public void adminOrg_canAccessOrgAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/org/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar propiedades de su organización")
    public void adminOrg_canAccessProperties() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/properties")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION puede consultar pista de auditoría organizacional")
    public void adminOrg_canAccessAudit() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/audit")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 2. ADMIN_ORGANIZACION DENEGADO EN PLATAFORMA SAAS (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede acceder al dashboard global de SUPERADMIN")
    public void adminOrg_cannotAccessPlatformDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/platform/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar planes globales de SUPERADMIN")
    public void adminOrg_cannotAccessPlatformPlans() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/platform/plans")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar operadores de plataforma de SUPERADMIN")
    public void adminOrg_cannotAccessPlatformAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/platform/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar membresías globales de SUPERADMIN")
    public void adminOrg_cannotAccessPlatformMemberships() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/platform/memberships")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 3. ADMIN_ORGANIZACION DENEGADO EN OPERACIONES RESIDENCIALES/PORTERIA (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar multas operativas de copropiedad")
    public void adminOrg_cannotAccessMultas() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/multas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar quejas operativas de copropiedad")
    public void adminOrg_cannotAccessQuejas() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/quejas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar tickets PQRS operativos")
    public void adminOrg_cannotAccessPqrsTickets() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/pqrs/todos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede consultar visitas operativas de portería")
    public void adminOrg_cannotAccessPorteriaVisitas() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/porteria/visitas/historial")
                        .param("fechaInicio", "2026-01-01")
                        .param("fechaFin", "2026-12-31")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 4. OTROS ROLES DENEGADOS EN CONSOLA ORGANIZACIONAL (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar perfil institucional de organización")
    public void adminPropiedad_cannotAccessOrgProfile() throws Exception {
        String token = jwtProvider.generateIdentityToken(999992L);
        mockMvc.perform(get("/api/v1/org/profile")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar dashboard de organización")
    public void adminPropiedad_cannotAccessOrgDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(999992L);
        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN_PROPIEDAD no puede consultar administradores de organización")
    public void adminPropiedad_cannotAccessOrgAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(999992L);
        mockMvc.perform(get("/api/v1/org/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", propAdminAssignment))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESIDENTE no puede consultar consola de organización")
    public void residente_cannotAccessOrgConsole() throws Exception {
        String token = jwtProvider.generateIdentityToken(999991L);
        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", residentAssignment))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 5. ANTI-PRIVILEGE ESCALATION Y TENANT ISOLATION
    // ==========================================

    @Test
    @DisplayName("ADMIN_ORGANIZACION no puede asignar rol SUPERADMIN (Anti-escalamiento)")
    public void adminOrg_cannotAssignSuperAdminRole() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(999993L)
                    .organizationId(999993L)
                    .roleCode("ADMIN_ORGANIZACION")
                    .roleScope("ORGANIZACION")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(999993L);
            req.setIdRol(1L); // 1 = SUPERADMIN (Alcance GLOBAL)
            req.setIdOrganizacion(999993L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("BD-02: ADMIN_ORGANIZACION no puede asignar un ADMIN_PROPIEDAD a una propiedad de otra organización")
    public void adminOrg_cannotAssignAdminToExternalProperty() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(999993L)
                    .organizationId(999993L)
                    .roleCode("ADMIN_ORGANIZACION")
                    .roleScope("ORGANIZACION")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(999993L);
            req.setIdRol(idRolProp);
            req.setIdOrganizacion(999993L);
            req.setIdPropiedad(888888L); // Propiedad perteneciente a Org 888888

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("BD-01: Exceder límite de propiedades contratadas devuelve HTTP 409 Conflict")
    public void adminOrg_planLimitExceeded_returns409() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        String body = """
            {
                "nombre": "Propiedad Excedente",
                "idTipoPropiedad": 1,
                "direccion": "Calle 1 # 2-3",
                "ciudad": "Bogotá",
                "pais": "Colombia",
                "tipoOcupacionPredominante": "RESIDENCIAL"
            }
        """;

        mockMvc.perform(post("/api/v1/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("TENANT ISOLATION: ADMIN_ORGANIZACION no puede ver administradores de otro tenant")
    public void adminOrg_cannotSeeOtherOrgAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        mockMvc.perform(get("/api/v1/org/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.nombreUsuario == 'admin888')]").doesNotExist());
    }

    @Test
    @DisplayName("TENANT ISOLATION: Modificación de perfil institucional solo afecta la organización autenticada")
    public void adminOrg_profileUpdateIsLockedToContextOrg() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);
        String body = """
            {
                "emailContacto": "nuevo_email_org999993@test.com",
                "telefonoContacto": "3100000000",
                "direccion": "Avenida 100",
                "ciudad": "Medellín",
                "pais": "Colombia"
            }
        """;

        mockMvc.perform(put("/api/v1/org/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment))
                .andExpect(status().isOk());

        // Verify Org A is updated and Org B is untouched
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            String emailOrgA = jdbcTemplate.queryForObject("SELECT EMAIL_CONTACTO FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 999993", String.class);
            String emailOrgB = jdbcTemplate.queryForObject("SELECT EMAIL_CONTACTO FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 888888", String.class);

            assertEquals("nuevo_email_org999993@test.com", emailOrgA);
            assertEquals("org888888@test.com", emailOrgB);
        } finally {
            SaedContextHolder.clearContext();
        }
    }
}
