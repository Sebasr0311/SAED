package com.saed.backend.identity;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.RoleDTO;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-02 Security Tests: PORTERO must be denied password change at the HTTP layer.
 *
 * TC-01 PORTERO              -> 403 FORBIDDEN
 * TC-02 RESIDENTE            -> not 403 (passes auth layer)
 * TC-03 ADMIN_PROPIEDAD      -> not 403
 * TC-04 ADMIN_ORGANIZACION   -> not 403
 * TC-05 SUPERADMIN           -> not 403
 * TC-06 No token             -> not 2xx
 * TC-07 PORTERO no header    -> not 2xx
 * TC-08 PORTERO cross-scope  -> 403
 * TC-09 RESIDENTE_CONVIVENCIA -> not 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PorteroPasswordChangeSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean  private AssignmentService assignmentService;

    private static final long PORTERO_USER_ID       = 888801L;
    private static final long RESIDENTE_USER_ID     = 888802L;
    private static final long ADMIN_PROP_USER_ID    = 888803L;
    private static final long ADMIN_ORG_USER_ID     = 888804L;
    private static final long SUPERADMIN_USER_ID    = 888805L;
    private static final long CONVIVIENTE_USER_ID   = 888806L;
    private static final long PORTERO_ASSIGN_ID     = 888801L;
    private static final long RESIDENTE_ASSIGN_ID   = 888802L;
    private static final long ADMIN_PROP_ASSIGN_ID  = 888803L;
    private static final long ADMIN_ORG_ASSIGN_ID   = 888804L;
    private static final long SUPERADMIN_ASSIGN_ID  = 888805L;
    private static final long CONVIVIENTE_ASSIGN_ID = 888806L;
    private static final long ORG_ID               = 888801L;

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L)
                .propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        seedUser(PORTERO_USER_ID,     "portero_p202",     "portero_p202@test.com");
        seedUser(RESIDENTE_USER_ID,   "residente_p202",   "residente_p202@test.com");
        seedUser(ADMIN_PROP_USER_ID,  "adminprop_p202",   "adminprop_p202@test.com");
        seedUser(ADMIN_ORG_USER_ID,   "adminorg_p202",    "adminorg_p202@test.com");
        seedUser(SUPERADMIN_USER_ID,  "superadmin_p202",  "superadmin_p202@test.com");
        seedUser(CONVIVIENTE_USER_ID, "conviviente_p202", "conviviente_p202@test.com");

        OrganizationDTO org = new OrganizationDTO(ORG_ID, "Org P2-02 Test");

        Mockito.when(assignmentService.validateAssignment(PORTERO_ASSIGN_ID,     PORTERO_USER_ID))    .thenReturn(Optional.of(buildAssignment(PORTERO_ASSIGN_ID,     "PORTERO",               "PORTERIA",     org)));
        Mockito.when(assignmentService.validateAssignment(RESIDENTE_ASSIGN_ID,   RESIDENTE_USER_ID))  .thenReturn(Optional.of(buildAssignment(RESIDENTE_ASSIGN_ID,   "RESIDENTE",             "UNIDAD",       org)));
        Mockito.when(assignmentService.validateAssignment(ADMIN_PROP_ASSIGN_ID,  ADMIN_PROP_USER_ID)) .thenReturn(Optional.of(buildAssignment(ADMIN_PROP_ASSIGN_ID,  "ADMIN_PROPIEDAD",       "PROPIEDAD",    org)));
        Mockito.when(assignmentService.validateAssignment(ADMIN_ORG_ASSIGN_ID,   ADMIN_ORG_USER_ID))  .thenReturn(Optional.of(buildAssignment(ADMIN_ORG_ASSIGN_ID,   "ADMIN_ORGANIZACION",    "ORGANIZACION", org)));
        Mockito.when(assignmentService.validateAssignment(SUPERADMIN_ASSIGN_ID,  SUPERADMIN_USER_ID)) .thenReturn(Optional.of(buildAssignment(SUPERADMIN_ASSIGN_ID,  "SUPERADMIN",            "GLOBAL",       org)));
        Mockito.when(assignmentService.validateAssignment(CONVIVIENTE_ASSIGN_ID, CONVIVIENTE_USER_ID)).thenReturn(Optional.of(buildAssignment(CONVIVIENTE_ASSIGN_ID, "RESIDENTE_CONVIVENCIA", "UNIDAD",       org)));
        Mockito.when(assignmentService.validateAssignment(RESIDENTE_ASSIGN_ID, PORTERO_USER_ID)).thenReturn(Optional.empty());

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void cleanup() {
        try { jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;"); } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-01: PORTERO forbidden from changing password -- 403")
    public void tc01_portero_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(PORTERO_USER_ID))
                .header("X-Assignment-Id", String.valueOf(PORTERO_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-02: RESIDENTE passes authorization layer -- not 403")
    public void tc02_residente_passesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(RESIDENTE_USER_ID))
                .header("X-Assignment-Id", String.valueOf(RESIDENTE_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @DisplayName("TC-03: ADMIN_PROPIEDAD passes authorization layer -- not 403")
    public void tc03_adminPropiedad_passesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(ADMIN_PROP_USER_ID))
                .header("X-Assignment-Id", String.valueOf(ADMIN_PROP_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @DisplayName("TC-04: ADMIN_ORGANIZACION passes authorization layer -- not 403")
    public void tc04_adminOrg_passesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(ADMIN_ORG_USER_ID))
                .header("X-Assignment-Id", String.valueOf(ADMIN_ORG_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @DisplayName("TC-05: SUPERADMIN passes authorization layer -- not 403")
    public void tc05_superadmin_passesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(SUPERADMIN_USER_ID))
                .header("X-Assignment-Id", String.valueOf(SUPERADMIN_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @DisplayName("TC-06: No token -- request is rejected (not 2xx)")
    public void tc06_noToken_rejected() throws Exception {
        int s = mockMvc.perform(post("/api/v1/me/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(200, s);
        assertNotEquals(201, s);
    }

    @Test
    @DisplayName("TC-07: PORTERO without X-Assignment-Id header -- not 2xx")
    public void tc07_portero_noHeader_notSuccessful() throws Exception {
        int s = mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(PORTERO_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(200, s, "PORTERO must never get 200 on change-password");
        assertNotEquals(201, s, "PORTERO must never get 201 on change-password");
    }

    @Test
    @DisplayName("TC-08: PORTERO using RESIDENTE assignment ID is rejected -- 403")
    public void tc08_portero_crossScope_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(PORTERO_USER_ID))
                .header("X-Assignment-Id", String.valueOf(RESIDENTE_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-09: RESIDENTE_CONVIVENCIA passes authorization layer -- not 403")
    public void tc09_conviviente_passesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID))
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"cualquier\",\"nuevaPassword\":\"nueva123\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    // Helpers

    private void seedUser(long userId, String username, String email) {
        try {
            jdbcTemplate.update(
                "MERGE INTO PERSONAS p USING (SELECT ? AS id, 1 AS td, ? AS doc, 'NATURAL' AS tp, ? AS em, 'Test' AS n, 'P202' AS a FROM DUAL) " +
                "s ON (p.ID_PERSONA = s.id) " +
                "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) " +
                "VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)",
                userId, "DOC" + userId, email);
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.update(
                "MERGE INTO USUARIOS u USING (SELECT ? AS id, ? AS p, ? AS u, ? AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) " +
                "s ON (u.ID_USUARIO = s.id) " +
                "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                "VALUES (s.id, s.p, s.u, s.em, s.h, s.st)",
                userId, userId, username, email);
        } catch (Exception ignored) {}
    }

    private AssignmentResponseDTO buildAssignment(long assignId, String roleCode, String roleScope, OrganizationDTO org) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(assignId);
        dto.setRol(new RoleDTO(roleCode, roleScope));
        dto.setOrganizacion(org);
        return dto;
    }
}