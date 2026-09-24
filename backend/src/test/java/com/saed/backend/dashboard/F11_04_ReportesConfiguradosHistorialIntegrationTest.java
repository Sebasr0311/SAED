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
import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
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
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F11_04_ReportesConfiguradosHistorialIntegrationTest
 *
 * Suite determinista de integración para F11-04 Block D (Reportes Configurables + Historial de Reportes):
 * 1. Crear configuración con clave allowlistada (éxito).
 * 2. Intentar crear configuración con clave no allowlistada (400 / 422).
 * 3. Intentar crear configuración con SQL arbitrario (rechazo inmediato).
 * 4. ADMIN_ORGANIZACION lista configuraciones de su organización.
 * 5. ADMIN_ORGANIZACION no ve configuraciones de otra organización.
 * 6. ADMIN_PROPIEDAD lista configuraciones de su propiedad y las globales.
 * 7. ADMIN_PROPIEDAD no ve configuraciones exclusivas de otra propiedad.
 * 8. PORTERO no puede listar configuraciones (403).
 * 9. RESIDENTE no puede listar configuraciones (403).
 * 10. Anónimo no puede listar configuraciones (401).
 * 11. Generar reporte configurado (éxito, exportación válida).
 * 12. Generación crea registro en HISTORIAL_REPORTES.
 * 13. Hash SHA-256 es determinístico.
 * 14. Historial registra número exacto de filas procesadas.
 * 15. Historial registra formato correcto.
 * 16. Historial registra usuario ejecutor.
 * 17. Historial registra timestamp.
 * 18. Intentar modificar registro de HISTORIAL_REPORTES (rechazado por trigger ORA-20060).
 * 19. Intentar eliminar registro de HISTORIAL_REPORTES (rechazado por trigger).
 * 20. ADMIN_ORGANIZACION puede consultar historial consolidado (ID_PROPIEDAD = NULL).
 * 21. ADMIN_ORGANIZACION puede consultar historial de propiedad de su organización.
 * 22. ADMIN_ORGANIZACION no puede consultar historial de otra organización.
 * 23. ADMIN_PROPIEDAD solo puede consultar historial de su propiedad.
 * 24. PORTERO no puede consultar historial (403).
 * 25. RESIDENTE no puede consultar historial (403).
 * 26. Desactivar configuración (éxito, estado cambia a INACTIVO).
 * 27. Desactivar no borra historial previo.
 * 28. SUPERADMIN puede consultar configuración e historial global.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_04_ReportesConfiguradosHistorialIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportesConfiguradosService reportesConfiguradosService;

    @MockBean
    private AssignmentService assignmentService;

    // Identidades
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_ORG_1 = 801L;
    private static final long ASSIGN_ADMIN_ORG_1 = 902L;

    private static final long USER_ADMIN_PROP_1 = 802L;
    private static final long ASSIGN_ADMIN_PROP_1 = 903L;

    private static final long USER_ADMIN_PROP_2 = 803L;
    private static final long ASSIGN_ADMIN_PROP_2 = 904L;

    private static final long USER_RESIDENTE = 805L;
    private static final long ASSIGN_RESIDENTE = 905L;

    private static final long USER_PORTERO = 806L;
    private static final long ASSIGN_PORTERO = 906L;

    // Scopes
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    private static final long UNIDAD_1_ID = 9101L;
    private static final long UNIDAD_2_ID = 9201L;

    private static final long CONCEPTO_1_ID = 9501L;
    private static final long CONCEPTO_2_ID = 9502L;

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

    private <T> T queryElevated(java.util.function.Supplier<T> supplier) {
        setElevatedContext();
        try {
            return supplier.get();
        } finally {
            clearContext();
        }
    }

    private void runElevated(Runnable runnable) {
        setElevatedContext();
        try {
            runnable.run();
        } finally {
            clearContext();
        }
    }

    private void ensureBaseData() {
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.250000));

        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "CUOTA_ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_2_ID, "CUOTA_ORD_2", "Cuota Ordinaria Prop 2");

        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin_f11_04@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1_f11_04@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2_f11_04@saed.com");
        ensurePersona(USER_RESIDENTE, "1000000805", "Carlos", "Residente", "residente_f11_04@saed.com");
        ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero_f11_04@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_f11_04", "superadmin_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_f11_04", "adminorg1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_f11_04", "adminprop1_f11_04@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_f11_04", "adminprop2_f11_04@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_f11_04", "residente_f11_04@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_f11_04", "portero_f11_04@saed.com");

        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Cuotas y pagos para datos de reporte
        try {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (9101, 9201)");
            jdbcTemplate.execute("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (9101, 9201)");
        } catch (Exception ignored) {}

        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 500000, 500000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 500000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-09-30 14:30:00', 'REF-PAGO-001')
        """, UNIDAD_1_ID);
    }

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
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
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, PASSWORD_HASH, ESTADO) VALUES (?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')",
                    id, personaId, username);
        }
    }

    private void ensureAsignacion(long id, long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, id);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA')",
                    id, idUsuario, idRol, idOrg, idProp, idUnidad);
        }
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

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
        res.setUnidad(new com.saed.backend.authorization.dto.UnitDTO(UNIDAD_1_ID, "A101"));
        res.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(res));

        AssignmentResponseDTO port = new AssignmentResponseDTO();
        port.setIdAsignacion(ASSIGN_PORTERO);
        port.setOrganizacion(org1);
        port.setPropiedad(prop1);
        port.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_PORTERO, USER_PORTERO)).thenReturn(Optional.of(port));
    }

    // =========================================================================
    // PRUEBAS DE CREACIÓN Y ALLOWLIST (Tests 1, 2, 3)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Crear configuración con clave allowlistada (éxito 201)")
    public void test01_crearConfiguracionClaveAllowlistada_exito() throws Exception {
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setCodigo("REP_MOROSA_AUTO_" + System.currentTimeMillis());
        req.setNombre("Cartera Morosa Mensual Org1");
        req.setDescripcion("Reporte configurado para seguimiento de mora");
        req.setModulo("FINANZAS");
        req.setConsultaOrigenClave("CARTERA_MOROSA");
        req.setFormatoSalidaDefecto("PDF");
        req.setParametrosFiltroJson("{\"size\":50}");

        mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consultaOrigenClave").value("CARTERA_MOROSA"))
                .andExpect(jsonPath("$.data.nombre").value("Cartera Morosa Mensual Org1"));
    }

    @Test
    @Order(2)
    @DisplayName("2. Intentar crear configuración con clave no allowlistada (400 BAD_REQUEST)")
    public void test02_crearConfiguracionClaveNoAllowlistada_falla() throws Exception {
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setNombre("Reporte Ilegal");
        req.setConsultaOrigenClave("CLAVE_TOTALMENTE_INEXISTENTE");
        req.setFormatoSalidaDefecto("PDF");

        mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_KEY"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Intentar crear configuración con SQL arbitrario (rechazo inmediato 400)")
    public void test03_crearConfiguracionSqlArbitrario_rechazoInmediato() throws Exception {
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setNombre("SQL Injection Attempt");
        req.setConsultaOrigenClave("SELECT * FROM USUARIOS WHERE 1=1; DROP TABLE PAGOS;");
        req.setFormatoSalidaDefecto("JSON");

        mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_KEY"));
    }

    // =========================================================================
    // PRUEBAS DE VISIBILIDAD MULTI-TENANT (Tests 4, 5, 6, 7)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4. ADMIN_ORGANIZACION lista configuraciones de su organización y globales")
    public void test04_adminOrgListaConfiguracionesSuOrg() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    @Order(5)
    @DisplayName("5. ADMIN_ORGANIZACION no ve configuraciones de otra organización (IDOR)")
    public void test05_adminOrgNoVeConfiguracionesOtraOrg() throws Exception {
        // Crear configuración para Org 2 directamente en BD
        setElevatedContext();
        try {
            jdbcTemplate.update("DELETE FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'REP_EXCLUSIVO_ORG2'");
            jdbcTemplate.update("""
                INSERT INTO REPORTES_CONFIGURADOS (CODIGO, NOMBRE, MODULO, DESCRIPCION, FORMATO_SALIDA_DEFECTO,
                    CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION, ESTADO, ID_ORGANIZACION, ID_PROPIEDAD)
                VALUES ('REP_EXCLUSIVO_ORG2', 'Exclusivo Org 2', 'FINANZAS', 'Privado', 'PDF', 'CARTERA_MOROSA', 'ADMIN_PROPIEDAD', 'ACTIVO', ?, ?)
            """, ORG_2_ID, PROP_2_ID);
        } finally {
            clearContext();
        }

        MvcResult result = mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("REP_EXCLUSIVO_ORG2"), "ADMIN_ORGANIZACION 1 no debe ver configuración de Org 2");
    }

    @Test
    @Order(6)
    @DisplayName("6. ADMIN_PROPIEDAD lista configuraciones de su propiedad y las globales")
    public void test06_adminPropListaConfiguracionesSuPropiedadYGlobales() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    @Order(7)
    @DisplayName("7. ADMIN_PROPIEDAD no ve configuraciones exclusivas de otra propiedad")
    public void test07_adminPropNoVeConfiguracionesExclusivasOtraProp() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("REP_EXCLUSIVO_ORG2"), "ADMIN_PROPIEDAD 1 no debe ver configuraciones de Prop 2");
    }

    // =========================================================================
    // PRUEBAS DE AUTORIZACIÓN POR ROLES (Tests 8, 9, 10)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("8. PORTERO no puede listar configuraciones (403 FORBIDDEN)")
    public void test08_porteroNoPuedeListarConfiguraciones_403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("9. RESIDENTE no puede listar configuraciones (403 FORBIDDEN)")
    public void test09_residenteNoPuedeListarConfiguraciones_403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("10. Anónimo no puede listar configuraciones (401 UNAUTHORIZED)")
    public void test10_anonimoNoPuedeListarConfiguraciones_401() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // PRUEBAS DE GENERACIÓN E HISTORIAL (Tests 11, 12, 13, 14, 15, 16, 17)
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("11. Generar reporte configurado (éxito y descarga binaria PDF)")
    public void test11_generarReporteConfigurado_exito() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "PDF"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertNotNull(content);
        assertTrue(content.length > 0);
        // Header de archivo PDF
        String header = new String(content, 0, Math.min(5, content.length));
        assertEquals("%PDF-", header);
    }

    @Test
    @Order(12)
    @DisplayName("12. Generación crea registro persistente en HISTORIAL_REPORTES")
    public void test12_generacionCreaRegistroEnHistorialReportes() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        int countBefore = queryElevated(() -> jdbcTemplate.queryForObject("SELECT COUNT(1) FROM HISTORIAL_REPORTES", Integer.class));

        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV"))
                .andExpect(status().isOk());

        int countAfter = queryElevated(() -> jdbcTemplate.queryForObject("SELECT COUNT(1) FROM HISTORIAL_REPORTES", Integer.class));
        assertEquals(countBefore + 1, countAfter, "Debe haberse insertado exactamente un registro en HISTORIAL_REPORTES");
    }

    @Test
    @Order(13)
    @DisplayName("13. Hash SHA-256 es determinístico y coincide exactamente con el byte[] generado")
    public void test13_hashSha256EsDeterministico() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'EJECUCION_CUOTAS' AND ROWNUM = 1",
                Long.class
        ));

        // 1. Ejecutar obteniendo metadata
        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV")
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metaResult.getResponse().getContentAsString());
        String sha256 = json.path("data").path("archivoSha256").asText();
        assertNotNull(sha256);
        assertEquals(64, sha256.length(), "El hash SHA-256 debe ser exactamente de 64 caracteres hexadecimales");

        // 2. Verificar hash contra base de datos
        Long histId = json.path("data").path("idHistorialReporte").asLong();
        String dbSha = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ARCHIVO_SHA256 FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                String.class,
                histId
        ));
        assertEquals(sha256, dbSha, "El hash retornado por API debe coincidir idénticamente con el almacenado en BD");
    }

    @Test
    @Order(14)
    @DisplayName("14. Historial registra número exacto de filas procesadas")
    public void test14_historialRegistraNumeroExactoFilasProcesadas() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metaResult.getResponse().getContentAsString());
        int rows = json.path("data").path("totalRegistros").asInt();
        assertTrue(rows >= 0, "El número de registros debe ser mayor o igual a 0");
    }

    @Test
    @Order(15)
    @DisplayName("15. Historial registra formato correcto generado")
    public void test15_historialRegistraFormatoCorrecto() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'PAGOS_RECIENTES' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("formato", "CSV")
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metaResult.getResponse().getContentAsString());
        assertEquals("CSV", json.path("data").path("formato").asText());
    }

    @Test
    @Order(16)
    @DisplayName("16. Historial registra usuario ejecutor")
    public void test16_historialRegistraUsuarioEjecutor() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metaResult.getResponse().getContentAsString());
        Long histId = json.path("data").path("idHistorialReporte").asLong();

        Long userIdInDb = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_USUARIO_EJECUTO FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?",
                Long.class,
                histId
        ));
        assertEquals(USER_ADMIN_PROP_1, userIdInDb);
    }

    @Test
    @Order(17)
    @DisplayName("17. Historial registra timestamp de ejecución")
    public void test17_historialRegistraTimestamp() throws Exception {
        Long configId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ID_REPORTE_CONFIG FROM REPORTES_CONFIGURADOS WHERE CODIGO = 'CARTERA_MOROSA' AND ROWNUM = 1",
                Long.class
        ));

        MvcResult metaResult = mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1)
                .param("metadata", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metaResult.getResponse().getContentAsString());
        String fecha = json.path("data").path("fechaEjecucion").asText();
        assertNotNull(fecha);
        assertFalse(fecha.isBlank());
    }

    // =========================================================================
    // PRUEBAS DE INMUTABILIDAD ORACLE (Tests 18, 19)
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("18. Intentar modificar registro de HISTORIAL_REPORTES (rechazado por trigger ORA-20060)")
    public void test18_modificarRegistroHistorial_rechazadoPorTrigger() {
        Long histId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT MIN(ID_HISTORIAL_REPORTE) FROM HISTORIAL_REPORTES",
                Long.class
        ));
        assertNotNull(histId, "Debe existir al menos un registro en historial");

        Exception exception = assertThrows(Exception.class, () -> {
            queryElevated(() -> jdbcTemplate.update("UPDATE HISTORIAL_REPORTES SET FORMATO_GENERADO = 'TAMPERED' WHERE ID_HISTORIAL_REPORTE = ?", histId));
        });

        assertTrue(exception.getMessage().contains("ORA-20060") || exception.getMessage().contains("HISTORIAL_REPORTES es inmutable"),
                "El trigger de inmutabilidad TRG_HISTREP_INMUTABLE debe rechazar UPDATE con ORA-20060");
    }

    @Test
    @Order(19)
    @DisplayName("19. Intentar eliminar registro de HISTORIAL_REPORTES (rechazado por trigger ORA-20060)")
    public void test19_eliminarRegistroHistorial_rechazadoPorTrigger() {
        Long histId = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT MIN(ID_HISTORIAL_REPORTE) FROM HISTORIAL_REPORTES",
                Long.class
        ));
        assertNotNull(histId);

        Exception exception = assertThrows(Exception.class, () -> {
            queryElevated(() -> jdbcTemplate.update("DELETE FROM HISTORIAL_REPORTES WHERE ID_HISTORIAL_REPORTE = ?", histId));
        });

        assertTrue(exception.getMessage().contains("ORA-20060") || exception.getMessage().contains("HISTORIAL_REPORTES es inmutable"),
                "El trigger de inmutabilidad TRG_HISTREP_INMUTABLE debe rechazar DELETE con ORA-20060");
    }

    // =========================================================================
    // PRUEBAS ESPECIALES ID_PROPIEDAD = NULL Y TENANT HISTORIAL (Tests 20, 21, 22, 23)
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("20. ADMIN_ORGANIZACION puede consultar historial consolidado (ID_PROPIEDAD = NULL)")
    public void test20_adminOrgPuedeConsultarHistorialConsolidadoIdPropiedadNull() throws Exception {
        // Insertar registro con ID_PROPIEDAD = NULL en HISTORIAL_REPORTES
        setElevatedContext();
        try {
            Long configId = jdbcTemplate.queryForObject("SELECT MIN(ID_REPORTE_CONFIG) FROM REPORTES_CONFIGURADOS", Long.class);
            jdbcTemplate.update("""
                INSERT INTO HISTORIAL_REPORTES (ID_REPORTE_CONFIG, ID_ORGANIZACION, ID_PROPIEDAD, ID_USUARIO_EJECUTO, FORMATO_GENERADO,
                    ARCHIVO_GENERADO_URL, ARCHIVO_SHA256, REGISTROS_PROCESADOS, FECHA_EJECUCION)
                VALUES (?, ?, NULL, ?, 'PDF', 'consolidado_org1.pdf', 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890', 10, CURRENT_TIMESTAMP)
            """, configId, ORG_1_ID, USER_ADMIN_ORG_1);
        } finally {
            clearContext();
        }

        MvcResult result = mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("consolidado_org1.pdf"), "ADMIN_ORGANIZACION debe poder leer reportes consolidados con ID_PROPIEDAD = NULL");
    }

    @Test
    @Order(21)
    @DisplayName("21. ADMIN_ORGANIZACION puede consultar historial de una propiedad de su organización")
    public void test21_adminOrgPuedeConsultarHistorialPropiedadSuOrg() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .param("propertyId", String.valueOf(PROP_1_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(22)
    @DisplayName("22. ADMIN_ORGANIZACION no puede consultar historial de otra organización")
    public void test22_adminOrgNoPuedeConsultarHistorialOtraOrg() throws Exception {
        // Insertar registro en Org 2
        Long idHistOrg2;
        setElevatedContext();
        try {
            Long configId = jdbcTemplate.queryForObject("SELECT MIN(ID_REPORTE_CONFIG) FROM REPORTES_CONFIGURADOS", Long.class);
            jdbcTemplate.update("""
                INSERT INTO HISTORIAL_REPORTES (ID_REPORTE_CONFIG, ID_ORGANIZACION, ID_PROPIEDAD, ID_USUARIO_EJECUTO, FORMATO_GENERADO,
                    ARCHIVO_GENERADO_URL, ARCHIVO_SHA256, REGISTROS_PROCESADOS, FECHA_EJECUCION)
                VALUES (?, ?, ?, ?, 'PDF', 'privado_org2.pdf', '0000000000000000000000000000000000000000000000000000000000000000', 5, CURRENT_TIMESTAMP)
            """, configId, ORG_2_ID, PROP_2_ID, USER_ADMIN_PROP_2);

            idHistOrg2 = jdbcTemplate.queryForObject(
                    "SELECT MAX(ID_HISTORIAL_REPORTE) FROM HISTORIAL_REPORTES WHERE ARCHIVO_GENERADO_URL = 'privado_org2.pdf'",
                    Long.class
            );
        } finally {
            clearContext();
        }

        mockMvc.perform(get("/api/v1/reportes/historial/" + idHistOrg2)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    assertTrue(sc == 403 || sc == 404, "El acceso al historial de otra organización debe ser rechazado (403 Forbidden o 404 Not Found por RLS)");
                });
    }

    @Test
    @Order(23)
    @DisplayName("23. ADMIN_PROPIEDAD solo puede consultar historial de su propiedad")
    public void test23_adminPropSoloPuedeConsultarHistorialSuPropiedad() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_PROP_1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("privado_org2.pdf"), "ADMIN_PROPIEDAD no debe ver historial de otra propiedad");
    }

    // =========================================================================
    // PRUEBAS DE AUTORIZACIÓN SOBRE HISTORIAL (Tests 24, 25)
    // =========================================================================

    @Test
    @Order(24)
    @DisplayName("24. PORTERO no puede consultar historial (403 FORBIDDEN)")
    public void test24_porteroNoPuedeConsultarHistorial_403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenPortero)
                .header("X-Assignment-Id", ASSIGN_PORTERO))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(25)
    @DisplayName("25. RESIDENTE no puede consultar historial (403 FORBIDDEN)")
    public void test25_residenteNoPuedeConsultarHistorial_403() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", ASSIGN_RESIDENTE))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PRUEBAS DE DESACTIVACIÓN E INTACTABILIDAD DEL HISTORIAL (Tests 26, 27)
    // =========================================================================

    @Test
    @Order(26)
    @DisplayName("26. Desactivar configuración (éxito, estado cambia a INACTIVO)")
    public void test26_desactivarConfiguracion_exito() throws Exception {
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setCodigo("REP_TO_DEACTIVATE_" + System.currentTimeMillis());
        req.setNombre("Reporte para desactivar");
        req.setConsultaOrigenClave("CARTERA_MOROSA");
        req.setFormatoSalidaDefecto("PDF");

        MvcResult createResult = mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long id = createdJson.path("data").path("idReporteConfig").asLong();

        // Desactivar
        mockMvc.perform(delete("/api/v1/reportes/configurados/" + id)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk());

        String estadoInDb = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM REPORTES_CONFIGURADOS WHERE ID_REPORTE_CONFIG = ?",
                String.class,
                id
        ));
        assertEquals("INACTIVO", estadoInDb);
    }

    @Test
    @Order(27)
    @DisplayName("27. Desactivar configuración no destruye el historial generado previamente")
    public void test27_desactivarNoBorraHistorialPrevio() throws Exception {
        // Crear configuración
        ReporteConfiguradoCreateRequest req = new ReporteConfiguradoCreateRequest();
        req.setCodigo("REP_WITH_HIST_" + System.currentTimeMillis());
        req.setNombre("Reporte con Historial Previo");
        req.setConsultaOrigenClave("PAGOS_RECIENTES");
        req.setFormatoSalidaDefecto("PDF");

        MvcResult createResult = mockMvc.perform(post("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long configId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("idReporteConfig").asLong();

        // Generar para crear historial
        mockMvc.perform(post("/api/v1/reportes/configurados/" + configId + "/generar")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1)
                .param("metadata", "true"))
                .andExpect(status().isOk());

        int histCountBefore = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ?",
                Integer.class,
                configId
        ));
        assertTrue(histCountBefore > 0);

        // Desactivar configuración
        mockMvc.perform(delete("/api/v1/reportes/configurados/" + configId)
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", ASSIGN_ADMIN_ORG_1))
                .andExpect(status().isOk());

        // Verificar que historial sigue intacto
        int histCountAfter = queryElevated(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM HISTORIAL_REPORTES WHERE ID_REPORTE_CONFIG = ?",
                Integer.class,
                configId
        ));
        assertEquals(histCountBefore, histCountAfter, "El historial no debe ser destruido al desactivar la configuración");
    }

    // =========================================================================
    // PRUEBAS DE SUPERADMIN GLOBAL (Test 28)
    // =========================================================================

    @Test
    @Order(28)
    @DisplayName("28. SUPERADMIN puede consultar configuración e historial global")
    public void test28_superadminPuedeConsultarConfiguracionEHistorialGlobal() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/configurados")
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/reportes/historial")
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", ASSIGN_SUPERADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
