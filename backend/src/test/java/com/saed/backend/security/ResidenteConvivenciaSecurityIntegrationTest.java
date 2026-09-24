package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.repository.AssignmentRepository;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.ConvivienteQuotaService;
import com.saed.backend.person.service.impl.UnitInhabitantServiceImpl;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.service.PaquetesService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Pruebas de Seguridad y Autorización para RESIDENTE_CONVIVENCIA (Fase 5).
 * Valida de forma rigurosa y adversarial:
 * 1. Rechazo a escalamiento de privilegios a roles administrativos.
 * 2. Bloqueo al historial general de visitas de la unidad (exclusivo para titular).
 * 3. Aislamiento estricto de unidades cruzadas (cross-unit rejection).
 * 4. Acceso permitido a buzón propio, quejas y cambio de contraseña.
 * 5. Aplicación estricta de cupo pesimista únicamente a CONVIVIENTE (excluyendo FAMILIAR y OTRO).
 * 6. Preservación del rol PORTERO cuando el usuario también es habitante físico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ResidenteConvivenciaSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private PaquetesService paquetesService;

    @MockBean
    private AssignmentService assignmentService;

    // Usuarios y asignaciones alineados con el seed canónico en Oracle
    private static final long CONVIVIENTE_USER_ID   = 6L;
    private static final long CONVIVIENTE_ASSIGN_ID = 206L;
    private static final long TITULAR_USER_ID       = 4L;
    private static final long TITULAR_ASSIGN_ID     = 104L;
    private static final long PORTERO_USER_ID       = 3L;
    private static final long PORTERO_ASSIGN_ID     = 103L;
    private static final long TEST_ORG_ID           = 1L;
    private static final long TEST_PROP_ID          = 1L;
    private static final long TEST_UNIT_ID          = 1L;

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Asegurar rol RESIDENTE_CONVIVENCIA en ROLES
        try {
            jdbcTemplate.execute("MERGE INTO ROLES r USING (SELECT 'RESIDENTE_CONVIVENCIA' AS CODIGO, 'Residente Conviviente' AS NOMBRE, 'UNIDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL) s ON (r.CODIGO = s.CODIGO) WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO)");
        } catch (Exception ignored) {}

        Long idRolConviviente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA'", Long.class);
        Long idRolResidente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);
        Long idRolPortero = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);

        // Asegurar que las asignaciones canónicas existen en base de datos real para PKG_AUTH_BOOTSTRAP
        try {
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 206 AS id, 6 AS u, ? AS r, 1 AS o, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolConviviente);
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'ACTIVA', FECHA_FIN = NULL WHERE ID_ASIGNACION = 104");
        } catch (Exception ignored) {}

        // Asegurar contraseña conocida para pruebas de cambio de password
        String hash = passwordEncoder.encode("Password123!");
        try {
            jdbcTemplate.update("UPDATE USUARIOS SET HASH_PASSWORD = ?, INTENTOS_FALLIDOS = 0, ESTADO = 'ACTIVO' WHERE ID_USUARIO = ?", hash, CONVIVIENTE_USER_ID);
        } catch (Exception ignored) {}

        OrganizationDTO org = new OrganizationDTO(TEST_ORG_ID, "Org Test");
        PropertyDTO prop = new PropertyDTO(TEST_PROP_ID, "Propiedad Test");
        UnitDTO unit = new UnitDTO(TEST_UNIT_ID, "Apt 101");

        AssignmentResponseDTO convAssign = new AssignmentResponseDTO();
        convAssign.setIdAsignacion(CONVIVIENTE_ASSIGN_ID);
        convAssign.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        convAssign.setOrganizacion(org);
        convAssign.setPropiedad(prop);
        convAssign.setUnidad(unit);

        AssignmentResponseDTO titAssign = new AssignmentResponseDTO();
        titAssign.setIdAsignacion(TITULAR_ASSIGN_ID);
        titAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        titAssign.setOrganizacion(org);
        titAssign.setPropiedad(prop);
        titAssign.setUnidad(unit);

        AssignmentResponseDTO portAssign = new AssignmentResponseDTO();
        portAssign.setIdAsignacion(PORTERO_ASSIGN_ID);
        portAssign.setRol(new RoleDTO("PORTERO", "PORTERIA"));
        portAssign.setOrganizacion(org);
        portAssign.setPropiedad(prop);

        when(assignmentService.validateAssignment(CONVIVIENTE_ASSIGN_ID, CONVIVIENTE_USER_ID))
                .thenReturn(Optional.of(convAssign));
        when(assignmentService.validateAssignment(TITULAR_ASSIGN_ID, TITULAR_USER_ID))
                .thenReturn(Optional.of(titAssign));
        when(assignmentService.validateAssignment(PORTERO_ASSIGN_ID, PORTERO_USER_ID))
                .thenReturn(Optional.of(portAssign));

        try { jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;"); } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void cleanup() {
        try { jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;"); } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @Test
    @DisplayName("SEC-F5-01: RESIDENTE_CONVIVENCIA no puede crear unidades (403 Forbidden)")
    public void testResidenteConvivenciaCannotEscalateToAdminUnitCreation() throws Exception {
        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        mockMvc.perform(post("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPropiedad\":1,\"idTipoUnidad\":1,\"identificador\":\"Apto 999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F5-02: RESIDENTE_CONVIVENCIA tiene denegado el historial general de visitas (exclusivo titular)")
    public void testResidenteConvivenciaCannotAccessUnitVisitHistory() throws Exception {
        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        mockMvc.perform(get("/api/v1/residentes/" + CONVIVIENTE_USER_ID + "/visitas-historial")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F5-03: RESIDENTE_CONVIVENCIA tiene denegado consultar otra unidad diferente a la suya (403 Forbidden)")
    public void testResidenteConvivenciaCrossUnitRejection() throws Exception {
        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        mockMvc.perform(get("/api/v1/units/999999")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F5-04: RESIDENTE_CONVIVENCIA puede acceder legítimamente a su buzón y quejas")
    public void testResidenteConvivenciaCanAccessOwnMailboxAndQuejas() throws Exception {
        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        mockMvc.perform(get("/api/v1/buzon")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/quejas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-F5-05: RESIDENTE_CONVIVENCIA puede cambiar su propia contraseña vía /api/v1/me/change-password")
    public void testResidenteConvivenciaCanChangeOwnPassword() throws Exception {
        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        mockMvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"Password123!\",\"nuevaPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-F5-06: El cupo pesimista se aplica EXCLUSIVAMENTE a tipo CONVIVIENTE (no a FAMILIAR ni OTRO)")
    public void testQuotaEnforcedExclusivelyOnConvivienteNotFamiliar() {
        UnitInhabitantRepository mockRepo = mock(UnitInhabitantRepository.class);
        ConvivienteQuotaService mockQuotaService = mock(ConvivienteQuotaService.class);
        UnitInhabitantServiceImpl service = new UnitInhabitantServiceImpl(mockRepo, mockQuotaService);

        Long unitId = 100L;

        // 1. Registro con tipo FAMILIAR -> NO debe llamar a validateAndLockQuota
        UnitResidentRequestDTO reqFamiliar = new UnitResidentRequestDTO(1L, "FAMILIAR");
        service.addResident(unitId, reqFamiliar);
        verify(mockQuotaService, never()).validateAndLockQuota(unitId);

        // 2. Registro con tipo OTRO -> NO debe llamar a validateAndLockQuota
        UnitResidentRequestDTO reqOtro = new UnitResidentRequestDTO(2L, "OTRO");
        service.addResident(unitId, reqOtro);
        verify(mockQuotaService, never()).validateAndLockQuota(unitId);

        // 3. Registro con tipo CONVIVIENTE -> SÍ DEBE llamar a validateAndLockQuota
        UnitResidentRequestDTO reqConviviente = new UnitResidentRequestDTO(3L, "CONVIVIENTE");
        service.addResident(unitId, reqConviviente);
        verify(mockQuotaService, times(1)).validateAndLockQuota(unitId);
    }

    @Test
    @DisplayName("SEC-F5-07: RESIDENTE_CONVIVENCIA puede consultar paquetes de su unidad pero se bloquea acceso a paquetes de otra unidad (403 Forbidden)")
    public void testResidenteConvivenciaPackageAccessAndCrossUnitBlocked() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(1L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 1, 'PORTERO'); END;");
        } catch (Exception ignored) {}

        PaqueteDTO p1 = paquetesService.registrarPaquete(new PaqueteRequestDTO(
                1L, 4L, "Servientrega", "TRK-F5-U1", "Paquete Unidad 1", "MEDIANO", null, 1L));

        PaqueteDTO p2 = paquetesService.registrarPaquete(new PaqueteRequestDTO(
                2L, 4L, "Servientrega", "TRK-F5-U2", "Paquete Unidad 2", "MEDIANO", null, 1L));

        String token = jwtProvider.generateIdentityToken(CONVIVIENTE_USER_ID);

        // 1. Acceso a lista de paquetes propios -> 200 OK
        mockMvc.perform(get("/api/v1/paquetes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isOk());

        // 2. Acceso a paquete de su propia unidad (Unidad 1) -> 200 OK
        mockMvc.perform(get("/api/v1/paquetes/" + p1.idPaquete())
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isOk());

        // 3. Intento de acceso a paquete de OTRA unidad (Unidad 2) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/paquetes/" + p2.idPaquete())
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(CONVIVIENTE_ASSIGN_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F5-08: Un PORTERO que figura como habitante físico no es degradado a RESIDENTE_CONVIVENCIA")
    public void testPorteroRoleNotOverriddenWhenUserIsCohabitant() {
        // Simular que la persona del portero (ID 3) también reside en la unidad 1 como conviviente
        try {
            jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 999931 AS id, 1 AS u, 3 AS p, 'CONVIVIENTE' AS tp, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_RESIDENTE_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_RESIDENTE_UNIDAD, ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.id, s.u, s.p, s.tp, s.st) WHEN MATCHED THEN UPDATE SET ru.ESTADO = 'ACTIVO', ru.TIPO_RESIDENTE = 'CONVIVIENTE'");
        } catch (Exception ignored) {}

        Optional<AssignmentResponseDTO> assignOpt = assignmentRepository.findByIdAndUsuarioId(PORTERO_ASSIGN_ID, PORTERO_USER_ID);
        assertTrue(assignOpt.isPresent());
        assertEquals("PORTERO", assignOpt.get().getRol().getCodigo(), "El rol asignado de PORTERO debe prevalecer sin mutar a RESIDENTE_CONVIVENCIA");
    }

    @Test
    @DisplayName("SEC-F5-09: Un conviviente con estado INACTIVO en RESIDENTES_UNIDAD no muta la asignación a RESIDENTE_CONVIVENCIA")
    public void testInactiveCohabitantNotConvertedToResidenteConvivencia() {
        // Persona 4 con asignación titular 201 en unidad 1, pero estado INACTIVO en RESIDENTES_UNIDAD
        try {
            jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 999941 AS id, 1 AS u, 4 AS p, 'CONVIVIENTE' AS tp, 'INACTIVO' AS st FROM DUAL) s ON (ru.ID_RESIDENTE_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_RESIDENTE_UNIDAD, ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.id, s.u, s.p, s.tp, s.st) WHEN MATCHED THEN UPDATE SET ru.ESTADO = 'INACTIVO', ru.TIPO_RESIDENTE = 'CONVIVIENTE'");
        } catch (Exception ignored) {}

        Optional<AssignmentResponseDTO> assignOpt = assignmentRepository.findByIdAndUsuarioId(TITULAR_ASSIGN_ID, TITULAR_USER_ID);
        assertTrue(assignOpt.isPresent());
        assertEquals("RESIDENTE", assignOpt.get().getRol().getCodigo(), "No debe mutar a RESIDENTE_CONVIVENCIA si el habitante está inactivo");
    }
}
