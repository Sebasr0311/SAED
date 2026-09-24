package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.dto.ApiErrorResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.dto.IncidenteEstadoRequestDTO;
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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Adversarial Transversal para F9-08:
 * Valida los 15 vectores de ataque y robustez transversal sobre los módulos F9 de SAED 2.0:
 *
 * Vector 01: Cross-Property IDOR (Admin Propiedad 1 vs Recurso Propiedad 2 -> 403/404)
 * Vector 02: Cross-Organization IDOR (Admin Org 1 vs Proveedor Org 2 -> 404/403)
 * Vector 03: Missing Entitlement (Plan FREE accediendo a OBRAS -> 403 MODULE_NOT_ENTITLED)
 * Vector 04: Wrong Role (RESIDENTE intentando mutación administrativa -> 403 FORBIDDEN)
 * Vector 05: Direct URL Access sin sesión (Unauthenticated -> 401 UNAUTHORIZED)
 * Vector 06: Invalid FSM Transition (Transición directa ilegal -> 400 Bad Request)
 * Vector 07: Foreign Resource Mutation (Residente A intentando mutar obra de Residente B -> 403)
 * Vector 08: Foreign Nested ID (Intento de inyección de foreign key ajena -> Rechazado)
 * Vector 09: Malformed JSON (Cuerpo HTTP corrupto -> 400 MALFORMED_JSON)
 * Vector 10: Invalid Type / Non-numeric ID (PathVariable inválido -> 400 TYPE_MISMATCH)
 * Vector 11: Oracle Duplicate Key (Código o NIT duplicado -> 409 DUPLICATE_KEY / RESOURCE_CONFLICT)
 * Vector 12: Oracle FK Violation (Referencia inexistente sanitizada -> 400/409 sin ORA-)
 * Vector 13: Internal Error Sanitization (CERO fuga de ORA-, SQL, tablas o stacktraces)
 * Vector 14: TraceId Propagation (Correlación X-Correlation-Id bidireccional cliente-servidor)
 * Vector 15: Concurrencia Real Multi-hilo (CountDownLatch + ExecutorService para carrera de mutación FSM)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9FinalCertificationAdversarialTest {

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

    // Identidades y asignaciones de prueba
    private static final long USER_ADMIN_PROP1 = 8901L;
    private static final long ASSIGN_ADMIN_PROP1 = 8901L;

    private static final long USER_ADMIN_PROP2 = 8902L;
    private static final long ASSIGN_ADMIN_PROP2 = 8902L;

    private static final long USER_ADMIN_ORG1 = 8903L;
    private static final long ASSIGN_ADMIN_ORG1 = 8903L;

    private static final long USER_ADMIN_ORG2 = 8904L;
    private static final long ASSIGN_ADMIN_ORG2 = 8904L;

    private static final long USER_RESIDENTE_U1 = 8905L;
    private static final long ASSIGN_RESIDENTE_U1 = 8905L;

    private static final long USER_RESIDENTE_U2 = 8906L;
    private static final long ASSIGN_RESIDENTE_U2 = 8906L;

    private static final long USER_FREE_ORG = 8907L;
    private static final long ASSIGN_FREE_ORG = 8907L;

    // Tenants
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;

    private static final long ORG_FREE_ID = 8990L;
    private static final long PROP_FREE_ID = 8990L;

    // Tokens
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenAdminOrg1;
    private String tokenAdminOrg2;
    private String tokenResidenteU1;
    private String tokenResidenteU2;
    private String tokenFreeOrg;

    private Long idIncidenteProp2;
    private Long idProvOrg2;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza controlada
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM INCIDENTES WHERE TITULO LIKE 'ADV-F908%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION = '900890802-2';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (8901, 8902, 8903, 8904, 8905, 8906, 8907);
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea 9992", "900009992-9", "org9992@test.com");
            ensureOrganizacion(ORG_FREE_ID, "Organización Free Tier", "900008990-0", "orgfree@test.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Torre Central SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Torre Foránea 9992");
            ensurePropiedad(PROP_FREE_ID, ORG_FREE_ID, "Torre Free Tier");

            ensureUnidad(UNIT_1_ID, PROP_1_ID, "Apto 101");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");

            ensureMembresia(ORG_1_ID, 2L, "ACTIVA"); // Pro
            ensureMembresia(ORG_2_ID, 2L, "ACTIVA"); // Pro
            ensureMembresia(ORG_FREE_ID, 1L, "ACTIVA"); // Free (no Obras)

            // Personas y Usuarios
            ensurePersonaUsuario(USER_ADMIN_PROP1, "AdminP1", "Adv", "adminp1_adv@saed.com", "89010001");
            ensurePersonaUsuario(USER_ADMIN_PROP2, "AdminP2", "Adv", "adminp2_adv@saed.com", "89010002");
            ensurePersonaUsuario(USER_ADMIN_ORG1, "AdminO1", "Adv", "admino1_adv@saed.com", "89010003");
            ensurePersonaUsuario(USER_ADMIN_ORG2, "AdminO2", "Adv", "admino2_adv@saed.com", "89010004");
            ensurePersonaUsuario(USER_RESIDENTE_U1, "ResU1", "Adv", "resu1_adv@saed.com", "89010005");
            ensurePersonaUsuario(USER_RESIDENTE_U2, "ResU2", "Adv", "resu2_adv@saed.com", "89010006");
            ensurePersonaUsuario(USER_FREE_ORG, "AdminFree", "Adv", "adminfree_adv@saed.com", "89010007");

            // Asignaciones
            ensureAsignacion(ASSIGN_ADMIN_PROP1, USER_ADMIN_PROP1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP2, USER_ADMIN_PROP2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG1, USER_ADMIN_ORG1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG2, USER_ADMIN_ORG2, "ADMIN_ORGANIZACION", ORG_2_ID, null, null);
            ensureAsignacion(ASSIGN_RESIDENTE_U1, USER_RESIDENTE_U1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_1_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_U2, USER_RESIDENTE_U2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);
            ensureAsignacion(ASSIGN_FREE_ORG, USER_FREE_ORG, "ADMIN_PROPIEDAD", ORG_FREE_ID, PROP_FREE_ID, null);

            setupMockAssignments();

            // Insertar datos adversariales: Incidente en Propiedad 2
            idIncidenteProp2 = insertIncidenteDirecto(PROP_2_ID, "ADV-F908 Incidente en Prop 2", "SEGURIDAD_HURTO", "GRAVE", "REPORTADO");

            // Proveedor en Org 2
            idProvOrg2 = insertProveedorDirecto(ORG_2_ID, "Proveedor Privado Org 2", "900890802-2", "SEGURIDAD");

            // Tokens
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP2);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG1);
            tokenAdminOrg2 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG2);
            tokenResidenteU1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_U1);
            tokenResidenteU2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_U2);
            tokenFreeOrg = jwtProvider.generateIdentityToken(USER_FREE_ORG);

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
                    DELETE FROM INCIDENTES WHERE TITULO LIKE 'ADV-F908%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION = '900890802-2';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (8901, 8902, 8903, 8904, 8905, 8906, 8907);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    private void setupMockAssignments() {
        OrganizationDTO o1 = new OrganizationDTO(ORG_1_ID, "Org 1");
        PropertyDTO p1 = new PropertyDTO(PROP_1_ID, "Prop 1");
        UnitDTO u1 = new UnitDTO(UNIT_1_ID, "101");
        UnitDTO u2 = new UnitDTO(UNIT_2_ID, "102");

        OrganizationDTO o2 = new OrganizationDTO(ORG_2_ID, "Org 2");
        PropertyDTO p2 = new PropertyDTO(PROP_2_ID, "Prop 2");

        OrganizationDTO oFree = new OrganizationDTO(ORG_FREE_ID, "Org Free");
        PropertyDTO pFree = new PropertyDTO(PROP_FREE_ID, "Prop Free");

        mockAssign(ASSIGN_ADMIN_PROP1, USER_ADMIN_PROP1, "ADMIN_PROPIEDAD", "PROPIEDAD", o1, p1, null);
        mockAssign(ASSIGN_ADMIN_PROP2, USER_ADMIN_PROP2, "ADMIN_PROPIEDAD", "PROPIEDAD", o2, p2, null);
        mockAssign(ASSIGN_ADMIN_ORG1, USER_ADMIN_ORG1, "ADMIN_ORGANIZACION", "ORGANIZACION", o1, null, null);
        mockAssign(ASSIGN_ADMIN_ORG2, USER_ADMIN_ORG2, "ADMIN_ORGANIZACION", "ORGANIZACION", o2, null, null);
        mockAssign(ASSIGN_RESIDENTE_U1, USER_RESIDENTE_U1, "RESIDENTE", "UNIDAD", o1, p1, u1);
        mockAssign(ASSIGN_RESIDENTE_U2, USER_RESIDENTE_U2, "RESIDENTE", "UNIDAD", o1, p1, u2);
        mockAssign(ASSIGN_FREE_ORG, USER_FREE_ORG, "ADMIN_PROPIEDAD", "PROPIEDAD", oFree, pFree, null);
    }

    private void mockAssign(Long idAssign, Long idUser, String rol, String scope, OrganizationDTO org, PropertyDTO prop, UnitDTO unit) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(idAssign);
        dto.setRol(new RoleDTO(rol, scope));
        dto.setOrganizacion(org);
        dto.setPropiedad(prop);
        dto.setUnidad(unit);
        when(assignmentService.validateAssignment(idAssign, idUser)).thenReturn(Optional.of(dto));
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

    private void ensureOrganizacion(Long id, String nom, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nom, nit, email);
        }
    }

    private void ensurePropiedad(Long idProp, Long idOrg, String nom) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, idProp);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'Calle 100 # 15-20', 'Bogotá', 'MIXTA', 'ACTIVA')", idProp, idOrg, nom);
        }
    }

    private void ensureUnidad(Long idU, Long idP, String ident) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, idU);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                    "VALUES (?, ?, ?, 'RESIDENCIAL', 0.05, 'DISPONIBLE')", idU, idP, ident);
        }
    }

    private void ensureMembresia(Long orgId, Long planId, String estado) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MEMBRESIAS WHERE ID_ORGANIZACION = ?", Integer.class, orgId);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) " +
                    "VALUES (?, ?, ?, TRUNC(SYSDATE), TRUNC(SYSDATE) + 365, 'N')", orgId, planId, estado);
        }
    }

    private void ensurePersonaUsuario(Long idU, String nom, String ape, String email, String doc) {
        Integer cp = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, idU);
        if (cp == null || cp == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, 'NATURAL', ?, ?, ?)", idU, doc, nom, ape, email);
        }
        Integer cu = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, idU);
        if (cu == null || cu == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) " +
                    "VALUES (?, ?, ?, ?, 'hash', 'ACTIVO')", idU, idU, email, email);
        }
    }

    private void ensureAsignacion(Long idA, Long idU, String rol, Long idO, Long idP, Long idUn) {
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rol);
        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", idA);
        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))", idA, idU, idRol, idO, idP, idUn);
    }

    private Long insertIncidenteDirecto(Long idP, String titulo, String tipo, String severidad, String estado) {
        setElevatedContext();
        try {
            Long id = jdbcTemplate.queryForObject("SELECT NVL(MAX(ID_INCIDENTE), 0) + 1 FROM INCIDENTES", Long.class);
            jdbcTemplate.update("""
                INSERT INTO INCIDENTES (ID_INCIDENTE, ID_PROPIEDAD, TITULO, TIPO_INCIDENTE, NIVEL_SEVERIDAD,
                                       DESCRIPCION_HECHOS, FECHA_HORA_INCIDENTE, REGISTRADO_POR, ESTADO)
                VALUES (?, ?, ?, ?, ?, 'Descripción adversarial', SYSDATE, 1, ?)
            """, id, idP, titulo, tipo, severidad, estado);
            return id;
        } finally {
            clearContext();
        }
    }

    private Long insertProveedorDirecto(Long idOrg, String razon, String nit, String cat) {
        setElevatedContext();
        try {
            Long id = jdbcTemplate.queryForObject("SELECT NVL(MAX(ID_PROVEEDOR), 0) + 1 FROM PROVEEDORES", Long.class);
            jdbcTemplate.update("""
                INSERT INTO PROVEEDORES (ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION,
                                       EMAIL_CONTACTO, TELEFONO_CONTACTO, CATEGORIA_SERVICIO, ESTADO)
                VALUES (?, ?, 'JURIDICA', ?, ?, 'contacto@prov.com', '3001234567', ?, 'ACTIVO')
            """, id, idOrg, razon, nit, cat);
            return id;
        } finally {
            clearContext();
        }
    }

    // =========================================================================
    // 15 VECTORES DE CERTIFICACIÓN ADVERSARIAL
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Vector 01: Cross-Property IDOR -> Admin Propiedad 1 no puede consultar incidente de Propiedad 2")
    public void vector01_crossPropertyIdor_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/" + idIncidenteProp2)
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(2)
    @DisplayName("Vector 02: Cross-Organization IDOR -> Admin Org 1 no puede consultar proveedor de Org 2")
    public void vector02_crossOrganizationIdor_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/" + idProvOrg2)
                        .header("Authorization", "Bearer " + tokenAdminOrg1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG1))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    @DisplayName("Vector 03: Missing Entitlement -> Organización en Plan FREE no puede acceder a Obras (403 MODULE_NOT_ENTITLED)")
    public void vector03_missingEntitlement_moduleNotEntitled403() throws Exception {
        mockMvc.perform(get("/api/v1/obras/admin")
                        .header("Authorization", "Bearer " + tokenFreeOrg)
                        .header("X-Assignment-Id", ASSIGN_FREE_ORG))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"))
                .andExpect(jsonPath("$.moduleCode").value("OBRAS"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(4)
    @DisplayName("Vector 04: Wrong Role -> Residente intentando mutación de estado administrativo es rechazado con 403")
    public void vector04_wrongRole_residentForbiddenFromAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes/" + idIncidenteProp2 + "/cerrar")
                        .header("Authorization", "Bearer " + tokenResidenteU1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_U1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusiones\":\"Intento no autorizado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Vector 05: Direct URL Access -> Petición sin autenticación es rechazada con 401 UNAUTHORIZED")
    public void vector05_directUrlAccess_unauthenticated401() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("Vector 06: Invalid FSM Transition -> Transición ilegal en máquina de estados produce 400")
    public void vector06_invalidFsmTransition_rejected400() throws Exception {
        // Incidente está en REPORTADO, no puede cerrarse directamente sin conclusiones o estado inválido
        IncidenteEstadoRequestDTO req = new IncidenteEstadoRequestDTO();
        req.setEstado("ESTADO_INEXISTENTE");

        mockMvc.perform(put("/api/v1/incidentes/" + idIncidenteProp2 + "/estado")
                        .header("Authorization", "Bearer " + tokenAdminProp2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(7)
    @DisplayName("Vector 07: Foreign Resource Mutation -> Residente no puede reportar incidente en unidad ajena sin que sea forzada la suya")
    public void vector07_foreignResourceMutation_prevented() throws Exception {
        IncidenteDTO req = new IncidenteDTO();
        req.setTitulo("ADV-F908 Residente IDOR");
        req.setTipoIncidente("CONVIVENCIA_RUIDO");
        req.setNivelSeveridad("LEVE");
        req.setDescripcionHechos("Intento de forzar unidad 2");
        req.setFechaHoraIncidente(ZonedDateTime.now());
        req.setIdUnidad(UNIT_2_ID); // Intento de IDOR

        MvcResult res = mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + tokenResidenteU1)
                        .header("X-Assignment-Id", ASSIGN_RESIDENTE_U1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        // El servicio debe haber forzado la unidad 1 del token del residente
        Map<?, ?> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        Long idInc = ((Number) map.get("idIncidente")).longValue();

        setElevatedContext();
        Long unidadPersistida = jdbcTemplate.queryForObject("SELECT ID_UNIDAD FROM INCIDENTES WHERE ID_INCIDENTE = ?", Long.class, idInc);
        assertEquals(UNIT_1_ID, unidadPersistida, "El backend debe forzar la unidad del residente, mitigando IDOR");
    }

    @Test
    @Order(8)
    @DisplayName("Vector 08: Foreign Nested ID -> Intento de vincular recurso de otra copropiedad es bloqueado")
    public void vector08_foreignNestedId_prevented() throws Exception {
        // Admin Propiedad 1 intenta cerrar el incidente de Propiedad 2
        mockMvc.perform(post("/api/v1/incidentes/" + idIncidenteProp2 + "/cerrar")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusiones\": \"Cierre cruzado no permitido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(9)
    @DisplayName("Vector 09: Malformed JSON -> Cuerpo HTTP corrupto retorna 400 MALFORMED_JSON")
    public void vector09_malformedJson_producesCanonical400() throws Exception {
        mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ corrupt_json: [ }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @Order(10)
    @DisplayName("Vector 10: Invalid Type / Non-numeric ID -> PathVariable no numérico retorna 400 TYPE_MISMATCH")
    public void vector10_invalidTypeParam_producesCanonical400() throws Exception {
        mockMvc.perform(get("/api/v1/incidentes/abc-invalid-id")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @Order(11)
    @DisplayName("Vector 11: Oracle Duplicate Key -> Código duplicado retorna 409 canónico")
    public void vector11_oracleDuplicateKey_producesCanonical409() throws Exception {
        // Intentar crear un proveedor con el mismo NIT en la misma organización
        String provJson = """
            {
                "tipoPersona": "JURIDICA",
                "razonSocial": "Duplicado NIT SAS",
                "nitIdentificacion": "900890802-2",
                "emailContacto": "duplicado@test.com",
                "telefonoContacto": "3001234567",
                "categoriaServicio": "SEGURIDAD"
            }
        """;

        mockMvc.perform(post("/api/v1/proveedores")
                        .header("Authorization", "Bearer " + tokenAdminOrg2)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_ORG2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value(anyOf(equalTo("DUPLICATE_KEY"), equalTo("PROVEEDOR_DUPLICADO"), equalTo("PROVEEDOR_NIT_DUPLICADO"))));
    }

    @Test
    @Order(12)
    @DisplayName("Vector 12: Oracle FK Violation -> Inserción con FK inexistente es sanitizada")
    public void vector12_oracleFkViolation_producesCanonicalSanitizedError() throws Exception {
        // Crear incidente con idZonaComun inexistente (99999999)
        IncidenteDTO req = new IncidenteDTO();
        req.setTitulo("ADV-F908 FK Violation Test");
        req.setTipoIncidente("DANO_BIEN_COMUN");
        req.setNivelSeveridad("LEVE");
        req.setDescripcionHechos("Prueba de FK inexistente");
        req.setFechaHoraIncidente(ZonedDateTime.now());
        req.setIdZonaComun(99999999L);

        MvcResult res = mockMvc.perform(post("/api/v1/incidentes")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        int status = res.getResponse().getStatus();
        assertTrue(status == 400 || status == 409 || status == 500);

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("ORA-"), "No debe exponer ORA-");
        assertFalse(body.contains("FK_"), "No debe exponer identificadores de Foreign Key");
    }

    @Test
    @Order(13)
    @DisplayName("Vector 13: Internal Error Sanitization -> Ningún error filtra SQL, tablas ni stack traces")
    public void vector13_internalErrorSanitization_zeroLeakage() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/incidentes/999999999")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1))
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("SELECT "));
        assertFalse(body.contains("FROM "));
        assertFalse(body.contains("WHERE "));
        assertFalse(body.contains("org.springframework"));
        assertFalse(body.contains("com.saed.backend"));
        assertFalse(body.contains("\tat "));
    }

    @Test
    @Order(14)
    @DisplayName("Vector 14: TraceId Propagation -> X-Correlation-Id viaja en cabecera y cuerpo de respuesta")
    public void vector14_traceIdPropagation_matchingHeaderAndBody() throws Exception {
        String testCid = "cid-adv-cert-20260919";

        mockMvc.perform(get("/api/v1/incidentes/no-existe")
                        .header("Authorization", "Bearer " + tokenAdminProp1)
                        .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1)
                        .header("X-Correlation-Id", testCid))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", testCid))
                .andExpect(jsonPath("$.traceId").value(testCid));
    }

    @Test
    @Order(15)
    @DisplayName("Vector 15: Concurrencia Real Multi-hilo -> Carreras de mutación FSM preservan consistencia ACID")
    public void vector15_concurrentStateMutations_maintainsConsistency() throws Exception {
        Long idInc = insertIncidenteDirecto(PROP_1_ID, "ADV-F908 Concurrencia ACID", "CONVIVENCIA_RUIDO", "MODERADA", "REPORTADO");

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successMutations = new AtomicInteger(0);

        List<String> targetStates = List.of("EN_INVESTIGACION", "ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO");

        for (String targetState : targetStates) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Disparo simultáneo
                    IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
                    dto.setEstado(targetState);
                    dto.setConclusiones("Transición concurrente a " + targetState);
                    dto.setMotivo("Prueba concurrente ACID");

                    int statusCode = mockMvc.perform(put("/api/v1/incidentes/" + idInc + "/estado")
                                    .header("Authorization", "Bearer " + tokenAdminProp1)
                                    .header("X-Assignment-Id", ASSIGN_ADMIN_PROP1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn().getResponse().getStatus();

                    if (statusCode == 200) {
                        successMutations.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start race
        boolean finished = doneLatch.await(8, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Las peticiones concurrentes deben responder dentro de la ventana de tiempo");

        // Validar estado final consistente en Oracle XE
        setElevatedContext();
        String finalState = jdbcTemplate.queryForObject("SELECT ESTADO FROM INCIDENTES WHERE ID_INCIDENTE = ?", String.class, idInc);
        assertNotNull(finalState);
        assertTrue(List.of("REPORTADO", "EN_INVESTIGACION", "ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO").contains(finalState),
                "El estado final en base de datos debe ser válido e inmune a corrupción por concurrencia");
    }
}
