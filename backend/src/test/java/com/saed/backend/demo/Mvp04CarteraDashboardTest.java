package com.saed.backend.demo;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.controller.CarteraController;
import com.saed.backend.finanzas.controller.PagosController;
import com.saed.backend.finanzas.dto.CuotaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class Mvp04CarteraDashboardTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CarteraController carteraController;

    @Autowired
    private PagosController pagosController;

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "admin")
    @DisplayName("MVP-04: Verify Cartera, Cuotas, and Dashboard financial endpoints under RLS")
    public void testCarteraAndDashboardFlow() {
        // 1. Establish ADMIN_PROPIEDAD context (User 2, Org 1, Prop 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).organizationId(1L).propertyId(1L)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(2, 1, 1, 'ADMIN_PROPIEDAD'); END;");

        try {
            // 2. Execute recalcular()
            ApiResponse<String> recalcResp = carteraController.recalcular();
            assertNotNull(recalcResp);
            assertTrue("SUCCESS".equalsIgnoreCase(recalcResp.getStatus()));

            // 3. Verify listar() contains all 4 units for Prop 1
            ApiResponse<List<Map<String, Object>>> listResp = carteraController.listar();
            assertNotNull(listResp);
            List<Map<String, Object>> unidades = listResp.getData();
            assertNotNull(unidades);
            // Other suites add their own units to property 1, so the demo dataset is checked by identifier instead of by
            // the global size of the portfolio.
            List<String> demoUnits = List.of("Apto 101", "Apto 102", "Apto 201", "Apto 202");
            List<Map<String, Object>> demo = unidades.stream()
                    .filter(u -> demoUnits.contains(String.valueOf(u.get("NUMERO_APARTAMENTO"))))
                    .toList();
            assertEquals(4, demo.size(), "The 4 demo units of Property 1 must be in Cartera");

            // Verify Apto 101 has 250,000 pending debt
            Map<String, Object> apto101 = unidades.stream()
                    .filter(u -> "Apto 101".equals(u.get("NUMERO_APARTAMENTO")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Apto 101 not found in cartera"));
            BigDecimal saldo101 = new BigDecimal(apto101.get("SALDO_TOTAL").toString());
            assertEquals(0, new BigDecimal("250000").compareTo(saldo101), "Apto 101 must have 250,000 COP balance");
            // The seeded fee of Apto 101 is due on 2026-09-15; the portfolio ages it against the current date, so the
            // expected bucket depends on how long ago it fell due (AL_DIA only before the due date).
            long diasVencida = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.of(2026, 9, 15), java.time.LocalDate.now());
            String estadoEsperado101 = diasVencida <= 0 ? "AL_DIA" : diasVencida <= 30 ? "MORA_30" : diasVencida <= 60 ? "MORA_60" : "MORA_90_MAS";
            assertEquals(estadoEsperado101, apto101.get("ESTADO_CARTERA"));

            // Verify Apto 102 has 0 balance (paid in full via Wompi)
            Map<String, Object> apto102 = unidades.stream()
                    .filter(u -> "Apto 102".equals(u.get("NUMERO_APARTAMENTO")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Apto 102 not found in cartera"));
            BigDecimal saldo102 = new BigDecimal(apto102.get("SALDO_TOTAL").toString());
            assertEquals(0, BigDecimal.ZERO.compareTo(saldo102), "Apto 102 must have 0 balance");
            assertEquals("AL_DIA", apto102.get("ESTADO_CARTERA"));

            // 4. Verify resumen() metrics match dataset
            ApiResponse<Map<String, Object>> resResp = carteraController.resumen();
            assertNotNull(resResp);
            Map<String, Object> resumen = resResp.getData();
            assertNotNull(resumen);

            assertTrue(((Number) resumen.get("TOTAL_UNIDADES")).intValue() >= 4, "The summary must cover at least the 4 demo units");
            BigDecimal totalCartera = new BigDecimal(resumen.get("TOTAL_CARTERA").toString());
            assertTrue(totalCartera.compareTo(new BigDecimal("250000")) >= 0, "Total Cartera must include the 250,000 COP of the demo dataset");
            assertTrue(((Number) resumen.get("COUNT_AL_DIA")).intValue() >= (diasVencida <= 0 ? 4 : 3),
                    "Every demo unit without an overdue fee is AL_DIA");
            // The demo units alone: 250,000 COP outstanding and nothing overdue.
            BigDecimal demoTotal = demo.stream().map(u -> new BigDecimal(u.get("SALDO_TOTAL").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("250000").compareTo(demoTotal), "The demo units must hold 250,000 COP in total");
            BigDecimal demoMora = demo.stream()
                    .map(u -> new BigDecimal(u.get("SALDO_MORA_30").toString())
                            .add(new BigDecimal(u.get("SALDO_MORA_60").toString()))
                            .add(new BigDecimal(u.get("SALDO_MORA_90_MAS").toString())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal(diasVencida <= 0 ? "0" : "250000").compareTo(demoMora),
                    "The overdue balance of the demo units is the seeded fee once its due date has passed");

            // 5. Verify getCuotasPendientes() returns only Cuota 2 (Apto 101)
            ResponseEntity<List<CuotaDTO>> cuotasResp = pagosController.getCuotasPendientes(true);
            assertNotNull(cuotasResp);
            List<CuotaDTO> cuotas = cuotasResp.getBody();
            assertNotNull(cuotas);
            List<CuotaDTO> demoCuotas = cuotas.stream().filter(c -> "Apto 101".equals(c.numeroApartamento())).toList();
            assertEquals(1, demoCuotas.size(), "There must be exactly 1 pending cuota for the demo Apto 101");
            CuotaDTO cuotaPendiente = demoCuotas.get(0);
            assertEquals("Apto 101", cuotaPendiente.numeroApartamento());
            assertEquals("Carlos Martinez", cuotaPendiente.nombreResidente());
            assertEquals(0, new BigDecimal("250000").compareTo(cuotaPendiente.saldoPendiente()));
            assertEquals("PENDIENTE", cuotaPendiente.estado());

        } finally {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            SaedContextHolder.clearContext();
        }
    }
}
