package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.service.ReportesService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
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

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F11_04_IngresosPresupuestalesIntegrationTest
 *
 * Suite exhaustiva de integración para el cálculo y auditoría de la ejecución presupuestal global (F11-04 Bloque B):
 * 1.  adminPropiedad consulta ejecución presupuestal exitosa de su propiedad.
 * 2.  Filtro por vigencia fiscal acota resultados al año solicitado.
 * 3.  Solo pagos aprobados ('APROBADO') se suman a ingresos ejecutados; estados pendientes/rechazados/anulados se ignoran.
 * 4.  Múltiples pagos por unidad se agregan correctamente sin duplicación.
 * 5.  Pagos fuera de la vigencia fiscal son ignorados en el cálculo.
 * 6.  Aislamiento estricto de propiedad: pagos de otras propiedades no se suman.
 * 7.  Sin presupuesto registrado: retorna montos en 0 y porcentajes en 0.0.
 * 8.  Sin pagos aprobados: retorna ingreso ejecutado en 0 y porcentaje en 0.0.
 * 9.  Cálculo matemático determinista de superavitPresupuestado y superavitEjecutado.
 * 10. Estado financiero DEFICIT cuando los egresos ejecutados superan a los ingresos ejecutados.
 * 11. Estado financiero SUPERAVIT cuando los ingresos ejecutados superan a los egresos ejecutados.
 * 12. adminOrganizacion consulta propiedad específica perteneciente a su organización.
 * 13. adminOrganizacion consulta consolidado global de toda su organización (sin propertyId).
 * 14. adminOrganizacion recibe 403 al intentar consultar propiedad ajena.
 * 15. adminPropiedad recibe 403 al intentar adulterar propertyId en query param.
 * 16. Roles no autorizados (RESIDENTE, PORTERO, anónimo) reciben 403 o 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_04_IngresosPresupuestalesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportesService reportesService;

    @MockBean
    private AssignmentService assignmentService;

    // Identidades aisladas para Bloque B
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 9831L;
    private static final long ASSIGN_ADMIN_ORG_1 = 9931L;

    private static final long USER_ADMIN_PROP_1 = 9832L;
    private static final long ASSIGN_ADMIN_PROP_1 = 9932L;

    private static final long USER_ADMIN_PROP_2 = 9833L;
    private static final long ASSIGN_ADMIN_PROP_2 = 9933L;

    private static final long USER_RESIDENTE = 9835L;
    private static final long ASSIGN_RESIDENTE = 9935L;

    private static final long USER_PORTERO = 9836L;
    private static final long ASSIGN_PORTERO = 9936L;

    // Scopes aislados para Bloque B (evitan contaminación por datos sembrados en otras pruebas)
    private static final long ORG_1_ID = 9701L;
    private static final long ORG_2_ID = 9702L;

    private static final long PROP_1_ID = 9801L;
    private static final long PROP_2_ID = 9802L;

    private static final long UNIDAD_1_ID = 9811L;
    private static final long UNIDAD_2_ID = 9821L;

    private static final long CONCEPTO_1_ID = 9851L;
    private static final long CONCEPTO_2_ID = 9852L;

    // Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente;
    private String tokenPortero;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            ensureBaseData();
            setupMockAssignments();

            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);
            tokenPortero = jwtProvider.generateIdentityToken(USER_PORTERO);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        clearContext();
    }

    private void setElevatedContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        SaedContext ctx = new SaedContext();
        ctx.setUserId(1L);
        ctx.setOrganizationId(1L);
        ctx.setPropertyId(1L);
        ctx.setRoleCode("SUPERADMIN");
        SaedContextHolder.setContext(ctx);
    }

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void ensureBaseData() {
        // Organizaciones
        ensureOrganizacion(ORG_1_ID, "Organización Central Bloque B", "900009701-1", "org9701@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Bloque B", "900009702-2", "org9702@saed.com");

        // Propiedades
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial Bloque B");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Bloque B");

        // Unidades
        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.250000));

        // Conceptos de Cobro
        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "CUOTA_ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_2_ID, "CUOTA_ORD_2", "Cuota Ordinaria Prop 2");

        // Personas & Usuarios
        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin_f11_04@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000009831", "Admin", "OrgUno", "adminorg1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000009832", "Admin", "PropUno", "adminprop1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000009833", "Admin", "PropDos", "adminprop2_f11_04@saed.com");
        ensurePersona(USER_RESIDENTE, "1000009835", "Carlos", "Residente", "residente_f11_04@saed.com");
        ensurePersona(USER_PORTERO, "1000009836", "Pedro", "Portero", "portero_f11_04@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_f11_04", "superadmin_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_9831", "adminorg1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_9832", "adminprop1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_9833", "adminprop2_f11_04@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_9835", "residente_f11_04@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_9836", "portero_f11_04@saed.com");

        // Asignaciones
        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Limpieza de datos transaccionales para asegurar determinismo
        try {
            jdbcTemplate.execute("DELETE FROM GASTOS_SOPORTES_HISTORIAL WHERE ID_GASTO IN (SELECT ID_GASTO FROM GASTOS WHERE ID_PROPIEDAD IN (9801, 9802))");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM GASTOS WHERE ID_PROPIEDAD IN (9801, 9802)");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM PAGO_DETALLE WHERE ID_PAGO IN (SELECT ID_PAGO FROM PAGOS WHERE ID_UNIDAD IN (9811, 9821))");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM TRANSACCIONES_PAGO WHERE ID_PAGO IN (SELECT ID_PAGO FROM PAGOS WHERE ID_UNIDAD IN (9811, 9821))");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (9811, 9821)");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DELETE FROM PRESUPUESTOS WHERE ID_PROPIEDAD IN (9801, 9802)");
        } catch (Exception ignored) {}

        // 1. Presupuestos para Propiedad 1 (Vigencia 2026)
        // Ingresos presupuestados: 50M + 10M = 60M
        jdbcTemplate.update("""
            INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO)
            VALUES (?, 2026, 'CUOTAS ORDINARIAS', 'INGRESO', 50000000, 0, 'APROBADO')
        """, PROP_1_ID);

        jdbcTemplate.update("""
            INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO)
            VALUES (?, 2026, 'ALQUILER SALON SOCIAL', 'INGRESO', 10000000, 0, 'APROBADO')
        """, PROP_1_ID);

        // Egresos presupuestados: 40M
        jdbcTemplate.update("""
            INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO)
            VALUES (?, 2026, 'MANTENIMIENTO PREVENTIVO', 'EGRESO', 40000000, 0, 'APROBADO')
        """, PROP_1_ID);

        // 2. Pagos para Propiedad 1 (Unidad 9811) en 2026
        // Pago 1: Aprobado por 15M
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 15000000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-03-15 10:00:00', 'REF-PAGO-001')
        """, UNIDAD_1_ID);

        // Pago 2: Aprobado por 10M
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 10000000, 'CONSIGNACION', 'APROBADO', TIMESTAMP '2026-06-20 11:30:00', 'REF-PAGO-002')
        """, UNIDAD_1_ID);

        // Pago 3: Pendiente de aprobación por 8M (NO debe sumarse a ingresos ejecutados)
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 8000000, 'TRANSFERENCIA', 'PENDIENTE_APROBACION', TIMESTAMP '2026-07-01 09:00:00', 'REF-PAGO-PEND')
        """, UNIDAD_1_ID);

        // Pago 4: Rechazado por 5M (NO debe sumarse a ingresos ejecutados)
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 5000000, 'TRANSFERENCIA', 'RECHAZADO', TIMESTAMP '2026-08-01 10:00:00', 'REF-PAGO-RECH')
        """, UNIDAD_1_ID);

        // Pago 5: Anulado por 3M (NO debe sumarse a ingresos ejecutados)
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 3000000, 'TRANSFERENCIA', 'ANULADO', TIMESTAMP '2026-08-15 12:00:00', 'REF-PAGO-ANUL')
        """, UNIDAD_1_ID);

        // Pago 6: Aprobado en año 2025 por 7M (NO debe sumarse en vigencia 2026)
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 7000000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2025-11-20 14:00:00', 'REF-PAGO-2025')
        """, UNIDAD_1_ID);

        // Pago 7: Aprobado en Propiedad 2 (Unidad 9821) por 30M (NO debe sumarse a Propiedad 1)
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 30000000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-05-10 16:00:00', 'REF-PAGO-PROP2')
        """, UNIDAD_2_ID);

        // 3. Gastos para Propiedad 1 en 2026
        // Gasto 1: Pagado por 12M
        jdbcTemplate.update("""
            INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, METODO_PAGO, ESTADO, FECHA_GASTO)
            VALUES (?, 'MANTENIMIENTO', 'Ascensores SA', 12000000, 'TRANSFERENCIA', 'PAGADO', DATE '2026-04-10')
        """, PROP_1_ID);

        // Gasto 2: Registrado (no pagado aún) por 5M (NO debe sumarse a egresos ejecutados)
        jdbcTemplate.update("""
            INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, METODO_PAGO, ESTADO, FECHA_GASTO)
            VALUES (?, 'JARDINERIA', 'Jardines Verdes', 5000000, 'EFECTIVO', 'REGISTRADO', DATE '2026-05-15')
        """, PROP_1_ID);

        // Gasto 3: Anulado por 2M (NO debe sumarse)
        jdbcTemplate.update("""
            INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, METODO_PAGO, ESTADO, FECHA_GASTO)
            VALUES (?, 'LIMPIEZA', 'Aseo Total', 2000000, 'EFECTIVO', 'ANULADO', DATE '2026-06-01')
        """, PROP_1_ID);
    }

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle 100 # 15-20', 'Bogotá', 'ACTIVA')",
                    id, orgId, nombre);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coef);
        }
    }

    private void ensureConcepto(long id, long orgId, long propId, String codigo, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM CONCEPTOS_COBRO WHERE ID_CONCEPTO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) VALUES (?, ?, ?, ?, ?, 'ADMINISTRACION', 'ACTIVO')",
                    id, orgId, propId, codigo, nombre);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, SALT_PASSWORD, ESTADO) VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'salt', 'ACTIVO')",
                    id, personaId, username, email);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, idRol, idOrg, idProp, idUnidad);
        } else {
            jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = ?, ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA' WHERE ID_ASIGNACION = ?",
                    idUsuario, idRol, idOrg, idProp, idUnidad, id);
        }
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central Bloque B");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Bloque B");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial Bloque B");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Bloque B");

        AssignmentResponseDTO sa = new AssignmentResponseDTO();
        sa.setIdAsignacion(ASSIGN_SUPERADMIN);
        sa.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(sa));

        AssignmentResponseDTO aOrg1 = new AssignmentResponseDTO();
        aOrg1.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        aOrg1.setOrganizacion(org1);
        aOrg1.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(aOrg1));

        AssignmentResponseDTO aProp1 = new AssignmentResponseDTO();
        aProp1.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        aProp1.setOrganizacion(org1);
        aProp1.setPropiedad(prop1);
        aProp1.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(aProp1));

        AssignmentResponseDTO aProp2 = new AssignmentResponseDTO();
        aProp2.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        aProp2.setOrganizacion(org2);
        aProp2.setPropiedad(prop2);
        aProp2.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(aProp2));

        AssignmentResponseDTO res = new AssignmentResponseDTO();
        res.setIdAsignacion(ASSIGN_RESIDENTE);
        res.setOrganizacion(org1);
        res.setPropiedad(prop1);
        res.setUnidad(new UnitDTO(UNIDAD_1_ID, "A101"));
        res.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(res));

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
    }

    // =========================================================================
    // CASOS DE PRUEBA DETERMINISTAS (16 TEST SCENARIOS)
    // =========================================================================

    /**
     * 1. Consulta exitosa de ejecución presupuestal por ADMIN_PROPIEDAD.
     */
    @Test
    @Order(1)
    public void testEjecucionPresupuestal_adminPropiedad_exitoso() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vigenciaAnio").value(2026))
                .andExpect(jsonPath("$.data.ingresosPresupuestados").value(60000000.0))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(25000000.0))
                .andExpect(jsonPath("$.data.porcentajeEjecucionIngresos").value(41.7))
                .andExpect(jsonPath("$.data.egresosPresupuestados").value(40000000.0))
                .andExpect(jsonPath("$.data.egresosEjecutados").value(12000000.0))
                .andExpect(jsonPath("$.data.porcentajeEjecucionEgresos").value(30.0))
                .andExpect(jsonPath("$.data.superavitPresupuestado").value(20000000.0))
                .andExpect(jsonPath("$.data.superavitEjecutado").value(13000000.0))
                .andExpect(jsonPath("$.data.estadoFinanciero").value("SUPERAVIT"))
                .andExpect(jsonPath("$.data.totalPagosAprobados").value(2))
                .andExpect(jsonPath("$.data.totalGastosPagados").value(1))
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
    }

    /**
     * 2. Filtro de vigencia temporal específico (ej. vigencia=2025).
     */
    @Test
    @Order(2)
    public void testEjecucionPresupuestal_filtroVigencia() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2025")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vigenciaAnio").value(2025))
                .andExpect(jsonPath("$.data.ingresosPresupuestados").value(0.0))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(7000000.0))
                .andExpect(jsonPath("$.data.totalPagosAprobados").value(1));
    }

    /**
     * 3. Solo pagos aprobados ('APROBADO') se suman a ingresos ejecutados.
     */
    @Test
    @Order(3)
    public void testEjecucionPresupuestal_soloPagosAprobados() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");

        // Existen 5 pagos registrados en 2026 para Prop 9801:
        // Aprobados: 15M + 10M = 25M (2 pagos)
        // Ignorados: 8M (PENDIENTE) + 5M (RECHAZADO) + 3M (ANULADO) = 16M
        assertEquals(25000000.0, data.get("ingresosEjecutados").asDouble(), 0.01);
        assertEquals(2, data.get("totalPagosAprobados").asLong());
    }

    /**
     * 4. Múltiples pagos por unidad se agregan correctamente sin duplicación.
     */
    @Test
    @Order(4)
    public void testEjecucionPresupuestal_multiplesPagosUnidad() throws Exception {
        // Unidad 9101 tiene dos pagos aprobados: 15M y 10M.
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(25000000.0, data.get("ingresosEjecutados").asDouble(), 0.01);
    }

    /**
     * 5. Pagos fuera de la vigencia fiscal son ignorados en el cálculo.
     */
    @Test
    @Order(5)
    public void testEjecucionPresupuestal_pagoFueraDeVigencia_ignorado() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        // El pago de 7M en 2025 no debe estar en 2026
        assertEquals(25000000.0, data.get("ingresosEjecutados").asDouble(), 0.01);
    }

    /**
     * 6. Aislamiento de propiedad: pagos de otra propiedad no se suman.
     */
    @Test
    @Order(6)
    public void testEjecucionPresupuestal_aislamientoPropiedad() throws Exception {
        // En Propiedad 2 (Unidad 9201) hay un pago aprobado de 30M en 2026
        // Propiedad 1 NO debe incluir esos 30M
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(25000000.0, data.get("ingresosEjecutados").asDouble(), 0.01);
        assertNotEquals(55000000.0, data.get("ingresosEjecutados").asDouble());
    }

    /**
     * 7. Sin presupuesto registrado en la vigencia: retorna montos en 0 y porcentajes en 0.0.
     */
    @Test
    @Order(7)
    public void testEjecucionPresupuestal_sinPresupuesto_retornaCeros() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2035")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingresosPresupuestados").value(0.0))
                .andExpect(jsonPath("$.data.egresosPresupuestados").value(0.0))
                .andExpect(jsonPath("$.data.porcentajeEjecucionIngresos").value(0.0))
                .andExpect(jsonPath("$.data.porcentajeEjecucionEgresos").value(0.0));
    }

    /**
     * 8. Sin pagos aprobados: retorna ingreso ejecutado en 0 y porcentaje en 0.0.
     */
    @Test
    @Order(8)
    public void testEjecucionPresupuestal_sinPagos_retornaEjecutadoCero() throws Exception {
        // Insertar presupuesto para vigencia 2027 sin pagos asociados
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO)
                VALUES (?, 2027, 'FONDO DE IMPREVISTOS', 'INGRESO', 10000000, 0, 'APROBADO')
            """, PROP_1_ID);
        } finally {
            clearContext();
        }

        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2027")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingresosPresupuestados").value(10000000.0))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(0.0))
                .andExpect(jsonPath("$.data.totalPagosAprobados").value(0))
                .andExpect(jsonPath("$.data.porcentajeEjecucionIngresos").value(0.0));
    }

    /**
     * 9. Cálculo matemático determinista de superávit presupuestado y ejecutado.
     */
    @Test
    @Order(9)
    public void testEjecucionPresupuestal_superavitCalculo() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        double ingPres = data.get("ingresosPresupuestados").asDouble();
        double egrPres = data.get("egresosPresupuestados").asDouble();
        double supPres = data.get("superavitPresupuestado").asDouble();
        assertEquals(ingPres - egrPres, supPres, 0.01);

        double ingEjec = data.get("ingresosEjecutados").asDouble();
        double egrEjec = data.get("egresosEjecutados").asDouble();
        double supEjec = data.get("superavitEjecutado").asDouble();
        assertEquals(ingEjec - egrEjec, supEjec, 0.01);
    }

    /**
     * 10. Estado financiero DEFICIT cuando los egresos ejecutados superan a los ingresos ejecutados.
     */
    @Test
    @Order(10)
    public void testEjecucionPresupuestal_deficitEstado() throws Exception {
        // Crear escenario con más gastos pagados que ingresos para 2028
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO PRESUPUESTOS (ID_PROPIEDAD, VIGENCIA_ANIO, RUBRO, TIPO, MONTO_PRESUPUESTADO, MONTO_EJECUTADO, ESTADO)
                VALUES (?, 2028, 'REPARACIONES URGENTES', 'EGRESO', 20000000, 0, 'APROBADO')
            """, PROP_1_ID);

            jdbcTemplate.update("""
                INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, METODO_PAGO, ESTADO, FECHA_GASTO)
                VALUES (?, 'EMERGENCIA', 'Plomeria Express', 15000000, 'TRANSFERENCIA', 'PAGADO', DATE '2028-02-10')
            """, PROP_1_ID);
        } finally {
            clearContext();
        }

        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2028")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.egresosEjecutados").value(15000000.0))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(0.0))
                .andExpect(jsonPath("$.data.superavitEjecutado").value(-15000000.0))
                .andExpect(jsonPath("$.data.estadoFinanciero").value("DEFICIT"));
    }

    /**
     * 11. Estado financiero SUPERAVIT cuando los ingresos ejecutados superan a los egresos ejecutados.
     */
    @Test
    @Order(11)
    public void testEjecucionPresupuestal_superavitEstado() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.superavitEjecutado").value(13000000.0))
                .andExpect(jsonPath("$.data.estadoFinanciero").value("SUPERAVIT"));
    }

    /**
     * 12. ADMIN_ORGANIZACION consulta propiedad específica de su organización.
     */
    @Test
    @Order(12)
    public void testEjecucionPresupuestal_adminOrg_consultaPropiedadDeSuOrg() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                        .param("propertyId", String.valueOf(PROP_1_ID))
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(25000000.0));
    }

    /**
     * 13. ADMIN_ORGANIZACION consulta consolidado de toda su organización (sin propertyId).
     */
    @Test
    @Order(13)
    public void testEjecucionPresupuestal_adminOrg_consultaGlobalOrg() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                        .param("vigencia", "2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ingresosEjecutados").value(25000000.0));
    }

    /**
     * 14. ADMIN_ORGANIZACION recibe 403 al consultar propiedad que no pertenece a su org.
     */
    @Test
    @Order(14)
    public void testEjecucionPresupuestal_adminOrg_propiedadAjena_denegado() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                        .param("propertyId", String.valueOf(PROP_2_ID)) // Pertenece a Org 9702
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * 15. ADMIN_PROPIEDAD recibe 403 al intentar adulterar propertyId fuera de su contexto.
     */
    @Test
    @Order(15)
    public void testEjecucionPresupuestal_adminPropiedad_propertyIdInvalido_denegado() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                        .param("propertyId", String.valueOf(PROP_2_ID)) // Distinta a su contexto (PROP_1_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * 16. Roles no autorizados (RESIDENTE, PORTERO, Anónimo) reciben 403 FORBIDDEN o 401 UNAUTHORIZED.
     */
    @Test
    @Order(16)
    public void testEjecucionPresupuestal_rolNoAutorizado_denegado() throws Exception {
        // Residente -> 403
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Portero -> 403
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", ASSIGN_PORTERO)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Anónimo -> 401
        mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
