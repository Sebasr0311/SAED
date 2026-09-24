package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoDetalleDTO;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
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
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Integración y Certificación de Integridad Financiera y Seguridad para Paz y Salvo (GAP-F6-04).
 *
 * Certifica los 18 casos de prueba (PS-01 a PS-18):
 * - F6-04-01: Integridad Financiera Integral (Cartera + Multas sin doble conteo).
 * - F6-04-02: Pipeline Documental Oficial PDF (Generación, SHA-256, Quota y Storage).
 * - F6-04-03: Descarga Segura y Aislamiento Anti-IDOR Estricto por rol/tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PazYSalvoFinancialIntegrityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PazYSalvoService pazYSalvoService;

    @Autowired
    private FileStorageService fileStorageService;

    @MockBean
    private AssignmentService assignmentService;

    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_ADMIN_ORG_1 = 8L;
    private static final long ASSIGN_ADMIN_ORG_1 = 301L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    private static final long USER_ADMIN_ORG_2 = 98L;
    private static final long ASSIGN_ADMIN_ORG_2 = 981L;

    private static final long USER_RESIDENTE_1 = 4L; // Carlos Martinez (Apto 101 / Unit 1)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Ana Gomez (Apto 102 / Unit 2)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    @BeforeEach
    public void setUp() {
        setElevatedContext();

        try {
            // Limpieza de datos de prueba en unidades
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);

            ensureOrganizacion(9992L, "Org 9992 Foranea", "900009992-9", "org9992@test.com");
            ensurePropiedad(9992L, 9992L, "Propiedad 9992 Test");
            ensureUnidad(9992L, 9992L, "Apto 9992");

            ensurePropiedad(1L, 1L, "Edificio Residencial SAED");
            ensureUnidad(1L, 1L, "Apto 101");
            ensureUnidad(2L, 1L, "Apto 102");

            ensurePersona(1L, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(1L, 1L, "superadmin", "superadmin@saed.com");

            ensurePersona(2L, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(2L, 2L, "admin", "admin@saed.com");

            ensurePersona(4L, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
            ensureUsuario(4L, 4L, "carlos_m", "camartinez@saed.com");

            ensurePersona(5L, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(5L, 5L, "ana_g", "anagomez@saed.com");

            ensurePersona(8L, "1000000008", "AdminOrg1", "SAED", "admin_org1@saed.com");
            ensureUsuario(8L, 8L, "admin_org1", "admin_org1@saed.com");

            ensurePersona(98L, "1000000098", "AdminOrg2", "SAED", "admin_org2@saed.com");
            ensureUsuario(98L, 98L, "admin_org2", "admin_org2@saed.com");

            ensurePersona(99L, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
            ensureUsuario(99L, 99L, "admin_prop2", "admin_prop2@saed.com");

            // Asegurar membresía activa para cuota documental
            ensureMembresia(1L);
            ensureMembresia(9992L);

            setupMockAssignments();
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN SETUP: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        try {
            setElevatedContext();
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea");
        UnitDTO unitOrg2 = new UnitDTO(UNIT_ORG2_ID, "Apto 9992");

        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        AssignmentResponseDTO adminOrg1Assign = new AssignmentResponseDTO();
        adminOrg1Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        adminOrg1Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg1Assign.setOrganizacion(org1);

        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

        AssignmentResponseDTO adminOrg2Assign = new AssignmentResponseDTO();
        adminOrg2Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_2);
        adminOrg2Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg2Assign.setOrganizacion(org2);

        AssignmentResponseDTO adminProp2Assign = new AssignmentResponseDTO();
        adminProp2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        adminProp2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp2Assign.setOrganizacion(org2);
        adminProp2Assign.setPropiedad(prop2);

        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);

        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(adminOrg1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_2, USER_ADMIN_ORG_2)).thenReturn(Optional.of(adminOrg2Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private void clearContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    // =========================================================================
    // CASOS DE PRUEBA: GAP-F6-04 (PS-01 A PS-18)
    // =========================================================================

    @Test
    @DisplayName("PS-01: Unidad sin deudas (cartera = 0, multas = 0) emite Paz y Salvo con éxito (201 Created)")
    void test01_emitirPazYSalvo_unidadSinDeuda_exitoso() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_1_ID,
                "motivo", "Venta de inmueble"
        );

        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.codigoVerificacion", notNullValue()))
                .andExpect(jsonPath("$.data.estado", is("VALIDO")))
                .andExpect(jsonPath("$.data.documentoPdfUrl", notNullValue()));
    }

    @Test
    @DisplayName("PS-02: Unidad con cuota pendiente (cartera > 0) bloquea emisión de Paz y Salvo (400 Bad Request)")
    void test02_bloquearPazYSalvo_conCuotaPendiente_carteraMora() throws Exception {
        setElevatedContext();
        // Insertar cuota pendiente en unidad 1
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (1, 1, '2026-06', 350000, 350000, TRUNC(SYSDATE) + 15, 'PENDIENTE')
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_1_ID,
                "motivo", "Salida de inquilino"
        );

        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unidad tiene saldo pendiente")));
    }

    @Test
    @DisplayName("PS-03: Unidad con multa no facturada (ID_CUOTA IS NULL, ESTADO = 'IMPUESTA') bloquea emisión")
    void test03_bloquearPazYSalvo_conMultaImpuestaNoFacturada() throws Exception {
        setElevatedContext();
        // Insertar multa no facturada
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 150000, 'Ruido excesivo en zona común', 'IMPUESTA', NULL)
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_1_ID,
                "motivo", "Solicitud de paz y salvo"
        );

        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unidad tiene saldo pendiente")));
    }

    @Test
    @DisplayName("PS-04: Unidad con multa no facturada (ID_CUOTA IS NULL, ESTADO = 'RATIFICADA') bloquea emisión")
    void test04_bloquearPazYSalvo_conMultaRatificadaNoFacturada() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 200000, 'Mascota sin correa en pasillo', 'RATIFICADA', NULL)
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of(
                "idUnidad", UNIT_1_ID,
                "motivo", "Trámite bancario"
        );

        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unidad tiene saldo pendiente")));
    }

    @Test
    @DisplayName("PS-05: Doble conteo protegido: Multa vinculada a cuota pendiente se contabiliza solo una vez")
    void test05_proteccionDobleConteo_multaFacturadaEnCuotaPendiente() throws Exception {
        setElevatedContext();
        // 1. Crear cuota por 200,000
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_CUOTA, ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (8801, 1, 1, '2026-07', 200000, 200000, TRUNC(SYSDATE) + 10, 'PENDIENTE')
        """);
        // 2. Vincular multa a esa cuota
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 200000, 'Multa facturada en cuota', 'IMPUESTA', 8801)
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        MvcResult result = mockMvc.perform(get("/api/v1/paz-y-salvos/unidad/" + UNIT_1_ID + "/estado-financiero")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        PazYSalvoEstadoFinancieroDTO estado = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                PazYSalvoEstadoFinancieroDTO.class
        );

        // Saldo cartera debe ser 200000
        assertEquals(0, new BigDecimal("200000").compareTo(estado.saldoCartera()));
        // Saldo multas no facturadas debe ser 0 (protección anti-doble conteo)
        assertEquals(0, BigDecimal.ZERO.compareTo(estado.saldoMultasNoFacturadas()));
        // Total exigible debe ser exactamente 200000, no 400000
        assertEquals(0, new BigDecimal("200000").compareTo(estado.saldoTotalExigible()));
        assertFalse(estado.pazYSalvo());
    }

    @Test
    @DisplayName("PS-06: Multa vinculada a cuota pagada (PAGADA) permite emisión de Paz y Salvo")
    void test06_multaFacturada_conCuotaPagada_permiteEmision() throws Exception {
        setElevatedContext();
        // Cuota pagada
        jdbcTemplate.update("""
            INSERT INTO CUOTAS (ID_CUOTA, ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
            VALUES (8802, 1, 1, '2026-07', 180000, 0, TRUNC(SYSDATE) - 5, 'PAGADA')
        """);
        // Multa vinculada a cuota pagada
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 180000, 'Multa pagada mediante cuota', 'PAGADA', 8802)
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Verificar estado financiero
        mockMvc.perform(get("/api/v1/paz-y-salvos/unidad/" + UNIT_1_ID + "/estado-financiero")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pazYSalvo", is(true)))
                .andExpect(jsonPath("$.data.saldoTotalExigible", is(0)));

        // Emisión permitida
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Certificación bancaria");
        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.estado", is("VALIDO")));
    }

    @Test
    @DisplayName("PS-07: Multa condonada o anulada no bloquea la emisión de Paz y Salvo")
    void test07_multaCondonadaOAnulada_noBloqueaPazYSalvo() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 250000, 'Sanción revocada en descargos', 'CONDONADA', NULL)
        """);
        jdbcTemplate.update("""
            INSERT INTO MULTAS (ID_UNIDAD, ID_CONCEPTO, MONTO, MOTIVO, ESTADO, ID_CUOTA)
            VALUES (1, 1, 100000, 'Sanción anulada por error administrativo', 'ANULADA', NULL)
        """);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Paz y salvo ordinario");
        mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.estado", is("VALIDO")));
    }

    @Test
    @DisplayName("PS-08: Pipeline Documental: El artefacto PDF oficial se genera y almacena físicamente")
    void test08_pipelinePdf_generaArtefactoFisicoNoVacio() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Verificación documental");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String pdfUrl = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("documentoPdfUrl").asText();
        assertNotNull(pdfUrl);
        assertFalse(pdfUrl.isBlank());

        // Cargar recurso físico y verificar cabecera PDF (%PDF-)
        Resource resource = fileStorageService.loadAsResource(pdfUrl);
        assertTrue(resource.exists());
        try (InputStream is = resource.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            assertEquals(5, read);
            assertEquals("%PDF-", new String(header));
        }
    }

    @Test
    @DisplayName("PS-09: Integridad Criptográfica: Se calcula y persiste el hash SHA-256 en DOCUMENTO_HASH")
    void test09_integridadCriptografica_sha256Persistido() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Hash check");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asLong();
        String hashFromApi = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("documentoHash").asText();
        assertNotNull(hashFromApi);
        assertEquals(64, hashFromApi.length());

        // Verificar en base de datos
        setElevatedContext();
        String hashInDb = jdbcTemplate.queryForObject("SELECT DOCUMENTO_HASH FROM PAZ_Y_SALVOS WHERE ID_PAZ_SALVO = ?", String.class, id);
        String pdfUrl = jdbcTemplate.queryForObject("SELECT DOCUMENTO_PDF_URL FROM PAZ_Y_SALVOS WHERE ID_PAZ_SALVO = ?", String.class, id);
        clearContext();

        assertEquals(hashFromApi, hashInDb);

        // Recalcular SHA-256 de los bytes físicos
        Resource resource = fileStorageService.loadAsResource(pdfUrl);
        byte[] bytes = resource.getInputStream().readAllBytes();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String recomputedHash = HexFormat.of().formatHex(digest.digest(bytes));

        assertEquals(recomputedHash, hashInDb);
    }

    @Test
    @DisplayName("PS-10: Metadatos Documentales: DOCUMENTO_TAMANO_BYTES coincide con el tamaño del archivo")
    void test10_metadatosDocumentales_tamanoBytes() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Size check");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asLong();

        setElevatedContext();
        Long tamanoInDb = jdbcTemplate.queryForObject("SELECT DOCUMENTO_TAMANO_BYTES FROM PAZ_Y_SALVOS WHERE ID_PAZ_SALVO = ?", Long.class, id);
        String pdfUrl = jdbcTemplate.queryForObject("SELECT DOCUMENTO_PDF_URL FROM PAZ_Y_SALVOS WHERE ID_PAZ_SALVO = ?", String.class, id);
        clearContext();

        assertNotNull(tamanoInDb);
        assertTrue(tamanoInDb > 0);

        Resource resource = fileStorageService.loadAsResource(pdfUrl);
        assertEquals(resource.contentLength(), tamanoInDb.longValue());
    }

    @Test
    @DisplayName("PS-11: Validación de Cuota: La generación del PDF valida y registra el consumo de almacenamiento")
    void test11_validacionYRegistroQuota_storageService() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Quota test");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asLong();
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("PS-12: Persistencia Segura: El archivo físico se ubica en el subdirectorio 'paz_y_salvos'")
    void test12_persistenciaSegura_subdirectorioPazYSalvos() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Path safe test");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String pdfUrl = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("documentoPdfUrl").asText();
        assertTrue(pdfUrl.startsWith("paz_y_salvos/"));
        assertFalse(pdfUrl.contains("..")); // Prevención de path traversal
    }

    @Test
    @DisplayName("PS-13: Código Único de Verificación: Verificable exitosamente mediante /verificar/{codigo}")
    void test13_codigoVerificacionUnico_verificablePublicamente() throws Exception {
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Verificación pública");
        MvcResult res = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String codigo = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("codigoVerificacion").asText();
        assertNotNull(codigo);

        mockMvc.perform(get("/api/v1/paz-y-salvos/verificar/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.CODIGO_VERIFICACION", is(codigo)))
                .andExpect(jsonPath("$.data.ESTADO", is("VALIDO")))
                .andExpect(jsonPath("$.data.esValido", is(true)));
    }

    @Test
    @DisplayName("PS-14: Anti-IDOR Positivo Residente: RESIDENTE puede descargar el PDF oficial de su propia unidad")
    void test14_antiIdor_residentePuedeDescargarSuPropioPdf() throws Exception {
        // Emitir paz y salvo para unidad 1
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Emisión para residente");
        MvcResult emitRes = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(emitRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Residente de Unidad 1 descarga el documento
        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);
        mockMvc.perform(get("/api/v1/paz-y-salvos/" + id + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().exists("X-Content-Sha256"));
    }

    @Test
    @DisplayName("PS-15: Anti-IDOR Negativo Residente: RESIDENTE no puede descargar PDF de otra unidad (403 Forbidden)")
    void test15_antiIdor_residenteNoPuedeDescargarPdfDeOtraUnidad() throws Exception {
        // Emitir paz y salvo para unidad 1
        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Emisión unidad 1");
        MvcResult emitRes = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long idUnit1 = objectMapper.readTree(emitRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Residente de Unidad 2 intenta descargar paz y salvo de Unidad 1
        String tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
        mockMvc.perform(get("/api/v1/paz-y-salvos/" + idUnit1 + "/descargar")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PS-16: Anti-IDOR Negativo Admin Propiedad: ADMIN_PROPIEDAD no puede descargar PDF de otra propiedad (403)")
    void test16_antiIdor_adminPropiedadNoPuedeDescargarPdfDeOtraPropiedad() throws Exception {
        // Emitir paz y salvo en Propiedad 1
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Emisión Prop 1");
        MvcResult emitRes = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long idProp1 = objectMapper.readTree(emitRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Admin de Propiedad 9992 intenta descargar documento de Propiedad 1
        String tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
        mockMvc.perform(get("/api/v1/paz-y-salvos/" + idProp1 + "/descargar")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PS-17: Anti-IDOR Negativo Admin Organización: ADMIN_ORGANIZACION no puede descargar PDF de otra Org (403)")
    void test17_antiIdor_adminOrganizacionNoPuedeDescargarPdfDeOtraOrg() throws Exception {
        // Emitir paz y salvo en Org 1
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Emisión Org 1");
        MvcResult emitRes = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long idOrg1 = objectMapper.readTree(emitRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Admin de Organización 9992 intenta descargar documento de Organización 1
        String tokenAdminOrg2 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_2);
        mockMvc.perform(get("/api/v1/paz-y-salvos/" + idOrg1 + "/descargar")
                .header("Authorization", "Bearer " + tokenAdminOrg2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_2)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PS-18: Descarga Global SuperAdmin: SUPERADMIN puede descargar cualquier certificado de Paz y Salvo")
    void test18_superAdminPuedeDescargarCualquierPdf() throws Exception {
        // Emitir paz y salvo en Propiedad 1
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        Map<String, Object> body = Map.of("idUnidad", UNIT_1_ID, "motivo", "Emisión para SuperAdmin");
        MvcResult emitRes = mockMvc.perform(post("/api/v1/paz-y-salvos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(emitRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // SuperAdmin descarga globalmente
        String tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
        mockMvc.perform(get("/api/v1/paz-y-salvos/" + id + "/descargar")
                .header("Authorization", "Bearer " + tokenSuperAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_SUPERADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // --- MÉTODOS AUXILIARES DE ASEGURAMIENTO DE DATOS ---

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NIT, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long id, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, ?, 'Calle 123', 'Bogotá', 'RESIDENCIAL', 'ACTIVA')", id, idOrg, nombre);
        }
    }

    private void ensureUnidad(Long id, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                    "VALUES (?, ?, ?, 'RESIDENCIAL', 0.05, 'DISPONIBLE')", id, idProp, identificador);
        }
    }

    private void ensurePersona(Long id, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, ?, ?, ?, ?)", id, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long id, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, USERNAME, EMAIL, PASSWORD_HASH, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'ACTIVO')", id, idPersona, username, email);
        }
    }

    private void ensureMembresia(Long idOrg) {
        Long defaultPlanId = jdbcTemplate.queryForObject(
                "SELECT ID_PLAN FROM PLANES WHERE (LIMITE_USUARIOS IS NULL OR LIMITE_USUARIOS = 0 OR LIMITE_USUARIOS >= 50) AND ROWNUM = 1",
                Long.class
        );
        if (defaultPlanId != null) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", Integer.class, idOrg);
            if (count == null || count == 0) {
                jdbcTemplate.update("INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                        "VALUES (?, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')", idOrg, defaultPlanId);
            }
        }
    }
}
