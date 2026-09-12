package com.saed.backend.emergencias;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.emergencias.controller.EmergenciasController;
import com.saed.backend.emergencias.dto.*;
import com.saed.backend.seguros.controller.PolizaSeguroController;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.dto.ResumenPolizasDTO;
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
public class EmergenciasYSegurosIntegrationTest {

    @Autowired
    private EmergenciasController emergenciasController;

    @Autowired
    private PolizaSeguroController polizaController;

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
                        "admin_prop",
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"),
                                new SimpleGrantedAuthority("SCOPE_SUPERADMIN"),
                                new SimpleGrantedAuthority("SCOPE_PORTERO"),
                                new SimpleGrantedAuthority("SCOPE_RESIDENTE")
                        )
                )
        );

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
    }

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Flujo E2E: Ciclo de vida completo de Planes y Contactos de Emergencia")
    public void testEmergenciasLifecycle() {
        // 1. Crear Contacto de Emergencia
        ContactoEmergenciaRequestDTO contactoReq = new ContactoEmergenciaRequestDTO();
        contactoReq.setEntidad("Defensa Civil Seccional Test");
        contactoReq.setTipoServicio("DEFENSA_CIVIL");
        contactoReq.setTelefonoPrincipal("144");
        contactoReq.setTelefonoAlterno("3110001122");
        contactoReq.setDireccion("Calle 100 # 15-20");
        contactoReq.setEsPrioritarioMinuta("S");
        contactoReq.setOrdenVisualizacion(10);

        ResponseEntity<Map<String, Object>> respContacto = emergenciasController.createContacto(contactoReq);
        assertEquals(201, respContacto.getStatusCode().value());
        assertNotNull(respContacto.getBody());
        Long idContacto = ((Number) respContacto.getBody().get("idContactoEmergencia")).longValue();
        assertTrue(idContacto > 0);

        // 2. Listar Contactos
        ResponseEntity<List<ContactoEmergenciaDTO>> contactosList = emergenciasController.getAllContactos();
        assertTrue(contactosList.getBody().stream().anyMatch(c -> c.getIdContactoEmergencia().equals(idContacto)));

        // 3. Listar Minuta Rápida
        ResponseEntity<List<ContactoEmergenciaDTO>> minutaList = emergenciasController.getContactosMinuta();
        assertTrue(minutaList.getBody().stream().anyMatch(c -> c.getIdContactoEmergencia().equals(idContacto)));

        // 4. Actualizar Contacto
        contactoReq.setTelefonoAlterno("3209998877");
        ResponseEntity<Map<String, String>> respUpdateContacto = emergenciasController.updateContacto(idContacto, contactoReq);
        assertEquals(200, respUpdateContacto.getStatusCode().value());

        // 5. Crear Plan de Emergencia
        PlanEmergenciaRequestDTO planReq = new PlanEmergenciaRequestDTO();
        planReq.setTitulo("Plan de Emergencia Anti-Terremoto Edificio");
        planReq.setTipoContingencia("TERREMOTO");
        planReq.setPuntosEncuentro("Zona Verde Cancha Multiple");
        planReq.setRutasEvacuacionDesc("Rutas senalizadas bajando por las escaleras este y oeste");
        planReq.setFechaUltimaRevision(LocalDate.now());
        planReq.setEstado("ACTIVO");

        ResponseEntity<Map<String, Object>> respPlan = emergenciasController.createPlan(planReq);
        assertEquals(201, respPlan.getStatusCode().value());
        Long idPlan = ((Number) respPlan.getBody().get("idPlanEmergencia")).longValue();
        assertTrue(idPlan > 0);

        // 6. Consultar Plan
        ResponseEntity<PlanEmergenciaDTO> planDetalle = emergenciasController.getPlanById(idPlan);
        assertEquals("TERREMOTO", planDetalle.getBody().getTipoContingencia());
        assertEquals("ACTIVO", planDetalle.getBody().getEstado());

        // 7. Resumen de Emergencias
        ResponseEntity<EmergenciasSummaryDTO> resumenResp = emergenciasController.getResumen();
        assertEquals(200, resumenResp.getStatusCode().value());
        assertNotNull(resumenResp.getBody());
        assertTrue(resumenResp.getBody().getTotalPlanes() >= 1);
        assertTrue(resumenResp.getBody().getTotalContactos() >= 1);

        // 8. Cleanup
        emergenciasController.deletePlan(idPlan);
        emergenciasController.deleteContacto(idContacto);
    }

    @Test
    @DisplayName("Flujo E2E: Pólizas de Seguro, Normalización de Estados y Resumen KPI")
    public void testPolizasLifecycleAndKPI() {
        PolizaSeguroDTO poliza = new PolizaSeguroDTO();
        poliza.setCompaniaAseguradora("Seguros del Estado Copropiedad");
        poliza.setNumeroPoliza("POL-675-9988");
        poliza.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        poliza.setValorAsegurado(new BigDecimal("1500000000.00"));
        poliza.setValorPrimaAnual(new BigDecimal("12500000.00"));
        poliza.setFechaInicio(LocalDate.now().minusMonths(6));
        poliza.setFechaFin(LocalDate.now().plusMonths(6));
        poliza.setDiasAlertaVencimiento(45);
        poliza.setNombreCorredorAgente("Carlos Asegurador");
        poliza.setTelefonoContactoAgente("3005554433");

        // Crear póliza
        ResponseEntity<Void> respCreate = polizaController.create(poliza);
        assertEquals(200, respCreate.getStatusCode().value());

        // Listar y verificar normalización a VIGENTE
        ResponseEntity<List<PolizaSeguroDTO>> listResp = polizaController.getAll();
        assertEquals(200, listResp.getStatusCode().value());
        PolizaSeguroDTO polizaGuardada = listResp.getBody().stream()
                .filter(p -> "POL-675-9988".equals(p.getNumeroPoliza()))
                .findFirst()
                .orElse(null);
        assertNotNull(polizaGuardada);
        assertEquals("VIGENTE", polizaGuardada.getEstado());

        // Consultar Resumen KPIs
        ResponseEntity<ResumenPolizasDTO> resumenResp = polizaController.getResumen();
        assertEquals(200, resumenResp.getStatusCode().value());
        assertNotNull(resumenResp.getBody());
        assertTrue(resumenResp.getBody().getTotalPolizas() >= 1);
        assertTrue(resumenResp.getBody().getVigentes() >= 1);
        assertTrue(resumenResp.getBody().getValorAseguradoTotal().compareTo(BigDecimal.ZERO) > 0);

        // Cleanup
        polizaController.delete(polizaGuardada.getIdPoliza());
    }
}
