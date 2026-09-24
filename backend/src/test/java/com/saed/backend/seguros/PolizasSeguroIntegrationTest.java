package com.saed.backend.seguros;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import com.saed.backend.seguros.controller.PolizaSeguroController;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.dto.ResumenPolizasDTO;
import com.saed.backend.seguros.service.PolizaSeguroService;
import com.saed.backend.seguros.service.PolizaVencimientoScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PolizasSeguroIntegrationTest {

    @Autowired
    private PolizaSeguroController polizaController;

    @Autowired
    private PolizaSeguroService polizaService;

    @Autowired
    private PolizaVencimientoScheduler scheduler;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testDocumentoIdProp1 = null;
    private Long testDocumentoIdProp2 = null;

    @BeforeEach
    public void setup() {
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Crear documentos de prueba para propiedad 1 y propiedad 2 para testear integración documental y aislamiento
        try {
            DocumentoDTO doc1 = new DocumentoDTO();
            doc1.setTitulo("Caratula Poliza Test Prop 1");
            doc1.setCategoria("POLIZA_SEGURO");
            doc1.setDescripcion("Documento de prueba F10-07");
            doc1.setEsPublicoResidentes("S");
            doc1.setRolMinimoAcceso("RESIDENTE");
            testDocumentoIdProp1 = documentoRepository.createDocumento(doc1, 1L, 1L, 1L);

            DocumentoDTO doc2 = new DocumentoDTO();
            doc2.setTitulo("Caratula Poliza Test Prop 2");
            doc2.setCategoria("POLIZA_SEGURO");
            doc2.setDescripcion("Documento ajeno para test de aislamiento cross-property");
            doc2.setEsPublicoResidentes("S");
            doc2.setRolMinimoAcceso("RESIDENTE");
            testDocumentoIdProp2 = documentoRepository.createDocumento(doc2, 1L, 2L, 1L);
        } catch (Exception e) {
            // Si falla por constraints de test se continúa sin doc opcional
        }
    }

    @AfterEach
    public void cleanup() {
        setSecurityContext(1L, List.of("SCOPE_SUPERADMIN", "SCOPE_ADMIN_PROPIEDAD"));
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Limpiar alertas de test (por mensaje o por ID_POLIZA de las pólizas de test)
        jdbcTemplate.update("DELETE FROM ALERTAS_ADMIN WHERE MENSAJE LIKE '%TEST-%' OR ID_POLIZA IN (SELECT ID_POLIZA FROM POLIZAS_SEGURO WHERE NUMERO_POLIZA LIKE 'TEST-%')");

        // Limpiar polizas de test
        jdbcTemplate.update("DELETE FROM POLIZAS_SEGURO WHERE NUMERO_POLIZA LIKE 'TEST-%'");

        // Limpiar documentos de test
        if (testDocumentoIdProp1 != null) {
            try { jdbcTemplate.update("DELETE FROM DOCUMENTOS WHERE ID_DOCUMENTO = ?", testDocumentoIdProp1); } catch (Exception ignored) {}
        }
        if (testDocumentoIdProp2 != null) {
            try { jdbcTemplate.update("DELETE FROM DOCUMENTOS WHERE ID_DOCUMENTO = ?", testDocumentoIdProp2); } catch (Exception ignored) {}
        }

        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(Long propId, List<String> authorities) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(propId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test_user",
                        "n/a",
                        authorities.stream().map(SimpleGrantedAuthority::new).toList()
                )
        );
    }

    @Test
    @DisplayName("F10-07 GAP 01 y 02: CRUD de Póliza con Deducible e Integración Documental F10-01")
    public void testCrudPolizaConDeducibleYDocumento() {
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        PolizaSeguroDTO poliza = new PolizaSeguroDTO();
        poliza.setCompaniaAseguradora("Aseguradora Solidaria de Colombia");
        poliza.setNumeroPoliza("TEST-POL-675-001");
        poliza.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        poliza.setValorAsegurado(new BigDecimal("2500000000.00"));
        poliza.setValorPrimaAnual(new BigDecimal("18500000.00"));
        poliza.setFechaInicio(LocalDate.now().minusMonths(2));
        poliza.setFechaFin(LocalDate.now().plusMonths(10));
        poliza.setDiasAlertaVencimiento(30);
        poliza.setDeducible("10% del siniestro, mínimo 3 SMMLV");
        poliza.setIdDocumento(testDocumentoIdProp1);
        poliza.setNombreCorredorAgente("Mariana Rios");
        poliza.setTelefonoContactoAgente("3158889900");

        // 1. Crear Póliza
        ResponseEntity<Void> respCreate = polizaController.create(poliza);
        assertEquals(200, respCreate.getStatusCode().value());

        // 2. Obtener y verificar persistencia de deducible e idDocumento
        ResponseEntity<List<PolizaSeguroDTO>> listResp = polizaController.getAll();
        assertEquals(200, listResp.getStatusCode().value());
        PolizaSeguroDTO creada = listResp.getBody().stream()
                .filter(p -> "TEST-POL-675-001".equals(p.getNumeroPoliza()))
                .findFirst()
                .orElse(null);
        assertNotNull(creada);
        assertEquals("10% del siniestro, mínimo 3 SMMLV", creada.getDeducible());
        if (testDocumentoIdProp1 != null) {
            assertEquals(testDocumentoIdProp1, creada.getIdDocumento());
        }
        assertEquals("VIGENTE", creada.getEstado());

        // 3. Consultar por ID
        ResponseEntity<PolizaSeguroDTO> byIdResp = polizaController.getById(creada.getIdPoliza());
        assertEquals(200, byIdResp.getStatusCode().value());
        assertEquals(creada.getIdPoliza(), byIdResp.getBody().getIdPoliza());
        assertEquals("10% del siniestro, mínimo 3 SMMLV", byIdResp.getBody().getDeducible());

        // 4. Actualizar Deducible y datos de contacto
        creada.setDeducible("5% del valor asegurable para terremoto");
        creada.setTelefonoContactoAgente("3151112233");
        ResponseEntity<Void> respUpdate = polizaController.update(creada.getIdPoliza(), creada);
        assertEquals(200, respUpdate.getStatusCode().value());

        ResponseEntity<PolizaSeguroDTO> updatedResp = polizaController.getById(creada.getIdPoliza());
        assertEquals("5% del valor asegurable para terremoto", updatedResp.getBody().getDeducible());
        assertEquals("3151112233", updatedResp.getBody().getTelefonoContactoAgente());

        // 5. Eliminar
        ResponseEntity<Void> respDelete = polizaController.delete(creada.getIdPoliza());
        assertEquals(200, respDelete.getStatusCode().value());

        ResponseEntity<List<PolizaSeguroDTO>> finalList = polizaController.getAll();
        assertTrue(finalList.getBody().stream().noneMatch(p -> "TEST-POL-675-001".equals(p.getNumeroPoliza())));
    }

    @Test
    @DisplayName("F10-07 Reglas de Negocio: Validaciones de valores monetarios, fechas y aislamiento de documento")
    public void testValidacionesDominio() {
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        PolizaSeguroDTO poliza = new PolizaSeguroDTO();
        poliza.setCompaniaAseguradora("Seguros Bolivar");
        poliza.setNumeroPoliza("TEST-VAL-001");
        poliza.setRamoCobertura("RESPONSABILIDAD_CIVIL");
        poliza.setValorAsegurado(new BigDecimal("500000000.00"));
        poliza.setValorPrimaAnual(new BigDecimal("5000000.00"));
        poliza.setFechaInicio(LocalDate.now());
        poliza.setFechaFin(LocalDate.now().plusYears(1));

        // Regla 1: Valor asegurado <= 0
        poliza.setValorAsegurado(BigDecimal.ZERO);
        IllegalArgumentException exAsegurado = assertThrows(IllegalArgumentException.class, () -> polizaService.createPoliza(poliza));
        assertTrue(exAsegurado.getMessage().contains("mayor a cero"));
        poliza.setValorAsegurado(new BigDecimal("500000000.00"));

        // Regla 2: Valor prima anual < 0
        poliza.setValorPrimaAnual(new BigDecimal("-100.00"));
        IllegalArgumentException exPrima = assertThrows(IllegalArgumentException.class, () -> polizaService.createPoliza(poliza));
        assertTrue(exPrima.getMessage().contains("no puede ser negativo"));
        poliza.setValorPrimaAnual(new BigDecimal("5000000.00"));

        // Regla 3: Fecha fin anterior a fecha inicio
        poliza.setFechaInicio(LocalDate.now());
        poliza.setFechaFin(LocalDate.now().minusDays(1));
        IllegalArgumentException exFechas = assertThrows(IllegalArgumentException.class, () -> polizaService.createPoliza(poliza));
        assertTrue(exFechas.getMessage().contains("posterior o igual"));
        poliza.setFechaFin(LocalDate.now().plusYears(1));

        // Regla 4: Documento no pertenece a la propiedad (Cross-property isolation en documentos)
        if (testDocumentoIdProp2 != null) {
            poliza.setIdDocumento(testDocumentoIdProp2);
            IllegalArgumentException exDoc = assertThrows(IllegalArgumentException.class, () -> polizaService.createPoliza(poliza));
            assertTrue(exDoc.getMessage().contains("no pertenece a esta propiedad"));
        }
    }

    @Test
    @DisplayName("F10-07 Estados Dinámicos: Normalización a VIGENTE, POR_VENCER, VENCIDA y CANCELADA")
    public void testEstadosDinamicos() {
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        // Caso 1: VIGENTE (vence en 6 meses con 30 días de alerta)
        PolizaSeguroDTO pVigente = new PolizaSeguroDTO();
        pVigente.setCompaniaAseguradora("Mapfre");
        pVigente.setNumeroPoliza("TEST-EST-VIG");
        pVigente.setRamoCobertura("INCENDIO_RAYO");
        pVigente.setValorAsegurado(new BigDecimal("1000000000.00"));
        pVigente.setValorPrimaAnual(new BigDecimal("8000000.00"));
        pVigente.setFechaInicio(LocalDate.now().minusMonths(6));
        pVigente.setFechaFin(LocalDate.now().plusMonths(6));
        pVigente.setDiasAlertaVencimiento(30);
        polizaController.create(pVigente);

        // Caso 2: POR_VENCER (vence en 10 días con 30 días de alerta)
        PolizaSeguroDTO pPorVencer = new PolizaSeguroDTO();
        pPorVencer.setCompaniaAseguradora("SURA");
        pPorVencer.setNumeroPoliza("TEST-EST-PVEN");
        pPorVencer.setRamoCobertura("RESPONSABILIDAD_CIVIL");
        pPorVencer.setValorAsegurado(new BigDecimal("800000000.00"));
        pPorVencer.setValorPrimaAnual(new BigDecimal("6000000.00"));
        pPorVencer.setFechaInicio(LocalDate.now().minusMonths(11));
        pPorVencer.setFechaFin(LocalDate.now().plusDays(10));
        pPorVencer.setDiasAlertaVencimiento(30);
        polizaController.create(pPorVencer);

        // Caso 3: VENCIDA (venció hace 5 días)
        PolizaSeguroDTO pVencida = new PolizaSeguroDTO();
        pVencida.setCompaniaAseguradora("Allianz");
        pVencida.setNumeroPoliza("TEST-EST-VENC");
        pVencida.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        pVencida.setValorAsegurado(new BigDecimal("2000000000.00"));
        pVencida.setValorPrimaAnual(new BigDecimal("15000000.00"));
        pVencida.setFechaInicio(LocalDate.now().minusYears(1));
        pVencida.setFechaFin(LocalDate.now().minusDays(5));
        pVencida.setDiasAlertaVencimiento(30);
        polizaController.create(pVencida);

        // Caso 4: CANCELADA
        PolizaSeguroDTO pCancelada = new PolizaSeguroDTO();
        pCancelada.setCompaniaAseguradora("Chubb");
        pCancelada.setNumeroPoliza("TEST-EST-CANC");
        pCancelada.setRamoCobertura("TERREMOTO");
        pCancelada.setValorAsegurado(new BigDecimal("500000000.00"));
        pCancelada.setValorPrimaAnual(new BigDecimal("4000000.00"));
        pCancelada.setFechaInicio(LocalDate.now().minusMonths(3));
        pCancelada.setFechaFin(LocalDate.now().plusMonths(9));
        pCancelada.setDiasAlertaVencimiento(30);
        pCancelada.setEstado("CANCELADA");
        polizaController.create(pCancelada);

        // Verificar normalización en getAll()
        List<PolizaSeguroDTO> polizas = polizaController.getAll().getBody();
        assertNotNull(polizas);

        PolizaSeguroDTO resVigente = polizas.stream().filter(p -> "TEST-EST-VIG".equals(p.getNumeroPoliza())).findFirst().orElseThrow();
        assertEquals("VIGENTE", resVigente.getEstado());

        PolizaSeguroDTO resPorVencer = polizas.stream().filter(p -> "TEST-EST-PVEN".equals(p.getNumeroPoliza())).findFirst().orElseThrow();
        assertEquals("POR_VENCER", resPorVencer.getEstado());

        PolizaSeguroDTO resVencida = polizas.stream().filter(p -> "TEST-EST-VENC".equals(p.getNumeroPoliza())).findFirst().orElseThrow();
        assertEquals("VENCIDA", resVencida.getEstado());

        PolizaSeguroDTO resCancelada = polizas.stream().filter(p -> "TEST-EST-CANC".equals(p.getNumeroPoliza())).findFirst().orElseThrow();
        assertEquals("CANCELADA", resCancelada.getEstado());

        // Verificar cálculo en Resumen KPIs
        ResumenPolizasDTO resumen = polizaController.getResumen().getBody();
        assertNotNull(resumen);
        assertTrue(resumen.getVigentes() >= 1);
        assertTrue(resumen.getPorVencer() >= 1);
        assertTrue(resumen.getVencidas() >= 1);
    }

    @Test
    @DisplayName("F10-07 GAP 03: Endpoint para Residentes (/vigentes) y Denegación en Rutas Administrativas")
    public void testResidenteVigentesTransparenciaYDenegacion() {
        // 1. Crear una póliza vigente y una vencida como ADMIN_PROPIEDAD
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        PolizaSeguroDTO pVig = new PolizaSeguroDTO();
        pVig.setCompaniaAseguradora("Aseguradora Vigente S.A.");
        pVig.setNumeroPoliza("TEST-RES-VIG");
        pVig.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        pVig.setValorAsegurado(new BigDecimal("1000000000.00"));
        pVig.setValorPrimaAnual(new BigDecimal("10000000.00"));
        pVig.setFechaInicio(LocalDate.now().minusMonths(1));
        pVig.setFechaFin(LocalDate.now().plusMonths(11));
        pVig.setDiasAlertaVencimiento(30);
        pVig.setDeducible("10% siniestro");
        polizaController.create(pVig);

        PolizaSeguroDTO pVenc = new PolizaSeguroDTO();
        pVenc.setCompaniaAseguradora("Aseguradora Pasada S.A.");
        pVenc.setNumeroPoliza("TEST-RES-VENC");
        pVenc.setRamoCobertura("INCENDIO");
        pVenc.setValorAsegurado(new BigDecimal("500000000.00"));
        pVenc.setValorPrimaAnual(new BigDecimal("5000000.00"));
        pVenc.setFechaInicio(LocalDate.now().minusYears(2));
        pVenc.setFechaFin(LocalDate.now().minusYears(1));
        pVenc.setDiasAlertaVencimiento(30);
        polizaController.create(pVenc);

        // 2. Cambiar credenciales a RESIDENTE
        setSecurityContext(1L, List.of("SCOPE_RESIDENTE"));

        // El endpoint /vigentes debe permitir lectura al residente
        ResponseEntity<List<PolizaSeguroDTO>> respVigentes = polizaController.getVigentes();
        assertEquals(200, respVigentes.getStatusCode().value());
        assertNotNull(respVigentes.getBody());

        // Debe incluir la póliza vigente
        assertTrue(respVigentes.getBody().stream().anyMatch(p -> "TEST-RES-VIG".equals(p.getNumeroPoliza())));
        // NO debe incluir la póliza vencida
        assertFalse(respVigentes.getBody().stream().anyMatch(p -> "TEST-RES-VENC".equals(p.getNumeroPoliza())));

        // Las rutas administrativas deben denegar acceso a RESIDENTE (403 AccessDeniedException)
        assertThrows(AccessDeniedException.class, () -> polizaController.getAll());
        assertThrows(AccessDeniedException.class, () -> polizaController.getResumen());
        assertThrows(AccessDeniedException.class, () -> polizaController.create(pVig));
        assertThrows(AccessDeniedException.class, () -> polizaController.delete(1L));
    }

    @Test
    @DisplayName("F10-07 GAP 06: ADMIN_ORGANIZACION tiene acceso de solo lectura y denegación de mutaciones")
    public void testAdminOrganizacionSupervisionSoloLectura() {
        // Contexto como ADMIN_ORGANIZACION
        setSecurityContext(1L, List.of("SCOPE_ADMIN_ORGANIZACION"));

        // Lectura de pólizas permitida
        ResponseEntity<List<PolizaSeguroDTO>> listResp = polizaController.getAll();
        assertEquals(200, listResp.getStatusCode().value());

        // Resumen KPI permitido
        ResponseEntity<ResumenPolizasDTO> resumenResp = polizaController.getResumen();
        assertEquals(200, resumenResp.getStatusCode().value());

        // Mutaciones prohibidas (403 AccessDeniedException)
        PolizaSeguroDTO polizaNueva = new PolizaSeguroDTO();
        polizaNueva.setCompaniaAseguradora("Mutacion Org Prohibida");
        polizaNueva.setNumeroPoliza("TEST-ORG-MUT");
        polizaNueva.setRamoCobertura("TERREMOTO");
        polizaNueva.setValorAsegurado(new BigDecimal("100000000.00"));
        polizaNueva.setValorPrimaAnual(new BigDecimal("1000000.00"));
        polizaNueva.setFechaInicio(LocalDate.now());
        polizaNueva.setFechaFin(LocalDate.now().plusYears(1));

        assertThrows(AccessDeniedException.class, () -> polizaController.create(polizaNueva));
        assertThrows(AccessDeniedException.class, () -> polizaController.update(1L, polizaNueva));
        assertThrows(AccessDeniedException.class, () -> polizaController.delete(1L));
    }

    @Test
    @DisplayName("F10-07 Multi-Tenancy: Aislamiento estricto de pólizas entre propiedades")
    public void testAislamientoMultiTenant() {
        // 1. Crear póliza en Propiedad 1
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        PolizaSeguroDTO pProp1 = new PolizaSeguroDTO();
        pProp1.setCompaniaAseguradora("Aseguradora Prop 1");
        pProp1.setNumeroPoliza("TEST-PROP1-ISO");
        pProp1.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        pProp1.setValorAsegurado(new BigDecimal("1000000000.00"));
        pProp1.setValorPrimaAnual(new BigDecimal("10000000.00"));
        pProp1.setFechaInicio(LocalDate.now().minusMonths(1));
        pProp1.setFechaFin(LocalDate.now().plusMonths(11));
        pProp1.setDiasAlertaVencimiento(30);
        polizaController.create(pProp1);

        // Verificar que existe en Propiedad 1
        List<PolizaSeguroDTO> listProp1 = polizaController.getAll().getBody();
        assertNotNull(listProp1);
        assertTrue(listProp1.stream().anyMatch(p -> "TEST-PROP1-ISO".equals(p.getNumeroPoliza())));

        // 2. Cambiar contexto a Propiedad 2
        setSecurityContext(2L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        // Verificar que en Propiedad 2 NO es visible la póliza de Propiedad 1
        List<PolizaSeguroDTO> listProp2 = polizaController.getAll().getBody();
        assertNotNull(listProp2);
        assertFalse(listProp2.stream().anyMatch(p -> "TEST-PROP1-ISO".equals(p.getNumeroPoliza())));

        // También verificar en /vigentes
        List<PolizaSeguroDTO> vigentesProp2 = polizaController.getVigentes().getBody();
        assertNotNull(vigentesProp2);
        assertFalse(vigentesProp2.stream().anyMatch(p -> "TEST-PROP1-ISO".equals(p.getNumeroPoliza())));
    }

    @Test
    @DisplayName("F10-07 GAP 05 & OBS-02: Scheduler de Vencimientos, Generación de Alertas y Deduplicación Atómica por ID_POLIZA")
    public void testSchedulerVencimientoYDeduplicacion() {
        setSecurityContext(1L, List.of("SCOPE_ADMIN_PROPIEDAD"));

        // Crear póliza que vence en 5 días (POR_VENCER)
        PolizaSeguroDTO pPorVencer = new PolizaSeguroDTO();
        pPorVencer.setCompaniaAseguradora("Seguros Scheduler S.A.");
        pPorVencer.setNumeroPoliza("TEST-SCHED-001");
        pPorVencer.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        pPorVencer.setValorAsegurado(new BigDecimal("1200000000.00"));
        pPorVencer.setValorPrimaAnual(new BigDecimal("9000000.00"));
        pPorVencer.setFechaInicio(LocalDate.now().minusMonths(11));
        pPorVencer.setFechaFin(LocalDate.now().plusDays(5));
        pPorVencer.setDiasAlertaVencimiento(45);
        polizaController.create(pPorVencer);

        // Obtener el ID persistido de la póliza
        Long idPoliza = polizaController.getAll().getBody().stream()
                .filter(p -> "TEST-SCHED-001".equals(p.getNumeroPoliza()))
                .findFirst()
                .orElseThrow()
                .getIdPoliza();

        // 1. Ejecutar scheduler primera vez
        scheduler.verificarYAlertarVencimientos();

        // 2. Verificar que se insertó la alerta administrativa estructurada con ID_POLIZA
        Integer countAlertas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ALERTAS_ADMIN WHERE TIPO_ALERTA = 'VENCIMIENTO_POLIZA' AND ID_POLIZA = ?",
                Integer.class,
                idPoliza
        );
        assertNotNull(countAlertas);
        assertEquals(1, countAlertas, "Debe existir exactamente 1 alerta generada y vinculada a ID_POLIZA");

        // 3. Ejecutar scheduler segunda vez (deduplicación atómica activa vía MERGE)
        scheduler.verificarYAlertarVencimientos();

        Integer countAlertas2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ALERTAS_ADMIN WHERE TIPO_ALERTA = 'VENCIMIENTO_POLIZA' AND ID_POLIZA = ?",
                Integer.class,
                idPoliza
        );
        assertEquals(1, countAlertas2, "La deduplicación atómica MERGE debe evitar crear alertas repetidas en ventana de 15 días");
    }

    @Test
    @DisplayName("OBS-01 & OBS-05: Validación determinística de 8 escenarios canónicos de estado dinámico")
    public void testCalcularEstadoDinamicoDeterministaOchoCasos() {
        LocalDate ref = LocalDate.of(2026, 9, 23);

        // Caso 1: Póliza vigente fuera del umbral (vence el 31-Dic con 45 días de alerta, umbral es 16-Nov)
        String st1 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 45, ref);
        assertEquals("VIGENTE", st1, "Caso 1: Fuera del umbral de alerta debe ser VIGENTE");

        // Caso 2: Exactamente en el límite del umbral (vence el 07-Nov con 45 días de alerta: 07-Nov - 45 días = 23-Sep)
        String st2 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 7), 45, ref);
        assertEquals("POR_VENCER", st2, "Caso 2: Exactamente en el umbral debe ser POR_VENCER");

        // Caso 3: Dentro del período POR_VENCER (vence el 15-Oct, dentro de la ventana de 45 días)
        String st3 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15), 45, ref);
        assertEquals("POR_VENCER", st3, "Caso 3: Dentro de la ventana de alerta debe ser POR_VENCER");

        // Caso 4: Exactamente en FECHA_FIN (vence hoy 23-Sep, último día de vigencia)
        String st4 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2025, 9, 23), LocalDate.of(2026, 9, 23), 45, ref);
        assertEquals("POR_VENCER", st4, "Caso 4: Exactamente en fechaFin debe ser POR_VENCER");

        // Caso 5: Después de FECHA_FIN (venció ayer 22-Sep)
        String st5 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2025, 9, 22), LocalDate.of(2026, 9, 22), 45, ref);
        assertEquals("VENCIDA", st5, "Caso 5: Posterior a fechaFin debe ser VENCIDA");

        // Caso 6: Póliza cancelada dentro de ventana de vencimiento (prioridad absoluta de CANCELADA)
        String st6 = PolizaSeguroService.calcularEstadoDinamico(
                "CANCELADA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15), 45, ref);
        assertEquals("CANCELADA", st6, "Caso 6: CANCELADA tiene prioridad absoluta dentro de ventana");

        // Caso 7: Póliza cancelada después de vencimiento (prioridad absoluta sobre VENCIDA)
        String st7 = PolizaSeguroService.calcularEstadoDinamico(
                "CANCELADA", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 30), 45, ref);
        assertEquals("CANCELADA", st7, "Caso 7: CANCELADA tiene prioridad absoluta tras vencimiento");

        // Caso 8: Diferentes valores de DIAS_ALERTA_VENCIMIENTO
        // Póliza que vence el 20-Oct (a 27 días de ref 23-Sep)
        LocalDate fin20Oct = LocalDate.of(2026, 10, 20);
        // Con 15 días: umbral es 05-Oct -> ref 23-Sep queda fuera -> VIGENTE
        String st8a = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), fin20Oct, 15, ref);
        assertEquals("VIGENTE", st8a, "Caso 8a: Con 15 días de alerta debe estar VIGENTE");

        // Con 30 días: umbral es 20-Sep -> ref 23-Sep queda dentro -> POR_VENCER
        String st8b = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), fin20Oct, 30, ref);
        assertEquals("POR_VENCER", st8b, "Caso 8b: Con 30 días de alerta debe estar POR_VENCER");

        // Con null (usa default canónico 45): umbral es 05-Sep -> ref 23-Sep queda dentro -> POR_VENCER
        String st8c = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 1, 1), fin20Oct, null, ref);
        assertEquals("POR_VENCER", st8c, "Caso 8c: Con null debe aplicar default 45 días (POR_VENCER)");

        // Caso 9 (Consistencia temporal): Fecha previa a fechaInicio (no inventar PENDIENTE, debe ser VIGENTE)
        String st9 = PolizaSeguroService.calcularEstadoDinamico(
                "VIGENTE", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1), 45, ref);
        assertEquals("VIGENTE", st9, "Caso 9: Póliza futura previa a inicio es VIGENTE sin inventar estados");
    }
}
