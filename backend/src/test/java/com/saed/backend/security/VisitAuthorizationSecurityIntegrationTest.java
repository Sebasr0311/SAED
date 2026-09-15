package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.dto.VisitaRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-02: Visit Authorization Impersonation Mitigation Suite.
 * Valida de forma adversarial y exhaustiva que el campo AUTORIZADO_POR en VISITAS
 * es estrictamente SERVER-CONTROLLED, derivado de la identidad autenticada,
 * y que cualquier intento de inyección o suplantación es ignorado y neutralizado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class VisitAuthorizationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PorteriaService porteriaService;

    @MockBean
    private AssignmentService assignmentService;

    private final Long resUserId1 = 4L;
    private final Long resUserId2 = 5L;
    private final Long resConvivienteUserId = 6L;
    private final Long porteroUserId = 3L;
    private final Long adminPropUserId = 2L;

    private final String resAssignment1 = "201";
    private final String resAssignment2 = "202";
    private final String resConvivienteAssignment = "206";
    private final String porteroAssignment = "103";
    private final String adminPropAssignment = "102";

    private Long idRolResidente;
    private Long idRolConviviente;
    private Long idRolPortero;
    private Long idRolPropAdmin;
    private Long testVisitanteId;

    @BeforeEach
    public void setupMocksAndData() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // 1. Roles
        idRolResidente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);
        idRolConviviente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA'", Long.class);
        idRolPortero = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);
        idRolPropAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);

        // 2. Unidades
        try { jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 1 AS id, 1 AS p, 'Apt 101' AS idn, 'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) VALUES (s.id, s.p, s.idn, s.tp, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 2 AS id, 1 AS p, 'Apt 102' AS idn, 'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) VALUES (s.id, s.p, s.idn, s.tp, s.st)"); } catch (Exception ignored) {}

        // 3. Personas y Usuarios
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 2 AS id, 1 AS td, 'CC2000' AS nd, 'NATURAL' AS tp, 'Admin' AS pn, 'Prop' AS pa, 'adminprop@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 2 AS id, 2 AS ip, 'adminprop' AS nu, 'adminprop@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 3 AS id, 1 AS td, 'CC3000' AS nd, 'NATURAL' AS tp, 'Pedro' AS pn, 'Portero' AS pa, 'portero@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 3 AS id, 3 AS ip, 'portero1' AS nu, 'portero@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 4 AS id, 1 AS td, 'CC4000' AS nd, 'NATURAL' AS tp, 'Carlos' AS pn, 'Martinez' AS pa, 'camartinez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 4 AS id, 4 AS ip, 'camartinez' AS nu, 'camartinez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 5 AS id, 1 AS td, 'CC5000' AS nd, 'NATURAL' AS tp, 'Ana' AS pn, 'Gomez' AS pa, 'anagomez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 5 AS id, 5 AS ip, 'anagomez' AS nu, 'anagomez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 6 AS id, 1 AS td, 'CC6000' AS nd, 'NATURAL' AS tp, 'Sofia' AS pn, 'Martinez' AS pa, 'sofia@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 6 AS id, 6 AS ip, 'sofiamartinez' AS nu, 'sofia@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        // User 888888 (Tenant 2 / Other Org)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 888888 AS id, 'Org Test 888888' AS n, 'NIT888888' AS if, 'org888888@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 888888 AS id, 1 AS td, 'DOC888888' AS nd, 'NATURAL' AS tp, 'admin888888@test.com' AS em, 'Alien' AS pn, 'User' AS pa FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.em, s.pn, s.pa)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 888888 AS id, 888888 AS ip, 'alien888' AS nu, 'alien@test.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'"); } catch (Exception ignored) {}

        // 4. Asignaciones con fechas validas para PKG_SAED_SESSION.SET_CONTEXT
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 201 AS id, 4 AS u, ? AS r, 1 AS o, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolResidente); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 202 AS id, 5 AS u, ? AS r, 1 AS o, 1 AS p, 2 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolResidente); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 206 AS id, 6 AS u, ? AS r, 1 AS o, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolConviviente); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 103 AS id, 3 AS u, ? AS r, 1 AS o, 1 AS p, NULL AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPortero); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 102 AS id, 2 AS u, ? AS r, 1 AS o, 1 AS p, NULL AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPropAdmin); } catch (Exception ignored) {}

        try { jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 1 AS u, 4 AS p, 'PRINCIPAL' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 1 AS u, 6 AS p, 'CONVIVIENTE' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 2 AS u, 5 AS p, 'PRINCIPAL' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st)"); } catch (Exception ignored) {}

        // 5. Visitante común de prueba
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 99 AS id, 1 AS td, 'VIS99' AS nd, 'NATURAL' AS tp, 'Visit' AS pn, 'SecTest' AS pa, 'vis99@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO VISITANTES vis USING (SELECT 99 AS p FROM DUAL) s ON (vis.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ES_FRECUENTE) VALUES (s.p, 'S')");
            testVisitanteId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 99", Long.class);
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();

        // 6. Mockito assignments
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(1L);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Edificio Residencial SAED");

        UnitDTO unitDTO1 = new UnitDTO();
        unitDTO1.setId(1L);
        unitDTO1.setIdentificador("Apt 101");

        UnitDTO unitDTO2 = new UnitDTO();
        unitDTO2.setId(2L);
        unitDTO2.setIdentificador("Apt 102");

        // Residente 1 (Unit 1)
        AssignmentResponseDTO aRes1 = new AssignmentResponseDTO();
        aRes1.setIdAsignacion(201L);
        aRes1.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        aRes1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aRes1.setPropiedad(propDTO);
        aRes1.setUnidad(unitDTO1);
        Mockito.when(assignmentService.validateAssignment(201L, 4L)).thenReturn(Optional.of(aRes1));

        // Residente 2 (Unit 2)
        AssignmentResponseDTO aRes2 = new AssignmentResponseDTO();
        aRes2.setIdAsignacion(202L);
        aRes2.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        aRes2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aRes2.setPropiedad(propDTO);
        aRes2.setUnidad(unitDTO2);
        Mockito.when(assignmentService.validateAssignment(202L, 5L)).thenReturn(Optional.of(aRes2));

        // Conviviente (Unit 1)
        AssignmentResponseDTO aConv = new AssignmentResponseDTO();
        aConv.setIdAsignacion(206L);
        aConv.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        aConv.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aConv.setPropiedad(propDTO);
        aConv.setUnidad(unitDTO1);
        Mockito.when(assignmentService.validateAssignment(206L, 6L)).thenReturn(Optional.of(aConv));

        // Portero
        AssignmentResponseDTO aPort = new AssignmentResponseDTO();
        aPort.setIdAsignacion(103L);
        aPort.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        aPort.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aPort.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(103L, 3L)).thenReturn(Optional.of(aPort));

        // Admin Propiedad
        AssignmentResponseDTO aPropAdmin = new AssignmentResponseDTO();
        aPropAdmin.setIdAsignacion(102L);
        aPropAdmin.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        aPropAdmin.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aPropAdmin.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(102L, 2L)).thenReturn(Optional.of(aPropAdmin));
    }

    @AfterEach
    public void cleanup() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private Long getAutorizadoPorFromDb(Long idVisita) {
        SaedContext prev = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            return jdbcTemplate.queryForObject("SELECT AUTORIZADO_POR FROM VISITAS WHERE ID_VISITA = ?", Long.class, idVisita);
        } finally {
            if (prev != null) {
                SaedContextHolder.setContext(prev);
            } else {
                SaedContextHolder.clearContext();
            }
        }
    }

    private Long getGeneradoPorFromDb(String tokenQr) {
        SaedContext prev = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            return jdbcTemplate.queryForObject("SELECT GENERADO_POR FROM QR_ACCESOS WHERE TOKEN_QR = ?", Long.class, tokenQr);
        } finally {
            if (prev != null) {
                SaedContextHolder.setContext(prev);
            } else {
                SaedContextHolder.clearContext();
            }
        }
    }

    // =========================================================================
    // TEST 1: Inyección maliciosa de autorizadoPor es ignorada y neutralizada
    // =========================================================================
    @Test
    @DisplayName("TEST 1: Inyección maliciosa de autorizadoPor (999999) es ignorada y se registra identidad del llamante (4)")
    void test01_inyeccionMaliciosaAutorizadoPor_esIgnorada() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Intento Spoofing 999999");
        payload.put("autorizadoPor", 999999L);
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        // Verificación directa en base de datos
        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor, "El autorizadoPor en base de datos debe ser el usuario autenticado (4), NO el inyectado (999999)");
    }

    // =========================================================================
    // TEST 2: Suplantación de otro usuario existente (User 5) es neutralizada
    // =========================================================================
    @Test
    @DisplayName("TEST 2: Intento de suplantar a otro usuario existente (5) es neutralizado y se registra llamante (4)")
    void test02_suplantarUsuarioExistente_esNeutralizado() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Intento Suplantar Usuario 5");
        payload.put("autorizadoPor", resUserId2);
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor, "El autorizadoPor debe ser el llamante (4) y jamás el suplantado (5)");
        assertNotEquals(resUserId2, dbAutorizadoPor, "La suplantación debe haber sido evitada");
    }

    // =========================================================================
    // TEST 3: autorizadoPor omitido o null se maneja limpiamente sin error 500
    // =========================================================================
    @Test
    @DisplayName("TEST 3: Payload sin autorizadoPor (o null) registra limpiamente la identidad del llamante")
    void test03_sinAutorizadoPor_registraLlamanteSinError() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita Normal Sin Campo Autorizador");
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 4: Intento de suplantación Cross-Tenant (Usuario de Organización B)
    // =========================================================================
    @Test
    @DisplayName("TEST 4: Suplantación Cross-Tenant con usuario de otra organización (888888) es neutralizada")
    void test04_suplantacionCrossTenant_neutralizada() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Ataque Cross-Tenant");
        payload.put("autorizadoPor", 888888L);
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor);
        assertNotEquals(888888L, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 5: Flujo legítimo de RESIDENTE registra autorizadoPor correctamente
    // =========================================================================
    @Test
    @DisplayName("TEST 5: Flujo legítimo de RESIDENTE registra su propio ID_USUARIO")
    void test05_flujoLegitimoResidente() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita Familiar Legitima");
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 6: Flujo legítimo de RESIDENTE_CONVIVENCIA registra su propio ID_USUARIO
    // =========================================================================
    @Test
    @DisplayName("TEST 6: Flujo legítimo de RESIDENTE_CONVIVENCIA registra su propio ID_USUARIO (6)")
    void test06_flujoLegitimoResidenteConvivencia() throws Exception {
        String token = jwtProvider.generateIdentityToken(resConvivienteUserId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita Amigo Conviviente");
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resConvivienteAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(6))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resConvivienteUserId, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 7: Flujo legítimo de PORTERO registra su propio ID_USUARIO
    // =========================================================================
    @Test
    @DisplayName("TEST 7: Flujo legítimo de PORTERO registra su propio ID_USUARIO (3)")
    void test07_flujoLegitimoPortero() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita Registrada en Porteria");
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(3))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(porteroUserId, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 8: Flujo legítimo de ADMIN_PROPIEDAD registra su propio ID_USUARIO
    // =========================================================================
    @Test
    @DisplayName("TEST 8: Flujo legítimo de ADMIN_PROPIEDAD registra su propio ID_USUARIO (2)")
    void test08_flujoLegitimoAdminPropiedad() throws Exception {
        String token = jwtProvider.generateIdentityToken(adminPropUserId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita Gestionada por Administracion");
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", adminPropAssignment)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(2))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(adminPropUserId, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 9: Generación de QR preserva la integridad del creador/autorizador
    // =========================================================================
    @Test
    @DisplayName("TEST 9: Generación de QR almacena GENERADO_POR correspondiente a la identidad del llamante")
    void test09_generacionQr_preservaIntegridad() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita con QR Automático");
        payload.put("autorizadoPor", 777777L); // Intento malicioso
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());
        String tokenQr = (String) resp.get("token");

        assertNotNull(tokenQr, "El token QR generado no debe ser nulo");

        // Validar en la tabla QR_ACCESOS que GENERADO_POR coincide con el llamante y no el ID inyectado
        Long dbGeneradoPor = getGeneradoPorFromDb(tokenQr);
        assertEquals(resUserId1, dbGeneradoPor, "GENERADO_POR en QR_ACCESOS debe ser el llamante autenticado (4)");
    }

    // =========================================================================
    // TEST 10: Endpoint /api/v1/visitas (alias directo) neutraliza suplantación
    // =========================================================================
    @Test
    @DisplayName("TEST 10: Endpoint alternativo POST /api/v1/visitas neutraliza intento de suplantación")
    void test10_endpointVisitasDirecto_neutralizaSuplantacion() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitanteId", testVisitanteId);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Test ruta /api/v1/visitas");
        payload.put("autorizadoPor", 999999L);
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 11: Visita con datos embebidos de visitante neutraliza suplantación
    // =========================================================================
    @Test
    @DisplayName("TEST 11: Visita con creación inline de visitante neutraliza suplantación de autorizadoPor")
    void test11_visitaConVisitanteInline_neutralizaSuplantacion() throws Exception {
        String token = jwtProvider.generateIdentityToken(resUserId1);

        Map<String, Object> visitanteMap = new HashMap<>();
        visitanteMap.put("numeroDocumento", "DOC-" + System.currentTimeMillis());
        visitanteMap.put("nombres", "Visitante");
        visitanteMap.put("apellidos", "Temporal");
        visitanteMap.put("telefono", "3001234567");

        Map<String, Object> payload = new HashMap<>();
        payload.put("unidadId", 1L);
        payload.put("visitante", visitanteMap);
        payload.put("metodoIngreso", "CODIGO_QR");
        payload.put("motivo", "Visita con visitante inline");
        payload.put("autorizadoPor", 999999L); // Intento malicioso
        payload.put("estado", "PROGRAMADA");

        MvcResult result = mockMvc.perform(post("/api/v1/porteria/visitas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", resAssignment1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizadoPor").value(4))
                .andReturn();

        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Long idVisita = Long.valueOf(resp.get("idVisita").toString());

        Long dbAutorizadoPor = getAutorizadoPorFromDb(idVisita);
        assertEquals(resUserId1, dbAutorizadoPor);
    }

    // =========================================================================
    // TEST 12: Actualización de visita retiene el autorizadoPor original
    // =========================================================================
    @Test
    @DisplayName("TEST 12: Actualización de visita mediante actualizarVisita retiene el AUTORIZADO_POR original")
    void test12_actualizarVisita_retieneAutorizadoPorOriginal() throws Exception {
        // Crear visita con usuario 4
        VisitaRequestDTO createReq = new VisitaRequestDTO(
                1L, testVisitanteId, "CODIGO_QR", "Visita Inicial", 4L, ZonedDateTime.now().plusHours(1), "PROGRAMADA"
        );
        SaedContextHolder.setContext(SaedContext.builder().userId(4L).organizationId(1L).propertyId(1L).unitId(1L).roleCode("RESIDENTE").roleScope("UNIDAD").build());
        VisitaDTO created = porteriaService.programarVisita(createReq);
        assertEquals(4L, created.autorizadoPor());

        // Intentar actualizar la visita cambiando autorizadoPor a 5L
        VisitaRequestDTO updateReq = new VisitaRequestDTO(
                1L, testVisitanteId, "CODIGO_QR", "Visita Modificada", 5L, ZonedDateTime.now().plusHours(2), "PROGRAMADA"
        );
        VisitaDTO updated = porteriaService.actualizarVisita(created.idVisita(), updateReq);

        // El autorizadoPor debe seguir siendo 4L
        assertEquals(4L, updated.autorizadoPor(), "El autorizadoPor original (4) debe ser retenido al actualizar");

        Long dbAutorizadoPor = getAutorizadoPorFromDb(created.idVisita());
        assertEquals(4L, dbAutorizadoPor, "La base de datos debe retener el autorizador original (4)");
    }
}
