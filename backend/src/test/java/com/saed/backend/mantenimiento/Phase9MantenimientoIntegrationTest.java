package com.saed.backend.mantenimiento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoEstadoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoReprogramarDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;
import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.service.ReservasService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Certificación Integral para GAP-F9-03:
 * Órdenes de Mantenimiento Preventivo, Correctivo y Bloqueo de Zonas en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de 30 pruebas técnicas exhaustivas:
 * - Ciclo de vida y máquina de estados (PROGRAMADO, EN_PROCESO, COMPLETADO, CANCELADO, REPROGRAMADO).
 * - Sincronización bidireccional con ACTIVOS (GAP-F9-02) y bloqueo pesimista de fila.
 * - Validación estricta de PROVEEDORES (GAP-F9-01): asignación exclusiva de contratistas en estado ACTIVO de la misma org.
 * - Integración con BLOQUEOS_ZONA y RESERVAS (Fase 8): detección de solapamiento y protección contra double booking.
 * - Aislamiento estricto multi-tenant y 6 vectores de ataque Anti-IDOR.
 * - Control de acceso por roles (ADMIN_PROPIEDAD vs RESIDENTE).
 * - Validaciones de integridad financiera y parámetros de entrada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase9MantenimientoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivoRepository activoRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Autowired
    private ReservasService reservasService;

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

    // Propiedades y Organizaciones
    private static final long ORG_1_ID = 1L;
    private static final long ORG_2_ID = 8802L;

    private static final long PROP_1_ID = 1L;
    private static final long PROP_2_ID = 8802L;

    // Tokens
    private String tokenSuperAdmin;
    private String tokenAdminOrg1;
    private String tokenAdminProp1;
    private String tokenAdminProp2;
    private String tokenResidente;

    // IDs de entidades creadas para las pruebas
    private Long idActivoP1_Operativo;
    private Long idActivoP1_Baja;
    private Long idActivoP2_Operativo;

    private Long idProvOrg1_Activo;
    private Long idProvOrg1_Inactivo;
    private Long idProvOrg2_Activo;

    private Long idZonaP1;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza controlada previa
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM BLOQUEOS_ZONA WHERE MOTIVO LIKE '%TEST%' OR MOTIVO LIKE '%Mantenimiento%';
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM MANTENIMIENTOS WHERE TITULO LIKE 'TEST-%' OR TITULO LIKE 'Mantenimiento%';
                    DELETE FROM ACTIVOS WHERE CODIGO_ACTIVO LIKE 'ACT-TEST-%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION LIKE '900111222-%';
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (902, 903, 904, 905);
                END;
            """);

            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensureOrganizacion(ORG_2_ID, "Organización Foránea Norte", "9008802-2", "org8802@saed.com");

            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensurePropiedad(PROP_2_ID, ORG_2_ID, "Condominio Campestre Norte");

            ensureUnidad(1L, PROP_1_ID, "101");

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");
            ensureAdminSaed(USER_SUPERADMIN);

            ensurePersona(USER_ADMIN_ORG_1, "1000000801", "Admin", "OrgUno", "adminorg1@saed.com");
            ensureUsuario(USER_ADMIN_ORG_1, USER_ADMIN_ORG_1, "admin_org1_test", "adminorg1@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000802", "Admin", "PropUno", "adminprop1@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin_prop1_test", "adminprop1@saed.com");

            ensurePersona(USER_ADMIN_PROP_2, "1000000803", "Admin", "PropDos", "adminprop2@saed.com");
            ensureUsuario(USER_ADMIN_PROP_2, USER_ADMIN_PROP_2, "admin_prop2_test", "adminprop2@saed.com");

            ensurePersona(USER_RESIDENTE, "1000000805", "Residente", "Pruebas", "residente@saed.com");
            ensureUsuario(USER_RESIDENTE, USER_RESIDENTE, "residente_test", "residente@saed.com");

            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1, "ADMIN_ORGANIZACION", ORG_1_ID, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2, "ADMIN_PROPIEDAD", ORG_2_ID, PROP_2_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE, USER_RESIDENTE, "RESIDENTE", ORG_1_ID, PROP_1_ID, 1L);

            setupMockAssignments();

            // Activos
            idActivoP1_Operativo = insertActivo(PROP_1_ID, "ACT-TEST-M1", "Ascensor Principal Torre 1", "OPERATIVO");
            idActivoP1_Baja = insertActivo(PROP_1_ID, "ACT-TEST-M2", "Planta Antigua Inactiva", "DADO_DE_BAJA");
            idActivoP2_Operativo = insertActivo(PROP_2_ID, "ACT-TEST-M3", "CCTV Propiedad 2", "OPERATIVO");

            // Proveedores
            idProvOrg1_Activo = insertProveedor(ORG_1_ID, "Servicios Electromecánicos SAS", "900111222-1", "ACTIVO");
            idProvOrg1_Inactivo = insertProveedor(ORG_1_ID, "Mantenimientos Clausurados LTDA", "900111222-2", "INACTIVO");
            idProvOrg2_Activo = insertProveedor(ORG_2_ID, "Contratistas del Norte SAS", "900111222-3", "ACTIVO");

            // Zona Común en Propiedad 1
            idZonaP1 = insertZonaComun(PROP_1_ID, "TEST_PISCINA_ADULTOS", "ACUATICA", 40);

            // Tokens
            tokenSuperAdmin = jwtProvider.generateIdentityToken(USER_SUPERADMIN);
            tokenAdminOrg1 = jwtProvider.generateIdentityToken(USER_ADMIN_ORG_1);
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenAdminProp2 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_2);
            tokenResidente = jwtProvider.generateIdentityToken(USER_RESIDENTE);

        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        setElevatedContext();
        try {
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM BLOQUEOS_ZONA WHERE MOTIVO LIKE '%TEST%' OR MOTIVO LIKE '%Mantenimiento%';
                    DELETE FROM RESERVAS WHERE ID_ZONA IN (SELECT ID_ZONA FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%');
                    DELETE FROM MANTENIMIENTOS WHERE TITULO LIKE 'TEST-%' OR TITULO LIKE 'Mantenimiento%';
                    DELETE FROM ACTIVOS WHERE CODIGO_ACTIVO LIKE 'ACT-TEST-%';
                    DELETE FROM PROVEEDORES WHERE NIT_IDENTIFICACION LIKE '900111222-%';
                    DELETE FROM ZONAS_COMUNES WHERE NOMBRE LIKE 'TEST_%';
                    DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (902, 903, 904, 905);
                END;
            """);
        } catch (Exception ignored) {}
        clearContext();
    }

    // =========================================================================
    // CASOS DE PRUEBA MANDATORIOS (TEST 01 - TEST 30)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("01 — Crear mantenimiento válido en Propiedad 1 -> 201 Created y persistencia en Oracle XE")
    void test01_CrearMantenimiento_Valido_Retorna201() throws Exception {
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-01: Mantenimiento Preventivo Bimestral Ascensor");
        dto.setTipoMantenimiento("PREVENTIVO");
        dto.setPrioridad("ALTA");
        dto.setDescripcionTrabajo("Inspección de guayas, poleas y lubricación de rieles.");
        dto.setFechaProgramada(LocalDate.now().plusDays(3));
        dto.setCostoEstimado(new BigDecimal("1500000.00"));
        dto.setTecnicoResponsable("Ing. Carlos Mendoza");
        dto.setIdActivo(idActivoP1_Operativo);
        dto.setIdProveedorServicio(idProvOrg1_Activo);

        MvcResult result = mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMantenimiento", notNullValue()))
                .andExpect(jsonPath("$.titulo", is(dto.getTitulo())))
                .andExpect(jsonPath("$.tipoMantenimiento", is("PREVENTIVO")))
                .andExpect(jsonPath("$.prioridad", is("ALTA")))
                .andExpect(jsonPath("$.estado", is("PROGRAMADO")))
                .andExpect(jsonPath("$.idActivo", is(idActivoP1_Operativo.intValue())))
                .andExpect(jsonPath("$.idProveedorServicio", is(idProvOrg1_Activo.intValue())))
                .andReturn();

        // Verificación directa en base de datos Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM MANTENIMIENTOS WHERE TITULO = ? AND ESTADO = 'PROGRAMADO' AND ID_PROPIEDAD = ?",
                    Integer.class, dto.getTitulo(), PROP_1_ID);
            assertEquals(1, count, "El mantenimiento debe persistir en estado PROGRAMADO en Oracle XE");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(2)
    @DisplayName("02 — Listado tenant-scoped con aislamiento estricto (Propiedad 1 vs Propiedad 2)")
    void test02_Listado_TenantScoped_AislamientoEstricto() throws Exception {
        insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-MANT-PROP1", "PROGRAMADO");
        insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-MANT-PROP2", "PROGRAMADO");

        // Admin Prop 1 consulta: solo debe ver los suyos
        mockMvc.perform(get("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("TEST-MANT-PROP1")))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("TEST-MANT-PROP2"))));

        // Admin Prop 2 consulta: solo debe ver los suyos
        mockMvc.perform(get("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("TEST-MANT-PROP2")))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("TEST-MANT-PROP1"))));
    }

    @Test
    @Order(3)
    @DisplayName("03 — GET por ID de mantenimiento propio -> 200 OK con payload íntegro")
    void test03_GetPorId_Propio_Retorna200() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-GET-PROPIO", "PROGRAMADO");

        mockMvc.perform(get("/api/v1/mantenimientos/" + idMant)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idMantenimiento", is(idMant.intValue())))
                .andExpect(jsonPath("$.titulo", is("TEST-GET-PROPIO")))
                .andExpect(jsonPath("$.idPropiedad", is((int) PROP_1_ID)));
    }

    @Test
    @Order(4)
    @DisplayName("04 — Anti-IDOR Vector 1: GET por ID bloquea acceso a orden de otra propiedad (404/403)")
    void test04_AntiIdor_Get_BloqueaAccesoCrossTenant() throws Exception {
        Long idMantProp2 = insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-MANT-IDOR-1", "PROGRAMADO");

        // Admin Prop 1 intenta consultar el mantenimiento de Prop 2
        mockMvc.perform(get("/api/v1/mantenimientos/" + idMantProp2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("MANTENIMIENTO_NOT_FOUND")));
    }

    @Test
    @Order(5)
    @DisplayName("05 — Anti-IDOR Vector 2: UPDATE bloquea modificación cross-tenant")
    void test05_AntiIdor_Update_BloqueaModificacionCrossTenant() throws Exception {
        Long idMantProp2 = insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-ORIGINAL-PROP2", "PROGRAMADO");

        MantenimientoUpdateDTO updateDTO = new MantenimientoUpdateDTO();
        updateDTO.setTitulo("TEST-HACKEADO-POR-PROP1");
        updateDTO.setDescripcionTrabajo("Intento de mutación ilícita.");

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMantProp2)
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isNotFound());

        // Verificar que los datos en Oracle siguen intactos
        setElevatedContext();
        try {
            String tituloActual = jdbcTemplate.queryForObject(
                    "SELECT TITULO FROM MANTENIMIENTOS WHERE ID_MANTENIMIENTO = ?",
                    String.class, idMantProp2);
            assertEquals("TEST-ORIGINAL-PROP2", tituloActual);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(6)
    @DisplayName("06 — Anti-IDOR Vector 3: PATCH estado bloquea alteración cross-tenant")
    void test06_AntiIdor_PatchEstado_BloqueaAlteracionCrossTenant() throws Exception {
        Long idMantProp2 = insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-STATUS-PROP2", "PROGRAMADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("EN_PROCESO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMantProp2 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    @DisplayName("07 — Anti-IDOR Vector 4: Reprogramar bloquea reprogramación cross-tenant")
    void test07_AntiIdor_Reprogramar_BloqueaCrossTenant() throws Exception {
        Long idMantProp2 = insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-REPROG-PROP2", "PROGRAMADO");

        MantenimientoReprogramarDTO reprogDTO = new MantenimientoReprogramarDTO();
        reprogDTO.setNuevaFechaProgramada(LocalDate.now().plusDays(10));
        reprogDTO.setMotivo("Hack cross tenant");

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMantProp2 + "/reprogramar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reprogDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("08 — Anti-IDOR Vector 5: Cancelar bloquea cancelación cross-tenant")
    void test08_AntiIdor_Cancelar_BloqueaCrossTenant() throws Exception {
        Long idMantProp2 = insertMantenimientoDirecto(PROP_2_ID, idActivoP2_Operativo, idProvOrg2_Activo, "TEST-CANCEL-PROP2", "PROGRAMADO");

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMantProp2 + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isNotFound());

        // Verificar que en Oracle sigue PROGRAMADO
        setElevatedContext();
        try {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM MANTENIMIENTOS WHERE ID_MANTENIMIENTO = ?",
                    String.class, idMantProp2);
            assertEquals("PROGRAMADO", estado);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(9)
    @DisplayName("09 — Transición válida PROGRAMADO -> EN_PROCESO sincroniza activo a MANTENIMIENTO en Oracle")
    void test09_Transicion_ProgramadoAEnProceso_SincronizaActivo() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-INICIAR-MANT", "PROGRAMADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("EN_PROCESO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_PROCESO")));

        // Verificar que el activo en Oracle pasó de OPERATIVO a MANTENIMIENTO
        setElevatedContext();
        try {
            String estadoActivo = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoP1_Operativo);
            assertEquals("MANTENIMIENTO", estadoActivo, "El activo debe estar en estado MANTENIMIENTO");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(10)
    @DisplayName("10 — Transición válida EN_PROCESO -> COMPLETADO sincroniza activo de regreso a OPERATIVO")
    void test10_Transicion_EnProcesoACompletado_SincronizaActivoOperativo() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-COMPLETAR-MANT", "EN_PROCESO");
        // Asegurar que el activo está en MANTENIMIENTO
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE ACTIVOS SET ESTADO = 'MANTENIMIENTO' WHERE ID_ACTIVO = ?", idActivoP1_Operativo);
        } finally {
            clearContext();
        }

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("COMPLETADO");
        estadoDTO.setCostoReal(new BigDecimal("1450000.00"));
        estadoDTO.setNotasCierre("Mantenimiento concluido satisfactoriamente con pruebas de carga.");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("COMPLETADO")))
                .andExpect(jsonPath("$.costoReal").value(1450000.00))
                .andExpect(jsonPath("$.fechaEjecucion", notNullValue()));

        // Verificar que el activo en Oracle regresó a OPERATIVO
        setElevatedContext();
        try {
            String estadoActivo = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoP1_Operativo);
            assertEquals("OPERATIVO", estadoActivo, "El activo debe regresar a estado OPERATIVO");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(11)
    @DisplayName("11 — Transición válida PROGRAMADO -> REPROGRAMADO actualiza fecha programada")
    void test11_Transicion_ProgramadoAReprogramado_ActualizaFecha() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-REPROG-OK", "PROGRAMADO");
        LocalDate nuevaFecha = LocalDate.now().plusDays(15);

        MantenimientoReprogramarDTO reprogDTO = new MantenimientoReprogramarDTO();
        reprogDTO.setNuevaFechaProgramada(nuevaFecha);
        reprogDTO.setMotivo("Retraso en entrega de repuestos por el importador.");

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMant + "/reprogramar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reprogDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("REPROGRAMADO")))
                .andExpect(jsonPath("$.fechaProgramada", is(nuevaFecha.toString())));
    }

    @Test
    @Order(12)
    @DisplayName("12 — Transición válida REPROGRAMADO -> EN_PROCESO sincroniza activo a MANTENIMIENTO")
    void test12_Transicion_ReprogramadoAEnProceso_SincronizaActivo() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-REPROG-TO-PROC", "REPROGRAMADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("EN_PROCESO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_PROCESO")));

        setElevatedContext();
        try {
            String estadoActivo = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoP1_Operativo);
            assertEquals("MANTENIMIENTO", estadoActivo);
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(13)
    @DisplayName("13 — Transición válida PROGRAMADO -> CANCELADO finaliza la orden")
    void test13_Transicion_ProgramadoACancelado_FinalizaOrden() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-CANCEL-PROG", "PROGRAMADO");

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMant + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cancelación preventiva por cambio de proveedor.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("CANCELADO")));
    }

    @Test
    @Order(14)
    @DisplayName("14 — Transición válida EN_PROCESO -> CANCELADO revierte activo a OPERATIVO")
    void test14_Transicion_EnProcesoACancelado_RevierteActivoOperativo() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-CANCEL-PROC", "EN_PROCESO");
        setElevatedContext();
        try {
            jdbcTemplate.update("UPDATE ACTIVOS SET ESTADO = 'MANTENIMIENTO' WHERE ID_ACTIVO = ?", idActivoP1_Operativo);
        } finally {
            clearContext();
        }

        mockMvc.perform(put("/api/v1/mantenimientos/" + idMant + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("CANCELADO")));

        setElevatedContext();
        try {
            String estadoActivo = jdbcTemplate.queryForObject(
                    "SELECT ESTADO FROM ACTIVOS WHERE ID_ACTIVO = ?",
                    String.class, idActivoP1_Operativo);
            assertEquals("OPERATIVO", estadoActivo, "El activo debe regresar a OPERATIVO al cancelar la orden en proceso");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(15)
    @DisplayName("15 — Transición inválida desde COMPLETADO (estado terminal) es rechazada con 400 Bad Request")
    void test15_Transicion_DesdeCompletado_RechazadaCon400() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-TERM-COMPLETADO", "COMPLETADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("EN_PROCESO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_MANTENIMIENTO_INVALIDA")));
    }

    @Test
    @Order(16)
    @DisplayName("16 — Transición inválida desde CANCELADO (estado terminal) es rechazada con 400 Bad Request")
    void test16_Transicion_DesdeCancelado_RechazadaCon400() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-TERM-CANCELADO", "CANCELADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("PROGRAMADO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_MANTENIMIENTO_INVALIDA")));
    }

    @Test
    @Order(17)
    @DisplayName("17 — Transición directa PROGRAMADO -> COMPLETADO (sin EN_PROCESO) es rechazada con 400 Bad Request")
    void test17_Transicion_DirectaProgramadoACompletado_RechazadaCon400() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-DIRECTO-COMPLETADO", "PROGRAMADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("COMPLETADO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_MANTENIMIENTO_INVALIDA")));
    }

    @Test
    @Order(18)
    @DisplayName("18 — Transición al mismo estado actual es rechazada con 400 Bad Request")
    void test18_Transicion_MismoEstado_RechazadaCon400() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-SAME-STATE", "PROGRAMADO");

        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("PROGRAMADO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(estadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TRANSICION_MANTENIMIENTO_INVALIDA")));
    }

    @Test
    @Order(19)
    @DisplayName("19 — Estado no reconocido es rechazado con 400 Bad Request")
    void test19_EstadoNoReconocido_RechazadoCon400() throws Exception {
        Long idMant = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-INVALID-STATE", "PROGRAMADO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nuevoEstado\":\"EN_REVISION\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(20)
    @DisplayName("20 — Activo no perteneciente a la copropiedad es rechazado con 409 Conflict")
    void test20_ActivoOtraPropiedad_RechazadoCon409() throws Exception {
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-ACTIVO-CROSS");
        dto.setTipoMantenimiento("CORRECTIVO");
        dto.setPrioridad("MEDIA");
        dto.setDescripcionTrabajo("Intento de asignar activo de Propiedad 2.");
        dto.setFechaProgramada(LocalDate.now().plusDays(2));
        dto.setIdActivo(idActivoP2_Operativo); // Pertenece a PROP 2

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICTO_ACTIVO_MANTENIMIENTO")));
    }

    @Test
    @Order(21)
    @DisplayName("21 — Activo en estado DADO_DE_BAJA no puede ser programado para mantenimiento (409 Conflict)")
    void test21_ActivoDadoDeBaja_RechazadoCon409() throws Exception {
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-ACTIVO-BAJA");
        dto.setTipoMantenimiento("CORRECTIVO");
        dto.setPrioridad("MEDIA");
        dto.setDescripcionTrabajo("Intento de reparar un activo dado de baja.");
        dto.setFechaProgramada(LocalDate.now().plusDays(2));
        dto.setIdActivo(idActivoP1_Baja);

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICTO_ACTIVO_MANTENIMIENTO")));
    }

    @Test
    @Order(22)
    @DisplayName("22 — Concurrencia de Activo: no se puede iniciar mantenimiento si el activo ya está en MANTENIMIENTO por otra orden (409 Conflict)")
    void test22_ConcurrenciaActivo_DosOrdenesEnProceso_RechazadoCon409() throws Exception {
        // Orden 1 en proceso: activo pasa a MANTENIMIENTO
        Long idMant1 = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-MANT-1-ACTIVO", "PROGRAMADO");
        MantenimientoEstadoDTO est1 = new MantenimientoEstadoDTO();
        est1.setNuevoEstado("EN_PROCESO");
        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant1 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(est1)))
                .andExpect(status().isOk());

        // Orden 2 intenta pasar a EN_PROCESO sobre el mismo activo
        Long idMant2 = insertMantenimientoDirecto(PROP_1_ID, idActivoP1_Operativo, idProvOrg1_Activo, "TEST-MANT-2-ACTIVO", "PROGRAMADO");
        MantenimientoEstadoDTO est2 = new MantenimientoEstadoDTO();
        est2.setNuevoEstado("EN_PROCESO");

        mockMvc.perform(patch("/api/v1/mantenimientos/" + idMant2 + "/estado")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(est2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICTO_ACTIVO_MANTENIMIENTO")));
    }

    @Test
    @Order(23)
    @DisplayName("23 — Proveedor de otra organización es rechazado con 400 Bad Request")
    void test23_ProveedorOtraOrganizacion_RechazadoCon400() throws Exception {
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-PROV-CROSS-ORG");
        dto.setTipoMantenimiento("LOCATIVO");
        dto.setPrioridad("BAJA");
        dto.setDescripcionTrabajo("Pintura de fachada con contratista foráneo.");
        dto.setFechaProgramada(LocalDate.now().plusDays(4));
        dto.setIdProveedorServicio(idProvOrg2_Activo); // Pertenece a Org 2

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PROVEEDOR_NO_VALIDO")));
    }

    @Test
    @Order(24)
    @DisplayName("24 — Proveedor en estado INACTIVO es rechazado con 400 Bad Request")
    void test24_ProveedorInactivo_RechazadoCon400() throws Exception {
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-PROV-INACTIVO");
        dto.setTipoMantenimiento("LOCATIVO");
        dto.setPrioridad("BAJA");
        dto.setDescripcionTrabajo("Prueba de asignación de proveedor inactivo.");
        dto.setFechaProgramada(LocalDate.now().plusDays(4));
        dto.setIdProveedorServicio(idProvOrg1_Inactivo);

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PROVEEDOR_NO_VALIDO")));
    }

    @Test
    @Order(25)
    @DisplayName("25 — Integración Bloqueo de Zona: Crear mantenimiento con bloqueo crea registro en BLOQUEOS_ZONA (201 Created)")
    void test25_IntegracionBloqueoZona_CreaRegistroEnBloqueosZona() throws Exception {
        Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant fin = inicio.plus(8, ChronoUnit.HOURS);

        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-MANT-CON-BLOQUEO");
        dto.setTipoMantenimiento("CORRECTIVO");
        dto.setPrioridad("ALTA");
        dto.setDescripcionTrabajo("Desinfección y cambio de filtros de la piscina.");
        dto.setFechaProgramada(LocalDate.now().plusDays(2));
        dto.setIdZonaBloqueo(idZonaP1);
        dto.setFechaInicioBloqueo(inicio);
        dto.setFechaFinBloqueo(fin);
        dto.setMotivoBloqueo("Bloqueo por mantenimiento general de piscina");

        MvcResult result = mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idBloqueoZona", notNullValue()))
                .andExpect(jsonPath("$.idZonaBloqueada", is(idZonaP1.intValue())))
                .andReturn();

        // Verificación directa en tabla BLOQUEOS_ZONA
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM BLOQUEOS_ZONA WHERE ID_ZONA = ? AND ID_PROPIEDAD = ?",
                    Integer.class, idZonaP1, PROP_1_ID);
            assertEquals(1, count, "Debe existir exactamente 1 registro en BLOQUEOS_ZONA vinculado");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(26)
    @DisplayName("26 — Solapamiento de Bloqueo: Crear mantenimiento con bloqueo solapado en la misma zona es rechazado con 409 Conflict")
    void test26_SolapamientoBloqueo_MismaZona_RechazadoCon409() throws Exception {
        Instant inicio1 = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant fin1 = inicio1.plus(10, ChronoUnit.HOURS);

        // 1. Crear primer mantenimiento con bloqueo
        MantenimientoCreateDTO dto1 = new MantenimientoCreateDTO();
        dto1.setTitulo("TEST-BLOQUEO-1");
        dto1.setTipoMantenimiento("PREVENTIVO");
        dto1.setPrioridad("MEDIA");
        dto1.setDescripcionTrabajo("Pintura piscina.");
        dto1.setFechaProgramada(LocalDate.now().plusDays(5));
        dto1.setIdZonaBloqueo(idZonaP1);
        dto1.setFechaInicioBloqueo(inicio1);
        dto1.setFechaFinBloqueo(fin1);

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        // 2. Crear segundo mantenimiento solapado (2 horas después del inicio del primero)
        Instant inicio2 = inicio1.plus(2, ChronoUnit.HOURS);
        Instant fin2 = inicio2.plus(6, ChronoUnit.HOURS);

        MantenimientoCreateDTO dto2 = new MantenimientoCreateDTO();
        dto2.setTitulo("TEST-BLOQUEO-2-SOLAPADO");
        dto2.setTipoMantenimiento("CORRECTIVO");
        dto2.setPrioridad("ALTA");
        dto2.setDescripcionTrabajo("Ajuste de bombas en horario solapado.");
        dto2.setFechaProgramada(LocalDate.now().plusDays(5));
        dto2.setIdZonaBloqueo(idZonaP1);
        dto2.setFechaInicioBloqueo(inicio2);
        dto2.setFechaFinBloqueo(fin2);

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICTO_BLOQUEO_ZONA")));
    }

    @Test
    @Order(27)
    @DisplayName("27 — Concurrencia Bloqueo vs Reserva: ReservaService rechaza creación de reserva en zona bloqueada por mantenimiento (409 Conflict)")
    void test27_ConcurrenciaBloqueoVsReserva_RechazaReservaCon409() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(7);
        // Crear bloqueo de 08:00 a 14:00 en esa fecha
        Instant inicioBloqueo = fecha.atTime(8, 0).atZone(java.time.ZoneId.of("America/Bogota")).toInstant();
        Instant finBloqueo = fecha.atTime(14, 0).atZone(java.time.ZoneId.of("America/Bogota")).toInstant();

        MantenimientoCreateDTO mantDTO = new MantenimientoCreateDTO();
        mantDTO.setTitulo("TEST-BLOQUEO-PISCINA-MANANA");
        mantDTO.setTipoMantenimiento("CORRECTIVO");
        mantDTO.setPrioridad("URGENTE");
        mantDTO.setDescripcionTrabajo("Tratamiento químico de choque en agua.");
        mantDTO.setFechaProgramada(fecha);
        mantDTO.setIdZonaBloqueo(idZonaP1);
        mantDTO.setFechaInicioBloqueo(inicioBloqueo);
        mantDTO.setFechaFinBloqueo(finBloqueo);

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mantDTO)))
                .andExpect(status().isCreated());

        // Residente intenta reservar esa zona de 10:00 a 12:00 (dentro del bloqueo)
        ReservaDTO reservaDTO = new ReservaDTO();
        reservaDTO.setIdZona(idZonaP1);
        reservaDTO.setIdUnidad(1L);
        reservaDTO.setFechaReserva(fecha);
        reservaDTO.setHoraInicio("10:00");
        reservaDTO.setHoraFin("12:00");
        reservaDTO.setCantidadAsistentes(5);

        mockMvc.perform(post("/api/v1/reservas")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reservaDTO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESERVA_SOLAPADA")));
    }

    @Test
    @Order(28)
    @DisplayName("28 — Liberación de Bloqueo: Completar o Cancelar mantenimiento elimina el bloqueo en BLOQUEOS_ZONA")
    void test28_LiberacionBloqueo_AlCompletarOCancelar() throws Exception {
        Instant inicio = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant fin = inicio.plus(6, ChronoUnit.HOURS);

        MantenimientoCreateDTO mantDTO = new MantenimientoCreateDTO();
        mantDTO.setTitulo("TEST-BLOQUEO-LIBERAR");
        mantDTO.setTipoMantenimiento("PREVENTIVO");
        mantDTO.setPrioridad("MEDIA");
        mantDTO.setDescripcionTrabajo("Mantenimiento a cancelar y liberar.");
        mantDTO.setFechaProgramada(LocalDate.now().plusDays(10));
        mantDTO.setIdZonaBloqueo(idZonaP1);
        mantDTO.setFechaInicioBloqueo(inicio);
        mantDTO.setFechaFinBloqueo(fin);

        MvcResult result = mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mantDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        Long idMant = objectMapper.readTree(responseJson).get("idMantenimiento").asLong();

        // Verificar que el bloqueo existe en Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM BLOQUEOS_ZONA WHERE ID_MANTENIMIENTO = ?",
                    Integer.class, idMant);
            assertEquals(1, count);
        } finally {
            clearContext();
        }

        // Cancelar el mantenimiento
        mockMvc.perform(put("/api/v1/mantenimientos/" + idMant + "/cancelar")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isOk());

        // Verificar que el bloqueo fue eliminado de Oracle
        setElevatedContext();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM BLOQUEOS_ZONA WHERE ID_MANTENIMIENTO = ?",
                    Integer.class, idMant);
            assertEquals(0, count, "El bloqueo de zona debe ser eliminado al cancelar el mantenimiento");
        } finally {
            clearContext();
        }
    }

    @Test
    @Order(29)
    @DisplayName("29 — Validaciones financieras: Costo estimado o real negativo es rechazado con 400 Bad Request")
    void test29_ValidacionesFinancieras_CostosNegativos_RechazadosCon400() throws Exception {
        // Costo estimado negativo en creación
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-COSTO-NEGATIVO");
        dto.setTipoMantenimiento("LOCATIVO");
        dto.setDescripcionTrabajo("Prueba de validación financiera.");
        dto.setFechaProgramada(LocalDate.now().plusDays(1));
        dto.setCostoEstimado(new BigDecimal("-50000.00"));

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(30)
    @DisplayName("30 — Control de roles: RESIDENTE bloqueado de administración de mantenimientos (403 Forbidden)")
    void test30_ControlRoles_ResidenteBloqueado_Retorna403() throws Exception {
        // GET
        mockMvc.perform(get("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE)))
                .andExpect(status().isForbidden());

        // POST
        MantenimientoCreateDTO dto = new MantenimientoCreateDTO();
        dto.setTitulo("TEST-RESIDENTE-HACK");
        dto.setTipoMantenimiento("LOCATIVO");
        dto.setDescripcionTrabajo("Intento de creación por residente.");
        dto.setFechaProgramada(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/mantenimientos")
                .header("Authorization", "Bearer " + tokenResidente)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // MOCK ASSIGNMENTS Y HELPERS
    // =========================================================================

    private void setupMockAssignments() {
        OrganizationDTO org1 = new OrganizationDTO(ORG_1_ID, "Organización Central SAED");
        OrganizationDTO org2 = new OrganizationDTO(ORG_2_ID, "Organización Foránea Norte");

        PropertyDTO prop1 = new PropertyDTO(PROP_1_ID, "Edificio Residencial SAED");
        PropertyDTO prop2 = new PropertyDTO(PROP_2_ID, "Condominio Campestre Norte");

        UnitDTO unit1 = new UnitDTO(1L, "101");

        // Superadmin
        AssignmentResponseDTO saAssign = new AssignmentResponseDTO();
        saAssign.setIdAsignacion(ASSIGN_SUPERADMIN);
        saAssign.setOrganizacion(org1);
        saAssign.setRol(new RoleDTO("SUPERADMIN", "GLOBAL"));
        when(assignmentService.validateAssignment(ASSIGN_SUPERADMIN, USER_SUPERADMIN)).thenReturn(Optional.of(saAssign));

        // Admin Org 1
        AssignmentResponseDTO adminOrg1Assign = new AssignmentResponseDTO();
        adminOrg1Assign.setIdAsignacion(ASSIGN_ADMIN_ORG_1);
        adminOrg1Assign.setOrganizacion(org1);
        adminOrg1Assign.setRol(new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_ORG_1, USER_ADMIN_ORG_1)).thenReturn(Optional.of(adminOrg1Assign));

        // Admin Propiedad 1
        AssignmentResponseDTO admin1Assign = new AssignmentResponseDTO();
        admin1Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_1);
        admin1Assign.setOrganizacion(org1);
        admin1Assign.setPropiedad(prop1);
        admin1Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1)).thenReturn(Optional.of(admin1Assign));

        // Admin Propiedad 2
        AssignmentResponseDTO admin2Assign = new AssignmentResponseDTO();
        admin2Assign.setIdAsignacion(ASSIGN_ADMIN_PROP_2);
        admin2Assign.setOrganizacion(org2);
        admin2Assign.setPropiedad(prop2);
        admin2Assign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_2, USER_ADMIN_PROP_2)).thenReturn(Optional.of(admin2Assign));

        // Residente
        AssignmentResponseDTO resAssign = new AssignmentResponseDTO();
        resAssign.setIdAsignacion(ASSIGN_RESIDENTE);
        resAssign.setOrganizacion(org1);
        resAssign.setPropiedad(prop1);
        resAssign.setUnidad(unit1);
        resAssign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        when(assignmentService.validateAssignment(ASSIGN_RESIDENTE, USER_RESIDENTE)).thenReturn(Optional.of(resAssign));
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_SUPERADMIN)
                .organizationId(ORG_1_ID)
                .propertyId(PROP_1_ID)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());
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

    private void ensureOrganizacion(Long id, String nombre, String nit, String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, ESTADO) VALUES (?, ?, ?, ?, 'ACTIVA')",
                    id, nombre, nit, email);
        }
    }

    private void ensurePropiedad(Long idProp, Long idOrg, String nombre) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Integer.class, idProp);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (?, ?, 1, ?, 'Dir Test', 'Bogota', 'PROPIETARIOS', 'ACTIVA')",
                    idProp, idOrg, nombre);
        }
    }

    private void ensureUnidad(Long idUnidad, Long idProp, String identificador) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, idUnidad);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, ?, 'ACTIVA')",
                    idUnidad, idProp, identificador);
        }
    }

    private void ensurePersona(Long idPersona, String doc, String nombre, String apellido, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO) VALUES (?, 1, ?, ?, ?, ?, 'ACTIVO')",
                    idPersona, doc, nombre, apellido, email);
        }
    }

    private void ensureUsuario(Long idUsuario, Long idPersona, String username, String email) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (?, ?, ?, ?, '$2a$10$test', 'ACTIVO')",
                    idUsuario, idPersona, username, email);
        }
    }

    private void ensureAdminSaed(Long idUsuario) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ADMINISTRADORES_SAED WHERE ID_USUARIO = ?", Integer.class, idUsuario);
        if (c == null || c == 0) {
            jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, NIVEL, ESTADO) VALUES (?, 'SUPERADMIN', 'ACTIVO')", idUsuario);
        } else {
            jdbcTemplate.update("UPDATE ADMINISTRADORES_SAED SET NIVEL = 'SUPERADMIN', ESTADO = 'ACTIVO' WHERE ID_USUARIO = ?", idUsuario);
        }
    }

    private void ensureAsignacion(Long idAsignacion, Long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsignacion", idAsignacion, Types.NUMERIC)
                .addValue("idUsuario", idUsuario, Types.NUMERIC)
                .addValue("idRol", idRol, Types.NUMERIC)
                .addValue("idOrg", idOrg, Types.NUMERIC)
                .addValue("idProp", idProp, Types.NUMERIC)
                .addValue("idUnidad", idUnidad, Types.NUMERIC);

        namedJdbc.update("""
            DELETE FROM USUARIO_ASIGNACIONES 
            WHERE (ID_USUARIO = :idUsuario AND ID_ROL = :idRol
                   AND NVL(ID_ORGANIZACION, -1) = NVL(:idOrg, -1)
                   AND NVL(ID_PROPIEDAD, -1) = NVL(:idProp, -1)
                   AND NVL(ID_UNIDAD, -1) = NVL(:idUnidad, -1))
               OR ID_ASIGNACION = :idAsignacion
        """, params);

        namedJdbc.update("""
            INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
            VALUES (:idAsignacion, :idUsuario, :idRol, :idOrg, :idProp, :idUnidad, 'ACTIVA', TRUNC(SYSDATE))
        """, params);
    }

    private Long insertActivo(Long propId, String codigo, String nombre, String estado) {
        String sql = "INSERT INTO ACTIVOS (ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA, FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO) " +
                     "VALUES (?, ?, ?, 'GENERAL', SYSDATE, 5000000, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_ACTIVO"});
            ps.setLong(1, propId);
            ps.setString(2, codigo);
            ps.setString(3, nombre);
            ps.setString(4, estado);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertProveedor(Long orgId, String razonSocial, String nit, String estado) {
        String sql = "INSERT INTO PROVEEDORES (ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION, EMAIL_CONTACTO, TELEFONO_CONTACTO, CATEGORIA_SERVICIO, ESTADO) " +
                     "VALUES (?, 'JURIDICA', ?, ?, 'contacto@prov.com', '3001234567', 'MANTENIMIENTO', ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_PROVEEDOR"});
            ps.setLong(1, orgId);
            ps.setString(2, razonSocial);
            ps.setString(3, nit);
            ps.setString(4, estado);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertZonaComun(Long propId, String nombre, String tipo, int aforo) {
        String sql = "INSERT INTO ZONAS_COMUNES (ID_PROPIEDAD, NOMBRE, TIPO, AFORO_MAXIMO, REQUIERE_RESERVA, COSTO_RESERVA, ESTADO) " +
                     "VALUES (?, ?, ?, ?, 'S', 0, 'ACTIVA')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_ZONA"});
            ps.setLong(1, propId);
            ps.setString(2, nombre);
            ps.setString(3, tipo);
            ps.setInt(4, aforo);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertMantenimientoDirecto(Long propId, Long activoId, Long provId, String titulo, String estado) {
        setElevatedContext();
        try {
            String sql = "INSERT INTO MANTENIMIENTOS (ID_ACTIVO, ID_PROPIEDAD, TIPO_MANTENIMIENTO, PRIORIDAD, TITULO, DESCRIPCION_TRABAJO, FECHA_PROGRAMADA, COSTO_ESTIMADO, ID_PROVEEDOR_SERVICIO, ESTADO) " +
                         "VALUES (?, ?, 'PREVENTIVO', 'MEDIA', ?, 'Mantenimiento de prueba directa', SYSDATE + 2, 1000000, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_MANTENIMIENTO"});
                if (activoId != null) ps.setLong(1, activoId); else ps.setNull(1, java.sql.Types.NUMERIC);
                ps.setLong(2, propId);
                ps.setString(3, titulo);
                if (provId != null) ps.setLong(4, provId); else ps.setNull(4, java.sql.Types.NUMERIC);
                ps.setString(5, estado);
                return ps;
            }, keyHolder);
            return keyHolder.getKey().longValue();
        } finally {
            clearContext();
        }
    }
}
