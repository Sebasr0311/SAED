package com.saed.backend.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentManagementService;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.authorization.service.UnitService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.person.dto.*;
import com.saed.backend.porteria.dto.VisitaRequestDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Pruebas Adversariales de Autorización y Seguridad para RESIDENTE V1.
 * Valida el principio fundamental: RESIDENTE NO ADMINISTRA LA COPROPIEDAD.
 * Opera exclusivamente bajo UNIT & IDENTITY BOUNDARY.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ResidenteAdversarialAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssignmentManagementService assignmentManagementService;

    @Autowired
    private UnitService unitService;

    @MockBean
    private AssignmentService assignmentService;

    private final String resAssignment1 = "201";
    private final String resAssignment2 = "202";
    private final String foreignAssignment = "888888";
    private final Long resUserId1 = 4L;
    private final Long resUserId2 = 5L;

    private Long idRolSuperAdmin;
    private Long idRolOrgAdmin;
    private Long idRolPropAdmin;
    private Long idRolPortero;
    private Long idRolResidente;

    private Long testVisitanteId;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // 1. Roles
        idRolSuperAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'SUPERADMIN'", Long.class);
        idRolOrgAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
        idRolPropAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
        idRolPortero = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);
        idRolResidente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);

        // 2. Seed Unidades
        try { jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 1 AS id, 1 AS p, 'Apt 101' AS idn, 'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) VALUES (s.id, s.p, s.idn, s.tp, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 2 AS id, 1 AS p, 'Apt 102' AS idn, 'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) VALUES (s.id, s.p, s.idn, s.tp, s.st)"); } catch (Exception ignored) {}

        // 3. Seed Personas & Usuarios
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 4 AS id, 1 AS td, 'CC4000' AS nd, 'NATURAL' AS tp, 'Carlos' AS pn, 'Martinez' AS pa, 'camartinez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 5 AS id, 1 AS td, 'CC5000' AS nd, 'NATURAL' AS tp, 'Ana' AS pn, 'Gomez' AS pa, 'anagomez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 4 AS id, 4 AS ip, 'camartinez' AS nu, 'camartinez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 5 AS id, 5 AS ip, 'anagomez' AS nu, 'anagomez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st)"); } catch (Exception ignored) {}

        // 4. Seed Asignaciones
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 201 AS id, 4 AS u, ? AS r, 1 AS o, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st", idRolResidente); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 202 AS id, 5 AS u, ? AS r, 1 AS o, 1 AS p, 2 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st", idRolResidente); } catch (Exception ignored) {}

        // 5. Seed Residentes Unidad
        try { jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 1 AS u, 4 AS p, 'PRINCIPAL' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 2 AS u, 5 AS p, 'PRINCIPAL' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st)"); } catch (Exception ignored) {}

        // Clean test records for idempotency
        try {
            jdbcTemplate.update("DELETE FROM VEHICULOS WHERE PLACA LIKE 'RES%' OR PLACA LIKE 'XYZ%'");
            jdbcTemplate.update("DELETE FROM MASCOTAS WHERE NOMBRE IN ('Firulais', 'MascotaAjena')");
        } catch (Exception ignored) {}

        // 6. Seed Visitante, Visita & QR
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 10 AS id, 1 AS td, 'VIS100' AS nd, 'NATURAL' AS tp, 'Visit' AS pn, 'One' AS pa, 'vis@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO VISITANTES vis USING (SELECT 10 AS p FROM DUAL) s ON (vis.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ES_FRECUENTE) VALUES (s.p, 'S')");
            testVisitanteId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 10", Long.class);

            jdbcTemplate.update("MERGE INTO VISITAS v USING (SELECT 100 AS id, 1 AS u, ? AS vis, 'CODIGO_QR' AS mi, 'Reunion Familiar' AS mo, CURRENT_TIMESTAMP AS fp, 'PROGRAMADA' AS st FROM DUAL) s ON (v.ID_VISITA = s.id) WHEN NOT MATCHED THEN INSERT (ID_VISITA, ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, FECHA_PROGRAMADA, ESTADO) VALUES (s.id, s.u, s.vis, s.mi, s.mo, s.fp, s.st) WHEN MATCHED THEN UPDATE SET v.ESTADO = s.st", testVisitanteId);

            jdbcTemplate.update("MERGE INTO QR_ACCESOS qr USING (SELECT 100 AS v, 'TOKEN_RESIDENTE_TEST' AS t, CURRENT_TIMESTAMP + INTERVAL '1' DAY AS exp, 5 AS up, 0 AS uc, 'ACTIVO' AS st FROM DUAL) s ON (qr.TOKEN_QR = s.t) WHEN NOT MATCHED THEN INSERT (ID_VISITA, TOKEN_QR, FECHA_EXPIRACION, USOS_PERMITIDOS, USOS_CONSUMIDOS, ESTADO) VALUES (s.v, s.t, s.exp, s.up, s.uc, s.st) WHEN MATCHED THEN UPDATE SET qr.ID_VISITA = s.v, qr.FECHA_EXPIRACION = s.exp, qr.ESTADO = s.st, qr.USOS_CONSUMIDOS = 0");
        } catch (Exception ignored) {}

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        // 7. Mockito expectations
        AssignmentResponseDTO res1 = new AssignmentResponseDTO();
        res1.setIdAsignacion(201L);
        res1.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO1 = new PropertyDTO();
        propDTO1.setId(1L);
        propDTO1.setIdOrganizacion(1L);
        propDTO1.setNombre("Edificio Residencial SAED");
        res1.setPropiedad(propDTO1);
        UnitDTO unitDTO1 = new UnitDTO();
        unitDTO1.setId(1L);
        unitDTO1.setIdentificador("Apt 101");
        res1.setUnidad(unitDTO1);
        Mockito.when(assignmentService.validateAssignment(201L, 4L)).thenReturn(Optional.of(res1));

        AssignmentResponseDTO res2 = new AssignmentResponseDTO();
        res2.setIdAsignacion(202L);
        res2.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        res2.setPropiedad(propDTO1);
        UnitDTO unitDTO2 = new UnitDTO();
        unitDTO2.setId(2L);
        unitDTO2.setIdentificador("Apt 102");
        res2.setUnidad(unitDTO2);
        Mockito.when(assignmentService.validateAssignment(202L, 5L)).thenReturn(Optional.of(res2));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // 1. OPERACIONES POSITIVAS DE RESIDENTE (UNIT & IDENTITY BOUNDARY)
    // =========================================================================

    @Test
    @DisplayName("RESIDENTE: Puede consultar información de su propia unidad")
    void residente_canAccessOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/units/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar habitantes de su propia unidad")
    void residente_canReadInhabitantsOfOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/units/1/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede programar una visita para su propia unidad")
    void residente_canProgramVisitForOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        VisitaRequestDTO req = new VisitaRequestDTO(1L, testVisitanteId, "CODIGO_QR", "Cena", null, ZonedDateTime.now().plusHours(2), "PROGRAMADA");
        mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar visitas de su propia unidad")
    void residente_canReadVisitsOfOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/porteria/unidades/1/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar visitantes frecuentes propios")
    void residente_canAccessOwnFrequentVisitors() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/residentes/4/frecuentes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar QRs activos propios")
    void residente_canAccessOwnActiveQrs() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/residentes/4/qr-activos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede registrar y consultar mascotas de su unidad")
    void residente_canManagePetsOfOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        MascotaRequestDTO req = new MascotaRequestDTO(1L, 4L, "Firulais", "PERRO", "Labrador", "Dorado", "M", null, 10.0, null, "N", null, null, null, "ACTIVA");
        mockMvc.perform(post("/api/v1/mascotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/unidades/1/mascotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede registrar y consultar vehículos de su unidad")
    void residente_canManageVehiclesOfOwnUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        String placa = "RES" + (System.currentTimeMillis() % 1000);
        VehiculoRequestDTO req = new VehiculoRequestDTO(4L, 1L, placa, "AUTOMOVIL", "Mazda", "2022", "Rojo", null, "ACTIVO");
        mockMvc.perform(post("/api/v1/vehiculos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/unidades/1/vehiculos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar paquetes entregados a su unidad")
    void residente_canReadPackages() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/paquetes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar su propio buzón de notificaciones y avisos")
    void residente_canAccessBuzonAndAvisos() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/buzon")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/buzon/avisos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede crear y consultar sus propios tickets PQRS")
    void residente_canManageOwnPqrs() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        TicketRequestDTO req = new TicketRequestDTO();
        req.setTipo("PETICION");
        req.setCategoria("MANTENIMIENTO");
        req.setPrioridad("MEDIA");
        req.setAsunto("Consulta de mantenimiento");
        req.setDescripcion("Solicitud de revision de bombillo pasillo");

        mockMvc.perform(post("/api/v1/pqrs")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/pqrs/mis-tickets")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RESIDENTE: Puede consultar parqueaderos y asignaciones operativas")
    void residente_canReadParqueaderos() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/parqueaderos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/parqueaderos/asignaciones")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. PROTECCIÓN IDOR & AISLAMIENTO CROSS-UNIT (STRICT UNIT BOUNDARY)
    // =========================================================================

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede consultar otra unidad")
    void residente_cannotAccessOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/units/2")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede consultar habitantes de otra unidad")
    void residente_cannotReadInhabitantsOfOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/units/2/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede programar visitas para otra unidad")
    void residente_cannotProgramVisitForOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        VisitaRequestDTO req = new VisitaRequestDTO(2L, 10L, "CODIGO_QR", "Ataque", null, ZonedDateTime.now().plusHours(2), "PROGRAMADA");
        mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede consultar visitas de otra unidad")
    void residente_cannotReadVisitsOfOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/porteria/unidades/2/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / IDENTITY: RESIDENTE no puede consultar visitantes frecuentes de otro residente")
    void residente_cannotAccessOtherResidentFrequentVisitors() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/residentes/5/frecuentes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / IDENTITY: RESIDENTE no puede consultar QRs activos de otro residente")
    void residente_cannotAccessOtherResidentActiveQrs() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/residentes/5/qr-activos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede consultar ni registrar mascotas en otra unidad")
    void residente_cannotAccessPetsOfOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        MascotaRequestDTO req = new MascotaRequestDTO(2L, 5L, "MascotaAjena", "CANINO", "Pug", "Negro", "M", null, 8.0, null, "N", null, null, null, "ACTIVO");
        mockMvc.perform(post("/api/v1/mascotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/unidades/2/mascotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / CROSS-UNIT: RESIDENTE no puede consultar ni registrar vehículos en otra unidad")
    void residente_cannotAccessVehiclesOfOtherUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        VehiculoRequestDTO req = new VehiculoRequestDTO(5L, 2L, "XYZ999", "CARRO", "Toyota", "2020", "Azul", null, "ACTIVO");
        mockMvc.perform(post("/api/v1/vehiculos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/unidades/2/vehiculos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR / IDENTITY: RESIDENTE no puede consultar el dashboard financiero de otro residente")
    void residente_cannotAccessOtherResidentFinancialDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/residentes/5/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. DENEGACIÓN EN MUTACIÓN DE PROPIEDADES, UNIDADES Y HABITANTES
    // =========================================================================

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede crear unidades")
    void residente_cannotCreateUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        UnitRequestDTO req = new UnitRequestDTO();
        req.setIdPropiedad(1L);
        req.setIdTipoUnidad(1L);
        req.setIdentificador("Apt 999");

        mockMvc.perform(post("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        assertThrows(AccessDeniedException.class, () -> {
            SaedContextHolder.setContext(SaedContext.builder().userId(4L).organizationId(1L).propertyId(1L).unitId(1L).roleCode("RESIDENTE").roleScope("UNIDAD").build());
            unitService.create(req);
        });
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede actualizar unidades")
    void residente_cannotUpdateUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        UnitRequestDTO req = new UnitRequestDTO();
        req.setIdPropiedad(1L);
        req.setIdTipoUnidad(1L);
        req.setIdentificador("Apt 101 Modificado");

        mockMvc.perform(put("/api/v1/units/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        assertThrows(AccessDeniedException.class, () -> {
            SaedContextHolder.setContext(SaedContext.builder().userId(4L).organizationId(1L).propertyId(1L).unitId(1L).roleCode("RESIDENTE").roleScope("UNIDAD").build());
            unitService.update(1L, req);
        });
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede asociar propietarios a una unidad")
    void residente_cannotAddOwners() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        UnitOwnerRequestDTO req = new UnitOwnerRequestDTO(5L, new BigDecimal("100.0"), "S");
        mockMvc.perform(post("/api/v1/units/1/owners")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede asociar residentes a una unidad")
    void residente_cannotAddResidents() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        UnitResidentRequestDTO req = new UnitResidentRequestDTO(5L, "PROPIETARIO");
        mockMvc.perform(post("/api/v1/units/1/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. DENEGACIÓN EN CONTROL DE OPERACIONES DE PORTERÍA & VIGILANCIA
    // =========================================================================

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede validar/consumir códigos QR en portería")
    void residente_cannotValidateQrInGuardhouse() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(post("/api/v1/porteria/qr/validar")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"TOKEN_RESIDENTE_TEST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede registrar ingresos de acceso peatonales en portería")
    void residente_cannotRegisterEntrada() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(post("/api/v1/porteria/registros/entrada")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propiedadId\":1,\"porteriaId\":1,\"personaId\":10,\"tipoMovimiento\":\"ENTRADA\",\"metodoAutorizacion\":\"MANUAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede recibir paquetes como portero")
    void residente_cannotReceivePackage() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        PaqueteRequestDTO req = new PaqueteRequestDTO(1L, 4L, "Servientrega", "GUIA-123", "Caja", "MEDIANO", null, 1L);
        mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede entregar paquetes como portero")
    void residente_cannotDeliverPackage() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(post("/api/v1/paquetes/1/entrega")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoRetiroPin\":\"1234\",\"idPersonaRecibe\":4,\"idPorteria\":1}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. DENEGACIÓN EN FINANZAS GLOBALES, SANCIONES, CONTRATOS Y PQRS ADMINISTRATIVAS
    // =========================================================================

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede consultar la cartera global de la propiedad")
    void residente_cannotAccessCarteraGlobal() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/cartera")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/cartera/resumen")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede acceder a administración de sanciones")
    void residente_cannotAccessSancionesAdmin() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/multas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede acceder a todas las quejas ni responderlas")
    void residente_cannotAccessOrRespondAllQuejas() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/quejas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/quejas/1/responder")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Respuesta no autorizada\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede ver todos los tickets PQRS ni cambiarles el estado")
    void residente_cannotAccessOrUpdateAllPqrs() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/pqrs/todos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/pqrs/1/estado?estado=CERRADO")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede acceder a pólizas ni contratos")
    void residente_cannotAccessLegalAndInsurance() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/seguros/polizas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede publicar avisos masivos ni alertas de ruido")
    void residente_cannotPublishAvisos() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(post("/api/v1/buzon/aviso")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Aviso ilegal\",\"mensaje\":\"Test\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/buzon/aviso-ruido")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idResidente\":1}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. DENEGACIÓN EN PLATAFORMA Y ORGANIZACIÓN (STRICT 403)
    // =========================================================================

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede acceder a la consola de plataforma")
    void residente_cannotAccessPlatformEndpoints() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/platform/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/platform/plans")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/platform/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede acceder a la consola de organización")
    void residente_cannotAccessOrgEndpoints() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/org/profile")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/org/subscription")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/org/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. ANTI-ESCALAMIENTO DE PRIVILEGIOS & CONMUTACIÓN DE ASIGNACIONES
    // =========================================================================

    @Test
    @DisplayName("ANTI-ESCALAMIENTO: RESIDENTE no puede asignarse roles de mayor jerarquía")
    void residente_cannotAssignElevatedRoles() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(resUserId1)
                    .organizationId(1L)
                    .propertyId(1L)
                    .unitId(1L)
                    .roleCode("RESIDENTE")
                    .roleScope("UNIDAD")
                    .build());

            AssignmentRequestDTO reqSuper = new AssignmentRequestDTO();
            reqSuper.setIdUsuario(resUserId1);
            reqSuper.setIdRol(idRolSuperAdmin);
            assertThrows(AccessDeniedException.class, () -> assignmentManagementService.create(reqSuper));

            AssignmentRequestDTO reqOrg = new AssignmentRequestDTO();
            reqOrg.setIdUsuario(resUserId1);
            reqOrg.setIdRol(idRolOrgAdmin);
            reqOrg.setIdOrganizacion(1L);
            assertThrows(AccessDeniedException.class, () -> assignmentManagementService.create(reqOrg));

            AssignmentRequestDTO reqProp = new AssignmentRequestDTO();
            reqProp.setIdUsuario(resUserId1);
            reqProp.setIdRol(idRolPropAdmin);
            reqProp.setIdOrganizacion(1L);
            reqProp.setIdPropiedad(1L);
            assertThrows(AccessDeniedException.class, () -> assignmentManagementService.create(reqProp));

            AssignmentRequestDTO reqPort = new AssignmentRequestDTO();
            reqPort.setIdUsuario(resUserId1);
            reqPort.setIdRol(idRolPortero);
            reqPort.setIdOrganizacion(1L);
            reqPort.setIdPropiedad(1L);
            assertThrows(AccessDeniedException.class, () -> assignmentManagementService.create(reqPort));
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede crear parqueaderos (403 Forbidden)")
    void residente_cannotCreateParkingSpots() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        String body = "{\"numeroParqueadero\":\"P-999\",\"tipo\":\"PRIVADO\",\"estado\":\"DISPONIBLE\"}";
        mockMvc.perform(post("/api/v1/parqueaderos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede asignar parqueaderos (403 Forbidden)")
    void residente_cannotCreateParkingAssignments() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        String body = "{\"idParqueadero\":1,\"idUnidad\":1,\"idVehiculo\":null,\"tipoAsignacion\":\"EXCLUSIVO\",\"canonMensual\":50000.00}";
        mockMvc.perform(post("/api/v1/parqueaderos/asignaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede consultar reportes financieros (403 Forbidden)")
    void residente_cannotAccessReportes() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede consultar registros de auditoría del sistema (403 Forbidden)")
    void residente_cannotAccessAudit() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/audit")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DENEGACIÓN: RESIDENTE no puede consultar presupuestos ni gastos (403 Forbidden)")
    void residente_cannotAccessPresupuestos() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        mockMvc.perform(get("/api/v1/presupuestos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/gastos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CONMUTACIÓN: RESIDENTE no puede utilizar asignación ajena (403 Forbidden)")
    void residente_cannotUseForeignAssignment() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);
        // Usuario 4 intenta usar Asignación 202 (pertenece a Usuario 5)
        mockMvc.perform(get("/api/v1/units/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment2))
                .andExpect(status().isForbidden());
    }
}
