package com.saed.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1DPersonIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.saed.backend.authorization.service.AssignmentService assignmentService;

    private final String superAdminAssignment = "1000";
    private final String orgAdminAssignment = "2000";

    @BeforeEach
    public void setupMocks() {
        // Mock SUPERADMIN
        com.saed.backend.authorization.dto.AssignmentResponseDTO superAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        superAdmin.setIdAsignacion(1000L);
        superAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("SUPERADMIN", "GLOBAL"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(1000L, 1000L))
            .thenReturn(java.util.Optional.of(superAdmin));

        // Mock ORG_ADMIN
        com.saed.backend.authorization.dto.AssignmentResponseDTO orgAdmin = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        orgAdmin.setIdAsignacion(2000L);
        orgAdmin.setRol(new com.saed.backend.authorization.dto.RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgAdmin.setOrganizacion(new com.saed.backend.authorization.dto.OrganizationDTO(2000L, "Org 2000"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(2000L, 2000L))
            .thenReturn(java.util.Optional.of(orgAdmin));
    }

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void testCreatePersona_Global_Success() throws Exception {
        String token = jwtProvider.generateIdentityToken(1000L);

        PersonaRequestDTO request = new PersonaRequestDTO(
                1L,
                "DOC-" + UUID.randomUUID().toString().substring(0, 8),
                "NATURAL",
                "Juan",
                "Carlos",
                "Perez",
                "Gomez",
                "juan" + UUID.randomUUID().toString().substring(0, 5) + "@test.com",
                "555123456"
        );

        mockMvc.perform(post("/api/v1/personas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", superAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCreateOwner_InvalidUnit_FailsWithDatabaseErrorOr403() throws Exception {
        String token = jwtProvider.generateIdentityToken(2000L);

        UnitOwnerRequestDTO request = new UnitOwnerRequestDTO(
                1L, // Some persona ID
                new BigDecimal("50.00"),
                "S"
        );

        // Attempt to insert owner into unit 99999 (doesn't exist or not owned by org 2000)
        mockMvc.perform(post("/api/v1/units/99999/owners")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 403 || status == 500);
                });
    }

    @Test
    public void testCreateResident_InvalidUnit_FailsWithDatabaseErrorOr403() throws Exception {
        String token = jwtProvider.generateIdentityToken(2000L);

        UnitResidentRequestDTO request = new UnitResidentRequestDTO(
                1L, // Some persona ID
                "ARRENDATARIO"
        );

        mockMvc.perform(post("/api/v1/units/99999/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", orgAdminAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 403 || status == 500);
                });
    }

    @Test
    public void testGetPersonas_Success() throws Exception {
        String token = jwtProvider.generateIdentityToken(1000L);

        mockMvc.perform(get("/api/v1/personas?page=0&size=5")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", superAdminAssignment))
                .andExpect(status().isOk());
    }
}
