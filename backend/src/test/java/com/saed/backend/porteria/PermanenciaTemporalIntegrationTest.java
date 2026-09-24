package com.saed.backend.porteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.authorization.service.PropertyConfigService;
import com.saed.backend.authorization.service.PropertyConfigServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;
import com.saed.backend.domicilios.service.DomicilioService;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.dto.VisitaDetalleDTO;
import com.saed.backend.porteria.dto.VisitaListDTO;
import com.saed.backend.porteria.dto.VisitaRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PermanenciaTemporalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DomicilioService domicilioService;

    @Autowired
    private PorteriaService porteriaService;

    @Autowired
    private PropertyConfigService propertyConfigService;

    @MockBean
    private AssignmentService assignmentService;

    private Long testPropiedadId = 1L;
    private Long testPorteriaId = 1L;
    private Long testUnidadId = 1L;
    private Long testPersonaId = 1L;
    private Long testVisitanteId = 1L;
    private final Long testPorteroUserId = 3L;
    private final Long testAssignmentId = 103L;

    private final List<Long> createdDomicilios = new ArrayList<>();
    private final List<Long> createdVisitas = new ArrayList<>();

    private void setElevatedContext() {
        SaedContext ctx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(testPropiedadId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        setElevatedContext();

        try {
            List<Map<String, Object>> props = jdbcTemplate.queryForList("SELECT ID_PROPIEDAD FROM PROPIEDADES ORDER BY ID_PROPIEDAD ASC");
            if (!props.isEmpty()) {
                testPropiedadId = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
            }

            List<Map<String, Object>> units = jdbcTemplate.queryForList(
                    "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ? ORDER BY ID_UNIDAD ASC",
                    testPropiedadId
            );
            if (!units.isEmpty()) {
                testUnidadId = ((Number) units.get(0).get("ID_UNIDAD")).longValue();
            }

            List<Map<String, Object>> ports = jdbcTemplate.queryForList(
                    "SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = ? ORDER BY ID_PORTERIA ASC",
                    testPropiedadId
            );
            if (!ports.isEmpty()) {
                testPorteriaId = ((Number) ports.get(0).get("ID_PORTERIA")).longValue();
            }

            List<Map<String, Object>> persons = jdbcTemplate.queryForList(
                    "SELECT ID_PERSONA FROM PERSONAS ORDER BY ID_PERSONA ASC"
            );
            if (!persons.isEmpty()) {
                testPersonaId = ((Number) persons.get(0).get("ID_PERSONA")).longValue();
            }

            List<Map<String, Object>> vis = jdbcTemplate.queryForList(
                    "SELECT ID_VISITANTE FROM VISITANTES ORDER BY ID_VISITANTE ASC"
            );
            if (!vis.isEmpty()) {
                testVisitanteId = ((Number) vis.get(0).get("ID_VISITANTE")).longValue();
            }
        } catch (Exception ignored) {}

        // Mock assignment
        AssignmentResponseDTO porteroAssignment = new AssignmentResponseDTO();
        porteroAssignment.setIdAsignacion(testAssignmentId);
        porteroAssignment.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        porteroAssignment.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(testPropiedadId);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Propiedad Test");
        porteroAssignment.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(testAssignmentId, testPorteroUserId))
                .thenReturn(Optional.of(porteroAssignment));

        // Ensure clean config before each test
        try {
            jdbcTemplate.update("DELETE FROM PROPIEDAD_CONFIGURACION WHERE ID_PROPIEDAD = ? AND CLAVE IN (?, ?)",
                    testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA);
        } catch (Exception ignored) {}
    }

    @AfterEach
    void tearDown() {
        setElevatedContext();
        for (Long id : createdDomicilios) {
            try {
                jdbcTemplate.update("DELETE FROM DOMICILIOS WHERE ID_DOMICILIO = ?", id);
            } catch (Exception ignored) {}
        }
        for (Long id : createdVisitas) {
            try {
                jdbcTemplate.update("DELETE FROM REGISTROS_ACCESO WHERE ID_VISITA = ?", id);
                jdbcTemplate.update("DELETE FROM VISITAS WHERE ID_VISITA = ?", id);
            } catch (Exception ignored) {}
        }
        try {
            jdbcTemplate.update("DELETE FROM PROPIEDAD_CONFIGURACION WHERE ID_PROPIEDAD = ? AND CLAVE IN (?, ?)",
                    testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Test 1: Cálculo de minutos transcurridos para visita activa (EN_CURSO)")
    void test01_calculoMinutosTranscurridos_visitaActiva_enCurso() {
        setElevatedContext();

        VisitaRequestDTO req = new VisitaRequestDTO(
                testUnidadId, testVisitanteId, "LLAMADA_PORTERIA", "Visita familiar", testPersonaId, null, "EN_CURSO"
        );
        VisitaDTO saved = porteriaService.programarVisita(req);
        Long idVisita = saved.idVisita();
        createdVisitas.add(idVisita);

        // Bitácora de entrada 45 minutos atrás
        jdbcTemplate.update("""
            INSERT INTO REGISTROS_ACCESO (
                ID_PROPIEDAD, ID_PORTERIA, ID_PERSONA, ID_UNIDAD, ID_VISITA,
                TIPO_MOVIMIENTO, METODO_AUTORIZACION, FECHA_HORA
            ) VALUES (
                ?, ?, ?, ?, ?, 'ENTRADA', 'MANUAL_PORTERO', SYSTIMESTAMP - INTERVAL '45' MINUTE
            )
        """, testPropiedadId, testPorteriaId, testPersonaId, testUnidadId, idVisita);

        List<VisitaListDTO> lista = porteriaService.getVisitasResumen();
        VisitaListDTO visita = lista.stream()
                .filter(v -> idVisita.equals(v.idVisita()))
                .findFirst()
                .orElse(null);

        assertNotNull(visita, "La visita activa debe ser retornada en el resumen");
        assertNotNull(visita.minutosTranscurridos(), "Los minutos transcurridos no deben ser nulos");
        assertTrue(visita.minutosTranscurridos() >= 40 && visita.minutosTranscurridos() <= 50,
                "Debe calcular aproximadamente 45 minutos transcurridos, actual: " + visita.minutosTranscurridos());
        assertEquals(240, visita.tiempoMaximoMinutos(), "El límite por defecto de visita debe ser 240m");
        assertFalse(visita.excedido(), "No debe estar excedido (45 < 240)");
    }

    @Test
    @DisplayName("Test 2: Cálculo de minutos transcurridos para domicilio activo (EN_CURSO)")
    void test02_calculoMinutosTranscurridos_domicilioActivo_enCurso() {
        setElevatedContext();

        DomicilioCreateDTO createDTO = new DomicilioCreateDTO(
                testUnidadId, "Rappi", "Carlos Repartidor",
                "10998877", "3001234567", "COMIDA", "G-100", "MOTO", "XYZ-123", "Piso 3",
                testPorteriaId, testPersonaId
        );
        DomicilioDTO saved = domicilioService.registrarDomicilio(createDTO);
        createdDomicilios.add(saved.idDomicilio());

        // Simular entrada hace 20 minutos
        jdbcTemplate.update(
                "UPDATE DOMICILIOS SET FECHA_ENTRADA = SYSTIMESTAMP - INTERVAL '20' MINUTE WHERE ID_DOMICILIO = ?",
                saved.idDomicilio()
        );

        DomicilioDTO dom = domicilioService.buscarPorId(saved.idDomicilio());
        assertNotNull(dom);
        assertNotNull(dom.minutosTranscurridos());
        assertTrue(dom.minutosTranscurridos() >= 18 && dom.minutosTranscurridos() <= 25,
                "Debe reflejar aprox 20 min transcurridos, actual: " + dom.minutosTranscurridos());
        assertEquals(30, dom.tiempoMaximoMinutos());
        assertFalse(dom.excedido());
    }

    @Test
    @DisplayName("Test 3: Bandera excedido = false cuando permanencia < tiempo_maximo")
    void test03_banderaExcedidoFalse_cuandoPermanenciaMenorQueTiempoMaximo() {
        setElevatedContext();

        DomicilioCreateDTO createDTO = new DomicilioCreateDTO(
                testUnidadId, "UberEats", "Andres Gomez",
                "10223344", "3119876543", "COMIDA", null, "BICICLETA", null, "Sin notas",
                testPorteriaId, testPersonaId
        );
        DomicilioDTO saved = domicilioService.registrarDomicilio(createDTO);
        createdDomicilios.add(saved.idDomicilio());

        // Simular entrada hace 10 minutos (límite es 30)
        jdbcTemplate.update(
                "UPDATE DOMICILIOS SET FECHA_ENTRADA = SYSTIMESTAMP - INTERVAL '10' MINUTE WHERE ID_DOMICILIO = ?",
                saved.idDomicilio()
        );

        DomicilioDTO dom = domicilioService.buscarPorId(saved.idDomicilio());
        assertNotNull(dom);
        assertTrue(dom.minutosTranscurridos() < dom.tiempoMaximoMinutos());
        assertFalse(dom.excedido(), "excedido debe ser false cuando permanencia < 30");
    }

    @Test
    @DisplayName("Test 4: Bandera excedido = true cuando permanencia > tiempo_maximo")
    void test04_banderaExcedidoTrue_cuandoPermanenciaMayorQueTiempoMaximo() {
        setElevatedContext();

        DomicilioCreateDTO createDTO = new DomicilioCreateDTO(
                testUnidadId, "MercadoLibre", "Pedro Logistica",
                "10887766", "3154567890", "PAQUETE_EXPRESS", "ML-9988", "CARRO", "ABC-789", "Entrega pesada",
                testPorteriaId, testPersonaId
        );
        DomicilioDTO saved = domicilioService.registrarDomicilio(createDTO);
        createdDomicilios.add(saved.idDomicilio());

        // Simular entrada hace 50 minutos (límite default es 30)
        jdbcTemplate.update(
                "UPDATE DOMICILIOS SET FECHA_ENTRADA = SYSTIMESTAMP - INTERVAL '50' MINUTE WHERE ID_DOMICILIO = ?",
                saved.idDomicilio()
        );

        DomicilioDTO dom = domicilioService.buscarPorId(saved.idDomicilio());
        assertNotNull(dom);
        assertTrue(dom.minutosTranscurridos() >= 45, "Minutos transcurridos >= 45");
        assertTrue(dom.excedido(), "excedido debe ser true cuando permanencia (50m) > tiempoMaximo (30m)");
    }

    @Test
    @DisplayName("Test 5: Obtención y aplicación de configuración personalizada por copropiedad vía PropertyConfigService")
    void test05_obtencionYAplicacion_configuracionPersonalizadaPorCopropiedad() {
        setElevatedContext();

        // Configurar tiempo máximo estricto de 15 minutos para domicilios en esta propiedad
        propertyConfigService.saveOrUpdate(testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, "15", "Límite estricto 15m");

        DomicilioCreateDTO createDTO = new DomicilioCreateDTO(
                testUnidadId, "Farmatodo", "Luis Mensajero",
                "10556677", "3176543210", "MEDICAMENTOS", null, "MOTO", "MED-001", "Urgente",
                testPorteriaId, testPersonaId
        );
        DomicilioDTO saved = domicilioService.registrarDomicilio(createDTO);
        createdDomicilios.add(saved.idDomicilio());

        // Simular entrada hace 20 minutos (20m > 15m)
        jdbcTemplate.update(
                "UPDATE DOMICILIOS SET FECHA_ENTRADA = SYSTIMESTAMP - INTERVAL '20' MINUTE WHERE ID_DOMICILIO = ?",
                saved.idDomicilio()
        );

        DomicilioDTO dom = domicilioService.buscarPorId(saved.idDomicilio());
        assertNotNull(dom);
        assertEquals(15, dom.tiempoMaximoMinutos(), "Debe respetar la configuración personalizada de 15 minutos");
        assertTrue(dom.excedido(), "Debe marcar excedido = true porque 20m > 15m");
    }

    @Test
    @DisplayName("Test 6: Fallback a valores por defecto (30 min domicilio, 240 min visita) ante configuración faltante")
    void test06_fallbackValoresPorDefecto_anteConfiguracionFaltante() {
        setElevatedContext();

        Long nonExistentPropId = 999988L;
        int maxDom = propertyConfigService.getIntValue(nonExistentPropId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, 30);
        int maxVis = propertyConfigService.getIntValue(nonExistentPropId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, 240);

        assertEquals(30, maxDom, "El fallback de domicilio debe ser exactamente 30 minutos");
        assertEquals(240, maxVis, "El fallback de visita debe ser exactamente 240 minutos");
    }

    @Test
    @DisplayName("Test 7: Validación de rechazo ante configuración no entera o <= 0")
    void test07_validacionRechazo_anteConfiguracionNoEnteraOMenorIgualCero() {
        setElevatedContext();

        // 1. Valor negativo
        assertThrows(IllegalArgumentException.class, () ->
                propertyConfigService.saveOrUpdate(testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, "-10", "Negativo")
        );

        // 2. Valor cero
        assertThrows(IllegalArgumentException.class, () ->
                propertyConfigService.saveOrUpdate(testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, "0", "Cero")
        );

        // 3. Valor no entero / alfanumérico
        assertThrows(IllegalArgumentException.class, () ->
                propertyConfigService.saveOrUpdate(testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, "invalid_num", "Invalido")
        );

        // 4. Valor vacío
        assertThrows(IllegalArgumentException.class, () ->
                propertyConfigService.saveOrUpdate(testPropiedadId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_VISITA, "   ", "Vacio")
        );
    }

    @Test
    @DisplayName("Test 8: Persistencia y preservación de tiempo total tras salida registrada (cálculo entre entrada y salida)")
    void test08_persistenciaYPreservacionTiempoTotal_trasSalidaRegistrada() {
        setElevatedContext();

        DomicilioCreateDTO createDTO = new DomicilioCreateDTO(
                testUnidadId, "DHL Express", "Hector Courier",
                "10778899", "3189998877", "MENSAJERIA", "DHL-5544", "CARRO", "DHL-901", "Entrega oficina",
                testPorteriaId, testPersonaId
        );
        DomicilioDTO saved = domicilioService.registrarDomicilio(createDTO);
        createdDomicilios.add(saved.idDomicilio());

        // Simular que entró hace 60 minutos y salió hace 15 minutos (tiempo total congelado: 45 minutos)
        jdbcTemplate.update("""
            UPDATE DOMICILIOS SET
                ESTADO = 'FINALIZADO',
                FECHA_ENTRADA = SYSTIMESTAMP - INTERVAL '60' MINUTE,
                FECHA_SALIDA = SYSTIMESTAMP - INTERVAL '15' MINUTE
            WHERE ID_DOMICILIO = ?
        """, saved.idDomicilio());

        DomicilioDTO finalizado = domicilioService.buscarPorId(saved.idDomicilio());
        assertNotNull(finalizado);
        assertEquals("FINALIZADO", finalizado.estado());
        assertNotNull(finalizado.minutosTranscurridos());
        assertTrue(finalizado.minutosTranscurridos() >= 44 && finalizado.minutosTranscurridos() <= 46,
                "El tiempo total congelado debe ser de 45 minutos (60 - 15), actual: " + finalizado.minutosTranscurridos());
        assertTrue(finalizado.excedido(), "Debe marcar excedido = true porque 45m > 30m");
    }
}
