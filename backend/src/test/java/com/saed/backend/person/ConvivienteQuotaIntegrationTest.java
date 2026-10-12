package com.saed.backend.person;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.controller.DashboardController;
import com.saed.backend.identity.controller.UsuarioController;
import com.saed.backend.person.controller.UnitInhabitantController;
import com.saed.backend.person.dto.ConvivienteQuotaDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.dto.UpdateResidentStatusRequestDTO;
import com.saed.backend.person.exception.ConvivienteLimitExceededException;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConvivienteQuotaIntegrationTest {

    @Autowired
    private UnitInhabitantController unitInhabitantController;

    @Autowired
    private ConvivienteQuotaService convivienteQuotaService;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private UsuarioController usuarioController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TEST_PROP_ID = 1L;
    // Test-owned unit, tenants and resident user: the demo unit 1 and persons 1-4 are the seeded household/users and
    // must not be re-assigned or wiped by this suite.
    private static final Long TEST_UNIT_ID = 9958L;
    private static final Long OTHER_UNIT_ID = 2L;
    private static final Long PERSONA_TITULAR = 9974L;
    private static final Long PERSONA_CONV_1 = 9971L;
    private static final Long PERSONA_CONV_2 = 9972L;
    private static final Long PERSONA_CONV_3 = 9973L;
    private static final Long RESIDENT_USER_ID = 9981L;

    private String originalLimit;

    @BeforeEach
    public void setup() {
        setSuperadminAuth();

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("MERGE INTO ROLES r USING (SELECT 'RESIDENTE_CONVIVENCIA' AS CODIGO, 'Residente Conviviente' AS NOMBRE, 'UNIDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL) s ON (r.CODIGO = s.CODIGO) WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO)");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("ALTER TABLE RESIDENTES_UNIDAD DROP CONSTRAINT CK_RESIDUNIDAD_TIPO");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE RESIDENTES_UNIDAD ADD CONSTRAINT CK_RESIDUNIDAD_TIPO CHECK (tipo_residente IN ('PROPIETARIO', 'ARRENDATARIO', 'FAMILIAR', 'CONVIVIENTE', 'OTRO', 'TITULAR'))");
        } catch (Exception ignored) {}

        try {
            fixture();
        } catch (RuntimeException e) {
            throw new IllegalStateException("ConvivienteQuota fixture failed", e);
        }
        setAdminAuth();
    }

    private void fixture() {
        Integer unit = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, TEST_UNIT_ID);
        if (unit == null || unit == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, ?, 1, 'T-9958', 'ACTIVA')",
                    TEST_UNIT_ID, TEST_PROP_ID);
        }
        for (long id : new long[]{PERSONA_TITULAR, PERSONA_CONV_1, PERSONA_CONV_2, PERSONA_CONV_3, RESIDENT_USER_ID}) {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, id);
            if (c == null || c == 0) {
                jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO, ID_ORGANIZACION, ID_PROPIEDAD) "
                        + "VALUES (?, 1, ?, 'NATURAL', 'Cuota', 'Test', ?, 'ACTIVO', 1, 1)", id, "CQ" + id, "cq" + id + "@saed.com");
            }
        }
        // The application start-up clears the tenant columns of persons without a household link, so they are set on every run.
        jdbcTemplate.update("UPDATE PERSONAS SET ID_ORGANIZACION = 1, ID_PROPIEDAD = ?, ESTADO = 'ACTIVO' WHERE ID_PERSONA IN (?, ?, ?, ?, ?)",
                TEST_PROP_ID, PERSONA_TITULAR, PERSONA_CONV_1, PERSONA_CONV_2, PERSONA_CONV_3, RESIDENT_USER_ID);
        Integer usr = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?", Integer.class, RESIDENT_USER_ID);
        if (usr == null || usr == 0) {
            jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) "
                    + "VALUES (?, ?, 'cq_resident_9981', 'cq9981@saed.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')",
                    RESIDENT_USER_ID, RESIDENT_USER_ID);
        }
        // The resident's visibility comes from its own active assignment (V5.58: units are read from USUARIO_ASIGNACIONES).
        jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = ?", RESIDENT_USER_ID);
        jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) "
                + "VALUES (?, (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'), 1, ?, ?, 'ACTIVA', TRUNC(SYSDATE))",
                RESIDENT_USER_ID, TEST_PROP_ID, TEST_UNIT_ID);

        // Limpiar habitantes previos de la unidad de prueba para aislar ejecuciones
        jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ?", TEST_UNIT_ID);
        // Limite de 2 para TEST_PROP_ID para probar saturacion rapida y predecible (restored in tearDown)
        List<String> previous = jdbcTemplate.queryForList(
                "SELECT VALOR FROM PROPIEDAD_CONFIGURACION WHERE ID_PROPIEDAD = ? AND CLAVE = 'LIMITE_CONVIVIENTES_POR_UNIDAD'",
                String.class, TEST_PROP_ID);
        originalLimit = previous.isEmpty() ? null : previous.get(0);
        jdbcTemplate.update(
                "MERGE INTO PROPIEDAD_CONFIGURACION c " +
                "USING (SELECT ? AS id_propiedad, 'LIMITE_CONVIVIENTES_POR_UNIDAD' AS clave, '2' AS valor FROM DUAL) s " +
                "ON (c.ID_PROPIEDAD = s.id_propiedad AND c.CLAVE = s.clave) " +
                "WHEN MATCHED THEN UPDATE SET c.VALOR = s.valor " +
                "WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, CLAVE, VALOR, DESCRIPCION) VALUES (s.id_propiedad, s.clave, s.valor, 'Limite test')",
                TEST_PROP_ID
        );
    }

    @AfterEach
    public void tearDown() {
        try {
            setSuperadminAuth();
            jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ?", TEST_UNIT_ID);
            jdbcTemplate.update("DELETE FROM PROPIEDAD_CONFIGURACION WHERE ID_PROPIEDAD = ? AND CLAVE = 'LIMITE_CONVIVIENTES_POR_UNIDAD'", TEST_PROP_ID);
            if (originalLimit != null) {
                jdbcTemplate.update("INSERT INTO PROPIEDAD_CONFIGURACION (ID_PROPIEDAD, CLAVE, VALOR, DESCRIPCION) VALUES (?, 'LIMITE_CONVIVIENTES_POR_UNIDAD', ?, 'Restaurado')",
                        TEST_PROP_ID, originalLimit);
            }
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SecurityContextHolder.clearContext();
        SaedContextHolder.clearContext();
    }

    private void setSuperadminAuth() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(TEST_PROP_ID)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin_global",
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("SCOPE_SUPERADMIN"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"),
                                new SimpleGrantedAuthority("SCOPE_RESIDENTE")
                        )
                )
        );
    }

    /** DEC-02: the SUPERADMIN has no operational access; the suite acts as the demo property administrator (user 2, assignment 102). */
    private void setAdminAuth() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).organizationId(1L).propertyId(TEST_PROP_ID).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"))));
    }

    private void setResidentAuth(Long unitId) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(RESIDENT_USER_ID)
                .organizationId(1L)
                .propertyId(TEST_PROP_ID)
                .unitId(unitId)
                .roleCode("RESIDENTE")
                .roleScope("UNIDAD")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "residente_test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("SCOPE_RESIDENTE"))
                )
        );
    }

    @Test
    @Order(1)
    @DisplayName("P2-01 [REQ-01]: Consultar cuota inicial refleja límite configurado y cupos disponibles")
    public void test01_ConsultarQuota_Inicial() {
        ResponseEntity<ConvivienteQuotaDTO> response = unitInhabitantController.getQuota(TEST_UNIT_ID);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        ConvivienteQuotaDTO quota = response.getBody();
        assertNotNull(quota);
        assertEquals(TEST_UNIT_ID, quota.unitId());
        assertEquals(2, quota.limiteConfigurado());
        assertEquals(0, quota.convivientesActivos());
        assertEquals(2, quota.cuposDisponibles());
        assertFalse(quota.limiteAlcanzado());
    }

    @Test
    @Order(2)
    @DisplayName("P2-01 [REQ-02]: Residente principal (PROPIETARIO/ARRENDATARIO) NO consume cupo de conviviente")
    public void test02_PrincipalResident_DoesNotConsumeQuota() {
        // Registrar residente titular
        UnitResidentRequestDTO titularReq = new UnitResidentRequestDTO(PERSONA_TITULAR, "PROPIETARIO");
        ResponseEntity<Long> respTitular = unitInhabitantController.addResident(TEST_UNIT_ID, titularReq);
        assertEquals(201, respTitular.getStatusCode().value());

        // La cuota debe seguir en 0 convivientes activos
        ConvivienteQuotaDTO quota = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(0, quota.convivientesActivos(), "El propietario principal no debe sumar a la cuota de convivientes");
        assertEquals(2, quota.cuposDisponibles());
        assertFalse(quota.limiteAlcanzado());
    }

    @Test
    @Order(3)
    @DisplayName("P2-01 [REQ-03/04]: Registrar convivientes hasta el límite y rechazar excedente con HTTP 409")
    public void test03_RegisterConvivientes_EnforcesLimitAndRejectsExcess() {
        // Conviviente 1 (Persona 2)
        UnitResidentRequestDTO conv1 = new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE");
        ResponseEntity<Long> res1 = unitInhabitantController.addResident(TEST_UNIT_ID, conv1);
        assertEquals(201, res1.getStatusCode().value());

        ConvivienteQuotaDTO quotaAfter1 = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(1, quotaAfter1.convivientesActivos());
        assertEquals(1, quotaAfter1.cuposDisponibles());
        assertFalse(quotaAfter1.limiteAlcanzado());

        // Conviviente 2 (Persona 3) -> Límite 2 alcanzado
        UnitResidentRequestDTO conv2 = new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE");
        ResponseEntity<Long> res2 = unitInhabitantController.addResident(TEST_UNIT_ID, conv2);
        assertEquals(201, res2.getStatusCode().value());

        ConvivienteQuotaDTO quotaAfter2 = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(2, quotaAfter2.convivientesActivos());
        assertEquals(0, quotaAfter2.cuposDisponibles());
        assertTrue(quotaAfter2.limiteAlcanzado());

        // Conviviente 3 (Persona 4) -> Intento de exceder cupo
        UnitResidentRequestDTO conv3 = new UnitResidentRequestDTO(PERSONA_CONV_3, "CONVIVIENTE");
        ConvivienteLimitExceededException ex = assertThrows(
                ConvivienteLimitExceededException.class,
                () -> unitInhabitantController.addResident(TEST_UNIT_ID, conv3),
                "Debe lanzar ConvivienteLimitExceededException al intentar registrar superando el límite"
        );

        assertEquals(TEST_UNIT_ID, ex.getUnitId());
        assertEquals(2, ex.getLimit());
        assertEquals(2, ex.getCurrentCount());
    }

    @Test
    @Order(4)
    @DisplayName("P2-01 [REQ-05]: Rollback transaccional estricto: sin inserciones parciales al exceder límite")
    public void test04_TransactionRollback_NoPartialInsertOnExceededLimit() {
        // Insertar los 2 permitidos
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID
        );

        // Intentar registrar el 3.º
        assertThrows(
                ConvivienteLimitExceededException.class,
                () -> unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_3, "CONVIVIENTE"))
        );

        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID
        );

        assertEquals(countBefore, countAfter, "No debe haber ningún registro nuevo insertado tras el rechazo 409");
    }

    @Test
    @Order(5)
    @DisplayName("P2-01 [REQ-06/07]: Desactivar conviviente libera cupo y permite nuevo registro")
    public void test05_DeactivateConviviente_ReleasesQuotaAndAllowsNewRegistration() {
        // Llenar cupo (2 convivientes)
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        ResponseEntity<Long> r2 = unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));
        Long residentId2 = r2.getBody();

        // Desactivar conviviente 2
        ResponseEntity<Void> patchResp = unitInhabitantController.updateResidentStatus(
                TEST_UNIT_ID, residentId2, new UpdateResidentStatusRequestDTO("INACTIVO")
        );
        assertEquals(200, patchResp.getStatusCode().value());

        // Verificar que cupo se liberó
        ConvivienteQuotaDTO quotaAfterDeact = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(1, quotaAfterDeact.convivientesActivos());
        assertEquals(1, quotaAfterDeact.cuposDisponibles());
        assertFalse(quotaAfterDeact.limiteAlcanzado());

        // Ahora registrar Persona 4 en el cupo liberado debe ser exitoso
        ResponseEntity<Long> r4 = unitInhabitantController.addResident(
                TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_3, "CONVIVIENTE")
        );
        assertEquals(201, r4.getStatusCode().value());

        ConvivienteQuotaDTO quotaFinal = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(2, quotaFinal.convivientesActivos());
        assertEquals(0, quotaFinal.cuposDisponibles());
        assertTrue(quotaFinal.limiteAlcanzado());
    }

    @Test
    @Order(6)
    @DisplayName("P2-01 [REQ-08]: Reactivar conviviente con cupo lleno es rechazado con HTTP 409")
    public void test06_ReactivateConviviente_WhenQuotaFull_ThrowsConflict() {
        // Conviviente 1 (Persona 2)
        ResponseEntity<Long> r1 = unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        Long resident1Id = r1.getBody();

        // Desactivar conviviente 1 para tenerlo en estado INACTIVO
        unitInhabitantController.updateResidentStatus(TEST_UNIT_ID, resident1Id, new UpdateResidentStatusRequestDTO("INACTIVO"));

        // Llenar el cupo con otros 2 convivientes activos (Persona 3 y 4)
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_3, "CONVIVIENTE"));

        // Intentar reactivar Conviviente 1 cuando el cupo ya está en 2/2
        assertThrows(
                ConvivienteLimitExceededException.class,
                () -> unitInhabitantController.updateResidentStatus(TEST_UNIT_ID, resident1Id, new UpdateResidentStatusRequestDTO("ACTIVO")),
                "Reactivar un habitante cuando el cupo está lleno debe lanzar ConvivienteLimitExceededException"
        );
    }

    @Test
    @Order(7)
    @DisplayName("P2-01 [REQ-09]: Desvinculación de habitante (DELETE) libera cupo preservando trazabilidad")
    public void test07_SoftUnlinkConviviente_ReleasesQuota() {
        ResponseEntity<Long> r1 = unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));

        Long resident1Id = r1.getBody();

        // Desvincular habitante 1
        ResponseEntity<Void> delResp = unitInhabitantController.unlinkResident(TEST_UNIT_ID, resident1Id);
        assertEquals(204, delResp.getStatusCode().value());

        // Debe figurar como INACTIVO con FECHA_FIN en base de datos
        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT ESTADO, FECHA_FIN FROM RESIDENTES_UNIDAD WHERE ID_RESIDENTE_UNIDAD = ?",
                resident1Id
        );
        assertEquals("INACTIVO", record.get("ESTADO"));
        assertNotNull(record.get("FECHA_FIN"));

        // Cupo disponible incrementado
        ConvivienteQuotaDTO quota = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(1, quota.convivientesActivos());
        assertEquals(1, quota.cuposDisponibles());
        assertFalse(quota.limiteAlcanzado());
    }

    @Test
    @Order(8)
    @DisplayName("P2-01 [REQ-10]: Aislamiento multi-tenant: residente no puede operar ni ver cuota de otra unidad")
    public void test08_CrossUnitAccess_ResidentCannotAccessAnotherUnit() {
        // Residente autenticado en TEST_UNIT_ID (1L)
        setResidentAuth(TEST_UNIT_ID);

        // Intento de consultar cuota de OTHER_UNIT_ID (2L) -> 403 Forbidden
        assertThrows(
                AccessDeniedException.class,
                () -> unitInhabitantController.getQuota(OTHER_UNIT_ID)
        );

        // Intento de agregar conviviente a OTHER_UNIT_ID -> 403 Forbidden
        assertThrows(
                AccessDeniedException.class,
                () -> unitInhabitantController.addResident(OTHER_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"))
        );

        // Intento de alterar estado en OTHER_UNIT_ID -> 403 Forbidden
        assertThrows(
                AccessDeniedException.class,
                () -> unitInhabitantController.updateResidentStatus(OTHER_UNIT_ID, 999L, new UpdateResidentStatusRequestDTO("INACTIVO"))
        );
    }

    @Test
    @Order(9)
    @DisplayName("P2-01 [REQ-11]: Residente en su propia unidad puede consultar y registrar conviviente")
    public void test09_ResidentCanRegisterConvivienteInOwnUnit() {
        setResidentAuth(TEST_UNIT_ID);

        // Residente consulta cuota de su propia unidad -> éxito
        ResponseEntity<ConvivienteQuotaDTO> quotaRes = unitInhabitantController.getQuota(TEST_UNIT_ID);
        assertEquals(200, quotaRes.getStatusCode().value());
        assertNotNull(quotaRes.getBody());

        // Residente agrega conviviente a su propia unidad -> éxito
        ResponseEntity<Long> addRes = unitInhabitantController.addResident(
                TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE")
        );
        assertEquals(201, addRes.getStatusCode().value());
    }

    @Test
    @Order(10)
    @DisplayName("P2-01 [REQ-12]: Blindaje contra bypass directo vía DashboardController.asignarApartamento")
    public void test10_DirectHttpBypass_DashboardController_EnforcesLimit() {
        // Llenar el cupo
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));

        // Intentar bypass vía dashboardController.asignarApartamento
        Map<String, Object> payload = Map.of(
                "idApartamento", TEST_UNIT_ID,
                "tipoRelacion", "CONVIVIENTE"
        );

        assertThrows(
                ConvivienteLimitExceededException.class,
                () -> dashboardController.asignarApartamento(PERSONA_CONV_3, payload),
                "dashboardController.asignarApartamento debe validar cuota y rechazar si se excede el límite"
        );
    }

    @Test
    @Order(11)
    @DisplayName("P2-01 [REQ-13]: Blindaje contra bypass directo vía UsuarioController.crearUsuario")
    public void test11_DirectHttpBypass_UsuarioController_EnforcesLimit() {
        // Llenar el cupo
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_1, "CONVIVIENTE"));
        unitInhabitantController.addResident(TEST_UNIT_ID, new UnitResidentRequestDTO(PERSONA_CONV_2, "CONVIVIENTE"));

        // UserRegistrationPolicy.authorizePerson: the target person must already belong to the caller's property
        // (a non-conviviente household link, which does not consume quota).
        jdbcTemplate.update("INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) VALUES (?, ?, 'FAMILIAR', TRUNC(SYSDATE), 'ACTIVO')",
                TEST_UNIT_ID, PERSONA_CONV_3);

        // Intentar crear un usuario con rol RESIDENTE_CONVIVENCIA para TEST_UNIT_ID
        String testUser = "conv_byp_" + System.currentTimeMillis();
        Map<String, Object> payload = Map.of(
                "username", testUser,
                "password", "Password123!",
                "email", testUser + "@test.com",
                "idPersona", PERSONA_CONV_3,
                "idOrganizacion", 1L,
                "idPropiedad", TEST_PROP_ID,
                "idUnidad", TEST_UNIT_ID,
                "rol", "RESIDENTE_CONVIVENCIA"
        );

        assertThrows(
                ConvivienteLimitExceededException.class,
                () -> usuarioController.crearUsuario(payload),
                "usuarioController.crearUsuario debe validar cuota y rechazar si el cupo está agotado"
        );
    }

    @Test
    @Order(12)
    @DisplayName("P2-01 [REQ-14]: Fallback automático de límite (4) cuando la propiedad no tiene configuración explícita")
    public void test12_FallbackLimit_UsedWhenNoPropertyConfig() {
        // Eliminar configuración específica de límite
        jdbcTemplate.update(
                "DELETE FROM PROPIEDAD_CONFIGURACION WHERE ID_PROPIEDAD = ? AND CLAVE = 'LIMITE_CONVIVIENTES_POR_UNIDAD'",
                TEST_PROP_ID
        );

        ConvivienteQuotaDTO quota = convivienteQuotaService.getQuota(TEST_UNIT_ID);
        assertEquals(4, quota.limiteConfigurado(), "Debe aplicar fallback por defecto de 4 convivientes por unidad");
        assertEquals(4, quota.cuposDisponibles());
        assertFalse(quota.limiteAlcanzado());
    }

    @Test
    @Order(13)
    @DisplayName("P2-01 [REQ-15]: Residente titular puede registrar exitosamente cuenta de conviviente vía UsuarioController sin error RLS")
    public void test13_ResidentCanCreateConvivienteAccountSuccessfully() {
        setResidentAuth(TEST_UNIT_ID);

        String testUsername = "conv_ok_" + System.currentTimeMillis();
        String testEmail = testUsername + "@saedtest.com";
        String testDoc = "CC" + (System.currentTimeMillis() % 1000000000);

        Map<String, Object> payload = Map.of(
                "nombreUsuario", testUsername,
                "password", "Password123!",
                "email", testEmail,
                "primerNombre", "Carlos",
                "primerApellido", "Conviviente",
                "tipoDocumentoId", 1L,
                "numeroDocumento", testDoc,
                "telefono", "3001234567",
                "rol", "RESIDENTE_CONVIVENCIA",
                "idUnidad", TEST_UNIT_ID
        );

        ResponseEntity<com.saed.backend.common.dto.ApiResponse<Map<String, Object>>> response =
                usuarioController.crearUsuario(payload);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value(), () -> "Error response: " + (response.getBody() != null ? response.getBody().getMessage() : "null"));
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().getStatus());

        Map<String, Object> data = response.getBody().getData();
        assertNotNull(data);
        assertNotNull(data.get("idUsuario"));
        assertEquals(testUsername, data.get("username"));
        assertEquals("RESIDENTE_CONVIVENCIA", data.get("rol"));

        // Verificar que el residente puede ver el habitante en su unidad
        Integer ruCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND TIPO_RESIDENTE = 'CONVIVIENTE'",
                Integer.class,
                TEST_UNIT_ID
        );
        assertTrue(ruCount != null && ruCount >= 1, "Debe existir registro activo en RESIDENTES_UNIDAD para la unidad");

        // Y bajo SUPERADMIN verificar que la cuenta de usuario se persistió en USUARIOS
        setSuperadminAuth();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        Number idUsuario = (Number) data.get("idUsuario");
        Integer usrCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIOS WHERE ID_USUARIO = ?",
                Integer.class,
                idUsuario.longValue()
        );
        assertEquals(1, usrCount, "El usuario debe existir en USUARIOS");
    }

    @Test
    @Order(14)
    @DisplayName("P2-02 [REQ-15-REAC]: Residente NO reactiva por registro un conviviente inactivo (política de registro)")
    public void test14_ResidentCannotReactivateInactiveConvivienteByRegistration() {
        setResidentAuth(TEST_UNIT_ID);

        String testUsername = "reac_" + System.currentTimeMillis();
        String testEmail = testUsername + "@saedtest.com";
        String testDoc = "DOC" + (System.currentTimeMillis() % 1000000000);

        Map<String, Object> payload = Map.of(
                "nombreUsuario", testUsername,
                "password", "Password123!",
                "email", testEmail,
                "primerNombre", "Juan",
                "primerApellido", "Conviviente",
                "tipoDocumentoId", 1L,
                "numeroDocumento", testDoc,
                "telefono", "3007654321",
                "rol", "RESIDENTE_CONVIVENCIA",
                "idUnidad", TEST_UNIT_ID
        );

        // 1. Crear por primera vez
        ResponseEntity<com.saed.backend.common.dto.ApiResponse<Map<String, Object>>> resp1 =
                usuarioController.crearUsuario(payload);
        assertNotNull(resp1);
        assertEquals(201, resp1.getStatusCode().value());
        Number idUsuario1 = (Number) resp1.getBody().getData().get("idUsuario");
        assertNotNull(idUsuario1);

        // 2. Simular eliminación/desvinculación del conviviente de la unidad
        setSuperadminAuth();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        jdbcTemplate.update("UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_UNIDAD = ? AND TIPO_RESIDENTE = 'CONVIVIENTE'", TEST_UNIT_ID);
        jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = ?", idUsuario1.longValue());
        jdbcTemplate.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'INACTIVA' WHERE ID_USUARIO = ?", idUsuario1.longValue());

        // 3. Registration never reactivates an inactive identity (UserRegistrationPolicy.authorizeAccount,
        //    F-series registration hardening; UserRegistrationAuthorizationTest.registrationCannotReactivateInactiveIdentity):
        //    the resident is refused and an administrator must reactivate the account.
        setResidentAuth(TEST_UNIT_ID);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> usuarioController.crearUsuario(payload));

        // 4. The refused attempt changed nothing: the account stays inactive and no assignment is active.
        setSuperadminAuth();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        String userEstado = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM USUARIOS WHERE ID_USUARIO = ?", String.class, idUsuario1.longValue());
        assertEquals("INACTIVO", userEstado, "El usuario inactivo no se reactiva por registro");
        Integer activeAsigs = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = ? AND ESTADO = 'ACTIVA' AND ID_UNIDAD = ?",
                Integer.class, idUsuario1.longValue(), TEST_UNIT_ID);
        assertEquals(0, activeAsigs, "No debe haber asignación ACTIVA tras el intento rechazado");
    }
}
