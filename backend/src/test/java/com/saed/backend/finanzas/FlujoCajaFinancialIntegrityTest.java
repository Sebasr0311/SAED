package com.saed.backend.finanzas;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.FlujoCajaResumenDTO;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import com.saed.backend.finanzas.service.FlujoCajaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class FlujoCajaFinancialIntegrityTest {

    @Autowired
    private FlujoCajaRepository repository;

    @Autowired
    private FlujoCajaService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testPropIdA;
    private Long testPropIdB;
    private Long testUnidadA;
    private Long testUnidadB;
    private Long testConceptoA;
    private Long testConceptoB;

    @BeforeEach
    void setUp() {
        setElevatedContext(1L, 1L, "SUPERADMIN");

        // Ensure Property A exists
        List<Map<String, Object>> props = jdbcTemplate.queryForList(
            "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_ORGANIZACION = 1 ORDER BY ID_PROPIEDAD ASC"
        );
        if (props.size() >= 2) {
            testPropIdA = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
            testPropIdB = ((Number) props.get(1).get("ID_PROPIEDAD")).longValue();
        } else if (props.size() == 1) {
            testPropIdA = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
            jdbcTemplate.update(
                "INSERT INTO PROPIEDADES (ID_ORGANIZACION, NOMBRE, DIRECCION, CIUDAD, TIPO_OCUPACION_PREDOMINANTE) " +
                "VALUES (1, 'Torre Beta Test', 'Calle 50 # 20-10', 'Bogotá', 'PROPIETARIOS')"
            );
            testPropIdB = jdbcTemplate.queryForObject(
                "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE NOMBRE = 'Torre Beta Test'", Long.class
            );
        }

        // Ensure Unit in Property A
        List<Map<String, Object>> unitsA = jdbcTemplate.queryForList(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? ORDER BY ID_UNIDAD ASC", testPropIdA
        );
        if (!unitsA.isEmpty()) {
            testUnidadA = ((Number) unitsA.get(0).get("ID_UNIDAD")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-TEST-A1', 1, 0.05, 'ACTIVA')", testPropIdA
            );
            testUnidadA = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-TEST-A1'",
                Long.class, testPropIdA
            );
        }

        // Ensure Unit in Property B
        List<Map<String, Object>> unitsB = jdbcTemplate.queryForList(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? ORDER BY ID_UNIDAD ASC", testPropIdB
        );
        if (!unitsB.isEmpty()) {
            testUnidadB = ((Number) unitsB.get(0).get("ID_UNIDAD")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO UNIDADES (ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, COEFICIENTE_COPROPIEDAD, ESTADO) " +
                "VALUES (?, 'APT-TEST-B1', 1, 0.05, 'ACTIVA')", testPropIdB
            );
            testUnidadB = jdbcTemplate.queryForObject(
                "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? AND IDENTIFICADOR = 'APT-TEST-B1'",
                Long.class, testPropIdB
            );
        }

        // Ensure Concept in Property A and B
        List<Map<String, Object>> cListA = jdbcTemplate.queryForList(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = ? FETCH FIRST 1 ROWS ONLY", testPropIdA
        );
        if (!cListA.isEmpty()) {
            testConceptoA = ((Number) cListA.get(0).get("ID_CONCEPTO")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                "VALUES (1, ?, 'ADMIN_FC_A', 'Administración Test A', 'ADMINISTRACION', 'ACTIVO')", testPropIdA
            );
            testConceptoA = jdbcTemplate.queryForObject(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_FC_A'", Long.class
            );
        }

        List<Map<String, Object>> cListB = jdbcTemplate.queryForList(
            "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = ? FETCH FIRST 1 ROWS ONLY", testPropIdB
        );
        if (!cListB.isEmpty()) {
            testConceptoB = ((Number) cListB.get(0).get("ID_CONCEPTO")).longValue();
        } else {
            jdbcTemplate.update(
                "INSERT INTO CONCEPTOS_COBRO (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO) " +
                "VALUES (1, ?, 'ADMIN_FC_B', 'Administración Test B', 'ADMINISTRACION', 'ACTIVO')", testPropIdB
            );
            testConceptoB = jdbcTemplate.queryForObject(
                "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE CODIGO = 'ADMIN_FC_B'", Long.class
            );
        }
    }

    private void setElevatedContext(Long orgId, Long propId, String roleCode) {
        SaedContext ctx = SaedContext.builder()
            .userId(1L)
            .organizationId(orgId)
            .propertyId(propId)
            .unitId(1L)
            .roleCode("SUPERADMIN")
            .roleScope(propId != null ? "PROPIEDAD" : "GLOBAL")
            .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute(String.format(
                "BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %s, 'SUPERADMIN'); END;",
                orgId, propId != null ? propId.toString() : "NULL"
            ));
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Test 1: Cuota pendiente $1.000.000 NO aumenta saldo en bancos (cartera != efectivo)")
    void test01_cuotaPendiente_noAumentaSaldoBancos() {
        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = ? AND PERIODO = '2026-11'", testUnidadA);

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoInicial = repository.getSaldoActual();
        BigDecimal esperadosInicial = repository.getIngresosEsperados();

        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update(
            "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO) " +
            "VALUES (?, ?, '2026-11', 1000000, 1000000, 'PENDIENTE', TRUNC(SYSDATE) + 15)",
            testUnidadA, testConceptoA
        );

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoNuevo = repository.getSaldoActual();
        BigDecimal esperadosNuevo = repository.getIngresosEsperados();

        // Saldo bancario NO debe aumentar con cuota pendiente
        assertEquals(saldoInicial, saldoNuevo, "El saldo bancario NO debe aumentar por emitir una cuota pendiente");
        // Ingresos esperados sí deben aumentar
        assertEquals(esperadosInicial.add(new BigDecimal("1000000")), esperadosNuevo,
            "Los ingresos esperados deben reflejar la cuota por cobrar");
    }

    @Test
    @DisplayName("Test 2: Pago aprobado $1.000.000 aumenta saldo bancario en $1.000.000")
    void test02_pagoAprobado_aumentaSaldoBancos() {
        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TRX-TEST-002'");

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoInicial = repository.getSaldoActual();
        BigDecimal ingresosInicial = repository.getTotalIngresos();

        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update(
            "INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE) " +
            "VALUES (?, 1000000, 'TRANSFERENCIA', 'APROBADO', FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'), 'TRX-TEST-002')",
            testUnidadA
        );

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoNuevo = repository.getSaldoActual();
        BigDecimal ingresosNuevo = repository.getTotalIngresos();

        assertEquals(saldoInicial.add(new BigDecimal("1000000")), saldoNuevo,
            "El saldo bancario debe aumentar en exactamente el monto del pago aprobado");
        assertEquals(ingresosInicial.add(new BigDecimal("1000000")), ingresosNuevo,
            "El total de ingresos debe aumentar en exactamente el monto del pago");
    }

    @Test
    @DisplayName("Test 3: Gasto pagado $300.000 disminuye saldo bancario en $300.000")
    void test03_gastoPagado_disminuyeSaldoBancos() {
        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM GASTOS WHERE ID_PROPIEDAD = ? AND BENEFICIARIO = 'Plomería SA'", testPropIdA);

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoInicial = repository.getSaldoActual();
        BigDecimal egresosInicial = repository.getTotalEgresos();

        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update(
            "INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, FECHA_GASTO, METODO_PAGO, ESTADO) " +
            "VALUES (?, 'Mantenimiento', 'Plomería SA', 300000, TRUNC(SYSDATE), 'TRANSFERENCIA', 'PAGADO')",
            testPropIdA
        );

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoNuevo = repository.getSaldoActual();
        BigDecimal egresosNuevo = repository.getTotalEgresos();

        assertEquals(saldoInicial.subtract(new BigDecimal("300000")), saldoNuevo,
            "El saldo bancario debe disminuir en el monto del gasto pagado");
        assertEquals(egresosInicial.add(new BigDecimal("300000")), egresosNuevo,
            "El total de egresos ejecutados debe aumentar en el monto del gasto");
    }

    @Test
    @DisplayName("Test 4: Gasto registrado (no pagado) NO disminuye saldo de efectivo en bancos")
    void test04_gastoRegistrado_noDisminuyeSaldoBancos() {
        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM GASTOS WHERE ID_PROPIEDAD = ? AND BENEFICIARIO = 'Vigilancia 24/7'", testPropIdA);

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoInicial = repository.getSaldoActual();
        BigDecimal egresosInicial = repository.getTotalEgresos();

        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update(
            "INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, FECHA_GASTO, METODO_PAGO, ESTADO) " +
            "VALUES (?, 'Seguridad', 'Vigilancia 24/7', 450000, TRUNC(SYSDATE), 'TRANSFERENCIA', 'REGISTRADO')",
            testPropIdA
        );

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoNuevo = repository.getSaldoActual();
        BigDecimal egresosNuevo = repository.getTotalEgresos();

        assertEquals(saldoInicial, saldoNuevo,
            "Un gasto solo registrado (no pagado aún) NO debe restar liquidez de bancos");
        assertEquals(egresosInicial, egresosNuevo,
            "Los egresos efectivamente desembolsados no deben incluir gastos pendientes de pago");
    }

    @Test
    @DisplayName("Test 5: Cuota vencida sin pago (SALDO_PENDIENTE = $500.000, ESTADO = VENCIDA) NO aparece como efectivo")
    void test05_cuotaVencida_noApareceComoEfectivo() {
        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD = ? AND PERIODO = '2026-08'", testUnidadA);

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoInicial = repository.getSaldoActual();

        setElevatedContext(1L, testPropIdA, "SUPERADMIN");
        jdbcTemplate.update(
            "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, ESTADO, FECHA_VENCIMIENTO) " +
            "VALUES (?, ?, '2026-08', 500000, 500000, 'VENCIDA', TRUNC(SYSDATE) - 30)",
            testUnidadA, testConceptoA
        );

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoNuevo = repository.getSaldoActual();

        assertEquals(saldoInicial, saldoNuevo,
            "Una cuota en mora vencida jamás debe ser sumada al saldo en bancos");
    }

    @Test
    @DisplayName("Test 6: Proyección matemática estricta: saldo + esperados - programados (sin doble conteo)")
    void test06_proyeccionMatematica_sinDobleConteo() {
        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        FlujoCajaResumenDTO resumen = service.getResumen();

        assertNotNull(resumen);
        assertNotNull(resumen.saldoActual());
        assertNotNull(resumen.ingresosEsperados());
        assertNotNull(resumen.gastosProgramados());
        assertNotNull(resumen.proyeccionSaldo());

        BigDecimal calculoEsperado = resumen.saldoActual()
            .add(resumen.ingresosEsperados())
            .subtract(resumen.gastosProgramados());

        assertEquals(calculoEsperado, resumen.proyeccionSaldo(),
            "La proyección debe coincidir exactamente con saldoActual + ingresosEsperados - gastosProgramados");
    }

    @Test
    @DisplayName("Test 7: Tenant isolation: movimientos de Propiedad B no afectan el saldo de Propiedad A")
    void test07_tenantIsolation_propiedadBNoAfectaPropiedadA() {
        setElevatedContext(1L, testPropIdB, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM PAGOS WHERE REFERENCIA_COMPROBANTE = 'TRX-PROP-B'");
        jdbcTemplate.update("DELETE FROM GASTOS WHERE ID_PROPIEDAD = ? AND BENEFICIARIO = 'Limpieza Total'", testPropIdB);

        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoA_inicial = repository.getSaldoActual();

        setElevatedContext(1L, testPropIdB, "SUPERADMIN");
        // Insert payment in Property B
        jdbcTemplate.update(
            "INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, ESTADO, FECHA_PAGO, REFERENCIA_COMPROBANTE) " +
            "VALUES (?, 5000000, 'TRANSFERENCIA', 'APROBADO', FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'), 'TRX-PROP-B')",
            testUnidadB
        );
        // Insert expense in Property B
        jdbcTemplate.update(
            "INSERT INTO GASTOS (ID_PROPIEDAD, CATEGORIA, BENEFICIARIO, MONTO, FECHA_GASTO, METODO_PAGO, ESTADO) " +
            "VALUES (?, 'Aseo', 'Limpieza Total', 2000000, TRUNC(SYSDATE), 'TRANSFERENCIA', 'PAGADO')",
            testPropIdB
        );

        // Verify Property A balance remained completely unchanged
        setElevatedContext(1L, testPropIdA, "ADMIN_PROPIEDAD");
        BigDecimal saldoA_final = repository.getSaldoActual();

        assertEquals(saldoA_inicial, saldoA_final,
            "Aislamiento multi-tenant estricto: los pagos y gastos de la Propiedad B no deben alterar la caja de la Propiedad A");
    }

    @Test
    @DisplayName("Test 8: Contexto vacio/sin tenant arroja AccessDeniedException impidiendo agregacion global")
    void test08_missingContext_throwsAccessDeniedException() {
        SaedContextHolder.clearContext();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            repository.getTotalIngresos();
        }, "Sin contexto de tenant debe arrojar AccessDeniedException y nunca agregar la base de datos");

        SaedContext emptyCtx = SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build();
        SaedContextHolder.setContext(emptyCtx);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            repository.getSaldoActual();
        }, "Contexto con orgId y propId nulos debe arrojar AccessDeniedException");
    }
}
