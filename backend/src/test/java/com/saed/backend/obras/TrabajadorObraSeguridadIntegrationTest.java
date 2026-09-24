package com.saed.backend.obras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Verificación de Seguridad y Separación de Capacidades RESIDENTE vs ADMIN
 * para GAP-F9-05 / Observación B.
 *
 * Cobertura de las 10 reglas de seguridad en Backend:
 * 1. RESIDENTE puede asignar trabajador a su obra -> asignado en estado AUTORIZADO = 'N', sin AUTORIZADO_POR ni FECHA_AUTORIZACION.
 * 2. RESIDENTE intento de request tampering por query param (?autorizado=S) -> backend descarta y mantiene AUTORIZADO = 'N'.
 * 3. RESIDENTE intento de request tampering por body ({"autorizado":"S"}) -> backend descarta y mantiene AUTORIZADO = 'N'.
 * 4. RESIDENTE puede desasignar trabajador de su propia obra -> 204 No Content.
 * 5. RESIDENTE intento de llamada directa a endpoint administrativo de autorizar -> 403 Forbidden.
 * 6. RESIDENTE intento de llamada directa a endpoint administrativo de revocar -> 403 Forbidden.
 * 7. RESIDENTE intento IDOR: asignar trabajador a obra de otra unidad -> 403 Access Denied.
 * 8. RESIDENTE intento IDOR: remover trabajador de obra de otra unidad -> 403 Access Denied.
 * 9. ADMIN autorizado puede autorizar trabajador -> 200 OK, AUTORIZADO = 'S', AUTORIZADO_POR registrado.
 * 10. ADMIN autorizado puede revocar trabajador -> 200 OK, AUTORIZADO = 'N', FECHA_REVOCACION registrada.
 * 11. ADMIN de otra propiedad intento IDOR de autorizar trabajador -> 403/404 Access Denied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TrabajadorObraSeguridadIntegrationTest {

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
    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_RESIDENTE_1 = 4L;
    private static final long ASSIGN_RESIDENTE_1 = 104L;

    private static final long USER_RESIDENTE_2 = 5L;
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_ADMIN_PROP_2 = 99L;
    private static final long ASSIGN_ADMIN_PROP_2 = 991L;

    // IDs de negocio
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIDAD_1_ID = 1L;
    private static final long UNIDAD_2_ID = 2L;

    private static final long ORG_2_ID = 9992L;
    private static final long PROP_2_ID = 9992L;

    private static final long OBRA_RES_1_ID = 9501L;
    private static final long OBRA_RES_2_ID = 9502L;

    private static final long PROV_ACTIVO_1 = 9601L;

    private static final long TRAB_SEG_1 = 9701L;
    private static final long TRAB_SEG_2 = 9702L;
    private static final long TRAB_SEG_3 = 9703L;
    private static final long TRAB_SEG_4 = 9704L;
    private static final long TRAB_SEG_5 = 9705L;

    @BeforeEach
    void setUp() {
        setElevatedContext();

        // 1. Limpieza idempotente
        jdbcTemplate.update("DELETE FROM OBRA_TRABAJADORES WHERE ID_OBRA IN (?, ?)", OBRA_RES_1_ID, OBRA_RES_2_ID);
        jdbcTemplate.update("DELETE FROM TRABAJADORES WHERE ID_TRABAJADOR IN (?, ?, ?, ?, ?)",
                TRAB_SEG_1, TRAB_SEG_2, TRAB_SEG_3, TRAB_SEG_4, TRAB_SEG_5);
        jdbcTemplate.update("DELETE FROM PERSONAS WHERE NUMERO_DOCUMENTO LIKE '9888%'");

        // 2. Infraestructura base
        ensureOrganizacion(ORG_1_ID, "Organización Central", "900000001-1", "org1@saed.com");
        ensureOrganizacion(ORG_2_ID, "Organización Foránea", "900000002-2", "org2@saed.com");

        ensurePropiedad(PROP_1_ID, ORG_1_ID, "Torre Central SAED");
        ensurePropiedad(PROP_2_ID, ORG_2_ID, "Torre Foránea 9992");

        ensureUnidad(UNIDAD_1_ID, PROP_1_ID, "Apto 101");
        ensureUnidad(UNIDAD_2_ID, PROP_1_ID, "Apto 102");

        ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Propiedad", "admin@saed.com");
        ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin", "admin@saed.com");

        ensurePersona(USER_RESIDENTE_1, "1000000004", "Carlos", "Martinez", "camartinez@saed.com");
        ensureUsuario(USER_RESIDENTE_1, USER_RESIDENTE_1, "carlos_m", "camartinez@saed.com");

        ensurePersona(USER_RESIDENTE_2, "1000000005", "Maria", "Gomez", "mgomez@saed.com");
        ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "maria_g", "mgomez@saed.com");

        ensurePersona(USER_ADMIN_PROP_2, "1000000099", "AdminProp2", "SAED", "admin_prop2@saed.com");
        ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2", "admin_prop2@saed.com");

        ensureMembresia(ORG_1_ID);
        ensureMembresia(ORG_2_ID);

        ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
        ensureAsignacion(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_1_ID);
        ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIDAD_2_ID);
        ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);

        // 3. Mock de asignaciones JWT
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central");
        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Torre Central SAED");
        UnitDTO unit1 = new UnitDTO(UNIDAD_1_ID, "Apto 101");
        UnitDTO unit2 = new UnitDTO(UNIDAD_2_ID, "Apto 102");

        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Torre Foránea 9992");

        AssignmentResponseDTO adminProp1Assign = new AssignmentResponseDTO();
        adminProp1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        adminProp1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp1Assign.setOrganizacion(org1);
        adminProp1Assign.setPropiedad(prop1);

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

        AssignmentResponseDTO adminProp2Assign = new AssignmentResponseDTO();
        adminProp2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        adminProp2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminProp2Assign.setOrganizacion(org2);
        adminProp2Assign.setPropiedad(prop2);

        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(adminProp1Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_1, USER_RESIDENTE_1)).thenReturn(Optional.of(res1Assign));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2)).thenReturn(Optional.of(res2Assign));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(adminProp2Assign));

        // 4. Proveedor y obras
        ensureProveedor(PROV_ACTIVO_1, ORG_1_ID, "Constructora Seguridad SA", "900999001", "ACTIVO");
        ensureObra(OBRA_RES_1_ID, UNIDAD_1_ID, "APROBADA", USER_RESIDENTE_1);
        ensureObra(OBRA_RES_2_ID, UNIDAD_2_ID, "APROBADA", USER_RESIDENTE_2);

        // 5. Trabajadores de prueba con ARL vigente
        ensureTrabajador(TRAB_SEG_1, 881L, PROV_ACTIVO_1, "Oficial", "Sura", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), "ACTIVO");
        ensureTrabajador(TRAB_SEG_2, 882L, PROV_ACTIVO_1, "Plomero", "Positiva", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), "ACTIVO");
        ensureTrabajador(TRAB_SEG_3, 883L, PROV_ACTIVO_1, "Electricista", "Colpatria", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), "ACTIVO");
        ensureTrabajador(TRAB_SEG_4, 884L, PROV_ACTIVO_1, "Pintor", "Sura", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), "ACTIVO");
        ensureTrabajador(TRAB_SEG_5, 885L, PROV_ACTIVO_1, "Carpintero", "Sura", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), "ACTIVO");

        clearContext();
    }

    @AfterEach
    void tearDown() {
        clearContext();
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

    @Test
    @Order(1)
    @DisplayName("Regla 1: RESIDENTE puede asignar trabajador -> queda en estado AUTORIZADO = 'N' sin autorización administrativa")
    void regla1_residenteAsignaTrabajador_quedaEnEstadoNoAutorizado() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isCreated());

        // Verificar en BD que AUTORIZADO = 'N' y no tiene AUTORIZADO_POR ni FECHA_AUTORIZACION
        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT AUTORIZADO, AUTORIZADO_POR, FECHA_AUTORIZACION, FECHA_REVOCACION FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                OBRA_RES_1_ID, TRAB_SEG_1
        );
        clearContext();

        assertEquals("N", row.get("AUTORIZADO"), "El trabajador asignado por residente debe quedar en estado 'N' (no autorizado)");
        assertNull(row.get("AUTORIZADO_POR"), "El campo AUTORIZADO_POR no debe ser establecido por un residente");
        assertNull(row.get("FECHA_AUTORIZACION"), "FECHA_AUTORIZACION no debe ser establecida en asignación por residente");
        assertNull(row.get("FECHA_REVOCACION"), "FECHA_REVOCACION debe ser nula en asignación inicial");
    }

    @Test
    @Order(2)
    @DisplayName("Regla 2: RESIDENTE intento de request tampering por query param (?autorizado=S) -> backend lo fuerza a 'N'")
    void regla2_residenteTamperingQueryParam_backendFuerzaNoAutorizado() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_2 + "?autorizado=S")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isCreated());

        // Verificar en BD que a pesar del parámetro autorizado=S, el backend lo forzó a 'N'
        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT AUTORIZADO, AUTORIZADO_POR FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                OBRA_RES_1_ID, TRAB_SEG_2
        );
        clearContext();

        assertEquals("N", row.get("AUTORIZADO"), "El backend debe neutralizar el request tampering y forzar AUTORIZADO = 'N'");
        assertNull(row.get("AUTORIZADO_POR"), "AUTORIZADO_POR no debe ser establecido mediante manipulación de request");
    }

    @Test
    @Order(3)
    @DisplayName("Regla 3: RESIDENTE intento de request tampering por body ({\"autorizado\":\"S\"}) -> backend lo fuerza a 'N'")
    void regla3_residenteTamperingBody_backendFuerzaNoAutorizado() throws Exception {
        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        Map<String, Object> body = Map.of(
                "idTrabajador", TRAB_SEG_3,
                "autorizado", "S",
                "autorizadoPor", USER_RESIDENTE_1
        );

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT AUTORIZADO, AUTORIZADO_POR FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                OBRA_RES_1_ID, TRAB_SEG_3
        );
        clearContext();

        assertEquals("N", row.get("AUTORIZADO"), "El backend debe neutralizar el body tampering y forzar 'N'");
        assertNull(row.get("AUTORIZADO_POR"), "El residente no puede autoasignarse como autorizador");
    }

    @Test
    @Order(4)
    @DisplayName("Regla 4: RESIDENTE puede desasignar trabajador de su propia obra -> 204 No Content")
    void regla4_residenteDesasignaTrabajador_exitoso() throws Exception {
        // Preasignar trabajador
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')",
                OBRA_RES_1_ID, TRAB_SEG_4);
        clearContext();

        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        mockMvc.perform(delete("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_4)
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isNoContent());

        setElevatedContext();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                Integer.class, OBRA_RES_1_ID, TRAB_SEG_4
        );
        clearContext();

        assertEquals(0, count, "El trabajador debe haber sido removido de la obra");
    }

    @Test
    @Order(5)
    @DisplayName("Regla 5: RESIDENTE intento de llamada directa a endpoint administrativo de autorizar -> 403 Forbidden")
    void regla5_residenteIntentoAutorizar_denegado403() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')",
                OBRA_RES_1_ID, TRAB_SEG_1);
        clearContext();

        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1 + "/autorizar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("Regla 6: RESIDENTE intento de llamada directa a endpoint administrativo de revocar -> 403 Forbidden")
    void regla6_residenteIntentoRevocar_denegado403() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'S')",
                OBRA_RES_1_ID, TRAB_SEG_1);
        clearContext();

        String tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1 + "/revocar")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Regla 7: RESIDENTE intento IDOR: asignar trabajador a obra de otra unidad -> 403 Access Denied")
    void regla7_residenteIntentoIdor_asignarObraOtraUnidad_denegado() throws Exception {
        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Residente 1 intenta asignar trabajador a la Obra de la Unidad 2
        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_2_ID + "/trabajadores/" + TRAB_SEG_5)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("Regla 8: RESIDENTE intento IDOR: remover trabajador de obra de otra unidad -> 403 Access Denied")
    void regla8_residenteIntentoIdor_desasignarObraOtraUnidad_denegado() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')",
                OBRA_RES_2_ID, TRAB_SEG_5);
        clearContext();

        String tokenResidente1 = jwtProvider.generateIdentityToken(USER_RESIDENTE_1);

        // Residente 1 intenta remover trabajador de la Obra de la Unidad 2
        mockMvc.perform(delete("/api/v1/obras/" + OBRA_RES_2_ID + "/trabajadores/" + TRAB_SEG_5)
                .header("Authorization", "Bearer " + tokenResidente1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("Regla 9: ADMIN autorizado puede autorizar trabajador -> 200 OK, AUTORIZADO = 'S' y AUTORIZADO_POR registrado")
    void regla9_adminAutorizado_puedeAutorizarTrabajador() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')",
                OBRA_RES_1_ID, TRAB_SEG_1);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1 + "/autorizar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT AUTORIZADO, AUTORIZADO_POR, FECHA_AUTORIZACION FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                OBRA_RES_1_ID, TRAB_SEG_1
        );
        clearContext();

        assertEquals("S", row.get("AUTORIZADO"), "El administrador debe poder autorizar al operario");
        assertEquals(USER_ADMIN_PROP_1, ((Number) row.get("AUTORIZADO_POR")).longValue(), "AUTORIZADO_POR debe ser el ID del administrador");
        assertNotNull(row.get("FECHA_AUTORIZACION"), "FECHA_AUTORIZACION debe haber sido establecida");
    }

    @Test
    @Order(10)
    @DisplayName("Regla 10: ADMIN autorizado puede revocar trabajador -> 200 OK, AUTORIZADO = 'N' y FECHA_REVOCACION registrada")
    void regla10_adminAutorizado_puedeRevocarTrabajador() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO, FECHA_AUTORIZACION, AUTORIZADO_POR) VALUES (?, ?, 'S', CURRENT_TIMESTAMP, ?)",
                OBRA_RES_1_ID, TRAB_SEG_1, USER_ADMIN_PROP_1);
        clearContext();

        String tokenAdmin = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1 + "/revocar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        setElevatedContext();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT AUTORIZADO, FECHA_REVOCACION FROM OBRA_TRABAJADORES WHERE ID_OBRA = ? AND ID_TRABAJADOR = ?",
                OBRA_RES_1_ID, TRAB_SEG_1
        );
        clearContext();

        assertEquals("N", row.get("AUTORIZADO"), "La autorización debe quedar revocada");
        assertNotNull(row.get("FECHA_REVOCACION"), "FECHA_REVOCACION debe quedar registrada");
    }

    @Test
    @Order(11)
    @DisplayName("Regla 11: ADMIN de otra propiedad intento IDOR de autorizar trabajador -> 403 Access Denied")
    void regla11_adminForaneoIntentoIdor_esDenegado() throws Exception {
        setElevatedContext();
        jdbcTemplate.update("INSERT INTO OBRA_TRABAJADORES (ID_OBRA, ID_TRABAJADOR, AUTORIZADO) VALUES (?, ?, 'N')",
                OBRA_RES_1_ID, TRAB_SEG_1);
        clearContext();

        // Admin de Propiedad 2 intenta autorizar obra de Propiedad 1
        String tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);

        mockMvc.perform(post("/api/v1/obras/" + OBRA_RES_1_ID + "/trabajadores/" + TRAB_SEG_1 + "/autorizar")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // HELPERS DE BASE DE DATOS
    // =========================================================================

    private void ensureProveedor(Long idProveedor, Long idOrganizacion, String razonSocial, String nit, String estado) {
        jdbcTemplate.update("""
            MERGE INTO PROVEEDORES target
            USING (SELECT ? AS id, ? AS id_org, ? AS razon, ? AS nit, ? AS estado FROM DUAL) src
            ON (target.ID_PROVEEDOR = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_ORGANIZACION = src.id_org,
                           target.RAZON_SOCIAL = src.razon,
                           target.NIT_IDENTIFICACION = src.nit,
                           target.ESTADO = src.estado
            WHEN NOT MATCHED THEN
                INSERT (ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION, EMAIL_CONTACTO, TELEFONO_CONTACTO, CATEGORIA_SERVICIO, ESTADO, CALIFICACION_PROM)
                VALUES (src.id, src.id_org, 'JURIDICA', src.razon, src.nit, 'prov' || src.id || '@test.com', '3001234567', 'Construccion y Mantenimiento', src.estado, 5.0)
        """, idProveedor, idOrganizacion, razonSocial, nit, estado);
    }

    private void ensureObra(Long idObra, Long idUnidad, String estado, Long solicitadoPor) {
        jdbcTemplate.update("""
            MERGE INTO OBRAS target
            USING (SELECT ? AS id, ? AS id_u, ? AS estado, ? AS sol FROM DUAL) src
            ON (target.ID_OBRA = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_UNIDAD = src.id_u,
                           target.ESTADO = src.estado,
                           target.SOLICITADO_POR = src.sol
            WHEN NOT MATCHED THEN
                INSERT (ID_OBRA, ID_UNIDAD, DESCRIPCION, FECHA_INICIO, FECHA_FIN_ESTIMADA,
                        RESPONSABLE_OBRA, TELEFONO_RESPONSABLE, DEPOSITO_GARANTIA, ESTADO, SOLICITADO_POR)
                VALUES (src.id, src.id_u, 'Obra de prueba', TRUNC(SYSDATE), TRUNC(SYSDATE + 30),
                        'Ing. Perez', '3001234567', 0, src.estado, src.sol)
        """, idObra, idUnidad, estado, solicitadoPor);
    }

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, NUMERO_DOCUMENTO, EMAIL_CONTACTO, ESTADO) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVA')", id, nombre, nit, email);
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
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                    "VALUES (?, 1, ?, ?, ?, ?)", id, doc, nombre, apellido, email);
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
        try {
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
        } catch (Exception ignored) {}
    }

    private void ensureAsignacion(Long idAsignacion, Long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", idAsignacion);
        jdbcTemplate.update("""
            INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))
        """, idAsignacion, idUsuario, idRol, idOrg, idProp, idUnidad);
    }

    private void ensureTrabajador(Long idTrabajador, Long idPersona, Long idProveedor, String oficio, String arl, LocalDate afiliacion, LocalDate vencimiento, String estado) {
        ensurePersona(idPersona, "9888" + idPersona, "Trabajador", "Num" + idPersona, "trab" + idPersona + "@test.com");

        jdbcTemplate.update("""
            MERGE INTO TRABAJADORES target
            USING (SELECT ? AS id, ? AS id_per, ? AS id_prov, ? AS oficio, ? AS arl, ? AS afil, ? AS venc, ? AS estado FROM DUAL) src
            ON (target.ID_TRABAJADOR = src.id)
            WHEN MATCHED THEN
                UPDATE SET target.ID_PERSONA = src.id_per,
                           target.ID_PROVEEDOR = src.id_prov,
                           target.OFICIO_ESPECIALIDAD = src.oficio,
                           target.ARL_ASEGURADORA = src.arl,
                           target.ARL_FECHA_AFILIACION = src.afil,
                           target.ARL_FECHA_VENCIMIENTO = src.venc,
                           target.ESTADO = src.estado
            WHEN NOT MATCHED THEN
                INSERT (ID_TRABAJADOR, ID_PERSONA, ID_PROVEEDOR, OFICIO_ESPECIALIDAD, ARL_ASEGURADORA, ARL_FECHA_AFILIACION, ARL_FECHA_VENCIMIENTO, ESTADO)
                VALUES (src.id, src.id_per, src.id_prov, src.oficio, src.arl, src.afil, src.venc, src.estado)
        """, idTrabajador, idPersona, idProveedor, oficio, arl,
                afiliacion != null ? java.sql.Date.valueOf(afiliacion) : null,
                vencimiento != null ? java.sql.Date.valueOf(vencimiento) : null,
                estado);
    }
}
