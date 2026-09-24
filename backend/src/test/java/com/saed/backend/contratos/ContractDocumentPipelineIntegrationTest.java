package com.saed.backend.contratos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Pruebas de Integración y Seguridad para el Pipeline Documental de Contratos de Arrendamiento (GAP-F5-02).
 *
 * Certifica de extremo a extremo:
 * 1. Uso de plantilla real desde base de datos (PLANTILLAS_CONTRATOS) con variables unificadas.
 * 2. Inmutabilidad histórica del snapshot contractual (HTML_CONGELADO) frente a cambios posteriores de plantilla.
 * 3. Validez física del PDF compilado (encabezado %PDF-) y almacenamiento seguro (uploads/contratos).
 * 4. Integridad criptográfica SHA-256 del archivo físico vs DOCUMENTO_HASH persistido.
 * 5. Descarga autorizada para ADMIN_PROPIEDAD, ADMIN_ORGANIZACION y RESIDENTE titular (200 OK + headers).
 * 6. Control de acceso anti-IDOR estricto (bloqueo a residentes de otras unidades y administradores cruzados).
 * 7. Limpieza de archivos huérfanos y validación de almacenamiento.
 * 8. Alineación con restricción de estados canónicos de Oracle (CK_CONTRATOS_ESTADO).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ContractDocumentPipelineIntegrationTest {

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
    private FinanzasService finanzasService;

    @Autowired
    private FileStorageService fileStorageService;

    @MockBean
    private AssignmentService assignmentService;

    // Usuarios y asignaciones de prueba alineados con USUARIO_ASIGNACIONES en Oracle DB
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

    private static final long USER_RESIDENTE_1 = 4L; // Carlos Martinez (Apto 101)
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L; // Ana Gomez (Apto 102 / Otra Unidad)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    // Entidades multi-tenant
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;
    private static final long UNIT_ORG2_ID = 9992L;

    private long personaResidente1Id = 4L;
    private long personaResidente2Id = 5L;

    private Long plantillaActivaOrg1Id;
    private Long testContractId;

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Limpiar contratos previos en unidades de prueba
        try {
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992)) OR ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR CLEANING CONTRATOS/CUOTAS: " + e.getMessage());
            e.printStackTrace();
        }

        // Asegurar Organización 9992, Propiedad 9992 y Unidad 9992 para pruebas IDOR cruzadas
        try {
            ensureOrganizacion(9992L, "Org 9992 IDOR", "900009992-9", "org9992@test.com");
            ensurePropiedad(9992L, 9992L, "Propiedad 9992 Test");
            ensureUnidad(9992L, 9992L, "Apto 9992");

            // Asegurar Propiedad 1 y Unidades 1 y 2
            ensurePropiedad(1L, 1L, "Edificio Residencial SAED");
            ensureUnidad(1L, 1L, "Apto 101");
            ensureUnidad(2L, 1L, "Apto 102");

            // Asegurar Personas y Usuarios de prueba
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

            // SuperAdmin en ADMINISTRADORES_SAED
            Integer countAdmin = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ADMINISTRADORES_SAED WHERE ID_USUARIO = 1", Integer.class);
            if (countAdmin == null || countAdmin == 0) {
                jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, ESTADO, NIVEL_ACCESO) VALUES (1, 'ACTIVO', 'TOTAL')");
            } else {
                jdbcTemplate.update("UPDATE ADMINISTRADORES_SAED SET ESTADO = 'ACTIVO' WHERE ID_USUARIO = 1");
            }

            // Asignaciones
            seedAsignaciones();
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN SEEDING SETUP: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Asegurar membresía activa con plan para Org 1 y Org 9992 para validar StorageQuotaService
        try {
            Long defaultPlanId = jdbcTemplate.queryForObject(
                    "SELECT ID_PLAN FROM PLANES WHERE (LIMITE_USUARIOS IS NULL OR LIMITE_USUARIOS = 0 OR LIMITE_USUARIOS >= 50) AND ROWNUM = 1",
                    Long.class
            );
            if (defaultPlanId != null) {
                Integer count1 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = 1", Integer.class);
                if (count1 == null || count1 == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                            "VALUES (1, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')",
                            defaultPlanId);
                } else {
                    jdbcTemplate.update(
                            "UPDATE MEMBRESIAS SET ID_PLAN = ?, ESTADO = 'ACTIVA', FECHA_FIN = TRUNC(SYSDATE) + 365 WHERE ID_ORGANIZACION = 1",
                            defaultPlanId);
                }

                Integer count2 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = 9992", Integer.class);
                if (count2 == null || count2 == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                            "VALUES (9992, ?, 'ACTIVA', TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')",
                            defaultPlanId);
                } else {
                    jdbcTemplate.update(
                            "UPDATE MEMBRESIAS SET ID_PLAN = ?, ESTADO = 'ACTIVA', FECHA_FIN = TRUNC(SYSDATE) + 365 WHERE ID_ORGANIZACION = 9992",
                            defaultPlanId);
                }
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR SEEDING MEMBERSHIP: " + e.getMessage());
            e.printStackTrace();
        }

        // Asegurar Plantilla contractual de prueba en Org 1 con placeholders canónicos y legacy
        try {
            jdbcTemplate.update("DELETE FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_PIPELINE_TEST'");
            jdbcTemplate.update(
                    "INSERT INTO PLANTILLAS_CONTRATOS (ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) " +
                    "VALUES (1, 'TPL_PIPELINE_TEST', 'Plantilla Pipeline Doc', 'INICIAL', " +
                    "'<html><head><style>body { font-family: sans-serif; }</style></head><body>" +
                    "<h1>CONTRATO DE ARRENDAMIENTO RESIDENCIAL</h1>" +
                    "<p>Propiedad: ${propiedad.nombre}</p>" +
                    "<p>Arrendatario: ${residente.nombreCompleto}</p>" +
                    "<p>Documento: ${residente.numeroDocumento}</p>" +
                    "<p>Unidad: ${unidad.identificador}</p>" +
                    "<p>Canon Mensual: ${contrato.valorMensual}</p>" +
                    "<p>Fecha de Inicio: {{contrato.fechaInicio}}</p>" +
                    "<p>Clausula de Integridad Documental.</p>" +
                    "</body></html>', 'ACTIVA', 1, 1)"
            );

            plantillaActivaOrg1Id = jdbcTemplate.queryForObject(
                    "SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE CODIGO = 'TPL_PIPELINE_TEST'", Long.class);
            System.out.println("PLANTILLA ACTIVA ORG 1 SEEDED WITH ID: " + plantillaActivaOrg1Id);
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR SEEDING PLANTILLAS_CONTRATOS: " + e.getMessage());
            e.printStackTrace();
        }

        setupMockAssignments();

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
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992));
                    DELETE FROM MULTAS WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992)) OR ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (1, 2, 9992);
                    DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (1, 2, 9992);
                END;
            """);

            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central");
        UnitDTO unit1 = new UnitDTO(UNIT_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIT_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea");
        UnitDTO unitOrg2 = new UnitDTO(UNIT_ORG2_ID, "Apto 9992");

        // SuperAdmin
        AssignmentResponseDTO superAdminAssign = new AssignmentResponseDTO();
        superAdminAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        superAdminAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));

        // Admin Org 1
        AssignmentResponseDTO adminOrg1Assign = new AssignmentResponseDTO();
        adminOrg1Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        adminOrg1Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg1Assign.setOrganizacion(org1);

        // Admin Prop 1
        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

        // Admin Org 2
        AssignmentResponseDTO adminOrg2Assign = new AssignmentResponseDTO();
        adminOrg2Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_2);
        adminOrg2Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        adminOrg2Assign.setOrganizacion(org2);

        // Admin Prop 2
        AssignmentResponseDTO adminProp2Assign = new AssignmentResponseDTO();
        adminProp2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        adminProp2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp2Assign.setOrganizacion(org2);
        adminProp2Assign.setPropiedad(prop2);

        // Residente 1 (Carlos Martinez -> Unit 1)
        AssignmentResponseDTO res1Assign = new AssignmentResponseDTO();
        res1Assign.setIdAsignacion(ASSIGN_RESIDENTE_1);
        res1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res1Assign.setOrganizacion(org1);
        res1Assign.setPropiedad(prop1);
        res1Assign.setUnidad(unit1);

        // Residente 2 (Ana Gomez -> Unit 2)
        AssignmentResponseDTO res2Assign = new AssignmentResponseDTO();
        res2Assign.setIdAsignacion(ASSIGN_RESIDENTE_2);
        res2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res2Assign.setOrganizacion(org1);
        res2Assign.setPropiedad(prop1);
        res2Assign.setUnidad(unit2);

        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN))
                .thenReturn(Optional.of(superAdminAssign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1))
                .thenReturn(Optional.of(adminOrg1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1))
                .thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_2, USER_ADMIN_ORG_2))
                .thenReturn(Optional.of(adminOrg2Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2))
                .thenReturn(Optional.of(adminProp2Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1))
                .thenReturn(Optional.of(res1Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2))
                .thenReturn(Optional.of(res2Assign));
    }

    private Long createTestContractViaApi() throws Exception {
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        ContratoRequestDTO requestDTO = new ContratoRequestDTO(
                UNIT_1_ID,
                personaResidente1Id,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                "ARRENDAMIENTO",
                new BigDecimal("1850000.00"),
                plantillaActivaOrg1Id
        );

        MvcResult result = mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andReturn();

        if (result.getResponse().getStatus() != 201) {
            System.err.println("CREATE CONTRACT FAILED [" + result.getResponse().getStatus() + "]: " + result.getResponse().getContentAsString());
        }
        assertEquals(201, result.getResponse().getStatus(), "Status expected 201 but got " + result.getResponse().getStatus() + ": " + result.getResponse().getContentAsString());

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(responseBody, Map.class);
        assertNotNull(map.get("id"), "El ID del contrato retornado no debe ser nulo");
        return ((Number) map.get("id")).longValue();
    }

    @Test
    @DisplayName("GAP-F5-02.1: El pipeline compila variables desde la plantilla DB y congela el HTML")
    public void testPipeline_UsesDatabaseTemplate() throws Exception {
        Long contratoId = createTestContractViaApi();
        assertNotNull(contratoId);

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        MvcResult result = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();

        ContratoDetalleDTO detalle = objectMapper.readValue(result.getResponse().getContentAsString(), ContratoDetalleDTO.class);
        assertNotNull(detalle);
        assertEquals(contratoId.longValue(), detalle.getIdContrato().longValue());

        // Validar que el HTML congelado contiene los valores de las variables resueltas
        String html = detalle.getHtmlCongelado();
        assertNotNull(html, "El HTML congelado no debe ser nulo");
        assertTrue(html.contains("CONTRATO DE ARRENDAMIENTO RESIDENCIAL"));
        assertTrue(html.contains("Carlos Martinez"), "Debe contener el nombre del arrendatario");
        assertTrue(html.contains("1000000004"), "Debe contener el documento del arrendatario");
        assertTrue(html.contains("Apto 101"), "Debe contener el identificador de la unidad");
        assertTrue(html.contains("1.850.000") || html.contains("1850000"), "Debe contener el canon formateado");

        // Validar metadata documental
        assertNotNull(detalle.getDocumentoUrl(), "DOCUMENTO_URL debe estar persistido");
        assertTrue(detalle.getDocumentoUrl().contains("contratos/"), "Debe guardarse en el subdirectorio de contratos");
        assertTrue(detalle.getDocumentoUrl().endsWith(".pdf"), "Debe tener extensión .pdf");
        assertNotNull(detalle.getDocumentoHash(), "DOCUMENTO_HASH debe estar persistido");
        assertEquals(64, detalle.getDocumentoHash().length(), "DOCUMENTO_HASH debe ser un hash SHA-256 de 64 caracteres");
        assertNotNull(detalle.getDocumentoTamanoBytes(), "DOCUMENTO_TAMANO_BYTES debe ser persistido");
        assertTrue(detalle.getDocumentoTamanoBytes() > 0, "El tamaño del PDF debe ser mayor a cero");
        assertNotNull(detalle.getDocumentoFechaGeneracion(), "DOCUMENTO_FECHA_GENERACION debe ser persistida");
    }

    @Test
    @DisplayName("GAP-F5-02.2: Inmutabilidad histórica del snapshot contractual frente a mutación de plantilla DB")
    public void testPipeline_SnapshotImmutability() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Obtener detalle antes de mutar la plantilla
        MvcResult resultBefore = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();
        ContratoDetalleDTO detalleBefore = objectMapper.readValue(resultBefore.getResponse().getContentAsString(), ContratoDetalleDTO.class);
        String originalHtml = detalleBefore.getHtmlCongelado();
        String originalHash = detalleBefore.getDocumentoHash();

        // Mutar la plantilla original en la base de datos
        jdbcTemplate.update(
                "UPDATE PLANTILLAS_CONTRATOS SET CONTENIDO_HTML = '<html><body><h1>PLANTILLA MUTADA V2</h1></body></html>' WHERE ID_PLANTILLA = ?",
                plantillaActivaOrg1Id
        );

        // Volver a consultar el contrato: el HTML_CONGELADO y el HASH deben ser idénticos al original
        MvcResult resultAfter = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();
        ContratoDetalleDTO detalleAfter = objectMapper.readValue(resultAfter.getResponse().getContentAsString(), ContratoDetalleDTO.class);

        assertEquals(originalHtml, detalleAfter.getHtmlCongelado(), "El HTML congelado debe ser inmutable");
        assertFalse(detalleAfter.getHtmlCongelado().contains("PLANTILLA MUTADA V2"), "No debe reflejar mutaciones de plantilla posteriores");
        assertEquals(originalHash, detalleAfter.getDocumentoHash(), "El hash documental debe ser inmutable");
    }

    @Test
    @DisplayName("GAP-F5-02.3: El PDF almacenado físicamente es válido (Magic Header %PDF- y tamaño consistente)")
    public void testPipeline_PdfValidAndStored() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        MvcResult result = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();
        ContratoDetalleDTO detalle = objectMapper.readValue(result.getResponse().getContentAsString(), ContratoDetalleDTO.class);

        // Cargar recurso físico mediante FileStorageService
        Resource resource = fileStorageService.loadAsResource(detalle.getDocumentoUrl());
        assertTrue(resource.exists(), "El archivo físico PDF debe existir en el almacenamiento");
        assertTrue(resource.isReadable(), "El archivo físico PDF debe ser legible");

        byte[] fileBytes = resource.getInputStream().readAllBytes();
        assertTrue(fileBytes.length > 0, "Los bytes del PDF deben ser mayores a cero");
        assertEquals(detalle.getDocumentoTamanoBytes().longValue(), fileBytes.length, "El tamaño en disco debe coincidir exactamente con DOCUMENTO_TAMANO_BYTES");

        // Validar magic number de PDF (%PDF-)
        String header = new String(fileBytes, 0, Math.min(fileBytes.length, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "El archivo debe iniciar con la cabecera estándar de PDF '%PDF-'");
    }

    @Test
    @DisplayName("GAP-F5-02.4: Integridad criptográfica SHA-256 entre bytes físicos y hash persistido")
    public void testPipeline_Sha256Integrity() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        MvcResult result = mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andReturn();
        ContratoDetalleDTO detalle = objectMapper.readValue(result.getResponse().getContentAsString(), ContratoDetalleDTO.class);

        Resource resource = fileStorageService.loadAsResource(detalle.getDocumentoUrl());
        byte[] fileBytes = resource.getInputStream().readAllBytes();

        // Calcular SHA-256 localmente de los bytes físicos
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(fileBytes);
        String calculatedSha256 = HexFormat.of().formatHex(digest);

        assertEquals(calculatedSha256, detalle.getDocumentoHash().toLowerCase(),
                "El hash SHA-256 de los bytes físicos debe coincidir exactamente con el hash persistido en la base de datos");

        // Validar que el endpoint de descarga también expone la cabecera X-Content-Sha256 con el mismo valor
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Sha256", calculatedSha256));
    }

    @Test
    @DisplayName("GAP-F5-02.5: Descarga autorizada para ADMIN_PROPIEDAD, ADMIN_ORGANIZACION y RESIDENTE titular (200 OK)")
    public void testPipeline_AuthorizedDownload() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        String tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // 1. ADMIN_PROPIEDAD descarga contrato de su propiedad
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"contrato_")));

        // 2. ADMIN_ORGANIZACION descarga contrato de su organización
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenAdminOrg1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 3. RESIDENTE titular descarga su propio contrato
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("GAP-F5-02.6: Control de acceso anti-IDOR estricto (403 Forbidden para accesos no autorizados)")
    public void testPipeline_AntiIdorEnforcement() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
        String tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
        String tokenAdminOrg2 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_2);

        // 1. Residente 2 (otra unidad) intenta descargar contrato de Unidad 1 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isForbidden());

        // 2. Residente 2 intenta ver detalle del contrato de Unidad 1 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/contratos/" + contratoId)
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2)))
                .andExpect(status().isForbidden());

        // 3. Admin Propiedad 2 (otra propiedad) intenta descargar contrato -> 403 Forbidden
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isForbidden());

        // 4. Admin Organización 2 (otra organización) intenta descargar contrato -> 403 Forbidden
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf")
                .header("Authorization", "Bearer " + tokenAdminOrg2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_ORG_2)))
                .andExpect(status().isForbidden());

        // 5. Descarga anónima / sin token -> 401 Unauthorized o 403 Forbidden
        mockMvc.perform(get("/api/v1/contratos/" + contratoId + "/pdf"))
                .andExpect(status().isUnauthorized());

        // 6. Contrato inexistente -> 404 Not Found
        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
        mockMvc.perform(get("/api/v1/contratos/999999/pdf")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GAP-F5-02.7: Almacenamiento seguro y limpieza de archivos")
    public void testPipeline_StorageQuotaAndOrphanCleanup() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            // Validar almacenamiento y eliminación limpia a través de FileStorageService
            byte[] dummyPdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            FileStorageService.StoredFile stored = fileStorageService.storeBytes(dummyPdf, "test_orphan.pdf", "application/pdf", "contratos", ORG_1_ID);

            assertNotNull(stored);
            Resource res = fileStorageService.loadAsResource(stored.relativePath());
            assertTrue(res.exists(), "El archivo debe crearse en el almacenamiento");

            // Limpieza del archivo
            fileStorageService.delete(stored.relativePath());
            assertFalse(res.exists(), "El archivo debe eliminarse limpiamente tras invocar delete");

            // Validar rechazo de archivos que superan el límite de tamaño
            byte[] giantBytes = new byte[11 * 1024 * 1024]; // 11 MB > 10 MB límite
            assertThrows(IllegalArgumentException.class, () -> {
                fileStorageService.storeBytes(giantBytes, "giant.pdf", "application/pdf", "contratos", ORG_1_ID);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("GAP-F5-02.8: Alineación con restricción de estados de Oracle (CK_CONTRATOS_ESTADO)")
    public void testPipeline_StateAlignment() throws Exception {
        Long contratoId = createTestContractViaApi();

        String tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        // Establecer estado inicial PENDIENTE_FIRMA para probar activación formal
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        jdbcTemplate.update("UPDATE CONTRATOS SET ESTADO = 'PENDIENTE_FIRMA' WHERE ID_CONTRATO = ?", contratoId);

        // 1. Activar formalmente el contrato
        mockMvc.perform(post("/api/v1/contratos/" + contratoId + "/activar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar estado ACTIVO en DB
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        String estadoDb = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM CONTRATOS WHERE ID_CONTRATO = ?", String.class, contratoId);
        assertEquals("ACTIVO", estadoDb);

        // 2. Cancelar formalmente el contrato
        mockMvc.perform(post("/api/v1/contratos/" + contratoId + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        estadoDb = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM CONTRATOS WHERE ID_CONTRATO = ?", String.class, contratoId);
        assertEquals("CANCELADO", estadoDb);

        // 3. Intentar transición a estados inválidos rechazados por la restricción canónica
        assertThrows(IllegalArgumentException.class, () -> {
            finanzasService.actualizarEstadoContrato(contratoId, "TERMINADO");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            finanzasService.actualizarEstadoContrato(contratoId, "INACTIVO");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            finanzasService.actualizarEstadoContrato(contratoId, "SUSPENDIDO");
        });
    }

    private void ensureOrganizacion(long id, String nombre, String nit, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (?, ?, ?, ?)",
                    id, nombre, nit, email);
        } else {
            jdbcTemplate.update(
                    "UPDATE ORGANIZACIONES SET NOMBRE = ?, IDENTIFICACION_FISCAL = ?, EMAIL_CONTACTO = ? WHERE ID_ORGANIZACION = ?",
                    nombre, nit, email, id);
        }
    }

    private void ensurePropiedad(long id, long orgId, String nombre) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'Carrera 99', 'Medellin', 'Colombia', 'MIXTA', 'ACTIVA')",
                    id, orgId, nombre);
        } else {
            jdbcTemplate.update(
                    "UPDATE PROPIEDADES SET ID_ORGANIZACION = ?, NOMBRE = ?, ESTADO = 'ACTIVA' WHERE ID_PROPIEDAD = ?",
                    orgId, nombre, id);
        }
    }

    private void ensureUnidad(long id, long propId, String num) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    id, propId, num);
        } else {
            jdbcTemplate.update(
                    "UPDATE UNIDADES SET ID_PROPIEDAD = ?, IDENTIFICADOR = ?, ESTADO = 'ACTIVA' WHERE ID_UNIDAD = ?",
                    propId, num, id);
        }
    }

    private void ensurePersona(long id, String doc, String nombre, String apellido, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, 'NATURAL', ?, ?, ?)",
                    id, doc, nombre, apellido, email);
        } else {
            jdbcTemplate.update(
                    "UPDATE PERSONAS SET NUMERO_DOCUMENTO = ?, PRIMER_NOMBRE = ?, PRIMER_APELLIDO = ?, EMAIL = ? WHERE ID_PERSONA = ?",
                    doc, nombre, apellido, email, id);
        }
    }

    private void ensureUsuario(long id, long personaId, String username, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                    "VALUES (?, ?, ?, ?, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')",
                    id, personaId, username, email);
        } else {
            jdbcTemplate.update(
                    "UPDATE USUARIOS SET ID_PERSONA = ?, NOMBRE_USUARIO = ?, EMAIL = ?, ESTADO = 'ACTIVO' WHERE ID_USUARIO = ?",
                    personaId, username, email, id);
        }
    }

    private void seedAsignaciones() {
        jdbcTemplate.execute("""
            DECLARE
                v_rol_super NUMBER;
                v_rol_org   NUMBER;
                v_rol_prop  NUMBER;
                v_rol_res   NUMBER;
            BEGIN
                SELECT ID_ROL INTO v_rol_super FROM ROLES WHERE CODIGO = 'SUPERADMIN';
                SELECT ID_ROL INTO v_rol_org   FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION';
                SELECT ID_ROL INTO v_rol_prop  FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD';
                SELECT ID_ROL INTO v_rol_res   FROM ROLES WHERE CODIGO = 'RESIDENTE';

                DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (101, 102, 301, 981, 991, 104, 105)
                    OR (ID_USUARIO = 1 AND ID_ROL = v_rol_super)
                    OR (ID_USUARIO = 2 AND ID_ROL = v_rol_prop AND NVL(ID_ORGANIZACION, -1) = 1 AND NVL(ID_PROPIEDAD, -1) = 1)
                    OR (ID_USUARIO = 8 AND ID_ROL = v_rol_org AND NVL(ID_ORGANIZACION, -1) = 1)
                    OR (ID_USUARIO = 98 AND ID_ROL = v_rol_org AND NVL(ID_ORGANIZACION, -1) = 9992)
                    OR (ID_USUARIO = 99 AND ID_ROL = v_rol_prop AND NVL(ID_ORGANIZACION, -1) = 9992 AND NVL(ID_PROPIEDAD, -1) = 9992)
                    OR (ID_USUARIO = 4 AND ID_ROL = v_rol_res AND NVL(ID_UNIDAD, -1) = 1)
                    OR (ID_USUARIO = 5 AND ID_ROL = v_rol_res AND NVL(ID_UNIDAD, -1) = 2);

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (101, 1, v_rol_super, NULL, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (102, 2, v_rol_prop, 1, 1, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (301, 8, v_rol_org, 1, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (981, 98, v_rol_org, 9992, NULL, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO)
                VALUES (991, 99, v_rol_prop, 9992, 9992, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (104, 4, v_rol_res, 1, 1, 1, 'ACTIVA', TRUNC(SYSDATE));

                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (105, 5, v_rol_res, 1, 1, 2, 'ACTIVA', TRUNC(SYSDATE));
            END;
        """);
    }
}
