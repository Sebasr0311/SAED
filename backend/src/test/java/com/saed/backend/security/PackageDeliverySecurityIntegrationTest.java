package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.audit.AuditSanitizer;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Pruebas de Seguridad y Autorizacion para Paqueteria y Entregas (Fase 8).
 * Valida de forma rigurosa y adversarial SEC-F8-01 a SEC-F8-20.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PackageDeliverySecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AssignmentService assignmentService;

    private String porteroToken;
    private String res1Token;
    private String res2Token;
    private String convToken;
    private String portero2Token;
    private String alienToken;

    private Long idRolPortero;
    private Long idRolResidente;
    private Long idRolConviviente;

    private <T> T querySuperAdmin(String sql, Class<T> clazz, Object... args) {
        SaedContext prev = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(1L)
                    .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            return jdbcTemplate.queryForObject(sql, clazz, args);
        } finally {
            if (prev != null) {
                SaedContextHolder.setContext(prev);
            } else {
                SaedContextHolder.clearContext();
            }
        }
    }

    @BeforeEach
    public void setupSecurityFixtures() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {
        }

        idRolPortero = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);
        idRolResidente = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);
        idRolConviviente = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA'", Long.class);

        // Propiedades
        try {
            jdbcTemplate.update("MERGE INTO PROPIEDADES p USING (SELECT 1 AS id, 1 AS o, 1 AS tp, "
                    + "'Edificio Principal' AS n, 'Dir 1' AS d, 'Bogota' AS c, 'MIXTA' AS to FROM DUAL) s "
                    + "ON (p.ID_PROPIEDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, "
                    + "NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE) "
                    + "VALUES (s.id, s.o, s.tp, s.n, s.d, s.c, s.to)");
            jdbcTemplate.update("MERGE INTO PROPIEDADES p USING (SELECT 2 AS id, 1 AS o, 1 AS tp, "
                    + "'Edificio Secundario' AS n, 'Dir 2' AS d, 'Bogota' AS c, 'MIXTA' AS to FROM DUAL) s "
                    + "ON (p.ID_PROPIEDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, "
                    + "NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE) "
                    + "VALUES (s.id, s.o, s.tp, s.n, s.d, s.c, s.to)");
        } catch (Exception ignored) {
        }

        // Unidades
        try {
            jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 1 AS id, 1 AS p, 'Apt 101' AS idn, "
                    + "'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) "
                    + "VALUES (s.id, s.p, s.idn, s.tp, s.st) "
                    + "WHEN MATCHED THEN UPDATE SET u.ID_PROPIEDAD = s.p, u.ESTADO = s.st");
            jdbcTemplate.update("MERGE INTO UNIDADES u USING (SELECT 2 AS id, 1 AS p, 'Apt 102' AS idn, "
                    + "'APARTAMENTO' AS tp, 'ACTIVA' AS st FROM DUAL) s ON (u.ID_UNIDAD = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, ESTADO) "
                    + "VALUES (s.id, s.p, s.idn, s.tp, s.st) "
                    + "WHEN MATCHED THEN UPDATE SET u.ID_PROPIEDAD = s.p, u.ESTADO = s.st");
        } catch (Exception ignored) {
        }

        // Comunicado para notificaciones
        try {
            jdbcTemplate.update("MERGE INTO COMUNICADOS c USING (SELECT 1 AS id, 1 AS p, 'Avisos' AS t, "
                    + "'Contenido' AS ct, 'TODOS' AS ts, 'NORMAL' AS pr, 'PUBLICADO' AS st FROM DUAL) s "
                    + "ON (c.ID_COMUNICADO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_COMUNICADO, ID_PROPIEDAD, TITULO, CONTENIDO, "
                    + "TIPO_SEGMENTACION, PRIORIDAD, ESTADO) VALUES (s.id, s.p, s.t, s.ct, s.ts, s.pr, s.st) "
                    + "WHEN MATCHED THEN UPDATE SET c.ID_PROPIEDAD = s.p, c.ESTADO = s.st");
        } catch (Exception ignored) {
        }

        // Porterias
        try {
            jdbcTemplate.update("MERGE INTO PORTERIAS p USING (SELECT 1 AS id, 1 AS prop, 'Principal' AS n, "
                    + "'ACTIVA' AS st FROM DUAL) s ON (p.ID_PORTERIA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PORTERIA, ID_PROPIEDAD, NOMBRE, ESTADO) "
                    + "VALUES (s.id, s.prop, s.n, s.st)");
        } catch (Exception ignored) {
        }

        // Personas y Usuarios
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 3 AS id, 1 AS td, 'CC3000' AS nd, 'NATURAL' AS tp, "
                    + "'Pedro' AS pn, 'Portero' AS pa, 'portero@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 3 AS id, 3 AS ip, 'portero1' AS nu, "
                    + "'portero@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");

            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 4 AS id, 1 AS td, 'CC4000' AS nd, 'NATURAL' AS tp, "
                    + "'Carlos' AS pn, 'Martinez' AS pa, 'camartinez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 4 AS id, 4 AS ip, 'camartinez' AS nu, "
                    + "'camartinez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");

            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 5 AS id, 1 AS td, 'CC5000' AS nd, 'NATURAL' AS tp, "
                    + "'Ana' AS pn, 'Gomez' AS pa, 'anagomez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 5 AS id, 5 AS ip, 'anagomez' AS nu, "
                    + "'anagomez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");

            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 6 AS id, 1 AS td, 'CC6000' AS nd, 'NATURAL' AS tp, "
                    + "'Sofia' AS pn, 'Martinez' AS pa, 'sofia@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 6 AS id, 6 AS ip, 'sofiamartinez' AS nu, "
                    + "'sofia@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");

            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 333 AS id, 1 AS td, 'CC3330' AS nd, 'NATURAL' AS tp, "
                    + "'Luis' AS pn, 'Portero2' AS pa, 'portero2@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 333 AS id, 333 AS ip, 'portero2' AS nu, "
                    + "'portero2@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");

            jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 888888 AS id, 'Org Test 888888' AS n, "
                    + "'NIT888888' AS if, 'org888888@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) "
                    + "VALUES (s.id, s.n, s.if, s.ec)");
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 888888 AS id, 1 AS td, 'DOC888888' AS nd, "
                    + "'NATURAL' AS tp, 'Alien' AS pn, 'User' AS pa, 'alien@test.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, "
                    + "EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.em, s.pn, s.pa)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 888888 AS id, 888888 AS ip, 'alien888' AS nu, "
                    + "'alien@test.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");
        } catch (Exception ignored) {
        }

        // Asignaciones
        try {
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 103 AS id, 3 AS u, ? AS r, 1 AS o, 1 AS p, "
                    + "NULL AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, "
                    + "ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) "
                    + "WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, "
                    + "ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL",
                    idRolPortero);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 201 AS id, 4 AS u, ? AS r, 1 AS o, 1 AS p, "
                    + "1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, "
                    + "ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) "
                    + "WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, "
                    + "ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL",
                    idRolResidente);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 202 AS id, 5 AS u, ? AS r, 1 AS o, 1 AS p, "
                    + "2 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, "
                    + "ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) "
                    + "WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, "
                    + "ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL",
                    idRolResidente);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 206 AS id, 6 AS u, ? AS r, 1 AS o, 1 AS p, "
                    + "1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, "
                    + "ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) "
                    + "WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, "
                    + "ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL",
                    idRolConviviente);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 133 AS id, 333 AS u, ? AS r, 1 AS o, 2 AS p, "
                    + "NULL AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) "
                    + "WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, "
                    + "ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) "
                    + "WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, "
                    + "ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL",
                    idRolPortero);
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();

        // Mocks de AssignmentService
        PropertyDTO prop1DTO = new PropertyDTO();
        prop1DTO.setId(1L);
        prop1DTO.setIdOrganizacion(1L);
        prop1DTO.setNombre("Edificio Principal");

        PropertyDTO prop2DTO = new PropertyDTO();
        prop2DTO.setId(2L);
        prop2DTO.setIdOrganizacion(1L);
        prop2DTO.setNombre("Edificio Secundario");

        UnitDTO unit1DTO = new UnitDTO();
        unit1DTO.setId(1L);
        unit1DTO.setIdentificador("Apt 101");

        UnitDTO unit2DTO = new UnitDTO();
        unit2DTO.setId(2L);
        unit2DTO.setIdentificador("Apt 102");

        AssignmentResponseDTO aPort = new AssignmentResponseDTO();
        aPort.setIdAsignacion(103L);
        aPort.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        aPort.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aPort.setPropiedad(prop1DTO);
        Mockito.when(assignmentService.validateAssignment(103L, 3L)).thenReturn(Optional.of(aPort));

        AssignmentResponseDTO aRes1 = new AssignmentResponseDTO();
        aRes1.setIdAsignacion(201L);
        aRes1.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        aRes1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aRes1.setPropiedad(prop1DTO);
        aRes1.setUnidad(unit1DTO);
        Mockito.when(assignmentService.validateAssignment(201L, 4L)).thenReturn(Optional.of(aRes1));

        AssignmentResponseDTO aRes2 = new AssignmentResponseDTO();
        aRes2.setIdAsignacion(202L);
        aRes2.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        aRes2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aRes2.setPropiedad(prop1DTO);
        aRes2.setUnidad(unit2DTO);
        Mockito.when(assignmentService.validateAssignment(202L, 5L)).thenReturn(Optional.of(aRes2));

        AssignmentResponseDTO aConv = new AssignmentResponseDTO();
        aConv.setIdAsignacion(206L);
        aConv.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        aConv.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aConv.setPropiedad(prop1DTO);
        aConv.setUnidad(unit1DTO);
        Mockito.when(assignmentService.validateAssignment(206L, 6L)).thenReturn(Optional.of(aConv));

        AssignmentResponseDTO aPort2 = new AssignmentResponseDTO();
        aPort2.setIdAsignacion(133L);
        aPort2.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        aPort2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        aPort2.setPropiedad(prop2DTO);
        Mockito.when(assignmentService.validateAssignment(133L, 333L)).thenReturn(Optional.of(aPort2));

        AssignmentResponseDTO aAlien = new AssignmentResponseDTO();
        aAlien.setIdAsignacion(888L);
        aAlien.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        aAlien.setOrganizacion(new OrganizationDTO(888888L, "Org Alien"));
        PropertyDTO alienProp = new PropertyDTO();
        alienProp.setId(888L);
        alienProp.setIdOrganizacion(888888L);
        aAlien.setPropiedad(alienProp);
        UnitDTO alienUnit = new UnitDTO();
        alienUnit.setId(888L);
        aAlien.setUnidad(alienUnit);
        Mockito.when(assignmentService.validateAssignment(888L, 888888L)).thenReturn(Optional.of(aAlien));

        porteroToken = jwtProvider.generateIdentityToken(3L);
        res1Token = jwtProvider.generateIdentityToken(4L);
        res2Token = jwtProvider.generateIdentityToken(5L);
        convToken = jwtProvider.generateIdentityToken(6L);
        portero2Token = jwtProvider.generateIdentityToken(333L);
        alienToken = jwtProvider.generateIdentityToken(888888L);
    }

    @AfterEach
    public void tearDownSecurityContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();
    }

    private PaqueteDTO createTestPackage(Long unitId, String guia, String foto) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idUnidad", unitId);
        payload.put("idPersonaDestinatario", 4L);
        payload.put("empresaMensajeria", "Servientrega");
        payload.put("numeroGuia", guia);
        payload.put("descripcion", "Paquete de prueba " + guia);
        payload.put("tamano", "MEDIANO");
        payload.put("fotoPaqueteUrl", foto);
        payload.put("idPorteria", 1L);

        MvcResult result = mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), PaqueteDTO.class);
    }

    @Test
    @DisplayName("SEC-F8-01: Registro valido de paquete por Portero retorna 201 Created")
    void secF8_01_registroValidoPaquete() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-01", null);

        assertNotNull(p.idPaquete());
        assertEquals("RECIBIDO", p.estado());
        assertEquals(1L, p.idUnidad());
        assertNotNull(p.codigoRetiroPin());
    }

    @Test
    @DisplayName("SEC-F8-02: Generacion automatica de PIN numerico de 4 digitos")
    void secF8_02_generacionAutomaticaPin4Digitos() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-02", null);

        String pin = p.codigoRetiroPin();
        assertNotNull(pin, "PIN must not be null");
        assertEquals(4, pin.length(), "PIN must have length 4");
        assertTrue(Pattern.matches("^\\d{4}$", pin), "PIN must be strictly 4 numeric digits [0000-9999]");
        int val = Integer.parseInt(pin);
        assertTrue(val >= 0 && val <= 9999);
    }

    @Test
    @DisplayName("SEC-F8-03: PIN suministrado por cliente es rechazado como autoridad y reemplazado")
    void secF8_03_pinFrontendRechazadoComoAutoridad() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idUnidad", 1L);
        payload.put("empresaMensajeria", "DHL");
        payload.put("tamano", "PEQUENO");
        payload.put("idPorteria", 1L);
        payload.put("codigoRetiroPin", "7777");
        payload.put("pin", "7777");

        MvcResult res = mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        PaqueteDTO p = objectMapper.readValue(res.getResponse().getContentAsString(), PaqueteDTO.class);
        assertNotNull(p.codigoRetiroPin());
        assertEquals(4, p.codigoRetiroPin().length());
        assertNotEquals("7777", p.codigoRetiroPin(), "Client-injected PIN must be ignored by backend");
    }

    @Test
    @DisplayName("SEC-F8-04: Notificacion IN_APP creada exclusivamente para la unidad destino")
    void secF8_04_notificacionCreadaParaUnidadCorrecta() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-04", null);

        Integer notifsRes1 = querySuperAdmin(
                "SELECT COUNT(*) FROM NOTIFICACIONES WHERE ID_USUARIO_DESTINATARIO = 4 "
                        + "AND CANAL = 'IN_APP' AND MENSAJE LIKE '%" + p.codigoRetiroPin() + "%'",
                Integer.class
        );
        assertTrue(notifsRes1 != null && notifsRes1 >= 1, "Resident of target unit must receive PIN notification");

        Integer notifsRes2 = querySuperAdmin(
                "SELECT COUNT(*) FROM NOTIFICACIONES WHERE ID_USUARIO_DESTINATARIO = 5 "
                        + "AND CANAL = 'IN_APP' AND MENSAJE LIKE '%" + p.codigoRetiroPin() + "%'",
                Integer.class
        );
        assertEquals(0, notifsRes2 != null ? notifsRes2 : 0, "Resident of other unit must not receive notification");
    }

    @Test
    @DisplayName("SEC-F8-05: Usuario de otra unidad no puede acceder al paquete (403 Forbidden o 404 RLS)")
    void secF8_05_usuarioOtraUnidadNoAccedePaquete() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-05", null);

        mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete())
                .header("Authorization", "Bearer " + res1Token)
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isOk());

        MvcResult resOther = mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete())
                .header("Authorization", "Bearer " + res2Token)
                .header("X-Assignment-Id", "202"))
                .andReturn();
        int st = resOther.getResponse().getStatus();
        assertTrue(st == 403 || st == 404, "Must be 403 Forbidden or 404 Hidden by RLS, got: " + st);
    }

    @Test
    @DisplayName("SEC-F8-06: Imagen accesible por destinatario autorizado de la unidad")
    void secF8_06_imagenAccesibleDestinatarioAutorizado() throws Exception {
        String base64Image = "data:image/jpeg;base64,/9j/4AAQSkZJRg==";
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-06", base64Image);

        mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete() + "/imagen")
                .header("Authorization", "Bearer " + res1Token)
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete() + "/imagen")
                .header("Authorization", "Bearer " + convToken)
                .header("X-Assignment-Id", "206"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-F8-07: Acceso a imagen bloqueado cross-tenant y cross-unit (403 o 404)")
    void secF8_07_imagenBloqueadaCrossTenant() throws Exception {
        String base64Image = "data:image/jpeg;base64,/9j/4AAQSkZJRg==";
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-07", base64Image);

        MvcResult resImgOtherUnit = mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete() + "/imagen")
                .header("Authorization", "Bearer " + res2Token)
                .header("X-Assignment-Id", "202"))
                .andReturn();
        int stUnit = resImgOtherUnit.getResponse().getStatus();
        assertTrue(stUnit == 403 || stUnit == 404, "Must be 403 or 404 for other unit, got: " + stUnit);

        MvcResult resImgAlien = mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete() + "/imagen")
                .header("Authorization", "Bearer " + alienToken)
                .header("X-Assignment-Id", "888"))
                .andReturn();
        int stAlien = resImgAlien.getResponse().getStatus();
        assertTrue(stAlien == 403 || stAlien == 404, "Must be 403 or 404 for alien tenant, got: " + stAlien);
    }

    @Test
    @DisplayName("SEC-F8-08: PIN correcto permite entrega y transiciona estado a ENTREGADO")
    void secF8_08_pinCorrectoPermiteEntrega() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-08", null);

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", p.codigoRetiroPin());
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        mockMvc.perform(post("/api/v1/paquetes/" + p.idPaquete() + "/entrega")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        String dbEstado = querySuperAdmin(
                "SELECT ESTADO FROM PAQUETES WHERE ID_PAQUETE = ?", String.class, p.idPaquete());
        assertEquals("ENTREGADO", dbEstado);
    }

    @Test
    @DisplayName("SEC-F8-09: PIN incorrecto no permite entrega e incrementa intentos fallidos")
    void secF8_09_pinIncorrectoNoPermiteEntrega() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-09", null);
        String wrongPin = "0000".equals(p.codigoRetiroPin()) ? "9999" : "0000";

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", wrongPin);
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        mockMvc.perform(post("/api/v1/paquetes/" + p.idPaquete() + "/entrega")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isBadRequest());

        String dbEstado = querySuperAdmin(
                "SELECT ESTADO FROM PAQUETES WHERE ID_PAQUETE = ?", String.class, p.idPaquete());
        assertEquals("RECIBIDO", dbEstado);

        Integer intentos = querySuperAdmin(
                "SELECT NVL(INTENTOS_FALLIDOS_PIN, 0) FROM PAQUETES WHERE ID_PAQUETE = ?",
                Integer.class, p.idPaquete());
        assertEquals(1, intentos);
    }

    @Test
    @DisplayName("SEC-F8-10: Intento de bypass de entrega directa sin PIN es rechazado (400 Bad Request)")
    void secF8_10_manipularEstadoDirectamenteNoPermiteSaltarsePin() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-10", null);

        mockMvc.perform(put("/api/v1/buzon/" + p.idPaquete() + "/entregado")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));

        String dbEstado = querySuperAdmin(
                "SELECT ESTADO FROM PAQUETES WHERE ID_PAQUETE = ?", String.class, p.idPaquete());
        assertNotEquals("ENTREGADO", dbEstado);
    }

    @Test
    @DisplayName("SEC-F8-11: Paquete ENTREGADO no puede volver a entregarse (409 Conflict)")
    void secF8_11_paqueteEntregadoNoPuedeVolverAEntregarse() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-11", null);

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", p.codigoRetiroPin());
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        mockMvc.perform(post("/api/v1/paquetes/" + p.idPaquete() + "/entrega")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/paquetes/" + p.idPaquete() + "/entrega")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("SEC-F8-12: PIN de otro paquete no funciona para entregar el paquete actual")
    void secF8_12_pinDeOtroPaqueteNoFunciona() throws Exception {
        PaqueteDTO p1 = createTestPackage(1L, "GUIA-F8-12A", null);
        PaqueteDTO p2 = createTestPackage(1L, "GUIA-F8-12B", null);

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", p2.codigoRetiroPin());
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        if (!p1.codigoRetiroPin().equals(p2.codigoRetiroPin())) {
            mockMvc.perform(post("/api/v1/paquetes/" + p1.idPaquete() + "/entrega")
                    .header("Authorization", "Bearer " + porteroToken)
                    .header("X-Assignment-Id", "103")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(entregaReq)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("SEC-F8-13: Portero de otra propiedad no puede entregar paquete (403 Forbidden)")
    void secF8_13_porteroOtraPropiedadNoPuedeEntregar() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-13", null);

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", p.codigoRetiroPin());
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        mockMvc.perform(post("/api/v1/paquetes/" + p.idPaquete() + "/entrega")
                .header("Authorization", "Bearer " + portero2Token)
                .header("X-Assignment-Id", "133")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F8-14: Usuario de otra organizacion no puede acceder al paquete (403 o 404)")
    void secF8_14_usuarioOtraOrganizacionNoPuedeAcceder() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-14", null);

        MvcResult alienRes = mockMvc.perform(get("/api/v1/paquetes/" + p.idPaquete())
                .header("Authorization", "Bearer " + alienToken)
                .header("X-Assignment-Id", "888"))
                .andReturn();
        int stAlien = alienRes.getResponse().getStatus();
        assertTrue(stAlien == 403 || stAlien == 404, "Alien user must be denied with 403 or 404, got: " + stAlien);
    }

    @Test
    @DisplayName("SEC-F8-15: Registro de paquete con idUnidad invalida o ajena es rechazado")
    void secF8_15_manipulacionIdUnidadRechazada() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idUnidad", 999999L);
        payload.put("empresaMensajeria", "Interrapidisimo");
        payload.put("tamano", "MEDIANO");
        payload.put("idPorteria", 1L);

        mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F8-16: Manipulacion de idPropiedad en payload es ignorada y neutralizada")
    void secF8_16_manipulacionIdPropiedadRechazada() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idUnidad", 1L);
        payload.put("empresaMensajeria", "Coordinadora");
        payload.put("tamano", "MEDIANO");
        payload.put("idPorteria", 1L);
        payload.put("idPropiedad", 9999L);

        MvcResult res = mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        PaqueteDTO p = objectMapper.readValue(res.getResponse().getContentAsString(), PaqueteDTO.class);
        Long propDb = querySuperAdmin(
                "SELECT ID_PROPIEDAD FROM PAQUETES WHERE ID_PAQUETE = ?", Long.class, p.idPaquete());
        assertEquals(1L, propDb, "Property ID must be server-controlled by token context (1), not 9999");
    }

    @Test
    @DisplayName("SEC-F8-17: Manipulacion de idOrganizacion en payload es ignorada y aislada")
    void secF8_17_manipulacionIdOrganizacionRechazada() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idUnidad", 1L);
        payload.put("empresaMensajeria", "4-72");
        payload.put("tamano", "MEDIANO");
        payload.put("idPorteria", 1L);
        payload.put("idOrganizacion", 888888L);

        MvcResult res = mockMvc.perform(post("/api/v1/paquetes")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        PaqueteDTO p = objectMapper.readValue(res.getResponse().getContentAsString(), PaqueteDTO.class);
        Long propDb = querySuperAdmin(
                "SELECT ID_PROPIEDAD FROM PAQUETES WHERE ID_PAQUETE = ?", Long.class, p.idPaquete());
        assertEquals(1L, propDb);
    }

    @Test
    @DisplayName("SEC-F8-18: Concurrencia real en entrega: exactamente una gana y la otra recibe 409 Conflict")
    void secF8_18_dosEntregasConcurrentesSolamenteUnaGana() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-18-CONC", null);
        Long idPaquete = p.idPaquete();
        String validPin = p.codigoRetiroPin();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> deliveryTask = () -> {
            MvcResult res = mockMvc.perform(post("/api/v1/paquetes/" + idPaquete + "/entrega")
                    .header("Authorization", "Bearer " + porteroToken)
                    .header("X-Assignment-Id", "103")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new PaqueteEntregaDTO(validPin, 4L, 1L, null))))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        List<Future<Integer>> futures = executor.invokeAll(List.of(deliveryTask, deliveryTask));
        executor.shutdown();

        int st1 = futures.get(0).get();
        int st2 = futures.get(1).get();

        assertTrue((st1 == 200 && st2 == 409) || (st1 == 409 && st2 == 200),
                "Exactly one delivery must succeed (200) and the other must be rejected (409 Conflict)");

        String finalEstado = querySuperAdmin(
                "SELECT ESTADO FROM PAQUETES WHERE ID_PAQUETE = ?", String.class, idPaquete);
        assertEquals("ENTREGADO", finalEstado);
    }

    @Test
    @DisplayName("SEC-F8-19: Multiples PIN incorrectos activan bloqueo de seguridad (429 SECURITY_BLOCKED)")
    void secF8_19_multiplesPinIncorrectosActivanBloqueo() throws Exception {
        PaqueteDTO p = createTestPackage(1L, "GUIA-F8-19-BRUTE", null);
        Long idPaquete = p.idPaquete();
        String wrongPin = "0000".equals(p.codigoRetiroPin()) ? "9999" : "0000";

        Map<String, Object> entregaReq = new HashMap<>();
        entregaReq.put("codigoRetiroPin", wrongPin);
        entregaReq.put("idPersonaRecibe", 4L);
        entregaReq.put("idPorteria", 1L);

        // Intentos 1, 2 y 3 fallan con 400 Bad Request
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/paquetes/" + idPaquete + "/entrega")
                    .header("Authorization", "Bearer " + porteroToken)
                    .header("X-Assignment-Id", "103")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(entregaReq)))
                    .andExpect(status().isBadRequest());
        }

        // Intento 4 (bloqueo por limite excedido -> 429 Too Many Requests)
        MvcResult blockedRes = mockMvc.perform(post("/api/v1/paquetes/" + idPaquete + "/entrega")
                .header("Authorization", "Bearer " + porteroToken)
                .header("X-Assignment-Id", "103")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entregaReq)))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String body = blockedRes.getResponse().getContentAsString();
        assertTrue(body.contains("SECURITY_BLOCKED") || body.contains("bloqueado"));
    }

    @Test
    @DisplayName("SEC-F8-20: PIN nunca aparece en texto plano en auditoria ni payloads serializados")
    void secF8_20_pinNuncaApareceEnLogsAplicacion() throws Exception {
        AuditSanitizer sanitizer = new AuditSanitizer(objectMapper);
        Map<String, Object> map = Map.of(
                "evento", "PACKAGE_DELIVERY_ATTEMPT",
                "codigoRetiroPin", "1234",
                "pin", "5678",
                "piningresado", "9999"
        );
        String sanitized = sanitizer.sanitizeToJson(map);
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("1234"), "PIN 1234 must be sanitized");
        assertFalse(sanitized.contains("5678"), "PIN 5678 must be sanitized");
        assertFalse(sanitized.contains("9999"), "PIN 9999 must be sanitized");
        assertTrue(sanitized.contains("[PROTECTED]"), "Sanitized keys must be [PROTECTED]");

        Integer plainPinInAudit = querySuperAdmin(
                "SELECT COUNT(*) FROM AUDITORIA_LOG WHERE ENTIDAD = 'PAQUETE' AND "
                        + "(ESTADO_ANTERIOR LIKE '%\"codigoRetiroPin\"%' OR ESTADO_NUEVO LIKE '%\"codigoRetiroPin\"%')",
                Integer.class
        );
        assertEquals(0, plainPinInAudit != null ? plainPinInAudit : 0,
                "No raw unredacted PINs should be stored in AUDITORIA_LOG");
    }
}
