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
            assertEquals(4, unidades.size(), "Property 1 must have exactly 4 units in Cartera");

            // Verify Apto 101 has 250,000 pending debt
            Map<String, Object> apto101 = unidades.stream()
                    .filter(u -> "Apto 101".equals(u.get("NUMERO_APARTAMENTO")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Apto 101 not found in cartera"));
            BigDecimal saldo101 = new BigDecimal(apto101.get("SALDO_TOTAL").toString());
            assertEquals(0, new BigDecimal("250000").compareTo(saldo101), "Apto 101 must have 250,000 COP balance");
            assertEquals("AL_DIA", apto101.get("ESTADO_CARTERA"));

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

            assertEquals(4, ((Number) resumen.get("TOTAL_UNIDADES")).intValue());
            BigDecimal totalCartera = new BigDecimal(resumen.get("TOTAL_CARTERA").toString());
            assertEquals(0, new BigDecimal("250000").compareTo(totalCartera), "Total Cartera must be 250,000 COP");
            BigDecimal totalMora = new BigDecimal(resumen.get("TOTAL_MORA").toString());
            assertEquals(0, BigDecimal.ZERO.compareTo(totalMora), "Total Mora must be 0 COP (not overdue yet)");
            assertEquals(4, ((Number) resumen.get("COUNT_AL_DIA")).intValue());

            // 5. Verify getCuotasPendientes() returns only Cuota 2 (Apto 101)
            ResponseEntity<List<CuotaDTO>> cuotasResp = pagosController.getCuotasPendientes(true);
            assertNotNull(cuotasResp);
            List<CuotaDTO> cuotas = cuotasResp.getBody();
            assertNotNull(cuotas);
            assertEquals(1, cuotas.size(), "There must be exactly 1 pending cuota for Property 1");
            CuotaDTO cuotaPendiente = cuotas.get(0);
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
