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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
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

    private static final Long ORG_ADMIN_USER_ID = 888003L;
    private static final Long ORG_ADMIN_ASIGNACION_ID = 888103L;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        // Test-owned fixture: plain inserts guarded by NOT EXISTS, no swallowed errors, no schema changes.
        Long rolOrg = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);

        jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) "
                + "SELECT 88801, 'Adv Org 1', 'NIT-88801', 'org1@adv.com' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 88801)");

        // The mocked assignment carries property 88811; the inactive-property guard treats a property that does not
        // exist as inactive (403 PROPERTY_INACTIVE), so a fresh database needs the row.
        jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) "
                + "SELECT 88811, 88801, 1, 'Edificio Adv 1', 'Calle Adv 1', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM PROPIEDADES WHERE ID_PROPIEDAD = 88811)");
        jdbcTemplate.update("UPDATE PROPIEDADES SET ESTADO = 'ACTIVA' WHERE ID_PROPIEDAD = 88811 AND ESTADO <> 'ACTIVA'");

        jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) "
                + "SELECT 888003, 1, 'DOC-888003', 'NATURAL', 'orgadmin@adv.com', 'Carlos', 'AdminOrg' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 888003)");

        jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                + "SELECT 888003, 888003, 'orgadmin_adv', 'orgadmin@adv.com', 'hash', 'ACTIVO' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM USUARIOS WHERE ID_USUARIO = 888003)");
        jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'ACTIVO' WHERE ID_USUARIO = 888003 AND ESTADO <> 'ACTIVO'");

        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) "
                + "SELECT 888103, 888003, ?, 88801, 'ACTIVA' FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = 888103)", rolOrg);
        jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = 888103 AND ESTADO <> 'ACTIVA'");

        // Mock ORG_ADMIN with Property
        com.saed.backend.authorization.dto.AssignmentResponseDTO orgDto = new com.saed.backend.authorization.dto.AssignmentResponseDTO();
        orgDto.setIdAsignacion(ORG_ADMIN_ASIGNACION_ID);
        orgDto.setRol(new com.saed.backend.authorization.dto.RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        orgDto.setOrganizacion(new com.saed.backend.authorization.dto.OrganizationDTO(88801L, "Adv Org 1"));
        orgDto.setPropiedad(new com.saed.backend.authorization.dto.PropertyDTO(88811L, "Edificio Adv 1"));
        org.mockito.Mockito.when(assignmentService.validateAssignment(ORG_ADMIN_ASIGNACION_ID, ORG_ADMIN_USER_ID))
                .thenReturn(java.util.Optional.of(orgDto));

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    public void testCreatePersona_OrgAdmin_Success() throws Exception {
        String token = jwtProvider.generateIdentityToken(ORG_ADMIN_USER_ID);

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
                .header("X-Assignment-Id", String.valueOf(ORG_ADMIN_ASIGNACION_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCreateOwner_InvalidUnit_Returns404() throws Exception {
        String token = jwtProvider.generateIdentityToken(ORG_ADMIN_USER_ID);

        UnitOwnerRequestDTO request = new UnitOwnerRequestDTO(
                1L,
                new BigDecimal("50.00"),
                "S"
        );

        mockMvc.perform(post("/api/v1/units/99999/owners")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ORG_ADMIN_ASIGNACION_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    // DEC-01 non-disclosure: an unknown or foreign unit answers 404 "Unidad no encontrada" (never a DB error).
                    assertEquals(404, result.getResponse().getStatus());
                });
    }

    @Test
    public void testCreateResident_InvalidUnit_Returns404() throws Exception {
        String token = jwtProvider.generateIdentityToken(ORG_ADMIN_USER_ID);

        UnitResidentRequestDTO request = new UnitResidentRequestDTO(
                1L,
                "ARRENDATARIO"
        );

        mockMvc.perform(post("/api/v1/units/99999/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ORG_ADMIN_ASIGNACION_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    // DEC-01 non-disclosure: an unknown or foreign unit answers 404 "Unidad no encontrada" (never a DB error).
                    assertEquals(404, result.getResponse().getStatus());
                });
    }

    @Test
    public void testGetPersonas_Success() throws Exception {
        String token = jwtProvider.generateIdentityToken(ORG_ADMIN_USER_ID);

        mockMvc.perform(get("/api/v1/personas?page=0&size=5")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ORG_ADMIN_ASIGNACION_ID)))
                .andExpect(status().isOk());
    }
}
