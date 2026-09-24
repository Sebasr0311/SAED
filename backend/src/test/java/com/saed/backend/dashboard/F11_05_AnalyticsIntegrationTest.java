package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_05_AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssignmentService assignmentService;

    // Identidades
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 9921L;
    private static final long ASSIGN_ADMIN_ORG_1 = 9922L;

    private static final long USER_ADMIN_PROP_1 = 9923L;
    private static final long ASSIGN_ADMIN_PROP_1 = 9924L;

    private static final long USER_ADMIN_PROP_2 = 9925L;
    private static final long ASSIGN_ADMIN_PROP_2 = 9926L;

    private static final long USER_RESIDENTE = 9927L;
    private static final long ASSIGN_RESIDENTE = 9928L;

    private static final long USER_PORTERO = 9929L;
    private static final long ASSIGN_PORTERO = 9930L;

    // Scopes
    private static final long ORG_1_ID = 9501L;
    private static final long ORG_2_ID = 9502L;

    private static final long PROP_1_ID = 9601L;
    private static final long PROP_2_ID = 9602L;
    private static final long PROP_3_ID = 9603L;

    private static final long UNIDAD_1_ID = 9701L;
    private static final long UNIDAD_2_ID = 9702L;
    private static final long UNIDAD_3_ID = 9703L;
    private static final long UNIDAD_FORANEA_ID = 9704L;

    private static final long CONCEPTO_1_ID = 9801L;
    private static final long CONCEPTO_2_ID = 9802L;

    // Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
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

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Central");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Campestre");

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

        AssignmentResponseDTO res = new AssignmentResponseDTO();
        res.setIdAsignacion(ASSIGN_RESIDENTE);
        res.setOrganizacion(org1);
        res.setPropiedad(prop1);
        res.setUnidad(new com.saed.backend.authorization.dto.UnitDTO(UNIDAD_1_ID, "101"));
        res.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(res));

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(port));
    }

    private void ensureBaseData() {
        // Organizaciones
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "9009501-1", "org9501@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9009502-2", "org9502@saed.com");

        // Propiedades de Org 1
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Central", "Bogotá");
        ensurePropiedad(PROP_2_ID, ORG_1_ID, "Torre Campestre", "Medellín");

        // Propiedad de Org 2 (Foránea)
        ensurePropiedad(PROP_3_ID, ORG_2_ID, "Condominio Costa", "Cali");

        // Unidades
        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "101", BigDecimal.valueOf(0.5));
        ensureUnidad(UNIDAD_2_ID, PROP_1_ID, "102", BigDecimal.valueOf(0.5));
        ensureUnidad(UNIDAD_3_ID, PROP_2_ID, "201", BigDecimal.valueOf(1.0));
        ensureUnidad(UNIDAD_FORANEA_ID, PROP_3_ID, "301", BigDecimal.valueOf(1.0));

        // Personas & Usuarios
        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin_an@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000009921", "Admin", "OrgUno", "adminorg9921@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000009923", "Admin", "PropUno", "adminprop9923@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000009925", "Admin", "PropDos", "adminprop9925@saed.com");
        ensurePersona(USER_RESIDENTE, "1000009927", "Carlos", "Residente", "residente9927@saed.com");
        ensurePersona(USER_PORTERO, "1000009929", "Pedro", "Portero", "portero9929@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_an", "superadmin_an@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org9921", "adminorg9921@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop9923", "adminprop9923@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop9925", "adminprop9925@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente9927", "residente9927@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero9929", "portero9929@saed.com");

        // Asignaciones
        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Residentes activos
        ensureResidente(UNIDAD_1_ID, USER_RESIDENTE);
        ensureResidente(UNIDAD_3_ID, USER_RESIDENTE);

        // Conceptos de cobro
        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_3_ID, "ORD_2", "Cuota Ordinaria Prop 3");

        // Limpieza de datos transaccionales para pruebas repetibles
        try {
            jdbcTemplate.execute("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = 9699");
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (" + UNIDAD_1_ID + ", " + UNIDAD_2_ID + ", " + UNIDAD_3_ID + ", " + UNIDAD_FORANEA_ID + ")");
            jdbcTemplate.execute("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (" + UNIDAD_1_ID + ", " + UNIDAD_2_ID + ", " + UNIDAD_3_ID + ", " + UNIDAD_FORANEA_ID + ")");
            jdbcTemplate.execute("DELETE FROM GASTOS WHERE ID_PROPIEDAD IN (" + PROP_1_ID + ", " + PROP_2_ID + ", " + PROP_3_ID + ")");
            jdbcTemplate.execute("DELETE FROM VISITAS WHERE ID_UNIDAD IN (" + UNIDAD_1_ID + ", " + UNIDAD_2_ID + ", " + UNIDAD_3_ID + ", " + UNIDAD_FORANEA_ID + ")");
            jdbcTemplate.execute("DELETE FROM PAQUETES WHERE ID_PROPIEDAD IN (" + PROP_1_ID + ", " + PROP_2_ID + ", " + PROP_3_ID + ")");
            jdbcTemplate.execute("DELETE FROM PQRS_TICKETS WHERE ID_PROPIEDAD IN (" + PROP_1_ID + ", " + PROP_2_ID + ", " + PROP_3_ID + ")");
        } catch (Exception ignored) {}

        // Semillas Transaccionales
        String currentMonth = YearMonth.now().toString(); // e.g. "2026-09"
        String prevMonth = YearMonth.now().minusMonths(1).toString(); // e.g. "2026-08"

        String twoMonthsAgo = YearMonth.now().minusMonths(2).toString();

        // Cuota 1: PENDIENTE en Prop 1 (Unidad 9701)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, ?, 1000000, 400000, 'PENDIENTE', SYSDATE)
        """, UNIDAD_1_ID, CONCEPTO_1_ID, prevMonth);

        // Cuota 2: PAGADA en Prop 1 (Unidad 9702)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, ?, 500000, 0, 'PAGADA', SYSDATE)
        """, UNIDAD_2_ID, CONCEPTO_1_ID, prevMonth);

        // Cuota 3: ANULADA en Prop 1 (Unidad 9701) - DEBE EXCLUIRSE DE FACTURADO
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, ?, 300000, 0, 'ANULADA', SYSDATE)
        """, UNIDAD_1_ID, CONCEPTO_1_ID, twoMonthsAgo);

        // Cuota 4: Histórica antigua de 2024 (VENCIDA) - No en ventana 6m pero SÍ en Cartera Total Actual
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2024-01', 200000, 200000, 'VENCIDA', DATE '2024-01-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        // Cuota en Prop 2 (Unidad 9703)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, ?, 800000, 100000, 'PENDIENTE', SYSDATE)
        """, UNIDAD_3_ID, CONCEPTO_1_ID, prevMonth);

        // Cuota en Prop 3 (Org 2 - Foránea)
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, ?, 999999, 999999, 'PENDIENTE', SYSDATE)
        """, UNIDAD_FORANEA_ID, CONCEPTO_2_ID, prevMonth);

        // Pago 1: APROBADO en Prop 1
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 600000, 'TRANSFERENCIA', 'APROBADO', SYSDATE - 5, 'REF-TEST-001')
        """, UNIDAD_1_ID);

        // Pago 2: RECHAZADO en Prop 1 - DEBE EXCLUIRSE DE RECAUDO
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 100000, 'TRANSFERENCIA', 'RECHAZADO', SYSDATE - 4, 'REF-TEST-002')
        """, UNIDAD_1_ID);

        // Pago 3: APROBADO en Prop 2
        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 700000, 'TRANSFERENCIA', 'APROBADO', SYSDATE - 3, 'REF-TEST-003')
        """, UNIDAD_3_ID);

        // Gasto en Prop 1
        jdbcTemplate.update("""
            INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, FECHA_GASTO, METODO_PAGO, ESTADO)
            VALUES (?, 'MANTENIMIENTO', 'Proveedor Test', 250000, SYSDATE - 2, 'TRANSFERENCIA', 'PAGADO')
        """, PROP_1_ID);

        // Visitante en Prop 1
        ensureVisitante(USER_RESIDENTE, USER_RESIDENTE);

        // Visita en Prop 1
        jdbcTemplate.update("""
            INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, ESTADO, FECHA_CREACION)
            VALUES (?, ?, 'CODIGO_QR', 'Visita Familiar', 'FINALIZADA', SYSDATE - 2)
        """, UNIDAD_1_ID, USER_RESIDENTE);

        // Porteria en Prop 1
        ensurePorteria(PROP_1_ID, PROP_1_ID);

        // Paquete en Prop 1
        jdbcTemplate.update("""
            INSERT INTO PAQUETES (ID_PROPIEDAD, ID_PORTERIA, ID_UNIDAD, EMPRESA_MENSAJERIA, DESCRIPCION, TAMANO, CODIGO_RETIRO_PIN, FECHA_RECEPCION, RECIBIDO_POR_PORTERO, ESTADO)
            VALUES (?, ?, ?, 'SERVIENTREGA', 'Caja de prueba', 'MEDIANO', '1234', SYSDATE - 2, ?, 'RECIBIDO')
        """, PROP_1_ID, PROP_1_ID, UNIDAD_1_ID, USER_PORTERO);

        // PQRS en Prop 1 (Resuelto en SLA)
        jdbcTemplate.update("""
            INSERT INTO PQRS_TICKETS (ID_PROPIEDAD, ID_UNIDAD, ID_PERSONA_RADICA, NUMERO_RADICADO, TIPO, CATEGORIA, PRIORIDAD, ASUNTO, DESCRIPCION, FECHA_RADICACION, FECHA_LIMITE_SLA, FECHA_CIERRE, ESTADO)
            VALUES (?, ?, ?, 'RAD-TEST-001', 'PETICION', 'MANTENIMIENTO', 'MEDIA', 'Asunto test', 'Desc test', SYSDATE - 5, SYSDATE, SYSDATE - 2, 'RESUELTO')
        """, PROP_1_ID, UNIDAD_1_ID, USER_RESIDENTE);
    }

    private void ensureVisitante(long id, long personaId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM VISITANTES WHERE ID_VISITANTE = ?", Integer.class, id);
        if (c == null || c == 0) {
            try {
                jdbcTemplate.update("INSERT INTO VISITANTES (ID_VISITANTE, ID_PERSONA, ES_FRECUENTE, ESTADO) VALUES (?, ?, 'N', 'ACTIVO')", id, personaId);
            } catch (Exception ignored) {}
        }
    }

    private void ensurePorteria(long id, long propId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PORTERIAS WHERE ID_PORTERIA = ?", Integer.class, id);
        if (c == null || c == 0) {
            try {
                jdbcTemplate.update("INSERT INTO PORTERIAS (ID_PORTERIA, ID_PROPIEDAD, NOMBRE, ESTADO) VALUES (?, ?, 'Porteria Principal', 'ACTIVA')", id, propId);
            } catch (Exception ignored) {}
        }
    }

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, PAIS, ESTADO) VALUES (?, ?, ?, ?, 'Colombia', 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre, String ciudad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, ESTADO) VALUES (?, ?, 1, ?, 'Calle Principal', ?, 'ACTIVA')",
                    id, orgId, nombre, ciudad);
        }
    }

    private void ensureUnidad(long id, long propId, String identificador, BigDecimal coef) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO) VALUES (?, ?, 1, ?, ?, 'ACTIVA')",
                    id, propId, identificador, coef);
        }
    }

    private void ensureResidente(long unidadId, long personaId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?", Integer.class, unidadId, personaId);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) VALUES (?, ?, 'PROPIETARIO', SYSDATE, 'ACTIVO')",
                    unidadId, personaId);
        }
    }

    private void ensurePersona(long id, String doc, String nom, String ape, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, 'NATURAL', ?, ?, ?, 'ACTIVO')",
                    id, doc, nom, ape, email);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')",
                    id, personaId, username, email);
        }
    }

    private void ensureConcepto(long id, long orgId, long propId, String codigo, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM CONCEPTOS_COBRO WHERE ID_CONCEPTO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) VALUES (?, ?, ?, ?, ?, 'ADMINISTRACION', 'ACTIVO')",
                    id, orgId, propId, codigo, nombre);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            try {
                jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', SYSDATE)",
                        id, idUsuario, idRol, idOrg, idProp, idUnidad);
            } catch (Exception ignored) {}
        }
    }

    // ==========================================
    // 35 CASOS DE PRUEBA EXIGIDOS (SECCIÓN 25)
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("01. ADMIN_ORGANIZACION recibe analytics de su organizacion (default 12 meses)")
    void test01_adminOrganizacionRecibeAnalyticsDeSuOrganizacion() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertEquals(12, root.get("data").get("mesesEvaluados").asInt(), "Default sin parametro meses debe ser 12");
        assertEquals(12, root.get("data").get("tendenciaMensual").size(), "Debe devolver exactamente 12 puntos mensuales por defecto");
        assertNotNull(root.get("data").get("kpisGlobales"));
        assertNotNull(root.get("data").get("benchmarkPropiedades"));
    }

    @Test
    @Order(2)
    @DisplayName("02. Organizacion A no puede ver Organizacion B")
    void test02_organizacionANoPuedeVerOrganizacionB() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");

        for (JsonNode prop : benchmark) {
            assertNotEquals(PROP_3_ID, prop.get("idPropiedad").asLong(), "Propiedad de Org 2 no debe ser visible");
        }
    }

    @Test
    @Order(3)
    @DisplayName("03. propertyId externo no modifica el scope de la organizacion")
    void test03_propertyIdExternoNoModificaElScope() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?idPropiedad=" + PROP_3_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");

        for (JsonNode prop : benchmark) {
            assertNotEquals(PROP_3_ID, prop.get("idPropiedad").asLong());
        }
    }

    @Test
    @Order(4)
    @DisplayName("04. ADMIN_PROPIEDAD solo ve su propiedad (default 12 meses)")
    void test04_adminPropiedadSoloVeSuPropiedad() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertEquals(PROP_1_ID, data.get("idPropiedad").asLong());
        assertEquals(12, data.get("tendenciaFinanciera").size(), "Default sin parametro meses debe ser 12");
        assertEquals(12, data.get("tendenciaOperativa").size(), "Default sin parametro meses debe ser 12");
    }

    @Test
    @Order(5)
    @DisplayName("05. PORTERO => 403 Forbidden")
    void test05_portero403() throws Exception {
        mockMvc.perform(get("/api/v1/org/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("06. RESIDENTE => 403 Forbidden")
    void test06_residente403() throws Exception {
        mockMvc.perform(get("/api/v1/org/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("07. Anonymous => 401 Unauthorized")
    void test07_anonymous401() throws Exception {
        mockMvc.perform(get("/api/v1/org/dashboard/analytics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    @DisplayName("08. meses=6 devuelve exactamente 6 meses")
    void test08_meses6DevuelveExactamente6Meses() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(6, root.get("data").get("mesesEvaluados").asInt());
        assertEquals(6, root.get("data").get("tendenciaMensual").size());
    }

    @Test
    @Order(9)
    @DisplayName("09. meses=12 devuelve exactamente 12 meses")
    void test09_meses12DevuelveExactamente12Meses() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=12")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(12, root.get("data").get("mesesEvaluados").asInt());
        assertEquals(12, root.get("data").get("tendenciaMensual").size());
    }

    @Test
    @Order(10)
    @DisplayName("10. anio=2025 ignora meses y devuelve enero-diciembre 2025")
    void test10_anio2025IgnoraMesesYDevuelveEneroDiciembre2025() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2025&meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertEquals(12, data.get("mesesEvaluados").asInt());
        JsonNode trend = data.get("tendenciaMensual");
        assertEquals(12, trend.size());
        assertEquals("2025-01", trend.get(0).get("periodo").asText());
        assertEquals("2025-12", trend.get(11).get("periodo").asText());
    }

    @Test
    @Order(11)
    @DisplayName("11. anio fuera de 2000-2100 => 400 Bad Request")
    void test11_anioFueraDeRango400() throws Exception {
        mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=1999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    @DisplayName("12. meses invalido => 400 Bad Request")
    void test12_mesesInvalido400() throws Exception {
        mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=24")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("13. meses sin movimientos aparecen con cero")
    void test13_mesesSinMovimientosAparecenConCero() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2021")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode trend = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("tendenciaMensual");
        for (JsonNode m : trend) {
            assertEquals(0.0, m.get("facturado").asDouble(), 0.01);
            assertEquals(0.0, m.get("recaudado").asDouble(), 0.01);
            assertEquals(0.0, m.get("carteraPeriodo").asDouble(), 0.01);
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. facturado calcula unicamente estados validos")
    void test14_facturadoCalculaUnicamenteEstadosValidos() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        // Facturado en Prop 1 = 1,000,000 (PENDIENTE) + 500,000 (PAGADA) = 1,500,000
        assertEquals(1500000.0, prop1.get("facturadoPeriodo").asDouble(), 1.0);
    }

    @Test
    @Order(15)
    @DisplayName("15. ANULADA queda excluida de facturado")
    void test15_anuladaQuedaExcluida() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        // Si incluyera la cuota ANULADA (300,000) daría 1,800,000. Debe ser 1,500,000.
        assertNotEquals(1800000.0, prop1.get("facturadoPeriodo").asDouble(), 1.0);
        assertEquals(1500000.0, prop1.get("facturadoPeriodo").asDouble(), 1.0);
    }

    @Test
    @Order(16)
    @DisplayName("16. recaudado calcula unicamente PAGOS APROBADOS")
    void test16_recaudadoCalculaUnicamentePagosAprobados() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        assertEquals(600000.0, prop1.get("recaudadoPeriodo").asDouble(), 1.0);
    }

    @Test
    @Order(17)
    @DisplayName("17. pagos no aprobados quedan excluidos")
    void test17_pagosNoAprobadosQuedanExcluidos() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        // Si incluyera el pago RECHAZADO (100,000) daría 700,000. Debe ser 600,000.
        assertNotEquals(700000.0, prop1.get("recaudadoPeriodo").asDouble(), 1.0);
        assertEquals(600000.0, prop1.get("recaudadoPeriodo").asDouble(), 1.0);
    }

    @Test
    @Order(18)
    @DisplayName("18. carteraPeriodo utiliza unicamente estados validos")
    void test18_carteraPeriodoUtilizaUnicamenteEstadosValidos() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        // En la ventana de 6 meses, saldo pendiente = 400,000 (de Cuota 1)
        assertEquals(400000.0, prop1.get("carteraPeriodo").asDouble(), 1.0);
    }

    @Test
    @Order(19)
    @DisplayName("19. carteraTotalActual representa cartera viva actual")
    void test19_carteraTotalActualRepresentaCarteraViva() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                prop1 = p;
                break;
            }
        }
        assertNotNull(prop1);
        // Cartera viva total = 400,000 (reciente) + 200,000 (de 2024 fuera de ventana) = 600,000
        assertEquals(600000.0, prop1.get("carteraTotalActual").asDouble(), 1.0);
        assertTrue(prop1.get("carteraTotalActual").asDouble() >= prop1.get("carteraPeriodo").asDouble());
    }

    @Test
    @Order(20)
    @DisplayName("20. facturado=0 => efectividad=100.0")
    void test20_facturadoCeroEfectividad100() throws Exception {
        // En un anio sin facturacion (ej. 2022)
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2022")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        for (JsonNode p : benchmark) {
            assertEquals(100.0, p.get("efectividadRecaudoPct").asDouble(), 0.01);
        }
    }

    @Test
    @Order(21)
    @DisplayName("20b. facturado=0 y recaudado>0 => efectividad=100.0 y conserva monto real recaudado")
    void test20b_facturadoCeroRecaudoPositivoConservaMontoYEfectividad100() throws Exception {
        setElevatedContext();
        try {
            // Insertar pago en 2022 para Propiedad 1 (donde no hay facturacion en 2022)
            jdbcTemplate.update("""
                INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
                VALUES (?, 500000, 'TRANSFERENCIA', 'APROBADO', DATE '2022-05-10', 'REF-PAGO-2022')
            """, UNIDAD_1_ID);

            MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2022")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("benchmarkPropiedades");
            JsonNode prop1 = null;
            for (JsonNode p : benchmark) {
                if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                    prop1 = p;
                    break;
                }
            }
            assertNotNull(prop1);
            assertEquals(0.0, prop1.get("facturadoPeriodo").asDouble(), 0.01);
            assertEquals(500000.0, prop1.get("recaudadoPeriodo").asDouble(), 1.0);
            assertEquals(100.0, prop1.get("efectividadRecaudoPct").asDouble(), 0.01);

            // Verificar tambien KPIs Globales
            JsonNode kpis = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("kpisGlobales");
            assertEquals(0.0, kpis.get("totalFacturadoPeriodo").asDouble(), 0.01);
            assertEquals(500000.0, kpis.get("totalRecaudadoPeriodo").asDouble(), 1.0);
            assertEquals(100.0, kpis.get("efectividadRecaudoGlobalPct").asDouble(), 0.01);
        } finally {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'REF-PAGO-2022'");
            clearContext();
        }
    }

    @Test
    @Order(22)
    @DisplayName("21. facturado=0 => morosidad=0.0")
    void test21_facturadoCeroMorosidadCero() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?anio=2022")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        for (JsonNode p : benchmark) {
            assertEquals(0.0, p.get("indiceMorosidadPct").asDouble(), 0.01);
        }
    }

    @Test
    @Order(22)
    @DisplayName("22. recaudado > facturado => efectividad clamp 100.0 pero conserva monto real")
    void test22_recaudadoMayorFacturadoClamp100ConservaMontoReal() throws Exception {
        // En Prop 2: facturado = 800,000, pago = 700,000. Creemos un pago adicional temporal para exceder facturado
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
                VALUES (?, 500000, 'TRANSFERENCIA', 'APROBADO', SYSDATE - 1, 'REF-EXCEED')
            """, UNIDAD_3_ID);

            MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("benchmarkPropiedades");
            JsonNode prop2 = null;
            for (JsonNode p : benchmark) {
                if (p.get("idPropiedad").asLong() == PROP_2_ID) {
                    prop2 = p;
                    break;
                }
            }
            assertNotNull(prop2);
            assertEquals(1200000.0, prop2.get("recaudadoPeriodo").asDouble(), 1.0);
            assertEquals(100.0, prop2.get("efectividadRecaudoPct").asDouble(), 0.01);
        } finally {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'REF-EXCEED'");
            clearContext();
        }
    }

    @Test
    @Order(23)
    @DisplayName("23. ocupacion calcula correctamente unidades ocupadas")
    void test23_ocupacionCalculaCorrectamenteUnidadesOcupadas() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                assertEquals(2, p.get("totalUnidades").asLong());
                assertEquals(1, p.get("unidadesOcupadas").asLong());
                assertEquals(50.0, p.get("ocupacionActualPct").asDouble(), 0.1);
            } else if (p.get("idPropiedad").asLong() == PROP_2_ID) {
                assertEquals(1, p.get("totalUnidades").asLong());
                assertEquals(1, p.get("unidadesOcupadas").asLong());
                assertEquals(100.0, p.get("ocupacionActualPct").asDouble(), 0.1);
            }
        }
    }

    @Test
    @Order(24)
    @DisplayName("24. propiedad sin unidades => ocupacion 0.0")
    void test24_propiedadSinUnidadesOcupacionCero() throws Exception {
        setElevatedContext();
        long propVaciaId = 9699L;
        try {
            jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", propVaciaId);
            ensurePropiedad(propVaciaId, ORG_1_ID, "Propiedad Vacia", "Cali");
            MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("benchmarkPropiedades");
            JsonNode propVacia = null;
            for (JsonNode p : benchmark) {
                if (p.get("idPropiedad").asLong() == propVaciaId) {
                    propVacia = p;
                    break;
                }
            }
            assertNotNull(propVacia);
            assertEquals(0.0, propVacia.get("ocupacionActualPct").asDouble(), 0.01);
        } finally {
            setElevatedContext();
            jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", propVaciaId);
            clearContext();
        }
    }

    @Test
    @Order(25)
    @DisplayName("25. rankings son deterministas")
    void test25_rankingsSonDeterministas() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        assertTrue(benchmark.size() >= 2);

        for (JsonNode p : benchmark) {
            assertTrue(p.get("rankingEfectividad").asInt() >= 1);
            assertTrue(p.get("rankingMorosidad").asInt() >= 1);
            assertTrue(p.get("rankingOcupacion").asInt() >= 1);
        }
    }

    @Test
    @Order(26)
    @DisplayName("25b. rankings respetan orden multicriterio y desempate por nombre")
    void test25b_rankingsOrdenMulticriterioYDesempateNombre() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        JsonNode prop1 = null;
        JsonNode prop2 = null;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) prop1 = p;
            if (p.get("idPropiedad").asLong() == PROP_2_ID) prop2 = p;
        }
        assertNotNull(prop1);
        assertNotNull(prop2);

        // Efectividad: Prop 2 (87.5%) > Prop 1 (40.0%) => Prop 2 rank 1, Prop 1 rank 2
        assertEquals(1, prop2.get("rankingEfectividad").asInt());
        assertEquals(2, prop1.get("rankingEfectividad").asInt());

        // Morosidad (ASC): Prop 2 (12.5%) < Prop 1 (26.67%) => Prop 2 rank 1, Prop 1 rank 2
        assertEquals(1, prop2.get("rankingMorosidad").asInt());
        assertEquals(2, prop1.get("rankingMorosidad").asInt());

        // Ocupacion (DESC): Prop 2 (100.0%) > Prop 1 (50.0%) => Prop 2 rank 1, Prop 1 rank 2
        assertEquals(1, prop2.get("rankingOcupacion").asInt());
        assertEquals(2, prop1.get("rankingOcupacion").asInt());
    }

    @Test
    @Order(27)
    @DisplayName("26. tendencia mensual esta ordenada ascendentemente")
    void test26_tendenciaMensualOrdenadaAscendentemente() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=12")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode trend = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("tendenciaMensual");
        for (int i = 0; i < trend.size() - 1; i++) {
            String curr = trend.get(i).get("periodo").asText();
            String next = trend.get(i + 1).get("periodo").asText();
            assertTrue(curr.compareTo(next) < 0, "Debe estar ordenado ascendentemente: " + curr + " < " + next);
        }
    }

    @Test
    @Order(27)
    @DisplayName("27. benchmark incluye todas las propiedades permitidas por el tenant")
    void test27_benchmarkIncluyeTodasLasPropiedadesDelTenant() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");

        boolean found1 = false;
        boolean found2 = false;
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) found1 = true;
            if (p.get("idPropiedad").asLong() == PROP_2_ID) found2 = true;
        }
        assertTrue(found1);
        assertTrue(found2);
    }

    @Test
    @Order(28)
    @DisplayName("28. no existe fuga de datos entre propiedades")
    void test28_noExisteFugaDeDatosEntrePropiedades() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/org/dashboard/analytics?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode benchmark = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("benchmarkPropiedades");
        for (JsonNode p : benchmark) {
            if (p.get("idPropiedad").asLong() == PROP_1_ID) {
                // Prop 1 no debe tener los 800,000 de Prop 2
                assertEquals(1500000.0, p.get("facturadoPeriodo").asDouble(), 1.0);
            }
        }
    }

    @Test
    @Order(29)
    @DisplayName("29. analytics de propiedad utiliza contexto y no propertyId externo")
    void test29_analyticsPropiedadUtilizaContextoYNoPropertyIdExterno() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica?idPropiedad=" + PROP_2_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertEquals(PROP_1_ID, data.get("idPropiedad").asLong(), "Debe usar el contexto y no el parámetro externo");
    }

    @Test
    @Order(30)
    @DisplayName("30. tendencia financiera de propiedad calcula balance = recaudo - gastos")
    void test30_tendenciaFinancieraPropiedadBalanceNeto() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fin = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("tendenciaFinanciera");
        for (JsonNode f : fin) {
            double rec = f.get("recaudado").asDouble();
            double gas = f.get("gastos").asDouble();
            double bal = f.get("balanceNeto").asDouble();
            assertEquals(rec - gas, bal, 0.01);
        }
    }

    @Test
    @Order(31)
    @DisplayName("31. tendencia operativa agrega visitas, paquetes y PQRS")
    void test31_tendenciaOperativaAgregaVisitasPaquetesPqrs() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode op = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("tendenciaOperativa");
        assertNotNull(op);
        assertTrue(op.size() == 6);

        long totalVisitas = 0;
        long totalPaquetes = 0;
        long totalPqrs = 0;
        for (JsonNode m : op) {
            totalVisitas += m.get("totalVisitas").asLong();
            totalPaquetes += m.get("totalPaquetes").asLong();
            totalPqrs += m.get("pqrsRadicadas").asLong();
        }
        assertTrue(totalVisitas >= 1);
        assertTrue(totalPaquetes >= 1);
        assertTrue(totalPqrs >= 1);
    }

    @Test
    @Order(32)
    @DisplayName("32. SLA se calcula segun definicion implementada")
    void test32_slaCalculadoSegunDefinicion() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica?meses=6")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode op = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("tendenciaOperativa");
        for (JsonNode m : op) {
            double sla = m.get("pqrsResueltasEnSlaPct").asDouble();
            assertTrue(sla >= 0.0 && sla <= 100.0);
        }
    }

    @Test
    @Order(33)
    @DisplayName("32b. ticket resuelto fuera de SLA reduce porcentaje SLA")
    void test32b_slaCalculaTicketFueraDeSla() throws Exception {
        setElevatedContext();
        try {
            // Insertar un ticket adicional en Prop 1 que se cerro despues de la fecha limite de SLA
            jdbcTemplate.update("""
                INSERT INTO PQRS_TICKETS (ID_PROPIEDAD, ID_UNIDAD, ID_PERSONA_RADICA, NUMERO_RADICADO, TIPO, CATEGORIA, PRIORIDAD, ASUNTO, DESCRIPCION, FECHA_RADICACION, FECHA_LIMITE_SLA, FECHA_CIERRE, ESTADO)
                VALUES (?, ?, ?, 'RAD-TEST-LATE', 'PETICION', 'MANTENIMIENTO', 'MEDIA', 'Asunto test tardio', 'Desc test tardio', SYSDATE - 6, SYSDATE - 4, SYSDATE - 1, 'RESUELTO')
            """, PROP_1_ID, UNIDAD_1_ID, USER_RESIDENTE);

            MvcResult res = mockMvc.perform(get("/api/v1/dashboard/propiedad/analitica?meses=6")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminProp1)
                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode op = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("tendenciaOperativa");
            JsonNode currentMonth = op.get(op.size() - 1);
            // Ahora hay 2 radicadas en el mes actual: 1 a tiempo y 1 tardia => SLA = 50.0%
            assertEquals(2, currentMonth.get("pqrsRadicadas").asLong());
            assertEquals(50.0, currentMonth.get("pqrsResueltasEnSlaPct").asDouble(), 0.01);
        } finally {
            jdbcTemplate.execute("DELETE FROM PQRS_TICKETS WHERE NUMERO_RADICADO = 'RAD-TEST-LATE'");
            clearContext();
        }
    }

    @Test
    @Order(34)
    @DisplayName("33. SUPERADMIN retencion organizacional")
    void test33_superAdminRetencionOrganizacional() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/platform/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertTrue(data.has("tasaRetencionOrganizacionesPct"));
        double ret = data.get("tasaRetencionOrganizacionesPct").asDouble();
        assertTrue(ret >= 0.0 && ret <= 100.0);
    }

    @Test
    @Order(34)
    @DisplayName("34. SUPERADMIN distribucion por ciudad")
    void test34_superAdminDistribucionPorCiudad() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/platform/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode dist = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("distribucionPropiedadesPorCiudad");
        assertNotNull(dist);
        assertTrue(dist.isArray());
        assertTrue(dist.size() > 0);
    }

    @Test
    @Order(35)
    @DisplayName("35. SUPERADMIN crecimiento mensual")
    void test35_superAdminCrecimientoMensual() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/platform/dashboard/analytics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode crec = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("crecimientoOrganizacionesMensual");
        assertNotNull(crec);
        assertEquals(12, crec.size(), "Debe contener los ultimos 12 meses");
    }
}
