package com.saed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Pruebas de Seguridad y Autorización para MODEL C (Fase 7).
 * Valida de forma rigurosa y adversarial:
 * SEC-F7-01: Anti-spoofing / IDOR en idOrganizacion (403 Forbidden).
 * SEC-F7-02: Protección de membresías: ADMIN_PROPIEDAD no puede mutar membresías (403 Forbidden).
 * SEC-F7-03: Bloqueo de cambio de plan no autorizado por admin de tenant (403 Forbidden).
 * SEC-F7-04: IDOR en idPropiedad: ADMIN_ORGANIZACION no puede crear unidades en propiedad ajena (403 Forbidden).
 * SEC-F7-05: Auditoría honesta del estado de tablas de módulos contratados.
 * SEC-F7-06: Enforcement de límite de propiedades del plan SaaS (409 Conflict).
 * SEC-F7-07: Enforcement de límite de unidades del plan SaaS (409 Conflict).
 * SEC-F7-08: Enforcement de límite de usuarios del plan SaaS y deduplicación por cuenta (409 Conflict).
 * SEC-F7-09: Rechazo de operaciones ante membresía cancelada o vencida (403 Forbidden).
 * SEC-F7-10: Aislamiento estricto multi-tenant entre ORG-A (Plan FREE) y ORG-B (Plan PRO).
 * SEC-F7-11: Liberación de cupo de propiedades/unidades tras inactivación o eliminación.
 * SEC-F7-12: Prevención de race conditions mediante bloqueo pesimista en base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ModelCLimitsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.saed.backend.authorization.service.PropertyStatusService propertyStatusService;

    @MockBean
    private AssignmentService assignmentService;

    // Organizaciones de prueba
    private static final long ORG_A_ID = 9971L; // Plan FREE (1 prop, 10 units, 5 users)
    private static final long ORG_B_ID = 9972L; // Plan PRO (5 props, 100 units, 50 users)
    private static final long ORG_EXP_ID = 9973L; // Membresía CANCELADA/VENCIDA

    // Propiedades de prueba
    private static final long PROP_A1_ID = 9971L;
    private static final long PROP_B1_ID = 9972L;

    // Usuarios y asignaciones de prueba
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 1L;

    private static final long USER_ADMIN_ORG_A = 9971L;
    private static final long ASSIGN_ADMIN_ORG_A = 9971L;

    private static final long USER_ADMIN_PROP_A = 9973L;
    private static final long ASSIGN_ADMIN_PROP_A = 9973L;

    private static final long USER_ADMIN_ORG_B = 9972L;
    private static final long ASSIGN_ADMIN_ORG_B = 9972L;

    private static final long USER_EXP_ORG = 9974L;
    private static final long ASSIGN_EXP_ORG = 9974L;

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {
        }

        // 1. Asegurar Organizaciones
        seedOrganizacion(ORG_A_ID, "Org Alpha Test", "900009971-1", "alpha@test.com");
        seedOrganizacion(ORG_B_ID, "Org Beta Test", "900009972-2", "beta@test.com");
        seedOrganizacion(ORG_EXP_ID, "Org Expired Test", "900009973-3", "expired@test.com");

        // 2. Limpiar membresias de test previas
        try {
            jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION IN (?, ?, ?)",
                    ORG_A_ID, ORG_B_ID, ORG_EXP_ID);
        } catch (Exception ignored) {
        }

        // 3. Crear membresias limpias
        // ORG_A -> Plan 1 (FREE: 1 prop, 10 units, 5 users)
        seedMembresia(ORG_A_ID, 1L, "ACTIVA", 365);
        // ORG_B -> Plan 2 (PRO: 5 props, 100 units, 50 users)
        seedMembresia(ORG_B_ID, 2L, "ACTIVA", 365);
        // ORG_EXP -> Plan 1 CANCELADA / VENCIDA
        seedMembresia(ORG_EXP_ID, 1L, "CANCELADA", -10);

        // 4 & 5 & 6 & 7. Limpieza, Propiedades, Personas, Usuarios y Asignaciones
        // en un único bloque PL/SQL con bootstrap context para garantizar aislamiento y validez.
        jdbcTemplate.execute(String.format("""
            DECLARE
                v_rol_org   NUMBER;
                v_rol_prop  NUMBER;
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
                PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');

                -- 1. Limpieza de unidades y asignaciones de tests anteriores
                DELETE FROM UNIDADES WHERE ID_PROPIEDAD IN (
                    SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_ORGANIZACION IN (%d, %d, %d)
                );
                DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ORGANIZACION IN (%d, %d, %d)
                    AND ID_ASIGNACION NOT IN (%d, %d, %d, %d);

                -- 2. Asegurar que propiedades base existan y estén ACTIVAS mediante MERGE
                MERGE INTO PROPIEDADES p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PROPIEDAD = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
                        VALUES (%d, %d, 1, 'Edificio Alpha Principal', 'Calle Falsa 123', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA')
                    WHEN MATCHED THEN UPDATE SET p.ESTADO = 'ACTIVA';
                MERGE INTO PROPIEDADES p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PROPIEDAD = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
                        VALUES (%d, %d, 1, 'Edificio Beta Central', 'Calle Falsa 123', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA')
                    WHEN MATCHED THEN UPDATE SET p.ESTADO = 'ACTIVA';

                -- 3. Inactivar propiedades secundarias creadas durante tests
                UPDATE PROPIEDADES SET ESTADO = 'INACTIVA'
                WHERE ID_ORGANIZACION IN (%d, %d) AND ID_PROPIEDAD NOT IN (%d, %d);

                SELECT ID_ROL INTO v_rol_org  FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION';
                SELECT ID_ROL INTO v_rol_prop FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD';
                -- alpha_admin (ADMIN_ORG_A)
                MERGE INTO PERSONAS p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PERSONA = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                        TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                        VALUES (%d, 1, 'DOC-A-9971', 'NATURAL', 'alpha_admin', 'Test', 'alpha_admin@test.com');
                MERGE INTO USUARIOS u USING (SELECT %d AS user_id_val FROM DUAL) s ON (u.ID_USUARIO = s.user_id_val)
                    WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL,
                        HASH_PASSWORD, ESTADO)
                        VALUES (%d, %d, 'alpha_admin', 'alpha_admin@test.com', 'hash_test', 'ACTIVO')
                    WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO';
                -- beta_admin (ADMIN_ORG_B)
                MERGE INTO PERSONAS p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PERSONA = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                        TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                        VALUES (%d, 1, 'DOC-B-9972', 'NATURAL', 'beta_admin', 'Test', 'beta_admin@test.com');
                MERGE INTO USUARIOS u USING (SELECT %d AS user_id_val FROM DUAL) s ON (u.ID_USUARIO = s.user_id_val)
                    WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL,
                        HASH_PASSWORD, ESTADO)
                        VALUES (%d, %d, 'beta_admin', 'beta_admin@test.com', 'hash_test', 'ACTIVO')
                    WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO';
                -- propA_admin (ADMIN_PROPIEDAD)
                MERGE INTO PERSONAS p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PERSONA = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                        TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                        VALUES (%d, 1, 'DOC-PA-9973', 'NATURAL', 'propA_admin', 'Test', 'propA_admin@test.com');
                MERGE INTO USUARIOS u USING (SELECT %d AS user_id_val FROM DUAL) s ON (u.ID_USUARIO = s.user_id_val)
                    WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL,
                        HASH_PASSWORD, ESTADO)
                        VALUES (%d, %d, 'propA_admin', 'propA_admin@test.com', 'hash_test', 'ACTIVO')
                    WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO';
                -- exp_admin (ADMIN_ORG_EXP)
                MERGE INTO PERSONAS p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PERSONA = s.id)
                    WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                        TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                        VALUES (%d, 1, 'DOC-EX-9974', 'NATURAL', 'exp_admin', 'Test', 'exp_admin@test.com');
                MERGE INTO USUARIOS u USING (SELECT %d AS user_id_val FROM DUAL) s ON (u.ID_USUARIO = s.user_id_val)
                    WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL,
                        HASH_PASSWORD, ESTADO)
                        VALUES (%d, %d, 'exp_admin', 'exp_admin@test.com', 'hash_test', 'ACTIVO')
                    WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO';
                -- Asignaciones
                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT %d AS aid FROM DUAL) s
                    ON (ua.ID_ASIGNACION = s.aid)
                    WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION,
                        ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                        VALUES (%d, %d, v_rol_org, %d, NULL, 'ACTIVA', TRUNC(SYSDATE))
                    WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = %d, ua.ID_ROL = v_rol_org,
                        ua.ID_ORGANIZACION = %d, ua.ID_PROPIEDAD = NULL,
                        ua.ESTADO = 'ACTIVA', ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL;
                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT %d AS aid FROM DUAL) s
                    ON (ua.ID_ASIGNACION = s.aid)
                    WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION,
                        ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                        VALUES (%d, %d, v_rol_org, %d, NULL, 'ACTIVA', TRUNC(SYSDATE))
                    WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = %d, ua.ID_ROL = v_rol_org,
                        ua.ID_ORGANIZACION = %d, ua.ID_PROPIEDAD = NULL,
                        ua.ESTADO = 'ACTIVA', ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL;
                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT %d AS aid FROM DUAL) s
                    ON (ua.ID_ASIGNACION = s.aid)
                    WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION,
                        ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                        VALUES (%d, %d, v_rol_prop, %d, %d, 'ACTIVA', TRUNC(SYSDATE))
                    WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = %d, ua.ID_ROL = v_rol_prop,
                        ua.ID_ORGANIZACION = %d, ua.ID_PROPIEDAD = %d,
                        ua.ESTADO = 'ACTIVA', ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL;
                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT %d AS aid FROM DUAL) s
                    ON (ua.ID_ASIGNACION = s.aid)
                    WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION,
                        ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                        VALUES (%d, %d, v_rol_org, %d, NULL, 'ACTIVA', TRUNC(SYSDATE))
                    WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = %d, ua.ID_ROL = v_rol_org,
                        ua.ID_ORGANIZACION = %d, ua.ID_PROPIEDAD = NULL,
                        ua.ESTADO = 'ACTIVA', ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL;
                PKG_SAED_SESSION.CLEAR_CONTEXT;
            END;
            """,
            // 1. Limpieza UNIDADES (3)
            ORG_A_ID, ORG_B_ID, ORG_EXP_ID,
            // Limpieza ASIGNACIONES (7)
            ORG_A_ID, ORG_B_ID, ORG_EXP_ID, ASSIGN_ADMIN_ORG_A, ASSIGN_ADMIN_ORG_B, ASSIGN_ADMIN_PROP_A, ASSIGN_EXP_ORG,
            // 2. MERGE PROP_A1_ID (3)
            PROP_A1_ID, PROP_A1_ID, ORG_A_ID,
            // MERGE PROP_B1_ID (3)
            PROP_B1_ID, PROP_B1_ID, ORG_B_ID,
            // 3. Inactivar secundarias (4)
            ORG_A_ID, ORG_B_ID, PROP_A1_ID, PROP_B1_ID,
            // alpha_admin PERSONA+USUARIO
            USER_ADMIN_ORG_A, USER_ADMIN_ORG_A,
            USER_ADMIN_ORG_A, USER_ADMIN_ORG_A, USER_ADMIN_ORG_A,
            // beta_admin PERSONA+USUARIO
            USER_ADMIN_ORG_B, USER_ADMIN_ORG_B,
            USER_ADMIN_ORG_B, USER_ADMIN_ORG_B, USER_ADMIN_ORG_B,
            // propA_admin PERSONA+USUARIO
            USER_ADMIN_PROP_A, USER_ADMIN_PROP_A,
            USER_ADMIN_PROP_A, USER_ADMIN_PROP_A, USER_ADMIN_PROP_A,
            // exp_admin PERSONA+USUARIO
            USER_EXP_ORG, USER_EXP_ORG,
            USER_EXP_ORG, USER_EXP_ORG, USER_EXP_ORG,
            // ASSIGN_ADMIN_ORG_A (idPropiedad is NULL)
            ASSIGN_ADMIN_ORG_A, ASSIGN_ADMIN_ORG_A, USER_ADMIN_ORG_A, ORG_A_ID,
            USER_ADMIN_ORG_A, ORG_A_ID,
            // ASSIGN_ADMIN_ORG_B (idPropiedad is NULL)
            ASSIGN_ADMIN_ORG_B, ASSIGN_ADMIN_ORG_B, USER_ADMIN_ORG_B, ORG_B_ID,
            USER_ADMIN_ORG_B, ORG_B_ID,
            // ASSIGN_ADMIN_PROP_A (idPropiedad is PROP_A1_ID)
            ASSIGN_ADMIN_PROP_A, ASSIGN_ADMIN_PROP_A, USER_ADMIN_PROP_A, ORG_A_ID, PROP_A1_ID,
            USER_ADMIN_PROP_A, ORG_A_ID, PROP_A1_ID,
            // ASSIGN_EXP_ORG (idPropiedad is NULL)
            ASSIGN_EXP_ORG, ASSIGN_EXP_ORG, USER_EXP_ORG, ORG_EXP_ID,
            USER_EXP_ORG, ORG_EXP_ID
        ));

        // 8. Configurar mock de asignaciones
        setupAssignmentMocks();

        // 9. Invalidar caché en memoria de estado de propiedades
        propertyStatusService.invalidateCache(PROP_A1_ID);
        propertyStatusService.invalidateCache(PROP_B1_ID);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();
    }


    @AfterEach
    public void tearDown() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {
        }
        SaedContextHolder.clearContext();
    }

    private void seedOrganizacion(Long orgId, String nombre, String nit, String email) {
        try {
            jdbcTemplate.execute(String.format("""
                MERGE INTO ORGANIZACIONES o
                USING (SELECT %d AS id, '%s' AS nom, '%s' AS nit, '%s' AS em FROM DUAL) s
                ON (o.ID_ORGANIZACION = s.id)
                WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO)
                VALUES (s.id, s.nom, s.nit, s.em, 'ACTIVA')
                WHEN MATCHED THEN UPDATE SET o.ESTADO = 'ACTIVA'
                """, orgId, nombre, nit, email));
        } catch (Exception ignored) {
        }
    }

    private void seedPersonaUsuario(Long personaId, Long usuarioId, String nombre, String email, String doc) {
        try {
            jdbcTemplate.update("""
                MERGE INTO PERSONAS p USING (SELECT ? AS id, 1 AS td, ? AS nd, 'NATURAL' AS tp,
                    ? AS pn, 'Test' AS pa, ? AS em FROM DUAL) s ON (p.ID_PERSONA = s.id)
                WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                    TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)
                """, personaId, doc, nombre, email);
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.update("""
                MERGE INTO USUARIOS u USING (SELECT ? AS uid, ? AS ip, ? AS nu, ? AS em,
                    '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.uid)
                WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO)
                VALUES (s.uid, s.ip, s.nu, s.em, s.pw, s.st)
                WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'
                """, usuarioId, personaId, nombre, email);
        } catch (Exception ignored) {
        }
    }

    private void seedAsignacion(Long assignId, Long userId, Long rolId, Long orgId, Long propId) {
        try {
            jdbcTemplate.update("""
                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT ? AS aid, ? AS uid, ? AS rid,
                    ? AS oid, ? AS pid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION,
                    ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (s.aid, s.uid, s.rid, s.oid, s.pid, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.uid, ua.ID_ROL = s.rid,
                    ua.ID_ORGANIZACION = s.oid, ua.ID_PROPIEDAD = s.pid, ua.ESTADO = 'ACTIVA',
                    ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL
                """, assignId, userId, rolId, orgId, propId);
        } catch (Exception ignored) {
        }
    }

    private void seedMembresia(Long orgId, Long planId, String estado, int diasValidez) {
        try {
            String fechaFinExpr = diasValidez >= 0
                    ? String.format("TRUNC(SYSDATE) + %d", diasValidez)
                    : String.format("TRUNC(SYSDATE) - %d", Math.abs(diasValidez));
            jdbcTemplate.execute(String.format("""
                INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
                VALUES (%d, %d, TRUNC(SYSDATE) - 30, %s, '%s', 'N')
                """, orgId, planId, fechaFinExpr, estado));
        } catch (Exception ignored) {
        }
    }

    private void seedPropiedad(Long propId, Long orgId, String nombre, String estado) {
        try {
            jdbcTemplate.execute(String.format("""
                INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
                VALUES (%d, %d, 1, '%s', 'Calle Falsa 123', 'Bogota', 'Colombia', 'MIXTA', '%s')
                """, propId, orgId, nombre, estado));
        } catch (Exception ignored) {
        }
    }

    private void setupAssignmentMocks() {
        OrganizationDTO orgA = new OrganizationDTO(ORG_A_ID, "Org Alpha Test");
        OrganizationDTO orgB = new OrganizationDTO(ORG_B_ID, "Org Beta Test");
        OrganizationDTO orgExp = new OrganizationDTO(ORG_EXP_ID, "Org Expired Test");

        PropertyDTO propA1 = new PropertyDTO(PROP_A1_ID, "Edificio Alpha Principal");
        propA1.setIdOrganizacion(ORG_A_ID);

        PropertyDTO propB1 = new PropertyDTO(PROP_B1_ID, "Edificio Beta Central");
        propB1.setIdOrganizacion(ORG_B_ID);

        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        AssignmentResponseDTO adminOrgAAssign = new AssignmentResponseDTO();
        adminOrgAAssign.setIdAsignacion(ASSIGN_ADMIN_ORG_A);
        adminOrgAAssign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrgAAssign.setOrganizacion(orgA);

        AssignmentResponseDTO adminPropAAssign = new AssignmentResponseDTO();
        adminPropAAssign.setIdAsignacion(ASSIGN_ADMIN_PROP_A);
        adminPropAAssign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminPropAAssign.setOrganizacion(orgA);
        adminPropAAssign.setPropiedad(propA1);

        AssignmentResponseDTO adminOrgBAssign = new AssignmentResponseDTO();
        adminOrgBAssign.setIdAsignacion(ASSIGN_ADMIN_ORG_B);
        adminOrgBAssign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrgBAssign.setOrganizacion(orgB);

        AssignmentResponseDTO expAssign = new AssignmentResponseDTO();
        expAssign.setIdAsignacion(ASSIGN_EXP_ORG);
        expAssign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        expAssign.setOrganizacion(orgExp);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN))
                .thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_A, USER_ADMIN_ORG_A))
                .thenReturn(Optional.of(adminOrgAAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_A, USER_ADMIN_PROP_A))
                .thenReturn(Optional.of(adminPropAAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_B, USER_ADMIN_ORG_B))
                .thenReturn(Optional.of(adminOrgBAssign));
        when(assignmentService.validateAssignment(ASSIGN_EXP_ORG, USER_EXP_ORG))
                .thenReturn(Optional.of(expAssign));
    }

    @Test
    @DisplayName("SEC-F7-01: Admin de Organización no puede crear propiedades imputadas a otra organización (Anti-spoofing)")
    public void test_SEC_F7_01_AntiSpoofing_OrgAdminCannotCreatePropertyInOtherOrg() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Propiedad Maliciosa Cross Tenant");
        body.put("direccion", "Carrera 1 # 2-3");
        body.put("ciudad", "Bogota");
        body.put("idTipoPropiedad", 1L);
        body.put("tipoOcupacionPredominante", "MIXTA");
        body.put("idOrganizacion", ORG_B_ID); // Intento de spoofing hacia Org B

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden()); // Oracle RLS or service-level anti-spoofing blocks this
    }

    @Test
    @DisplayName("SEC-F7-02: Admin de Propiedad no puede crear ni cambiar estado de membresías SaaS (403 Forbidden)")
    public void test_SEC_F7_02_AntiSpoofing_PropAdminCannotModifyMembership() throws Exception {
        String tokenPropA = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        // Intento de crear membresia
        Map<String, Object> createBody = Map.of(
                "idOrganizacion", ORG_A_ID,
                "idPlan", 3L, // Intento de auto-escalamiento a ENTERPRISE
                "estado", "ACTIVA"
        );
        mockMvc.perform(post("/api/v1/membresias")
                .header("Authorization", "Bearer " + tokenPropA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isForbidden());

        // Intento de cambiar estado
        mockMvc.perform(patch("/api/v1/membresias/1/status")
                .header("Authorization", "Bearer " + tokenPropA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("estado", "ACTIVA"))))
                .andExpect(status().isForbidden());

        // Intento de cancelar
        mockMvc.perform(delete("/api/v1/membresias/1")
                .header("Authorization", "Bearer " + tokenPropA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F7-03: Admin de Organización no puede alterar membresías de plataforma (403 Forbidden)")
    public void test_SEC_F7_03_AntiSpoofing_TenantAdminCannotChangePlanOrMembership() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        Map<String, Object> body = Map.of(
                "idOrganizacion", ORG_A_ID,
                "idPlan", 3L,
                "estado", "ACTIVA"
        );

        mockMvc.perform(post("/api/v1/platform/memberships")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-F7-04: IDOR idPropiedad - Admin de Organización A no puede crear unidades en propiedad de Organización B")
    public void test_SEC_F7_04_IDOR_OrgAdminCannotCreateUnitInOtherOrgProperty() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        Map<String, Object> unitBody = new HashMap<>();
        unitBody.put("identificador", "Apto 999-Malicioso");
        unitBody.put("idPropiedad", PROP_B1_ID); // Propiedad que pertenece a ORG_B
        unitBody.put("idTipoUnidad", 1L);

        mockMvc.perform(post("/api/v1/units")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unitBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SEC-F7-05: Auditoría honesta de tablas de módulos contratados y suscripción")
    public void test_SEC_F7_05_ContractedModulesAudit() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // Validar endpoint de consulta de suscripcion
        mockMvc.perform(get("/api/v1/org/subscription")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCodigo").value("FREE"))
                .andExpect(jsonPath("$.data.limitePropiedades").value(1))
                .andExpect(jsonPath("$.data.limiteUnidades").value(10))
                .andExpect(jsonPath("$.data.limiteUsuarios").value(5));

        // Verificar existencia en esquema Oracle
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME IN ('MODULOS', 'PLAN_MODULOS')",
                Integer.class
        );
        assertNotNull(tableCount);
        assertEquals(2, tableCount, "Ambas tablas MODULOS y PLAN_MODULOS deben existir en el catálogo Oracle");
    }

    @Test
    @DisplayName("SEC-F7-06: Enforcement de límite de propiedades del plan (409 Conflict al superar límite)")
    public void test_SEC_F7_06_PropertyLimitEnforced_409Conflict() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // ORG_A tiene Plan FREE (límite = 1 propiedad) y ya posee PROP_A1_ID sembrada y ACTIVA.
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Segunda Propiedad Excedente");
        body.put("direccion", "Carrera 10 # 20-30");
        body.put("ciudad", "Bogota");
        body.put("idTipoPropiedad", 1L);
        body.put("tipoOcupacionPredominante", "MIXTA");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitType").value("PROPIEDADES"))
                .andExpect(jsonPath("$.maxLimit").value(1));
    }

    @Test
    @DisplayName("SEC-F7-07: Enforcement de límite de unidades del plan (409 Conflict al superar límite)")
    public void test_SEC_F7_07_UnitLimitEnforced_409Conflict() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // ORG_A tiene Plan FREE (límite = 10 unidades).
        // Sembrar 10 unidades en PROP_A1_ID en un único bloque PL/SQL (misma conexión) para evitar ORA-28115
        jdbcTemplate.execute(String.format("""
            DECLARE
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
                PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
                FOR i IN 1..10 LOOP
                    BEGIN
                        INSERT INTO UNIDADES (ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO)
                        VALUES (%d, 1, 'Apto-Seed-' || i, 'ACTIVA');
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;
                END LOOP;
                PKG_SAED_SESSION.CLEAR_CONTEXT;
            END;
            """, PROP_A1_ID));

        // Intento de crear la 11va unidad (Property Admin de PROP_A1 en ORG_A)
        String tokenPropA = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);
        Map<String, Object> unitBody = new HashMap<>();
        unitBody.put("identificador", "Apto-11-Excedente");
        unitBody.put("idPropiedad", PROP_A1_ID);
        unitBody.put("idTipoUnidad", 1L);

        mockMvc.perform(post("/api/v1/units")
                .header("Authorization", "Bearer " + tokenPropA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unitBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitType").value("UNIDADES"))
                .andExpect(jsonPath("$.maxLimit").value(10));

    }


    @Test
    @DisplayName("SEC-F7-08: Enforcement de límite de usuarios y deduplicación de asignaciones por cuenta")
    public void test_SEC_F7_08_UserLimitEnforced_409Conflict() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // Plan FREE tiene límite de 5 usuarios.
        // Sembrar 5 usuarios con asignaciones activas en ORG_A en un único bloque PL/SQL (misma conexión)
        Long porteroRolId = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);
        List<Long> userIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            userIds.add(9980L + i);
        }
        jdbcTemplate.execute(String.format("""
            DECLARE
                v_rol_id NUMBER := %d;
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
                PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
                FOR i IN 1..5 LOOP
                    DECLARE v_uid NUMBER := 9980 + i;
                    BEGIN
                        MERGE INTO PERSONAS p USING (SELECT v_uid AS id FROM DUAL) s ON (p.ID_PERSONA = s.id)
                        WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                            TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                            VALUES (v_uid, 1, 'DOC-LIM-' || i, 'NATURAL', 'User', 'Limit' || i, 'user' || i || '@lim.com');
                        MERGE INTO USUARIOS u USING (SELECT v_uid AS user_id_val FROM DUAL) s ON (u.ID_USUARIO = s.user_id_val)
                        WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO)
                            VALUES (v_uid, v_uid, 'user_lim_' || i, 'user' || i || '@lim.com', 'hash123', 'ACTIVO')
                        WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO';
                        INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                            VALUES (v_uid, v_rol_id, %d, %d, 'ACTIVA', TRUNC(SYSDATE));
                    END;
                END LOOP;
                PKG_SAED_SESSION.CLEAR_CONTEXT;
            END;
            """, porteroRolId, ORG_A_ID, PROP_A1_ID));

        // Intento de crear una 6ta asignación para un usuario NUEVO en ORG_A
        Map<String, Object> newAssign = new HashMap<>();
        newAssign.put("idUsuario", 9999L);
        newAssign.put("idRol", porteroRolId);
        newAssign.put("idOrganizacion", ORG_A_ID);
        newAssign.put("idPropiedad", PROP_A1_ID);

        mockMvc.perform(post("/api/v1/assignments")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAssign)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitType").value("USUARIOS"))
                .andExpect(jsonPath("$.maxLimit").value(5));

        // Deduplicación: reasignar a uno de los 5 usuarios ya activos en ORG_A NO consume cupo adicional
        Long adminPropRolId = jdbcTemplate.queryForObject(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
        Map<String, Object> existingUserAssign = new HashMap<>();
        existingUserAssign.put("idUsuario", userIds.get(0)); // Ya activo en ORG_A
        existingUserAssign.put("idRol", adminPropRolId);
        existingUserAssign.put("idOrganizacion", ORG_A_ID);
        existingUserAssign.put("idPropiedad", PROP_A1_ID);

        mockMvc.perform(post("/api/v1/assignments")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(existingUserAssign)))
                .andExpect(status().isCreated());
    }



    @Test
    @DisplayName("SEC-F7-09: Membresía inactiva o vencida bloquea creación de recursos (403 Forbidden)")
    public void test_SEC_F7_09_InactiveOrExpiredMembership_403Forbidden() throws Exception {
        String tokenExp = jwtProvider.generateIdentityToken(USER_EXP_ORG);

        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Propiedad en Org Vencida");
        body.put("direccion", "Carrera 50 # 100-10");
        body.put("ciudad", "Bogota");
        body.put("idTipoPropiedad", 1L);
        body.put("tipoOcupacionPredominante", "MIXTA");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenExp)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_EXP_ORG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_INACTIVE"));
    }

    @Test
    @DisplayName("SEC-F7-10: Aislamiento multi-tenant entre ORG-A (Plan FREE) y ORG-B (Plan PRO)")
    public void test_SEC_F7_10_MultiTenantIsolation_OrgAActivityDoesNotAffectOrgB() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);
        String tokenOrgB = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_B);

        // ORG_A está en su límite de propiedades (1/1)
        Map<String, Object> propA = new HashMap<>();
        propA.put("nombre", "Propiedad Org A Excedente");
        propA.put("direccion", "Calle 1");
        propA.put("ciudad", "Bogota");
        propA.put("idTipoPropiedad", 1L);
        propA.put("tipoOcupacionPredominante", "MIXTA");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(propA)))
                .andExpect(status().isConflict());

        // ORG_B tiene Plan PRO (1/5 propiedades usadas), crear una segunda propiedad DEBE ser exitoso
        Map<String, Object> propB = new HashMap<>();
        propB.put("nombre", "Segunda Propiedad Org B");
        propB.put("direccion", "Avenida Siempre Viva 742");
        propB.put("ciudad", "Medellin");
        propB.put("idTipoPropiedad", 1L);
        propB.put("tipoOcupacionPredominante", "MIXTA");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgB)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_B))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(propB)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEC-F7-11: La inactivación de propiedades libera cupo para crear nuevas propiedades")
    public void test_SEC_F7_11_DeletionOrInactivationReleasesQuota() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // Inicialmente ORG_A tiene 1/1 propiedad ACTIVA -> Conflicto al crear
        Map<String, Object> propReq = new HashMap<>();
        propReq.put("nombre", "Nueva Propiedad de Reemplazo");
        propReq.put("direccion", "Carrera 7 # 72-10");
        propReq.put("ciudad", "Bogota");
        propReq.put("idTipoPropiedad", 1L);
        propReq.put("tipoOcupacionPredominante", "MIXTA");

        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isConflict());

        // Inactivar la propiedad existente PROP_A1_ID con elevación de contexto RLS
        jdbcTemplate.execute(String.format("""
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
                PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
                UPDATE PROPIEDADES SET ESTADO = 'INACTIVA' WHERE ID_PROPIEDAD = %d;
                PKG_SAED_SESSION.CLEAR_CONTEXT;
            END;
            """, PROP_A1_ID));
        propertyStatusService.invalidateCache(PROP_A1_ID);

        // Ahora el conteo de propiedades activas es 0/1 -> Debe crearse exitosamente
        mockMvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + tokenOrgA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SEC-F7-12: Prevención de race condition en último cupo mediante bloqueo pesimista")
    public void test_SEC_F7_12_ConcurrencyPessimisticLocking_RaceConditionPrevention() throws Exception {
        String tokenOrgA = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_A);

        // Dejar a ORG_A con exactamente 0 propiedades (inactivar con elevación de contexto RLS)
        jdbcTemplate.execute(String.format("""
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
                PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
                UPDATE PROPIEDADES SET ESTADO = 'INACTIVA' WHERE ID_ORGANIZACION = %d;
                PKG_SAED_SESSION.CLEAR_CONTEXT;
            END;
            """, ORG_A_ID));
        propertyStatusService.invalidateCache(PROP_A1_ID);

        // Dos hilos concurrentes intentan consumir el único cupo disponible (0/1 -> 1 cupo libre)
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> task1 = () -> {
            Map<String, Object> body = Map.of(
                    "nombre", "Propiedad Concurrente Hilo 1",
                    "direccion", "Calle 1 Concurrente",
                    "ciudad", "Bogota",
                    "idTipoPropiedad", 1L,
                    "tipoOcupacionPredominante", "MIXTA"
            );
            MvcResult res = mockMvc.perform(post("/api/v1/properties")
                    .header("Authorization", "Bearer " + tokenOrgA)
                    .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        Callable<Integer> task2 = () -> {
            Map<String, Object> body = Map.of(
                    "nombre", "Propiedad Concurrente Hilo 2",
                    "direccion", "Calle 2 Concurrente",
                    "ciudad", "Bogota",
                    "idTipoPropiedad", 1L,
                    "tipoOcupacionPredominante", "MIXTA"
            );
            MvcResult res = mockMvc.perform(post("/api/v1/properties")
                    .header("Authorization", "Bearer " + tokenOrgA)
                    .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_A))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        int status1 = f1.get();
        int status2 = f2.get();
        executor.shutdown();

        // Exactamente uno debe tener éxito (201 Created) y el otro debe recibir 409 Conflict
        boolean oneSucceeded = (status1 == 201 && status2 == 409) || (status1 == 409 && status2 == 201);
        assertTrue(oneSucceeded, String.format("Se esperaba exactamente un 201 y un 409, pero se obtuvo %d y %d", status1, status2));

        // Verificar en base de datos que el número de propiedades activas en ORG_A es estrictamente 1 (nunca 2)
        Integer activeCount = jdbcTemplate.execute((java.sql.Connection conn) -> {
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM PROPIEDADES WHERE ID_ORGANIZACION = ? AND ESTADO = 'ACTIVA'")) {
                ps.setLong(1, ORG_A_ID);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } finally {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
                } catch (Exception ignored) {}
            }
        });
        assertEquals(1, activeCount, "El número de propiedades activas no puede superar el límite del plan bajo concurrencia");
    }
}
