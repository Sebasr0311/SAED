package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.exception.ModuleNotEntitledException;
import com.saed.backend.platform.service.ModuleEntitlementService;
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

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ModuleEntitlementsSecurityTest — Suite integral de verificación para GAP-ENT-03 (P1):
 * Entitlements de Módulos SAED 2.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ModuleEntitlementsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ModuleEntitlementService moduleEntitlementService;

    @MockBean
    private AssignmentService assignmentService;

    // Organizaciones de prueba
    private static final long ORG_FREE_ID   = 8801L; // Plan 1 (FREE)
    private static final long ORG_PRO_ID    = 8802L; // Plan 2 (PRO - incluye OBRAS, POLIZAS, RESERVAS; NO ASAMBLEAS)
    private static final long ORG_ENT_ID    = 8803L; // Plan 3 (ENTERPRISE - incluye todos los módulos)
    private static final long ORG_SUSP_ID   = 8804L; // Plan 2 (PRO, pero membresía SUSPENDIDA)
    private static final long ORG_EXP_ID    = 8805L; // Plan 2 (PRO, pero membresía EXPIRADA)
    private static final long ORG_TRIAL_ID  = 8806L; // Plan 2 (PRO, membresía PRUEBA activa)

    // Propiedades de prueba
    private static final long PROP_FREE_ID  = 8801L;
    private static final long PROP_PRO_ID   = 8802L;
    private static final long PROP_ENT_ID   = 8803L;
    private static final long PROP_SUSP_ID  = 8804L;
    private static final long PROP_EXP_ID   = 8805L;
    private static final long PROP_TRIAL_ID = 8806L;

    // Usuarios y asignaciones
    private static final long USER_FREE     = 8801L;
    private static final long ASSIGN_FREE   = 8801L;

    private static final long USER_PRO      = 8802L;
    private static final long ASSIGN_PRO    = 8802L;

    private static final long USER_ENT      = 8803L;
    private static final long ASSIGN_ENT    = 8803L;

    private static final long USER_SUSP     = 8804L;
    private static final long ASSIGN_SUSP   = 8804L;

    private static final long USER_EXP      = 8805L;
    private static final long ASSIGN_EXP    = 8805L;

    private static final long USER_TRIAL    = 8806L;
    private static final long ASSIGN_TRIAL  = 8806L;

    private static final long USER_PORTERO_PRO   = 8807L;
    private static final long ASSIGN_PORTERO_PRO = 8807L;

    private String tokenFree;
    private String tokenPro;
    private String tokenEnt;
    private String tokenSusp;
    private String tokenExp;
    private String tokenTrial;
    private String tokenPorteroPro;

    @BeforeEach
    public void setUp() {
        // Establecer SaedContext elevado para que todas las conexiones del pool en setup eludan RLS
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // 1. Asegurar organizaciones
        seedOrganizacion(ORG_FREE_ID, "Org Free Test", "900008801-1", "free@test.com");
        seedOrganizacion(ORG_PRO_ID, "Org Pro Test", "900008802-2", "pro@test.com");
        seedOrganizacion(ORG_ENT_ID, "Org Enterprise Test", "900008803-3", "enterprise@test.com");
        seedOrganizacion(ORG_SUSP_ID, "Org Suspended Test", "900008804-4", "suspended@test.com");
        seedOrganizacion(ORG_EXP_ID, "Org Expired Test", "900008805-5", "expired@test.com");
        seedOrganizacion(ORG_TRIAL_ID, "Org Trial Test", "900008806-6", "trial@test.com");

        // 2. Asegurar propiedades
        seedPropiedad(PROP_FREE_ID, ORG_FREE_ID, "Propiedad Free");
        seedPropiedad(PROP_PRO_ID, ORG_PRO_ID, "Propiedad Pro");
        seedPropiedad(PROP_ENT_ID, ORG_ENT_ID, "Propiedad Enterprise");
        seedPropiedad(PROP_SUSP_ID, ORG_SUSP_ID, "Propiedad Suspended");
        seedPropiedad(PROP_EXP_ID, ORG_EXP_ID, "Propiedad Expired");
        seedPropiedad(PROP_TRIAL_ID, ORG_TRIAL_ID, "Propiedad Trial");

        // 3. Limpiar y recrear membresías de prueba
        jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION IN (?, ?, ?, ?, ?, ?)",
                ORG_FREE_ID, ORG_PRO_ID, ORG_ENT_ID, ORG_SUSP_ID, ORG_EXP_ID, ORG_TRIAL_ID);

        seedMembresia(ORG_FREE_ID, 1L, "ACTIVA", "N", 365);
        seedMembresia(ORG_PRO_ID, 2L, "ACTIVA", "N", 365);
        seedMembresia(ORG_ENT_ID, 3L, "ACTIVA", "N", 365);
        seedMembresia(ORG_SUSP_ID, 2L, "SUSPENDIDA", "N", 365);
        seedMembresia(ORG_EXP_ID, 2L, "EXPIRADA", "N", -10);
        seedMembresia(ORG_TRIAL_ID, 2L, "PRUEBA", "S", 14);

        // 4. Personas y usuarios
        seedPersonaUsuario(USER_FREE, USER_FREE, "admin_free", "free@test.com", "DOC-8801");
        seedPersonaUsuario(USER_PRO, USER_PRO, "admin_pro", "pro@test.com", "DOC-8802");
        seedPersonaUsuario(USER_ENT, USER_ENT, "admin_ent", "ent@test.com", "DOC-8803");
        seedPersonaUsuario(USER_SUSP, USER_SUSP, "admin_susp", "susp@test.com", "DOC-8804");
        seedPersonaUsuario(USER_EXP, USER_EXP, "admin_exp", "exp@test.com", "DOC-8805");
        seedPersonaUsuario(USER_TRIAL, USER_TRIAL, "admin_trial", "trial@test.com", "DOC-8806");
        seedPersonaUsuario(USER_PORTERO_PRO, USER_PORTERO_PRO, "portero_pro", "portero@test.com", "DOC-8807");

        // 5. Asignaciones reales en Oracle para que PKG_SAED_SESSION.SET_CONTEXT valide
        seedAsignaciones();

        // 6. Configurar mock de asignaciones
        setupAssignmentMocks();

        // 7. Generar tokens JWT
        tokenFree = jwtProvider.generateIdentityToken(USER_FREE);
        tokenPro = jwtProvider.generateIdentityToken(USER_PRO);
        tokenEnt = jwtProvider.generateIdentityToken(USER_ENT);
        tokenSusp = jwtProvider.generateIdentityToken(USER_SUSP);
        tokenExp = jwtProvider.generateIdentityToken(USER_EXP);
        tokenTrial = jwtProvider.generateIdentityToken(USER_TRIAL);
        tokenPorteroPro = jwtProvider.generateIdentityToken(USER_PORTERO_PRO);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void tearDown() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION IN (?, ?, ?, ?, ?, ?)",
                    ORG_FREE_ID, ORG_PRO_ID, ORG_ENT_ID, ORG_SUSP_ID, ORG_EXP_ID, ORG_TRIAL_ID);
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void seedOrganizacion(Long orgId, String nombre, String nit, String email) {
        jdbcTemplate.execute(String.format("""
            MERGE INTO ORGANIZACIONES o
            USING (SELECT %d AS id, '%s' AS nom, '%s' AS nit, '%s' AS em FROM DUAL) s
            ON (o.ID_ORGANIZACION = s.id)
            WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO)
            VALUES (s.id, s.nom, s.nit, s.em, 'ACTIVA')
            WHEN MATCHED THEN UPDATE SET o.ESTADO = 'ACTIVA'
            """, orgId, nombre, nit, email));
    }

    private void seedPropiedad(Long propId, Long orgId, String nombre) {
        jdbcTemplate.execute(String.format("""
            MERGE INTO PROPIEDADES p USING (SELECT %d AS id FROM DUAL) s ON (p.ID_PROPIEDAD = s.id)
            WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO)
            VALUES (%d, %d, 1, '%s', 'Calle Falsa 123', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA')
            WHEN MATCHED THEN UPDATE SET p.ESTADO = 'ACTIVA'
            """, propId, propId, orgId, nombre));
    }

    private void seedMembresia(Long orgId, Long planId, String estado, String esPrueba, int diasValidez) {
        String fechaFinExpr = diasValidez >= 0
                ? String.format("TRUNC(SYSDATE) + %d", diasValidez)
                : String.format("TRUNC(SYSDATE) - %d", Math.abs(diasValidez));
        jdbcTemplate.execute(String.format("""
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
            VALUES (%d, %d, TRUNC(SYSDATE) - 30, %s, '%s', '%s')
            """, orgId, planId, fechaFinExpr, estado, esPrueba));
    }

    private void seedPersonaUsuario(Long personaId, Long usuarioId, String nombre, String email, String doc) {
        jdbcTemplate.update("""
            MERGE INTO PERSONAS p USING (SELECT ? AS id, 1 AS td, ? AS nd, 'NATURAL' AS tp,
                ? AS pn, 'Test' AS pa, ? AS em FROM DUAL) s ON (p.ID_PERSONA = s.id)
            WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL)
                VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)
            """, personaId, doc, nombre, email);

        jdbcTemplate.update("""
            MERGE INTO USUARIOS u USING (SELECT ? AS id, ? AS pid, ? AS nu, ? AS em FROM DUAL) s
            ON (u.ID_USUARIO = s.id)
            WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO)
                VALUES (s.id, s.pid, s.nu, s.em, 'hash_test', 'ACTIVO')
            WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'
            """, usuarioId, personaId, nombre, email);
    }

    private void seedAsignaciones() {
        jdbcTemplate.execute("""
            DECLARE
                v_rol_prop  NUMBER;
                v_rol_port  NUMBER;
            BEGIN
                SELECT ID_ROL INTO v_rol_prop FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD';
                SELECT ID_ROL INTO v_rol_port FROM ROLES WHERE CODIGO = 'PORTERO';

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8801 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8801, 8801, v_rol_prop, 8801, 8801, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8801, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8802 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8802, 8802, v_rol_prop, 8802, 8802, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8802, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8803 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8803, 8803, v_rol_prop, 8803, 8803, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8803, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8804 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8804, 8804, v_rol_prop, 8804, 8804, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8804, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8805 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8805, 8805, v_rol_prop, 8805, 8805, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8805, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8806 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8806, 8806, v_rol_prop, 8806, 8806, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8806, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_prop;

                MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 8807 AS aid FROM DUAL) s ON (ua.ID_ASIGNACION = s.aid)
                WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (8807, 8807, v_rol_port, 8802, 8802, 'ACTIVA', TRUNC(SYSDATE))
                WHEN MATCHED THEN UPDATE SET ua.ESTADO = 'ACTIVA', ua.ID_PROPIEDAD = 8802, ua.FECHA_FIN = NULL, ua.ID_ROL = v_rol_port;
            END;
        """);
    }

    private void setupAssignmentMocks() {
        mockAssignment(ASSIGN_FREE, USER_FREE, ORG_FREE_ID, PROP_FREE_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_PRO, USER_PRO, ORG_PRO_ID, PROP_PRO_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_ENT, USER_ENT, ORG_ENT_ID, PROP_ENT_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_SUSP, USER_SUSP, ORG_SUSP_ID, PROP_SUSP_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_EXP, USER_EXP, ORG_EXP_ID, PROP_EXP_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_TRIAL, USER_TRIAL, ORG_TRIAL_ID, PROP_TRIAL_ID, "ADMIN_PROPIEDAD", "PROPIEDAD");
        mockAssignment(ASSIGN_PORTERO_PRO, USER_PORTERO_PRO, ORG_PRO_ID, PROP_PRO_ID, "PORTERO", "PROPIEDAD");
    }

    private void mockAssignment(Long assignId, Long userId, Long orgId, Long propId, String roleCode, String roleScope) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(assignId);

        RoleDTO rol = new RoleDTO();
        rol.setCodigo(roleCode);
        rol.setAlcance(roleScope);
        dto.setRol(rol);

        OrganizationDTO org = new OrganizationDTO();
        org.setId(orgId);
        org.setNombre("Org " + orgId);
        org.setEstado("ACTIVA");
        dto.setOrganizacion(org);

        if (propId != null) {
            PropertyDTO prop = new PropertyDTO();
            prop.setId(propId);
            prop.setIdOrganizacion(orgId);
            prop.setNombre("Propiedad " + propId);
            prop.setEstado("ACTIVA");
            dto.setPropiedad(prop);
        }

        when(assignmentService.validateAssignment(eq(assignId), eq(userId)))
                .thenReturn(Optional.of(dto));
    }

    // =========================================================================
    // 1. ESCENARIO A: PLAN SIN MÓDULO -> 403 FORBIDDEN CON MODULE_NOT_ENTITLED
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO A: Plan PRO no incluye ASAMBLEAS -> 403 con MODULE_NOT_ENTITLED")
    public void orgPro_accessAsambleas_forbiddenModuleNotEntitled() throws Exception {
        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("ASAMBLEAS"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("ESCENARIO A: Plan FREE no incluye OBRAS -> 403 con MODULE_NOT_ENTITLED")
    public void orgFree_accessObras_forbiddenModuleNotEntitled() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenFree)
                        .header("X-Assignment-Id", ASSIGN_FREE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("OBRAS"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("ESCENARIO A: Plan FREE no incluye POLIZAS -> 403 con MODULE_NOT_ENTITLED")
    public void orgFree_accessPolizas_forbiddenModuleNotEntitled() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas")
                        .header("Authorization", "Bearer " + tokenFree)
                        .header("X-Assignment-Id", ASSIGN_FREE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("POLIZAS"));
    }

    @Test
    @DisplayName("ESCENARIO A: Plan FREE no incluye RESERVAS -> 403 con MODULE_NOT_ENTITLED")
    public void orgFree_accessReservas_forbiddenModuleNotEntitled() throws Exception {
        mockMvc.perform(get("/api/v1/zonas-comunes")
                        .header("Authorization", "Bearer " + tokenFree)
                        .header("X-Assignment-Id", ASSIGN_FREE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("RESERVAS"));
    }

    // =========================================================================
    // 2. ESCENARIO B: PLAN CON MÓDULO HABILITADO -> 200 OK
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO B: Plan ENTERPRISE incluye ASAMBLEAS -> 200 OK")
    public void orgEnterprise_accessAsambleas_successOk() throws Exception {
        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenEnt)
                        .header("X-Assignment-Id", ASSIGN_ENT)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ESCENARIO B: Plan PRO incluye OBRAS -> 200 OK")
    public void orgPro_accessObras_successOk() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ESCENARIO B: Plan PRO incluye POLIZAS -> 200 OK")
    public void orgPro_accessPolizas_successOk() throws Exception {
        mockMvc.perform(get("/api/v1/seguros/polizas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ESCENARIO B: Plan PRO incluye RESERVAS -> 200 OK")
    public void orgPro_accessReservas_successOk() throws Exception {
        mockMvc.perform(get("/api/v1/zonas-comunes")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. ESCENARIO C: CAMBIO DINÁMICO DE PLAN EN BASE DE DATOS -> INMEDIATO
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO C: Actualizar membresía de PRO a ENTERPRISE desbloquea ASAMBLEAS inmediatamente sin reinicio")
    public void dynamicPlanUpgradeInDatabase_immediatelyGrantsAccess() throws Exception {
        // 1. Estado inicial: Org PRO -> 403 MODULE_NOT_ENTITLED
        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        // 2. Simular pago/upgrade en base de datos: pasar a Plan 3 (ENTERPRISE)
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("UPDATE MEMBRESIAS SET ID_PLAN = 3 WHERE ID_ORGANIZACION = ?", ORG_PRO_ID);
        } finally {
            try {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } catch (Exception ignored) {}
            SaedContextHolder.clearContext();
        }

        // 3. Próxima petición refleja inmediatamente el entitlement sin lag ni caché estática
        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 4. Downgrade a Plan 2 (PRO) bloquea de inmediato nuevamente
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("UPDATE MEMBRESIAS SET ID_PLAN = 2 WHERE ID_ORGANIZACION = ?", ORG_PRO_ID);
        } finally {
            try {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } catch (Exception ignored) {}
            SaedContextHolder.clearContext();
        }

        mockMvc.perform(get("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    // =========================================================================
    // 4. ESCENARIO D: MEMBRESÍA SUSPENDIDA O VENCIDA -> 403 FORBIDDEN
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO D: Membresía SUSPENDIDA rechaza acceso a módulo incluso si está en el plan")
    public void suspendedMembership_rejectsAccessToEntitledModule() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenSusp)
                        .header("X-Assignment-Id", ASSIGN_SUSP)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.message", containsString("SUSPENDIDA")));
    }

    @Test
    @DisplayName("ESCENARIO D: Membresía EXPIRADA rechaza acceso a módulo")
    public void expiredMembership_rejectsAccessToEntitledModule() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenExp)
                        .header("X-Assignment-Id", ASSIGN_EXP)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    // =========================================================================
    // 5. ESCENARIO E: MEMBRESÍA DE PRUEBA (PRUEBA) ACTIVA -> PERMITE MÓDULOS
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO E: Membresía en PRUEBA activa concede acceso a módulos incluidos en el plan")
    public void trialMembershipActive_grantsAccessToEntitledModule() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenTrial)
                        .header("X-Assignment-Id", ASSIGN_TRIAL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 6. ESCENARIO F: ROL SIN PERMISOS RBAC -> 403 FORBIDDEN (PreAuthorize primero)
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO F: Rol sin permiso RBAC (PORTERO en obras/admin) es rechazado con FORBIDDEN antes del entitlement")
    public void roleWithoutPermission_rejectedByRbacForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenPorteroPro)
                        .header("X-Assignment-Id", ASSIGN_PORTERO_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // =========================================================================
    // 7. ESCENARIO G: ROL CON PERMISOS PERO PLAN SIN MÓDULO -> MODULE_NOT_ENTITLED
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO G: ADMIN_ORGANIZACION tiene rol válido pero plan FREE sin módulo -> MODULE_NOT_ENTITLED")
    public void roleWithPermission_butPlanWithoutModule_rejectedByEntitlement() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenFree)
                        .header("X-Assignment-Id", ASSIGN_FREE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    // =========================================================================
    // 8. ESCENARIO H: AISLAMIENTO MULTI-TENANT CROSS-ORGANIZATION
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO H: Aislamiento estricto multi-tenant: Org FREE no hereda módulos de Org PRO")
    public void multiTenantCrossOrganizationIsolation() throws Exception {
        // Petición Org PRO -> 200 OK
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Petición concurrente Org FREE -> 403 MODULE_NOT_ENTITLED
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenFree)
                        .header("X-Assignment-Id", ASSIGN_FREE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    // =========================================================================
    // 9. ESCENARIO I: COBERTURA DE MÉTODOS HTTP (GET, POST)
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO I: Bloqueo de métodos POST sobre módulos no contratados")
    public void postMethod_forbiddenOnUnentitledModule() throws Exception {
        Map<String, Object> body = Map.of(
                "tipo", "ORDINARIA",
                "modalidad", "PRESENCIAL",
                "titulo", "Asamblea Anual Ordinaria",
                "fechaHoraPrimeraConv", "2026-10-01T10:00:00",
                "lugarOEnlace", "Salón Comunal",
                "ordenDelDia", "1. Verificación de quórum"
        );
        mockMvc.perform(post("/api/v1/asambleas")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("ASAMBLEAS"));
    }

    // =========================================================================
    // 10. ESCENARIO J: CÓDIGO DE MÓDULO DESCONOCIDO
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO J: Código de módulo desconocido lanza excepción determinista")
    public void unknownModule_throwsDeterministicException() {
        ModuleNotEntitledException ex = assertThrows(
                ModuleNotEntitledException.class,
                () -> moduleEntitlementService.checkModuleAccess("MODULO_FANTASMA_INEXISTENTE")
        );
        assertEquals("MODULO_FANTASMA_INEXISTENTE", ex.getModuleCode());
    }

    // =========================================================================
    // 11. ESCENARIO K: ENDPOINT DE CAPACIDADES GET /api/v1/me/entitlements
    // =========================================================================

    @Test
    @DisplayName("ESCENARIO K: GET /api/v1/me/entitlements retorna módulos habilitados del tenant")
    public void meEntitlementsEndpoint_returnsEnabledModulesForTenant() throws Exception {
        // Org PRO: debe incluir OBRAS, POLIZAS, RESERVAS pero NO ASAMBLEAS
        mockMvc.perform(get("/api/v1/me/entitlements")
                        .header("Authorization", "Bearer " + tokenPro)
                        .header("X-Assignment-Id", ASSIGN_PRO)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.modules", hasItems("OBRAS", "POLIZAS", "RESERVAS", "PAQUETES", "PARQUEADEROS", "PQRS", "FINANZAS")))
                .andExpect(jsonPath("$.data.modules", not(hasItem("ASAMBLEAS"))));

        // Org ENTERPRISE: debe incluir ASAMBLEAS además de los anteriores
        mockMvc.perform(get("/api/v1/me/entitlements")
                        .header("Authorization", "Bearer " + tokenEnt)
                        .header("X-Assignment-Id", ASSIGN_ENT)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.modules", hasItems("ASAMBLEAS", "OBRAS", "POLIZAS", "RESERVAS")));
    }
}
