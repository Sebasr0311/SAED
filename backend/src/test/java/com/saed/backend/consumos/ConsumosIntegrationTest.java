package com.saed.backend.consumos;

import com.saed.backend.consumos.controller.ConsumosController;
import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ConsumosIntegrationTest {

    @Autowired
    private ConsumosController controller;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin_global",
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("SCOPE_SUPERADMIN"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"),
                                new SimpleGrantedAuthority("SCOPE_RESIDENTE")
                        )
                )
        );

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        jdbcTemplate.update("DELETE FROM MEDICIONES_CONSUMO WHERE PERIODO = '2026-05'");
    }

    @AfterEach
    public void cleanup() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("DELETE FROM MEDICIONES_CONSUMO WHERE PERIODO = '2026-05'");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Flujo E2E: Ciclo completo de Mediciones de Consumo, cálculo de costos y tendencias")
    public void testMedicionesConsumoLifecycle() {
        // 1. Crear medición de Agua en zonas comunes
        MedicionConsumoRequestDTO aguaReq = new MedicionConsumoRequestDTO();
        aguaReq.setIdUnidad(null); // Zonas comunes
        aguaReq.setTipoServicio("AGUA");
        aguaReq.setNumeroMedidor("MED-AGUA-GEN-01");
        aguaReq.setPeriodo("2026-05");
        aguaReq.setLecturaAnterior(new BigDecimal("100.00"));
        aguaReq.setLecturaActual(new BigDecimal("150.00")); // Consumo = 50 m3
        aguaReq.setUnidadMedida("M3");
        aguaReq.setTarifaUnitaria(new BigDecimal("6500.00"));
        aguaReq.setFechaTomaLectura(LocalDate.of(2026, 5, 30));

        ResponseEntity<Map<String, Object>> respAgua = controller.create(aguaReq);
        assertEquals(201, respAgua.getStatusCode().value());
        Long idAgua = ((Number) respAgua.getBody().get("idMedicion")).longValue();
        assertTrue(idAgua > 0);

        // 2. Crear medición de Energía para Unidad 1
        MedicionConsumoRequestDTO luzReq = new MedicionConsumoRequestDTO();
        luzReq.setIdUnidad(1L);
        luzReq.setTipoServicio("ENERGIA");
        luzReq.setNumeroMedidor("MED-LUZ-APT-101");
        luzReq.setPeriodo("2026-05");
        luzReq.setLecturaAnterior(new BigDecimal("1200.00"));
        luzReq.setLecturaActual(new BigDecimal("1450.00")); // Consumo = 250 kWh
        luzReq.setUnidadMedida("KWH");
        luzReq.setTarifaUnitaria(new BigDecimal("850.00"));
        luzReq.setFechaTomaLectura(LocalDate.of(2026, 5, 30));

        ResponseEntity<Map<String, Object>> respLuz = controller.create(luzReq);
        assertEquals(201, respLuz.getStatusCode().value());
        Long idLuz = ((Number) respLuz.getBody().get("idMedicion")).longValue();
        assertTrue(idLuz > 0);

        // 3. Consultar detalle y verificar cálculo de costo y consumo
        ResponseEntity<MedicionConsumoDTO> detalleAgua = controller.getById(idAgua);
        assertEquals(200, detalleAgua.getStatusCode().value());
        assertNotNull(detalleAgua.getBody());
        assertTrue(new BigDecimal("50.00").compareTo(detalleAgua.getBody().getConsumoCalculado()) == 0);
        // Costo = 50 * 6500 = 325,000.00
        assertTrue(new BigDecimal("325000.00").compareTo(detalleAgua.getBody().getCostoTotal()) == 0);

        // 4. Listar filtrando por servicio
        ResponseEntity<List<MedicionConsumoDTO>> listAgua = controller.getAll("2026-05", "AGUA", null);
        assertTrue(listAgua.getBody().stream().anyMatch(m -> m.getIdMedicion().equals(idAgua)));
        assertFalse(listAgua.getBody().stream().anyMatch(m -> m.getIdMedicion().equals(idLuz)));

        // 5. Consultar Resumen KPIs del período
        ResponseEntity<ConsumosSummaryDTO> resumenResp = controller.getSummary("2026-05");
        assertEquals(200, resumenResp.getStatusCode().value());
        assertNotNull(resumenResp.getBody());
        assertTrue(resumenResp.getBody().getTotalMediciones() >= 2);
        assertTrue(resumenResp.getBody().getConsumoTotalAgua().compareTo(new BigDecimal("50.00")) >= 0);
        assertTrue(resumenResp.getBody().getConsumoTotalEnergia().compareTo(new BigDecimal("250.00")) >= 0);

        // 6. Consultar Tendencias
        ResponseEntity<List<ConsumoTendenciaDTO>> tendencias = controller.getTendencias(null, 12);
        assertEquals(200, tendencias.getStatusCode().value());
        assertTrue(tendencias.getBody().stream().anyMatch(t -> "2026-05".equals(t.getPeriodo())));

        // 7. Cleanup
        controller.delete(idAgua);
        controller.delete(idLuz);
    }

    @Test
    @DisplayName("Validación: Lectura actual no puede ser menor a lectura anterior")
    public void testValidacionLecturas() {
        MedicionConsumoRequestDTO invalida = new MedicionConsumoRequestDTO();
        invalida.setTipoServicio("GAS");
        invalida.setNumeroMedidor("MED-GAS-ERR");
        invalida.setPeriodo("2026-06");
        invalida.setLecturaAnterior(new BigDecimal("500.00"));
        invalida.setLecturaActual(new BigDecimal("450.00")); // Invalida!

        assertThrows(IllegalArgumentException.class, () -> {
            controller.create(invalida);
        });
    }
}
