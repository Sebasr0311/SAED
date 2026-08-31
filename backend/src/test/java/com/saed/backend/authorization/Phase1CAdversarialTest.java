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
    private final String orgAdminAssignment = "2000";

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    public void setupMocks() {
        // Mock SUPERADMIN assignment (User 1000)
        com.saed.backend.authorization.dto.AssignmentResponseDTO superAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        superAdmin.setIdAsignacion(1000L);
        superAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("SUPERADMIN", "GLOBAL"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(1000L, 1000L))
            .thenReturn(java.util.Optional.of(superAdmin));

        // Insert omitted, manually seeded by DBA using sqlplus to bypass RLS

        // Mock ADMIN_ORGANIZACION assignment (User 2000, Org 2000)
        com.saed.backend.authorization.dto.AssignmentResponseDTO orgAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        orgAdmin.setIdAsignacion(2000L);
        orgAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgAdmin.setOrganizacion(new com.saed.backend.authorization.dto.OrganizationDTO(2000L, "Org 2000"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(2000L, 2000L))
            .thenReturn(java.util.Optional.of(orgAdmin));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE nombre LIKE 'Test Org %'");
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
        String token = jwtProvider.generateIdentityToken(2000L);

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
                .header("X-Assignment-Id", superAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"));
    }

    @Test
    public void testContextBleed_PropertyCrossTenant() throws Exception {
        String token = jwtProvider.generateIdentityToken(2000L); // User 2000 is Org Admin for Org 2000
        
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
                    assertTrue(status == 403); // RLS physically rejects inserting out-of-tenant ID.
                });
    }
}
