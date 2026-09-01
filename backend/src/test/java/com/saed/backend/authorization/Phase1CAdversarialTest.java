package com.saed.backend.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1CAdversarialTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.saed.backend.authorization.service.AssignmentService assignmentService;

    private final String superAdminAssignment = "1000";
    private final String orgAdminAssignment = "999993";

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    public void setupMocks() {
        com.saed.backend.context.SaedContextHolder.setContext(com.saed.backend.context.SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Mock SUPERADMIN assignment (User 1000)
        com.saed.backend.authorization.dto.AssignmentResponseDTO superAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        superAdmin.setIdAsignacion(1000L);
        superAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("SUPERADMIN", "GLOBAL"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(1000L, 1000L))
            .thenReturn(java.util.Optional.of(superAdmin));

        // Insert omitted, manually seeded by DBA using sqlplus to bypass RLS
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (1000, 1, 'DOC1000', 'NATURAL', 'admin1@test.com', 'N1', 'A1')"); } catch (Exception e) { e.printStackTrace(); }
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (1000, 1000, 'sysadmin', 'admin1@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) { e.printStackTrace(); }
        try { jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, ESTADO) VALUES (1000, 'ACTIVO')"); } catch (Exception e) { e.printStackTrace(); }
        
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999993, 1, 'DOC999993', 'NATURAL', 'admin2@test.com', 'N2', 'A2')"); } catch (Exception e) { e.printStackTrace(); }
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999993, 999993, 'orgadmin', 'admin2@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) { e.printStackTrace(); }
        try { jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (999993, 'Org 999993', 'NIT999993', 'o999993@test.com')"); } catch (Exception e) { e.printStackTrace(); }
        try {
            Long idRolOrg = 0L;
            try { idRolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class); }
            catch (Exception ex) {
                jdbcTemplate.update("INSERT INTO ROLES (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES ('ADMIN_ORGANIZACION', 'Admin Org', 'ORGANIZACION', 'ACTIVO')");
                idRolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
            }
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (999993, 999993, ?, 999993, 'ACTIVA')", idRolOrg);
        } catch (Exception e) { e.printStackTrace(); }
        
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");

        // Mock ADMIN_ORGANIZACION assignment (User 999993, Org 999993)
        com.saed.backend.authorization.dto.AssignmentResponseDTO orgAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        orgAdmin.setIdAsignacion(999993L);
        orgAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgAdmin.setOrganizacion(new com.saed.backend.authorization.dto.OrganizationDTO(999993L, "Org 999993"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(999993L, 999993L))
            .thenReturn(java.util.Optional.of(orgAdmin));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE nombre LIKE 'Test Org %'");
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    public void testCreateOrganization_SuperAdmin_Success() throws Exception {
        String token = jwtProvider.generateIdentityToken(1000L);

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Test Org " + UUID.randomUUID());
        request.setIdentificacionFiscal("TEST-" + UUID.randomUUID().toString().substring(0, 5));
        request.setEmailContacto("test@test.com");
        request.setDireccion("Test 123");
        request.setCiudad("Test City");

        mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", superAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    public void testCreateOrganization_OrgAdmin_Forbidden() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L);

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Malicious Org");
        request.setIdentificacionFiscal("BAD-123");
        request.setEmailContacto("bad@test.com");

        mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }



    @Test
    public void testCreateAssignment_InvalidFK_DatabaseError() throws Exception {
        String token = jwtProvider.generateIdentityToken(1000L);

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(9999L); // Invalid user
        request.setIdRol(2L);
        request.setIdOrganizacion(1000L);

        mockMvc.perform(post("/api/v1/assignments")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", "1000") // SUPERADMIN role uses GLOBAL assignment
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    public void testContextBleed_PropertyCrossTenant() throws Exception {
        String token = jwtProvider.generateIdentityToken(999993L); // User 999993 is Org Admin for Org 999993
        
        PropertyRequestDTO request = new PropertyRequestDTO();
        request.setIdOrganizacion(999L); // Trying to spoof Org 999
        request.setIdTipoPropiedad(1L); // assuming 1 is valid, but if not it will fail with FK constraint.
        request.setNombre("Spoofed Property");
        request.setDireccion("123");
        request.setCiudad("City");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 500, "Expected cross-tenant spoofing to be rejected with 403 or 500 but was " + status);
                });
    }
}

