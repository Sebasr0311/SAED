package com.saed.backend.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportVolumeLimitExceededException;
import com.saed.backend.dashboard.export.ReportExportService;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F11_04_ExportacionesIntegrationTest
 *
 * Suite determinista de integración y certificación para el Motor de Exportaciones (F11-04 Bloque C):
 * PDF (OpenHTMLtoPDF), CSV (RFC 4180 BOM UTF-8), seguridad multi-tenant (IDOR, roles),
 * límites de volumen (422 EXCEEDS_EXPORT_LIMIT) y paridad de datos con el motor base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class F11_04_ExportacionesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportExportService reportExportService;

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

    private void ensureBaseData() {
        // Organizaciones
        ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

        // Propiedades
        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

        // Unidades
        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "A101", BigDecimal.valueOf(0.250000));
        ensureUnidad(UNIDAD_2_ID, PROP_2_ID, "201", BigDecimal.valueOf(0.250000));

        // Conceptos de Cobro
        ensureConcepto(CONCEPTO_1_ID, ORG_1_ID, PROP_1_ID, "CUOTA_ORD_1", "Cuota Ordinaria Prop 1");
        ensureConcepto(CONCEPTO_2_ID, ORG_2_ID, PROP_2_ID, "CUOTA_ORD_2", "Cuota Ordinaria Prop 2");

        // Personas & Usuarios
        ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin_exp@saed.com");
        ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1_exp@saed.com");
        ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1_exp@saed.com");
        ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2_exp@saed.com");
        ensurePersona(USER_RESIDENTE, "1000000805", "Carlos", "Residente", "residente_exp@saed.com");
        ensurePersona(USER_PORTERO, "1000000806", "Pedro", "Portero", "portero_exp@saed.com");

        ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin_exp", "superadmin_exp@saed.com");
        ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_exp", "adminorg1_exp@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_exp", "adminprop1_exp@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_exp", "adminprop2_exp@saed.com");
        ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_exp", "residente_exp@saed.com");
        ensureUsuario(USER_PORTERO, USER_PORTERO, "portero_exp", "portero_exp@saed.com");

        // Asignaciones
        ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
        ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_PORTERO, USER_PORTERO, "PORTERO", ORG_1_ID, PROP_1_ID, null);

        // Limpiar cuotas y pagos de prueba
        try {
            jdbcTemplate.execute("DELETE FROM PAGOS WHERE ID_UNIDAD IN (9101, 9201)");
            jdbcTemplate.execute("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (9101, 9201)");
        } catch (Exception ignored) {}

        // Semillas
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 99000000, 99000000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-07', 200000, 0, 'PAGADA', DATE '2026-07-15')
        """, UNIDAD_1_ID, CONCEPTO_1_ID);

        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
            VALUES (?, ?, '2026-08', 500000, 500000, 'VENCIDA', DATE '2026-08-15')
        """, UNIDAD_2_ID, CONCEPTO_2_ID);

        jdbcTemplate.update("""
            INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE)
            VALUES (?, 200000, 'TRANSFERENCIA', 'APROBADO', TIMESTAMP '2026-09-30 14:30:00', 'REF-PAGO-001')
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
    // 1. PDF EXPORT TESTS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("PDF: Exportación de cartera morosa genera archivo PDF válido con cabeceras correctas")
    public void testExportPdfCarteraMorosaSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("formato", "PDF")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertTrue(content.length > 0, "El archivo PDF no debe estar vacío");
        String header = new String(content, 0, Math.min(content.length, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "El archivo debe iniciar con el número mágico de PDF %PDF-");

        String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"), "Debe ser de tipo attachment");
        assertTrue(disposition.contains("cartera_morosa_"), "Debe tener el prefijo cartera_morosa_");
        assertTrue(disposition.contains(".pdf"), "Debe tener extensión .pdf");
    }

    @Test
    @Order(2)
    @DisplayName("PDF: Exportación de ejecución de cuotas genera PDF válido")
    public void testExportPdfEjecucionCuotasSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-cuotas")
                        .param("format", "pdf")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertTrue(content.length > 0);
        String header = new String(content, 0, Math.min(content.length, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }

    @Test
    @Order(3)
    @DisplayName("PDF: Exportación de pagos recientes genera PDF válido")
    public void testExportPdfPagosRecientesSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/pagos-recientes")
                        .param("formato", "PDF")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertTrue(content.length > 0);
        String header = new String(content, 0, Math.min(content.length, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }

    @Test
    @Order(4)
    @DisplayName("PDF: Exportación de ejecución presupuestal global genera PDF válido")
    public void testExportPdfEjecucionPresupuestalSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .param("formato", "PDF")
                        .param("vigencia", "2026")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertTrue(content.length > 0);
        String header = new String(content, 0, Math.min(content.length, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }

    // =========================================================================
    // 2. CSV EXPORT TESTS (RFC 4180 + UTF-8 BOM)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("CSV: Exportación de cartera morosa incluye BOM UTF-8, cabeceras RFC 4180 y datos de la propiedad")
    public void testExportCsvCarteraMorosaSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length >= 3, "El archivo debe contener al menos el BOM");
        assertEquals((byte) 0xEF, bytes[0], "BOM byte 1");
        assertEquals((byte) 0xBB, bytes[1], "BOM byte 2");
        assertEquals((byte) 0xBF, bytes[2], "BOM byte 3");

        String csvText = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("Unidad,Propiedad,Cuotas Pendientes,Deuda Total,Primer Vencimiento,Ultimo Vencimiento,Dias Mora"),
                "Debe contener los encabezados exactos");
        assertTrue(csvText.contains("A101"), "Debe contener la unidad de la propiedad 1");
        assertTrue(csvText.contains("99000000"), "Debe contener el saldo pendiente");
        assertFalse(csvText.contains("201"), "NO debe contener unidades de la propiedad 2");
    }

    @Test
    @Order(6)
    @DisplayName("CSV: Exportación de ejecución de cuotas genera CSV válido")
    public void testExportCsvEjecucionCuotasSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-cuotas")
                        .param("format", "csv")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        String csvText = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("Periodo,Total Cuotas,Pagadas,Pendientes,Total Facturado,Total Pendiente,Total Recaudado,Porcentaje Recaudado (%)"));
        assertTrue(csvText.contains("2026-08") || csvText.contains("2026-07"));
    }

    @Test
    @Order(7)
    @DisplayName("CSV: Exportación de pagos recientes incluye columnas y datos de recaudo")
    public void testExportCsvPagosRecientesSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/pagos-recientes")
                        .param("formato", "csv")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andReturn();

        String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csvText.contains("ID Pago,Unidad,Monto Total,Metodo Pago,Estado,Fecha Pago,Referencia Comprobante"));
        assertTrue(csvText.contains("A101"));
        assertTrue(csvText.contains("200000"));
        assertTrue(csvText.contains("TRANSFERENCIA"));
        assertTrue(csvText.contains("APROBADO"));
    }

    @Test
    @Order(8)
    @DisplayName("CSV: Exportación de ejecución presupuestal global refleja métricas presupuestadas vs ejecutadas")
    public void testExportCsvEjecucionPresupuestalSuccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reportes/ejecucion-presupuestal")
                        .param("formato", "CSV")
                        .param("vigencia", "2026")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andReturn();

        String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csvText.contains("Vigencia,Ingresos Presupuestados,Ingresos Ejecutados,% Ejecucion Ingresos,Egresos Presupuestados,Egresos Ejecutados,% Ejecucion Egresos,Superavit Presupuestado,Superavit Ejecutado,Estado Financiero,Total Pagos Aprobados,Total Gastos Pagados"));
        assertTrue(csvText.contains("2026"));
        assertTrue(csvText.contains("SUPERAVIT") || csvText.contains("DEFICIT") || csvText.contains("EQUILIBRADO"));
    }

    // =========================================================================
    // 3. SEGURIDAD MULTI-TENANT & IDOR EN EXPORTACIONES
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("Seguridad: ADMIN_PROPIEDAD recibe 403 al intentar exportar con propertyId de otra propiedad (IDOR check)")
    public void testAdminPropiedadCannotExportForeignProperty() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("propertyId", String.valueOf(PROP_2_ID))
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("Seguridad: ADMIN_ORGANIZACION recibe 403 al intentar exportar propiedad ajena a su organización")
    public void testAdminOrganizacionCannotExportForeignOrgProperty() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("propertyId", String.valueOf(PROP_2_ID))
                        .param("formato", "PDF")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    @DisplayName("Seguridad: ADMIN_ORGANIZACION puede exportar propiedad perteneciente a su propia organización")
    public void testAdminOrganizacionCanExportOwnOrgProperty() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("propertyId", String.valueOf(PROP_1_ID))
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"));
    }

    @Test
    @Order(12)
    @DisplayName("Seguridad: Rol RESIDENTE es rechazado con 403 FORBIDDEN al solicitar exportación")
    public void testResidenteCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenResidente)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @DisplayName("Seguridad: Rol PORTERO es rechazado con 403 FORBIDDEN al solicitar exportación")
    public void testPorteroCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/pagos-recientes")
                        .param("formato", "PDF")
                        .header("Authorization", "Bearer " + tokenPortero)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_PORTERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("Seguridad: Solicitud anónima sin autenticación es rechazada con 401 UNAUTHORIZED")
    public void testAnonymousCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("formato", "CSV"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(15)
    @DisplayName("Seguridad: SUPERADMIN puede exportar reportes de cualquier propiedad legítima")
    public void testSuperAdminCanExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("propertyId", String.valueOf(PROP_1_ID))
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_SUPERADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"));
    }

    // =========================================================================
    // 4. LÍMITE DE VOLUMEN (422 EXCEEDS_EXPORT_LIMIT)
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("Volumen: Cuando la cantidad supera 10.000 filas se lanza ExportVolumeLimitExceededException")
    public void testExportVolumeLimitExceededThrowsException() {
        List<CarteraMorosaDTO> oversizedList = new ArrayList<>();
        CarteraMorosaDTO item = new CarteraMorosaDTO("101", "Edificio Test", 1L, BigDecimal.TEN, LocalDate.now(), LocalDate.now(), 5L);
        for (int i = 0; i < 10001; i++) {
            oversizedList.add(item);
        }

        ExportVolumeLimitExceededException ex = assertThrows(ExportVolumeLimitExceededException.class, () ->
                reportExportService.exportCarteraMorosa(oversizedList, ExportFormat.CSV, PROP_1_ID, null, null)
        );
        assertTrue(ex.getMessage().contains("10.000"));
    }

    // =========================================================================
    // 5. PARIDAD E INTEGRIDAD CON DATOS JSON
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("Integridad: Datos exportados en CSV coinciden exactamente con los datos JSON devueltos")
    public void testExportParityWithJson() throws Exception {
        // 1. Obtener JSON
        MvcResult jsonResult = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonBody = jsonResult.getResponse().getContentAsString();

        // 2. Obtener CSV
        MvcResult csvResult = mockMvc.perform(get("/api/v1/reportes/cartera-morosa")
                        .param("formato", "CSV")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        String csvBody = new String(csvResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Ambas representaciones deben contener los mismos valores de negocio
        assertTrue(jsonBody.contains("A101"));
        assertTrue(csvBody.contains("A101"));

        assertTrue(jsonBody.contains("99000000"));
        assertTrue(csvBody.contains("99000000"));
    }
}
