package com.saed.backend.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Certificación Rigurosa para GAP-F8-08:
 * Banner Preventivo de Mora Financiera en SAED 2.0 (Oracle 23ai / XE).
 *
 * Cobertura de Aserciones Deterministas:
 * - F8-08-01 / F8-08-03: Consulta segura de estado financiero sin mora -> pazYSalvo = true, enMora = false.
 * - F8-08-01 / F8-08-03: Consulta segura de estado financiero con mora -> pazYSalvo = false, enMora = true, saldoTotalExigible > 0.
 * - F8-08-04: Transición de estado: liquidación/pago de deuda -> desaparece mora (enMora = false, pazYSalvo = true).
 * - F8-08-05: Anti-IDOR: Residente intentando consultar otra unidad -> 403 Forbidden.
 * - F8-08-05: Inmunidad a Parameter Tampering: query params/body ignorados, solo rige el contexto seguro.
 * - F8-08-05: Rol Conviviente (RESIDENTE_CONVIVENCIA) hereda estado de su unidad y es bloqueado ante otras unidades.
 * - F8-08-05: Usuario sin unidad asignada en contexto -> 403 Forbidden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase8MoraBannerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinanzasRepository finanzasRepository;

    // Identidades y Roles
    private static final long USER_SUPERADMIN = 1L;
    private static final long ASSIGN_SUPERADMIN = 101L;

    private static final long USER_ADMIN_PROP_1 = 2L;
    private static final long ASSIGN_ADMIN_PROP_1 = 102L;

    private static final long USER_RESIDENTE_2 = 5L; // Unidad 2 (Apto 102)
    private static final long ASSIGN_RESIDENTE_2 = 105L;

    private static final long USER_RESIDENTE_3 = 7L; // Unidad 3 (Apto 701)
    private static final long ASSIGN_RESIDENTE_3 = 107L;

    private static final long USER_CONVIVIENTE_2 = 8L; // Conviviente Unidad 2
    private static final long ASSIGN_CONVIVIENTE_2 = 108L;

    // Organizaciones, Propiedades y Unidades
    private static final long ORG_1_ID = 1L;
    private static final long PROP_1_ID = 1L;
    private static final long UNIT_2_ID = 2L;
    private static final long UNIT_3_ID = 701L;

    private String tokenAdminProp1;
    private String tokenResidente2;
    private String tokenResidente3;
    private String tokenConviviente2;

    @BeforeEach
    public void setUp() {
        setElevatedContext();
        try {
            // Limpieza de estados financieros previos de prueba
            jdbcTemplate.execute("""
                BEGIN
                    DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (2, 701));
                    DELETE FROM MULTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CUOTAS WHERE ID_UNIDAD IN (2, 701);
                    DELETE FROM CARTERA WHERE ID_UNIDAD IN (2, 701);
                END;
            """);

            // Entidades base
            ensureOrganizacion(ORG_1_ID, "Organización Central SAED", "900000001-1", "org1@saed.com");
            ensurePropiedad(PROP_1_ID, ORG_1_ID, "Edificio Residencial SAED");
            ensureUnidad(UNIT_2_ID, PROP_1_ID, "Apto 102");
            ensureUnidad(UNIT_3_ID, PROP_1_ID, "Apto 701");

            ensureConceptoCobro(1L, ORG_1_ID, PROP_1_ID);

            ensurePersona(USER_SUPERADMIN, "1000000001", "Super", "Admin", "superadmin@saed.com");
            ensureUsuario(USER_SUPERADMIN, USER_SUPERADMIN, "superadmin", "superadmin@saed.com");

            ensurePersona(USER_ADMIN_PROP_1, "1000000002", "Admin", "Propiedad", "admin@saed.com");
            ensureUsuario(USER_ADMIN_PROP_1, USER_ADMIN_PROP_1, "admin", "admin@saed.com");

            ensurePersona(USER_RESIDENTE_2, "1000000005", "Ana", "Gomez", "anagomez@saed.com");
            ensureUsuario(USER_RESIDENTE_2, USER_RESIDENTE_2, "ana_g", "anagomez@saed.com");

            ensurePersona(USER_RESIDENTE_3, "1000000007", "Maria", "Rodriguez", "mrodriguez@saed.com");
            ensureUsuario(USER_RESIDENTE_3, USER_RESIDENTE_3, "maria_r", "mrodriguez@saed.com");

            ensurePersona(USER_CONVIVIENTE_2, "1000000008", "Carlos", "Gomez", "carlosgomez@saed.com");
            ensureUsuario(USER_CONVIVIENTE_2, USER_CONVIVIENTE_2, "carlos_g", "carlosgomez@saed.com");

            // Asignaciones con cumplimiento estricto del disparador TRG_ASIGNACION_VALIDA_SCOPE
            ensureAsignacion(ASSIGN_SUPERADMIN, USER_SUPERADMIN, "SUPERADMIN", null, null, null);
            ensureAsignacion(ASSIGN_ADMIN_PROP_1, USER_ADMIN_PROP_1, "ADMIN_PROPIEDAD", ORG_1_ID, PROP_1_ID, null);
            ensureAsignacion(ASSIGN_RESIDENTE_2, USER_RESIDENTE_2, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_2_ID);
            ensureAsignacion(ASSIGN_RESIDENTE_3, USER_RESIDENTE_3, "RESIDENTE", ORG_1_ID, PROP_1_ID, UNIT_3_ID);
            ensureAsignacion(ASSIGN_CONVIVIENTE_2, USER_CONVIVIENTE_2, "RESIDENTE_CONVIVENCIA", ORG_1_ID, PROP_1_ID, UNIT_2_ID);

            ensureResidenteUnidad(UNIT_2_ID, USER_RESIDENTE_2, "PROPIETARIO");
            ensureResidenteUnidad(UNIT_3_ID, USER_RESIDENTE_3, "PROPIETARIO");
            ensureResidenteUnidad(UNIT_2_ID, USER_CONVIVIENTE_2, "CONVIVIENTE");

            // Unidad 2: Al día (sin mora)
            finanzasRepository.recalcularCarteraUnidad(UNIT_2_ID);

            // Generación de tokens JWT
            tokenAdminProp1 = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_1);
            tokenResidente2 = jwtProvider.generateIdentityToken(USER_RESIDENTE_2);
            tokenResidente3 = jwtProvider.generateIdentityToken(USER_RESIDENTE_3);
            tokenConviviente2 = jwtProvider.generateIdentityToken(USER_CONVIVIENTE_2);

        } finally {
            clearContext();
        }
    }

    @AfterEach
    public void tearDown() {
        clearContext();
    }

    // =========================================================================
    // CASO 1: Residente sin mora -> pazYSalvo = true, enMora = false
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("F8-08-01/03 [1/7]: Residente al día consulta mi-estado-financiero -> pazYSalvo=true, enMora=false")
    void test01_ResidenteSinMora_ObtieneEstadoAlDia() throws Exception {
        // 1. Endpoint en módulo finanzas (/paz-y-salvos/mi-estado-financiero)
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.idUnidad").value(UNIT_2_ID))
                .andExpect(jsonPath("$.data.identificadorUnidad").value("Apto 102"))
                .andExpect(jsonPath("$.data.pazYSalvo").value(true))
                .andExpect(jsonPath("$.data.enMora").value(false))
                .andExpect(jsonPath("$.data.motivosBloqueo", hasSize(0)));

        // 2. Endpoint en módulo reservas (/reservas/mi-estado-mora)
        mockMvc.perform(get("/api/v1/reservas/mi-estado-mora")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.idUnidad").value(UNIT_2_ID))
                .andExpect(jsonPath("$.data.pazYSalvo").value(true))
                .andExpect(jsonPath("$.data.enMora").value(false));
    }

    // =========================================================================
    // CASO 2: Residente con mora -> pazYSalvo = false, enMora = true, saldo > 0
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("F8-08-01/03 [2/7]: Residente con mora consulta mi-estado-financiero -> pazYSalvo=false, enMora=true")
    void test02_ResidenteConMora_ObtieneEstadoMoraActiva() throws Exception {
        setElevatedContext();
        try {
            // Insertar cuota vencida para Unidad 3
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_CUOTA, ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
                VALUES (9901, ?, 1, '2026-08', 150000.00, 150000.00, 'VENCIDA', TRUNC(SYSDATE - 15))
            """, UNIT_3_ID);
            finanzasRepository.recalcularCarteraUnidad(UNIT_3_ID);
        } finally {
            clearContext();
        }

        // Consultar estado financiero de la unidad 3
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.idUnidad").value(UNIT_3_ID))
                .andExpect(jsonPath("$.data.identificadorUnidad").value("Apto 701"))
                .andExpect(jsonPath("$.data.pazYSalvo").value(false))
                .andExpect(jsonPath("$.data.enMora").value(true))
                .andExpect(jsonPath("$.data.saldoTotalExigible").value(150000.00))
                .andExpect(jsonPath("$.data.motivosBloqueo", hasSize(greaterThanOrEqualTo(1))));

        // También vía /reservas/mi-estado-mora
        mockMvc.perform(get("/api/v1/reservas/mi-estado-mora")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.enMora").value(true))
                .andExpect(jsonPath("$.data.pazYSalvo").value(false));
    }

    // =========================================================================
    // CASO 3: Anti-IDOR: Residente intentando consultar otra unidad -> 403 Forbidden
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("F8-08-05 [3/7]: Anti-IDOR: Residente de Unidad 2 consulta Unidad 3 -> 403 Forbidden")
    void test03_AntiIdor_ResidenteNoPuedeConsultarOtraUnidad() throws Exception {
        mockMvc.perform(get("/api/v1/paz-y-salvos/unidad/" + UNIT_3_ID + "/estado-financiero")
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CASO 4: Parameter Tampering: query params/body ignorados, rige solo contexto
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("F8-08-05 [4/7]: Parameter Tampering: idUnidad en query param es ignorado, rige contexto")
    void test04_ParameterTamperingIgnorado_RigeContextoSeguro() throws Exception {
        // Residente 2 envía ?idUnidad=701 intentando espiar Unidad 3
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero?idUnidad=" + UNIT_3_ID + "&unitId=" + UNIT_3_ID)
                .header("Authorization", "Bearer " + tokenResidente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_2))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.idUnidad").value(UNIT_2_ID))
                .andExpect(jsonPath("$.data.identificadorUnidad").value("Apto 102"));
    }

    // =========================================================================
    // CASO 5: Transición de estado: liquidación de deuda -> desaparece mora
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("F8-08-04 [5/7]: Transición de estado: Pago de deuda elimina mora dinámicamente")
    void test05_TransicionEstado_PagoDeuda_EliminaMora() throws Exception {
        setElevatedContext();
        try {
            // 1. Unidad 3 entra en mora
            jdbcTemplate.update("""
                INSERT INTO CUOTAS (ID_CUOTA, ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO)
                VALUES (9902, ?, 1, '2026-08', 80000.00, 80000.00, 'VENCIDA', TRUNC(SYSDATE - 5))
            """, UNIT_3_ID);
            finanzasRepository.recalcularCarteraUnidad(UNIT_3_ID);
        } finally {
            clearContext();
        }

        // Verificar que está en mora
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.enMora").value(true));

        // 2. Simular pago completo de la cuota (liquidación de cartera)
        setElevatedContext();
        try {
            jdbcTemplate.update("""
                UPDATE CUOTAS SET ESTADO = 'PAGADA', SALDO_PENDIENTE = 0
                WHERE ID_CUOTA = 9902
            """);
            finanzasRepository.recalcularCarteraUnidad(UNIT_3_ID);
        } finally {
            clearContext();
        }

        // 3. Re-consultar: La mora desapareció -> banner debe ocultarse
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenResidente3)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_RESIDENTE_3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.pazYSalvo").value(true))
                .andExpect(jsonPath("$.data.enMora").value(false))
                .andExpect(jsonPath("$.data.motivosBloqueo", hasSize(0)));
    }

    // =========================================================================
    // CASO 6: Conviviente hereda estado de su unidad y es bloqueado ante otras
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("F8-08-05 [6/7]: Conviviente hereda estado de su unidad y es bloqueado ante otra unidad")
    void test06_Conviviente_HeredaEstadoDeSuUnidad() throws Exception {
        // Conviviente de Unidad 2 consulta su unidad (al día)
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenConviviente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.idUnidad").value(UNIT_2_ID))
                .andExpect(jsonPath("$.data.pazYSalvo").value(true))
                .andExpect(jsonPath("$.data.enMora").value(false));

        // Conviviente intenta consultar Unidad 3 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/paz-y-salvos/unidad/" + UNIT_3_ID + "/estado-financiero")
                .header("Authorization", "Bearer " + tokenConviviente2)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_CONVIVIENTE_2)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CASO 7: Contexto sin unidad asignada (ej. Admin Propiedad) -> 403 Forbidden
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("F8-08-05 [7/7]: Contexto sin unidad asignada (ej. Admin Propiedad) -> 403 Forbidden")
    void test07_ContextoSinUnidad_RechazadoConForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/paz-y-salvos/mi-estado-financiero")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reservas/mi-estado-mora")
                .header("Authorization", "Bearer " + tokenAdminProp1)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_1)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // HELPERS DE INFRAESTRUCTURA DE PRUEBA
    // =========================================================================

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
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) " +
                    "VALUES (?, ?, 1, ?, 'ACTIVA')", id, idProp, identificador);
        }
    }

    private void ensureConceptoCobro(Long id, Long idOrg, Long idProp) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CONCEPTOS_COBRO WHERE ID_CONCEPTO = ?", Integer.class, id);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO)
                VALUES (?, ?, ?, 'CUOTA_ADMIN', 'Cuota de Administracion', 'ADMINISTRACION', 'ACTIVO')
            """, id, idOrg, idProp);
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

    private void ensureAsignacion(Long idAsignacion, Long idUsuario, String rolCodigo, Long idOrg, Long idProp, Long idUnidad) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, idAsignacion);
        Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, rolCodigo);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))
            """, idAsignacion, idUsuario, idRol, idOrg, idProp, idUnidad);
        } else {
            jdbcTemplate.update("""
                UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = ?, ID_ORGANIZACION = ?, ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA'
                WHERE ID_ASIGNACION = ?
            """, idUsuario, idRol, idOrg, idProp, idUnidad, idAsignacion);
        }
    }

    private void ensureResidenteUnidad(Long idUnidad, Long idPersona, String tipoResidente) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ?", Integer.class, idUnidad, idPersona);
        if (c == null || c == 0) {
            jdbcTemplate.update("""
                INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO)
                VALUES (?, ?, ?, TRUNC(SYSDATE), 'ACTIVO')
            """, idUnidad, idPersona, tipoResidente);
        } else {
            jdbcTemplate.update("""
                UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = ?, ESTADO = 'ACTIVO'
                WHERE ID_UNIDAD = ? AND ID_PERSONA = ?
            """, tipoResidente, idUnidad, idPersona);
        }
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
}
