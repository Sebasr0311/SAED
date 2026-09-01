package com.saed.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
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
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        try { jdbcTemplate.update("DELETE FROM ADMINISTRADORES_SAED WHERE ID_USUARIO IN (1000, 2000)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO IN (1000, 2000)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO IN (1000, 2000)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA IN (1000, 2000)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 2000"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (1000, 1, 'DOC1000', 'NATURAL', 'admin1@test.com', 'N1', 'A1')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (1000, 1000, 'sysadmin', 'admin1@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO) VALUES (1000, 1000, 'SUPERADMIN', 'ACTIVO')"); } catch (Exception e) {}
        try {
            Long idRolGlobal = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_GLOBAL'", Long.class);
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ESTADO) VALUES (1000, 1000, ?, 'ACTIVA')", idRolGlobal);
        } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (2000, 1, 'DOC2000', 'NATURAL', 'admin2@test.com', 'N2', 'A2')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (2000, 2000, 'orgadmin', 'admin2@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL) VALUES (2000, 'Org 2000', 'NIT2000')"); } catch (Exception e) {}
        try {
            Long idRolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (2000, 2000, ?, 2000, 'ACTIVA')", idRolOrg);
        } catch (Exception e) {}

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

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
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
                    assertTrue(status == 403 || status == 500);
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
                    assertTrue(status == 403 || status == 500);
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
