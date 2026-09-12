package com.saed.backend.automatizaciones;

import com.saed.backend.automatizaciones.controller.AutomatizacionesController;
import com.saed.backend.automatizaciones.dto.AccionRequestDTO;
import com.saed.backend.automatizaciones.dto.AutomatizacionesSummaryDTO;
import com.saed.backend.automatizaciones.dto.EjecucionDTO;
import com.saed.backend.automatizaciones.dto.EventoDTO;
import com.saed.backend.automatizaciones.dto.ReglaDTO;
import com.saed.backend.automatizaciones.dto.ReglaRequestDTO;
import com.saed.backend.automatizaciones.dto.SimulacionReglaRequestDTO;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AutomatizacionesIntegrationTest {

    @Autowired
    private AutomatizacionesController controller;

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

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("DELETE FROM EJECUCIONES_AUTOMATIZACION WHERE ID_REGLA IN (SELECT ID_REGLA FROM REGLAS_AUTOMATIZACION WHERE NOMBRE LIKE 'Regla Test E2E%')");
            jdbcTemplate.update("DELETE FROM REGLAS_AUTOMATIZACION WHERE NOMBRE LIKE 'Regla Test E2E%'");
        } catch (Exception ignored) {}
    }

    @AfterEach
    public void cleanup() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            jdbcTemplate.update("DELETE FROM EJECUCIONES_AUTOMATIZACION WHERE ID_REGLA IN (SELECT ID_REGLA FROM REGLAS_AUTOMATIZACION WHERE NOMBRE LIKE 'Regla Test E2E%')");
            jdbcTemplate.update("DELETE FROM REGLAS_AUTOMATIZACION WHERE NOMBRE LIKE 'Regla Test E2E%'");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Flujo E2E: Catálogo de Eventos, Creación de Reglas con Acciones, Simulación y KPIs")
    public void testMotorAutomatizacionesLifecycle() {
        // 1. Consultar catálogo de Eventos del Sistema
        ResponseEntity<List<EventoDTO>> respEventos = controller.getEventos();
        assertEquals(200, respEventos.getStatusCode().value());
        assertNotNull(respEventos.getBody());
        assertTrue(respEventos.getBody().size() >= 8, "Debe haber al menos 8 eventos en el catálogo");

        EventoDTO eventoCuota = respEventos.getBody().stream()
                .filter(e -> "CUOTA_VENCIDA".equals(e.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el evento CUOTA_VENCIDA"));

        // 2. Crear Regla con dos acciones (Enviar Notificación y Correo Admin)
        ReglaRequestDTO reglaReq = ReglaRequestDTO.builder()
                .idEvento(eventoCuota.getIdEvento())
                .idPropiedad(1L)
                .nombre("Regla Test E2E Notificación Mora")
                .descripcion("Regla automática de prueba para avisar a residentes morosos")
                .condicionJson("{\"diasMoraMinimos\": 5}")
                .estado("ACTIVA")
                .acciones(List.of(
                        AccionRequestDTO.builder()
                                .tipoAccion("ENVIAR_NOTIFICACION")
                                .parametrosJson("{\"canal\":\"IN_APP\",\"mensaje\":\"Su cuota mensual se encuentra vencida.\"}")
                                .ordenEjecucion(1)
                                .build(),
                        AccionRequestDTO.builder()
                                .tipoAccion("ENVIAR_CORREO_ADMIN")
                                .parametrosJson("{\"destinatario\":\"admin@saed.com\",\"asunto\":\"Alerta de mora registrada\"}")
                                .ordenEjecucion(2)
                                .build()
                ))
                .build();

        ResponseEntity<Map<String, Object>> respCrear = controller.createRegla(reglaReq);
        assertEquals(201, respCrear.getStatusCode().value());
        Long idRegla = ((Number) respCrear.getBody().get("idRegla")).longValue();
        assertTrue(idRegla > 0);

        // 3. Consultar detalle de la regla y verificar acciones ordenadas
        ResponseEntity<ReglaDTO> respDetalle = controller.getReglaById(idRegla);
        assertEquals(200, respDetalle.getStatusCode().value());
        assertNotNull(respDetalle.getBody());
        assertEquals("Regla Test E2E Notificación Mora", respDetalle.getBody().getNombre());
        assertEquals("ACTIVA", respDetalle.getBody().getEstado());
        assertEquals(2, respDetalle.getBody().getAcciones().size());
        assertEquals("ENVIAR_NOTIFICACION", respDetalle.getBody().getAcciones().get(0).getTipoAccion());
        assertEquals("ENVIAR_CORREO_ADMIN", respDetalle.getBody().getAcciones().get(1).getTipoAccion());

        // 4. Simular/Ejecutar la regla manualmente
        SimulacionReglaRequestDTO simReq = new SimulacionReglaRequestDTO(101L, "CUOTA", "{\"idCuota\":101,\"monto\":250000}");
        ResponseEntity<EjecucionDTO> respSim = controller.simularRegla(idRegla, simReq);
        assertEquals(200, respSim.getStatusCode().value());
        assertNotNull(respSim.getBody());
        assertEquals("EXITOSA", respSim.getBody().getResultado());
        assertTrue(respSim.getBody().getLogDetalle().contains("Notificación push/in-app"));
        assertTrue(respSim.getBody().getLogDetalle().contains("Email prioritario despachado"));
        assertTrue(respSim.getBody().getTiempoMs() >= 0);

        // 5. Consultar Historial de Ejecuciones
        ResponseEntity<List<EjecucionDTO>> respHistorial = controller.getEjecuciones(idRegla, 10);
        assertEquals(200, respHistorial.getStatusCode().value());
        assertNotNull(respHistorial.getBody());
        assertFalse(respHistorial.getBody().isEmpty());
        assertTrue(respHistorial.getBody().stream().anyMatch(e -> e.getIdRegla().equals(idRegla)));

        // 6. Cambiar estado a INACTIVA
        ResponseEntity<Map<String, String>> respToggle = controller.toggleEstado(idRegla);
        assertEquals(200, respToggle.getStatusCode().value());
        ReglaDTO reglaPausada = controller.getReglaById(idRegla).getBody();
        assertEquals("INACTIVA", reglaPausada.getEstado());

        // 7. Consultar Resumen KPIs de Automatización
        ResponseEntity<AutomatizacionesSummaryDTO> respSummary = controller.getSummary();
        assertEquals(200, respSummary.getStatusCode().value());
        assertNotNull(respSummary.getBody());
        assertTrue(respSummary.getBody().getTotalReglas() >= 1);
        assertTrue(respSummary.getBody().getTotalEventos() >= 8);
        assertTrue(respSummary.getBody().getTotalEjecucionesMes() >= 1);

        // 8. Borrado / Desactivación protegida por auditoría
        controller.deleteRegla(idRegla);
        ReglaDTO reglaFinal = controller.getReglaById(idRegla).getBody();
        assertNotNull(reglaFinal);
        assertEquals("INACTIVA", reglaFinal.getEstado());
    }
}
