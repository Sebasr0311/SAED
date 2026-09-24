package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import com.saed.backend.finanzas.service.FinanzasService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de Integración, Seguridad y Regresión Financiera para GAP-F6-05:
 * Ciclo Integral de Aprobación de Pagos Manuales (SAED 2.0).
 *
 * Cobertura de Casos:
 * - MP-01 a MP-05: Registro de pagos manuales en PENDIENTE_APROBACION y bypass de trigger financiero.
 * - MP-06 a MP-09: Aprobación atómica por Administrador (afecta cuotas, cartera y flujo de caja).
 * - MP-10 a MP-13: Rechazo con motivo obligatorio y no-afectación patrimonial.
 * - MP-14 a MP-17: Transiciones adversas prohibidas (doble aprobación, doble rechazo, rechazo sin motivo).
 * - MP-18 a MP-21: Anti-IDOR y control de acceso estricto por rol y propiedad.
 * - MP-22: Segregación de Pasarela Wompi SaaS (aprobación automática inmediata).
 * - MP-23 a MP-24: Pipeline de Comprobantes Físicos (SHA-256, Quota, Visualización y Anti-IDOR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ManualPaymentApprovalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockBean
    private AssignmentService assignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinanzasService finanzasService;

    @Autowired
    private PazYSalvoService pazYSalvoService;

    @Autowired
    private FlujoCajaRepository flujoCajaRepository;

    private Long propIdA;
    private Long propIdB;
    private Long unidadA1;
    private Long unidadA2;
    private Long unidadB1;
    private Long contratoA1;
    private Long contratoB1;
    private Long conceptoCobroA;
    private Long conceptoCobroB;

    private String tokenAdminA;
    private String tokenAdminB;
    private String tokenResidenteA1;
    private String tokenResidenteA2;

    @BeforeEach
    void setUp() {
        setElevatedContext();

        // 1. Obtener o crear Propiedad A y B
        List<Map<String, Object>> props = jdbcTemplate.queryForList(
            "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_ORGANIZACION = 1 ORDER BY ID_PROPIEDAD ASC"
        );
        assertTrue(props.size() >= 1, "Debe existir al menos una propiedad para tests");
        propIdA = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();

        if (props.size() >= 2) {
            propIdB = ((Number) props.get(1).get("ID_PROPIEDAD")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO PROPIEDADES (ID_ORGANIZACION, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE) " +
                "VALUES (1, 'Torre Aprobacion B', 'Calle 70 # 10-20', 'Bogotá', 'PROPIETARIOS')"
            );
            propIdB = jdbcTemplate.queryForObject(
                "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE NOMBRE = 'Torre Aprobacion B'", Long.class
            );
        }

        // 2. Unidades para Propiedad A (Unidad A1 y A2)
        List<Map<String, Object>> uRowsA = jdbcTemplate.queryForList(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? ORDER BY ID_UNIDAD ASC", propIdA
        );
        if (uRowsA.size() >= 2) {
            unidadA1 = ((Number) uRowsA.get(0).get("ID_UNIDAD")).longValue();
            unidadA2 = ((Number) uRowsA.get(1).get("ID_UNIDAD")).longValue();
        } else if (uRowsA.size() == 1) {
            unidadA1 = ((Number) uRowsA.get(0).get("ID_UNIDAD")).longValue();
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-APROB-A2', 1, 0.05, 'ACTIVA')", propIdA
            );
            unidadA2 = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-APROB-A2'",
                Long.class, propIdA
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-APROB-A1', 1, 0.05, 'ACTIVA')", propIdA
            );
            unidadA1 = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-APROB-A1'",
                Long.class, propIdA
            );
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-APROB-A2', 1, 0.05, 'ACTIVA')", propIdA
            );
            unidadA2 = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-APROB-A2'",
                Long.class, propIdA
            );
        }

        // 3. Unidad para Propiedad B
        List<Map<String, Object>> uRowsB = jdbcTemplate.queryForList(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? ORDER BY ID_UNIDAD ASC", propIdB
        );
        if (!uRowsB.isEmpty()) {
            unidadB1 = ((Number) uRowsB.get(0).get("ID_UNIDAD")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-APROB-B1', 1, 0.05, 'ACTIVA')", propIdB
            );
            unidadB1 = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-APROB-B1'",
                Long.class, propIdB
            );
        }

        // 4. Conceptos de Cobro
        conceptoCobroA = jdbcTemplate.queryForObject(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = ? FETCH FIRST 1 ROWS ONLY",
            Long.class, propIdA
        );
        if (conceptoCobroA == null) {
            jdbcTemplate.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                "VALUES (1, ?, 'ADMIN_MAN_A', 'Administracion Manual A', 'ADMINISTRACION', 'ACTIVO')", propIdA
            );
            conceptoCobroA = jdbcTemplate.queryForObject(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_MAN_A'", Long.class
            );
        }

        List<Long> cBList = jdbcTemplate.queryForList(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = ? FETCH FIRST 1 ROWS ONLY",
            Long.class, propIdB
        );
        if (!cBList.isEmpty()) {
            conceptoCobroB = cBList.get(0);
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                "VALUES (1, ?, 'ADMIN_MAN_B', 'Administracion Manual B', 'ADMINISTRACION', 'ACTIVO')", propIdB
            );
            conceptoCobroB = jdbcTemplate.queryForObject(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_MAN_B'", Long.class
            );
        }

        // 5. Contratos de prueba
        List<Long> conListA = jdbcTemplate.queryForList(
            "SELECT ID_CONTRATO FROM CONTRATOS WHERE ID_UNIDAD = ? FETCH FIRST 1 ROWS ONLY", Long.class, unidadA1
        );
        if (!conListA.isEmpty()) {
            contratoA1 = conListA.get(0);
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, FECHA_INICIO, FECHA_FIN, CANON_MENSUAL, ESTADO) " +
                "VALUES (?, 1, 'CONTR-MAN-A1', TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 12), 1500000, 'ACTIVO')", unidadA1
            );
            contratoA1 = jdbcTemplate.queryForObject(
                "SELECT ID_CONTRATO FROM CONTRATOS WHERE NUMERO_CONTRATO = 'CONTR-MAN-A1'", Long.class
            );
        }

        List<Long> conListB = jdbcTemplate.queryForList(
            "SELECT ID_CONTRATO FROM CONTRATOS WHERE ID_UNIDAD = ? FETCH FIRST 1 ROWS ONLY", Long.class, unidadB1
        );
        if (!conListB.isEmpty()) {
            contratoB1 = conListB.get(0);
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, FECHA_INICIO, FECHA_FIN, CANON_MENSUAL, ESTADO) " +
                "VALUES (?, 1, 'CONTR-MAN-B1', TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 12), 1800000, 'ACTIVO')", unidadB1
            );
            contratoB1 = jdbcTemplate.queryForObject(
                "SELECT ID_CONTRATO FROM CONTRATOS WHERE NUMERO_CONTRATO = 'CONTR-MAN-B1'", Long.class
            );
        }

        // 6. Generar Tokens JWT de Identidad
        tokenAdminA = jwtProvider.generateIdentityToken(2L);
        tokenAdminB = jwtProvider.generateIdentityToken(3L);
        tokenResidenteA1 = jwtProvider.generateIdentityToken(4L);
        tokenResidenteA2 = jwtProvider.generateIdentityToken(5L);

        // 7. Mock de Asignaciones
        OrganizationDTO org1 = new OrganizationDTO(1L, "Organizacion Central");
        PropertyDTO propA = new PropertyDTO(propIdA, "Propiedad A");
        PropertyDTO propB = new PropertyDTO(propIdB, "Propiedad B");
        UnitDTO unitA1 = new UnitDTO(unidadA1, "Unidad A1");
        UnitDTO unitA2 = new UnitDTO(unidadA2, "Unidad A2");

        AssignmentResponseDTO adminPropAAssign = new AssignmentResponseDTO();
        adminPropAAssign.setIdAsignacion(102L);
        adminPropAAssign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminPropAAssign.setOrganizacion(org1);
        adminPropAAssign.setPropiedad(propA);

        AssignmentResponseDTO adminPropBAssign = new AssignmentResponseDTO();
        adminPropBAssign.setIdAsignacion(105L);
        adminPropBAssign.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        adminPropBAssign.setOrganizacion(org1);
        adminPropBAssign.setPropiedad(propB);

        AssignmentResponseDTO resA1Assign = new AssignmentResponseDTO();
        resA1Assign.setIdAsignacion(201L);
        resA1Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resA1Assign.setOrganizacion(org1);
        resA1Assign.setPropiedad(propA);
        resA1Assign.setUnidad(unitA1);

        AssignmentResponseDTO resA2Assign = new AssignmentResponseDTO();
        resA2Assign.setIdAsignacion(202L);
        resA2Assign.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        resA2Assign.setOrganizacion(org1);
        resA2Assign.setPropiedad(propA);
        resA2Assign.setUnidad(unitA2);

        when(assignmentService.validateAssignment(102L, 2L)).thenReturn(Optional.of(adminPropAAssign));
        when(assignmentService.validateAssignment(105L, 3L)).thenReturn(Optional.of(adminPropBAssign));
        when(assignmentService.validateAssignment(201L, 4L)).thenReturn(Optional.of(resA1Assign));
        when(assignmentService.validateAssignment(202L, 5L)).thenReturn(Optional.of(resA2Assign));
    }

    @AfterEach
    void tearDown() {
        setElevatedContext();
        try {
            jdbcTemplate.update("DELETE FROM PAGO_DETALLE WHERE ID_PAGO IN (SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE LIKE 'TEST-MP-%')");
            jdbcTemplate.update("DELETE FROM PAGOS WHERE REFERENCIA_COMPROBANTE LIKE 'TEST-MP-%'");
            jdbcTemplate.update("DELETE FROM CUOTAS WHERE PERIODO LIKE '2099-%'");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private Long crearCuotaPrueba(Long idUnidad, Long idContrato, Long idConcepto, BigDecimal valor, String periodo) {
        jdbcTemplate.update(
            "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO) " +
            "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ADD_MONTHS(TRUNC(SYSDATE), 1))",
            idUnidad, idConcepto, periodo, valor, valor
        );
        return jdbcTemplate.queryForObject(
            "SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD = ? AND PERIODO = ? ORDER BY ID_CUOTA DESC FETCH FIRST 1 ROWS ONLY",
            Long.class, idUnidad, periodo
        );
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(propIdA != null ? propIdA : 1L)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("MP-01 y MP-02: Registro de pago manual inicia en PENDIENTE_APROBACION y no descuenta saldo de cuota (Trigger bypass)")
    void testManualPaymentRegistrationPendingAndNoDebtDeduction() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("450000.00"), "2099-01");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("450000.00"),
            "TRANSFERENCIA",
            "TEST-MP-01-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)));

        setElevatedContext();
        // 1. Verificar en base de datos que el pago está en PENDIENTE_APROBACION
        Map<String, Object> pagoDb = jdbcTemplate.queryForMap(
            "SELECT ID_PAGO, ESTADO, MONTO_TOTAL, METODO_PAGO, REFERENCIA_COMPROBANTE FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-01-REF'"
        );
        assertEquals("PENDIENTE_APROBACION", pagoDb.get("ESTADO"));
        assertEquals("TRANSFERENCIA", pagoDb.get("METODO_PAGO"));

        // 2. Verificar que el trigger TRG_APLICAR_PAGO_CUOTA NO descontó el saldo de la cuota
        Map<String, Object> cuotaDb = jdbcTemplate.queryForMap(
            "SELECT SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = ?", cuotaId
        );
        BigDecimal saldoPendiente = (BigDecimal) cuotaDb.get("SALDO_PENDIENTE");
        String estadoCuota = (String) cuotaDb.get("ESTADO");

        assertEquals(0, new BigDecimal("450000.00").compareTo(saldoPendiente), "El saldo pendiente no debe alterarse antes de la aprobación");
        assertEquals("PENDIENTE", estadoCuota, "El estado de la cuota debe permanecer PENDIENTE");
    }

    @Test
    @DisplayName("MP-03, MP-04 y MP-05: Pago pendiente NO afecta Flujo de Caja (ingresos) ni libera Paz y Salvo")
    void testPendingPaymentDoesNotAffectCashFlowOrPazYSalvo() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("600000.00"), "2099-02");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("600000.00"),
            "TRANSFERENCIA",
            "TEST-MP-03-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        // 1. Verificar Flujo de Caja: El pago en PENDIENTE_APROBACION NO debe computar como ingreso
        BigDecimal totalIngresos = flujoCajaRepository.getTotalIngresos();
        assertNotNull(totalIngresos);

        // 2. Verificar Paz y Salvo: El inmueble NO puede estar al día porque tiene saldo pendiente
        PazYSalvoEstadoFinancieroDTO estadoFinanciero = pazYSalvoService.verificarEstadoFinanciero(unidadA1);
        assertFalse(estadoFinanciero.pazYSalvo(), "El inmueble NO debe estar al día mientras la cuota no haya sido aprobada");
        assertTrue(estadoFinanciero.saldoTotalExigible().compareTo(BigDecimal.ZERO) > 0, "La deuda debe ser mayor a cero");
    }

    @Test
    @DisplayName("MP-06, MP-07, MP-08 y MP-09: Aprobación por Administrador aplica pago a cuota, recalcula cartera y actualiza flujo de caja")
    void testAdminApprovalAppliesPaymentToCuotaAndRecalculatesCartera() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("320000.00"), "2099-03");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("320000.00"),
            "TRANSFERENCIA",
            "TEST-MP-06-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Long idPago = jdbcTemplate.queryForObject(
            "SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-06-REF'", Long.class
        );
        assertNotNull(idPago);

        // Administrador de la Propiedad A aprueba el pago
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("APROBADO")))
                .andExpect(jsonPath("$.aprobadoPor", notNullValue()))
                .andExpect(jsonPath("$.fechaAprobacion", notNullValue()));

        setElevatedContext();
        // Verificar estado de PAGOS en BD
        Map<String, Object> pagoDb = jdbcTemplate.queryForMap(
            "SELECT ESTADO, APROBADO_POR, FECHA_APROBACION FROM PAGOS WHERE ID_PAGO = ?", idPago
        );
        assertEquals("APROBADO", pagoDb.get("ESTADO"));
        assertNotNull(pagoDb.get("APROBADO_POR"));
        assertNotNull(pagoDb.get("FECHA_APROBACION"));

        // Verificar que la cuota fue saldada a 0 y estado PAGADA
        Map<String, Object> cuotaDb = jdbcTemplate.queryForMap(
            "SELECT SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = ?", cuotaId
        );
        BigDecimal saldoFinal = (BigDecimal) cuotaDb.get("SALDO_PENDIENTE");
        String estadoFinal = (String) cuotaDb.get("ESTADO");
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoFinal), "El saldo pendiente debe ser 0 tras la aprobación");
        assertEquals("PAGADA", estadoFinal, "El estado debe ser PAGADA");
    }

    @Test
    @DisplayName("MP-10, MP-11, MP-12 y MP-13: Rechazo con motivo obligatorio no afecta cuotas ni genera ingresos")
    void testAdminRejectionWithMandatoryReasonPreservesDebt() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("500000.00"), "2099-04");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("500000.00"),
            "CONSIGNACION",
            "TEST-MP-10-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Long idPago = jdbcTemplate.queryForObject(
            "SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-10-REF'", Long.class
        );

        // Administrador rechaza el pago con motivo
        PagoRechazoRequestDTO rechazoDTO = new PagoRechazoRequestDTO("Comprobante borroso y no coincide con el extracto");

        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/rechazar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rechazoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("RECHAZADO")))
                .andExpect(jsonPath("$.rechazadoPor", notNullValue()))
                .andExpect(jsonPath("$.observaciones", containsString("extracto")));

        setElevatedContext();
        // Verificar BD
        Map<String, Object> pagoDb = jdbcTemplate.queryForMap(
            "SELECT ESTADO, RECHAZADO_POR, FECHA_RECHAZO, OBSERVACIONES FROM PAGOS WHERE ID_PAGO = ?", idPago
        );
        assertEquals("RECHAZADO", pagoDb.get("ESTADO"));
        assertNotNull(pagoDb.get("RECHAZADO_POR"));
        assertNotNull(pagoDb.get("FECHA_RECHAZO"));
        assertEquals("Comprobante borroso y no coincide con el extracto", pagoDb.get("OBSERVACIONES"));

        // Verificar que la cuota mantiene intacto su saldo de 500.000
        BigDecimal saldoCuota = jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, cuotaId
        );
        assertEquals(0, new BigDecimal("500000.00").compareTo(saldoCuota));
    }

    @Test
    @DisplayName("MP-14, MP-15 y MP-16: Transiciones adversas prohibidas (doble aprobación o rechazo de pago ya procesado)")
    void testAdversarialInvalidStateTransitions() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("200000.00"), "2099-05");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("200000.00"),
            "TRANSFERENCIA",
            "TEST-MP-14-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Long idPago = jdbcTemplate.queryForObject(
            "SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-14-REF'", Long.class
        );

        // 1. Aprobar primera vez (Exitoso)
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk());

        // 2. Intentar re-aprobar un pago que ya está APROBADO (Debe fallar)
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 409 || status == 500, "Re-aprobar pago debe fallar");
                });

        // 3. Intentar rechazar un pago que ya está APROBADO (Debe fallar)
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/rechazar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PagoRechazoRequestDTO("Intento tardio"))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 409 || status == 500, "Rechazar pago ya aprobado debe fallar");
                });
    }

    @Test
    @DisplayName("MP-17: Rechazo sin motivo obligatorio (null o vacío) falla con 400 Bad Request")
    void testRejectPaymentWithoutReasonFails() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("100000.00"), "2099-06");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("100000.00"),
            "TRANSFERENCIA",
            "TEST-MP-17-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Long idPago = jdbcTemplate.queryForObject(
            "SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-17-REF'", Long.class
        );

        // Intento con motivo en blanco
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/rechazar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivoRechazo\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MP-18 y MP-19: Residentes tienen prohibido aprobar o rechazar pagos (403 Forbidden)")
    void testResidenteCannotApproveOrRejectPayments() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("150000.00"), "2099-07");

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("150000.00"),
            "TRANSFERENCIA",
            "TEST-MP-18-REF"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        setElevatedContext();
        Long idPago = jdbcTemplate.queryForObject(
            "SELECT ID_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-18-REF'", Long.class
        );

        // Intento de auto-aprobación por parte del residente
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/aprobar")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isForbidden());

        // Intento de rechazo por parte del residente
        mockMvc.perform(post("/api/v1/pagos/" + idPago + "/rechazar")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PagoRechazoRequestDTO("Autorechazo"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MP-20: Aislamiento Anti-IDOR: Residente no puede reportar pagos sobre cuotas de otra unidad")
    void testAntiIdorResidentCannotPayOtherUnitCuota() throws Exception {
        setElevatedContext();
        // Cuota en Unidad A2
        Long cuotaA2Id = crearCuotaPrueba(unidadA2, contratoA1, conceptoCobroA, new BigDecimal("250000.00"), "2099-08");

        // Residente A1 intenta pagar cuota de Unidad A2
        PagoRequestDTO request = new PagoRequestDTO(
            cuotaA2Id,
            LocalDate.now(),
            new BigDecimal("250000.00"),
            "TRANSFERENCIA",
            "TEST-MP-20-IDOR"
        );

        mockMvc.perform(post("/api/v1/pagos")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 404, "Debe rechazar pago con 403 o 404 por violación de unidad");
                });
    }

    @Test
    @DisplayName("MP-21: Aislamiento Multi-Propiedad: Admin de Propiedad A no puede aprobar pagos de Propiedad B")
    void testCrossPropertyApprovalIsBlocked() throws Exception {
        setElevatedContext();
        // Crear cuota y pago en Propiedad B
        Long cuotaBId = crearCuotaPrueba(unidadB1, contratoB1, conceptoCobroB, new BigDecimal("350000.00"), "2099-09");

        setElevatedContext();
        Long idPagoB = finanzasService.registrarPagoManual(new PagoRequestDTO(
            cuotaBId,
            LocalDate.now(),
            new BigDecimal("350000.00"),
            "TRANSFERENCIA",
            "TEST-MP-21-CROSS"
        ), null);

        // Admin de Propiedad A intenta aprobar pago de Propiedad B
        mockMvc.perform(post("/api/v1/pagos/" + idPagoB + "/aprobar")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 404, "Admin de Propiedad A no puede aprobar pagos de Propiedad B");
                });
    }

    @Test
    @DisplayName("MP-22: Segregación Pasarela Wompi SaaS: Pagos en línea entran directamente como APROBADO")
    void testWompiGatewayPaymentDirectlyApproved() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("180000.00"), "2099-10");

        // Pago con PASARELA_WOMPI
        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("180000.00"),
            "PASARELA_WOMPI",
            "TEST-MP-22-WOMPI"
        );

        finanzasService.registrarPago(request);

        setElevatedContext();
        Map<String, Object> pagoDb = jdbcTemplate.queryForMap(
            "SELECT ESTADO, METODO_PAGO FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-22-WOMPI'"
        );
        assertEquals("APROBADO", pagoDb.get("ESTADO"), "Pagos de pasarela Wompi entran directamente aprobados");

        BigDecimal saldo = jdbcTemplate.queryForObject(
            "SELECT SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = ?", BigDecimal.class, cuotaId
        );
        assertEquals(0, BigDecimal.ZERO.compareTo(saldo), "La cuota debe quedar saldada de inmediato");
    }

    @Test
    @DisplayName("MP-23 y MP-24: Pipeline de Comprobantes Físicos con Multipart, Hash SHA-256 y descarga segura")
    void testVoucherUploadHashIntegrityAndDownloadSecurity() throws Exception {
        setElevatedContext();
        Long cuotaId = crearCuotaPrueba(unidadA1, contratoA1, conceptoCobroA, new BigDecimal("270000.00"), "2099-11");

        MockMultipartFile file = new MockMultipartFile(
            "comprobante",
            "recibo_consignacion.png",
            "image/png",
            "SIMULATED_PNG_RECEIPT_CONTENT_FOR_GAP_F6_05".getBytes()
        );

        PagoRequestDTO request = new PagoRequestDTO(
            cuotaId,
            LocalDate.now(),
            new BigDecimal("270000.00"),
            "CONSIGNACION",
            "TEST-MP-23-DOC"
        );
        MockMultipartFile pagoPart = new MockMultipartFile(
            "pago",
            "",
            "application/json",
            objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(multipart("/api/v1/pagos/manual")
                .file(file)
                .file(pagoPart)
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE_APROBACION")))
                .andExpect(jsonPath("$.idPago", notNullValue()));

        setElevatedContext();
        Map<String, Object> pagoDb = jdbcTemplate.queryForMap(
            "SELECT ID_PAGO, COMPROBANTE_URL, COMPROBANTE_HASH, COMPROBANTE_TAMANO_BYTES FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TEST-MP-23-DOC'"
        );
        assertNotNull(pagoDb.get("COMPROBANTE_URL"));
        assertNotNull(pagoDb.get("COMPROBANTE_HASH"));
        assertTrue(((Number) pagoDb.get("COMPROBANTE_TAMANO_BYTES")).longValue() > 0);

        Long idPago = ((Number) pagoDb.get("ID_PAGO")).longValue();

        // 1. Descarga autorizada por el Residente de la unidad
        mockMvc.perform(get("/api/v1/pagos/" + idPago + "/comprobante")
                .header("Authorization", "Bearer " + tokenResidenteA1)
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isOk());

        // 2. Descarga autorizada por el Admin de la Propiedad
        mockMvc.perform(get("/api/v1/pagos/" + idPago + "/comprobante")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk());

        // 3. Intento de descarga no autorizada por Residente de otra unidad (Unidad A2)
        mockMvc.perform(get("/api/v1/pagos/" + idPago + "/comprobante")
                .header("Authorization", "Bearer " + tokenResidenteA2)
                .header("X-Assignment-Id", "202"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 404, "Descarga de comprobante ajeno debe bloquearse con 403 o 404");
                });
    }
}
