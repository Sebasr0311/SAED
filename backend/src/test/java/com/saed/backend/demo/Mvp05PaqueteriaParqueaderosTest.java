package com.saed.backend.demo;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.convivencia.controller.BuzonController;
import com.saed.backend.paquetes.controller.PaquetesController;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.parqueaderos.controller.ParqueaderosController;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;
import com.saed.backend.porteria.controller.PorteriaController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Mvp05PaqueteriaParqueaderosTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaquetesController paquetesController;

    @Autowired
    private BuzonController buzonController;

    @Autowired
    private ParqueaderosController parqueaderosController;

    @Autowired
    private PorteriaController porteriaController;

    private static Long createdPaqueteId;
    private static String createdPaquetePin;

    @BeforeEach
    public void setupContext() {
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void tearDownContext() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(1)
    @WithMockUser(authorities = {"SCOPE_PORTERO"}, username = "portero")
    @DisplayName("MVP-05: Flow A1 - Portero registers package for Unit 1, notification created")
    public void test01_porteroRegistersPackageAndDispatchesNotification() {
        // Context: Portero (User 3, Prop 1, Org 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(1L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 1, 'PORTERO'); END;");

        PaqueteRequestDTO request = new PaqueteRequestDTO(
                1L, // idUnidad (Apto 101)
                4L, // idPersonaDestinatario (Carlos Martinez)
                "Servientrega", // empresaMensajeria
                "TRK-MVP05-9988", // numeroGuia
                "Caja mediana con monitor de computadora", // descripcion
                "MEDIANO", // tamano
                null, // fotoPaqueteUrl
                1L // idPorteria
        );

        ResponseEntity<PaqueteDTO> response = paquetesController.registrarPaquete(request);
        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());

        PaqueteDTO paquete = response.getBody();
        assertNotNull(paquete);
        assertNotNull(paquete.idPaquete());
        createdPaqueteId = paquete.idPaquete();
        createdPaquetePin = paquete.codigoRetiroPin();

        assertEquals(1L, paquete.idUnidad());
        assertEquals("RECIBIDO", paquete.estado());
        assertEquals("Apto 101", paquete.numeroApartamento());
        assertNotNull(createdPaquetePin, "PIN must be generated upon registration");

        // Verify in-app notification in NOTIFICACIONES table for resident of Unit 1 (User 4, Carlos Martinez)
        Integer notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM NOTIFICACIONES WHERE ID_USUARIO_DESTINATARIO = 4 AND CANAL = 'IN_APP'",
                Integer.class
        );
        assertNotNull(notifCount);
        assertTrue(notifCount >= 1, "At least one package notification must be registered for resident Carlos Martinez");
    }

    @Test
    @Order(2)
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username = "camartinez")
    @DisplayName("MVP-05: Flow A2 - Resident queries packages and views their package")
    public void test02_residentViewsPackageAndNotification() {
        assertNotNull(createdPaqueteId, "Package must have been created in test01");

        // Context: Resident of Unit 1 (User 4, Prop 1, Org 1, Unit 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(4L).organizationId(1L).propertyId(1L).unitId(1L)
                .roleCode("RESIDENTE").roleScope("UNIDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(4); PKG_SAED_SESSION.SET_CONTEXT(4, 1, 1, 'RESIDENTE'); END;");

        // 1. Query /api/v1/paquetes as resident
        ResponseEntity<List<PaqueteDTO>> paquetesResp = paquetesController.getPaquetes();
        assertNotNull(paquetesResp);
        List<PaqueteDTO> paquetes = paquetesResp.getBody();
        assertNotNull(paquetes);
        assertTrue(paquetes.stream().anyMatch(p -> createdPaqueteId.equals(p.idPaquete())),
                "Resident must see their registered package");

        // 2. Query package by ID
        ResponseEntity<PaqueteDTO> singleResp = paquetesController.getPaqueteById(createdPaqueteId);
        assertNotNull(singleResp);
        PaqueteDTO single = singleResp.getBody();
        assertNotNull(single);
        assertEquals(createdPaqueteId, single.idPaquete());
        assertEquals("Apto 101", single.numeroApartamento());

        // 3. Query /api/v1/buzon?idApartamento=1
        ResponseEntity<?> buzonResp = buzonController.getMyBuzon(1L);
        assertNotNull(buzonResp);
        assertTrue(buzonResp.getBody() instanceof List);
        @SuppressWarnings("unchecked")
        List<PaqueteDTO> buzonPaquetes = (List<PaqueteDTO>) buzonResp.getBody();
        assertTrue(buzonPaquetes.stream().anyMatch(p -> createdPaqueteId.equals(p.idPaquete())));
    }

    @Test
    @Order(3)
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username = "ana_residente2")
    @DisplayName("MVP-05: Flow A3 - Anti-IDOR: Foreign resident denied access to Unit 1 package")
    public void test03_antiIdorProtectionForeignResidentDenied() {
        assertNotNull(createdPaqueteId, "Package must have been created in test01");

        // Context: Resident of Unit 2 (User 5, Prop 1, Org 1, Unit 2)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(5L).organizationId(1L).propertyId(1L).unitId(2L)
                .roleCode("RESIDENTE").roleScope("UNIDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(5); PKG_SAED_SESSION.SET_CONTEXT(5, 1, 1, 'RESIDENTE'); END;");

        // 1. Attempt to get package belonging to Unit 1
        assertThrows(AccessDeniedException.class, () -> {
            paquetesController.getPaqueteById(createdPaqueteId);
        }, "Resident from Unit 2 must be denied access to Unit 1's package (Anti-IDOR)");

        // 2. Attempt to query buzon of Unit 1
        assertThrows(AccessDeniedException.class, () -> {
            buzonController.getMyBuzon(1L);
        }, "Resident from Unit 2 must be denied querying Unit 1 packages directly");
    }

    @Test
    @Order(4)
    @WithMockUser(authorities = {"SCOPE_PORTERO"}, username = "portero")
    @DisplayName("MVP-05: Flow A4 - Delivery/Retiro of package transitions to ENTREGADO")
    public void test04_deliverPackageTransitionsState() {
        assertNotNull(createdPaqueteId, "Package must have been created in test01");

        // Context: Portero (User 3, Prop 1, Org 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(1L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 1, 'PORTERO'); END;");

        // Call direct entrega via compatibility endpoint
        ResponseEntity<Void> entregaResp = buzonController.marcarPaqueteEntregado(createdPaqueteId);
        assertNotNull(entregaResp);
        assertEquals(200, entregaResp.getStatusCode().value());

        // Verify state is ENTREGADO in database
        String estadoDb = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM PAQUETES WHERE ID_PAQUETE = ?",
                String.class,
                createdPaqueteId
        );
        assertEquals("ENTREGADO", estadoDb);

        // Verify through controller query
        ResponseEntity<PaqueteDTO> resp = paquetesController.getPaqueteById(createdPaqueteId);
        assertNotNull(resp.getBody());
        assertEquals("ENTREGADO", resp.getBody().estado());
    }

    @Test
    @Order(5)
    @WithMockUser(authorities = {"SCOPE_PORTERO"}, username = "portero")
    @DisplayName("MVP-05: Flow B1 - Portero queries parking spots, verifies V-01 and demo vehicle DEM-123")
    public void test05_parqueaderosConsultAndVisitorVehicleAssociation() {
        // Context: Portero (User 3, Prop 1, Org 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(1L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 1, 'PORTERO'); END;");

        ResponseEntity<List<ParqueaderoDTO>> response = parqueaderosController.getParqueaderos(null, null);
        assertNotNull(response);
        List<ParqueaderoDTO> spots = response.getBody();
        assertNotNull(spots);
        assertTrue(spots.size() >= 3, "There should be at least 3 seeded spots (V-01, V-02, P-101)");

        // Check V-01 spot
        ParqueaderoDTO v01 = spots.stream()
                .filter(p -> "V-01".equalsIgnoreCase(p.numeroParqueadero()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Spot V-01 not found"));

        assertTrue(v01.esVisitante(), "V-01 must be flagged as visitor spot");
        assertEquals("VISITANTES", v01.tipo());
        assertEquals("DEM-123", v01.placaVehiculo(), "Spot V-01 must be linked to active visitor vehicle DEM-123");

        // Test filter by tipo
        ResponseEntity<List<ParqueaderoDTO>> visitorSpotsResp = parqueaderosController.getParqueaderos(null, "VISITANTES");
        assertNotNull(visitorSpotsResp.getBody());
        for (ParqueaderoDTO s : visitorSpotsResp.getBody()) {
            assertEquals("VISITANTES", s.tipo());
        }

        // Verify Access Log contains vehicle plate DEM-123
        List<com.saed.backend.porteria.dto.RegistroAccesoDTO> registros = porteriaController.getRegistrosByPropiedad(1L);
        assertNotNull(registros);
        assertTrue(registros.stream().anyMatch(r -> "DEM-123".equalsIgnoreCase(r.placaVehiculo())),
                "Access register (REGISTROS_ACCESO) must contain vehicle plate DEM-123");
    }

    @Test
    @Order(6)
    @WithMockUser(authorities = {"SCOPE_PORTERO"}, username = "portero")
    @DisplayName("MVP-05: Flow B2 - Register visitor vehicle departure, spot V-01 becomes free")
    public void test06_visitorVehicleDepartureFreesSpot() {
        // Context: Portero (User 3, Prop 1, Org 1)
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(1L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 1, 'PORTERO'); END;");

        // 1. Register vehicle exit for VEHICULOS_VISITA ID = 1
        porteriaController.registrarSalidaVehiculo(1L, Map.of("costoTotal", BigDecimal.ZERO));

        // 2. Verify state in DB is 'SALIO'
        String estadoVeh = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM VEHICULOS_VISITA WHERE ID_VEHICULO_VISITA = 1",
                String.class
        );
        assertEquals("SALIO", estadoVeh);

        // 3. Verify spot V-01 no longer reflects DEM-123 as active
        ResponseEntity<List<ParqueaderoDTO>> response = parqueaderosController.getParqueaderos(null, null);
        assertNotNull(response.getBody());
        ParqueaderoDTO v01 = response.getBody().stream()
                .filter(p -> "V-01".equalsIgnoreCase(p.numeroParqueadero()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Spot V-01 not found"));

        assertNull(v01.placaVehiculo(), "Spot V-01 must not have an active vehicle after exit");

        // 4. Restore DEM-123 to 'DENTRO' to keep dataset idempotent for future tests
        jdbcTemplate.update("UPDATE VEHICULOS_VISITA SET ESTADO = 'DENTRO' WHERE ID_VEHICULO_VISITA = 1");
    }

    @Test
    @Order(7)
    @WithMockUser(authorities = {"SCOPE_PORTERO"}, username = "portero")
    @DisplayName("MVP-05: Flow C - Multi-Tenant Isolation: Foreign property cannot access Property 1 resources")
    public void test07_multiTenantIsolationUnderRls() {
        // Ensure foreign property 999994 and assignment exist
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        try {
            jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999994 AS id, 1 AS o, 1 AS t, 'Prop Test 999994' AS n, 'Calle 101 # 10-20' AS dir, 'Bogota' AS ciu, 'Colombia' AS pai, 'MIXTA' AS ocu, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pai, s.ocu, s.st)");
            Long idRolPortero = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 106 AS id, 3 AS u, ? AS r, 1 AS o, 999994 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPortero);
        } catch (Exception ignored) {}

        // Context: Portero for foreign property 999994
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).organizationId(1L).propertyId(999994L)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(3); PKG_SAED_SESSION.SET_CONTEXT(3, 1, 999994, 'PORTERO'); END;");

        // Query parking spots
        ResponseEntity<List<ParqueaderoDTO>> spotsResp = parqueaderosController.getParqueaderos(null, null);
        assertNotNull(spotsResp.getBody());
        // Under RLS, spots from Property 1 (V-01, V-02, P-101) must NOT appear
        boolean hasProp1Spots = spotsResp.getBody().stream()
                .anyMatch(p -> Long.valueOf(1L).equals(p.idPropiedad()));
        assertFalse(hasProp1Spots, "Foreign property context must not see Property 1 parking spots under RLS");

        // Query packages
        ResponseEntity<List<PaqueteDTO>> paquetesResp = paquetesController.getPaquetes();
        assertNotNull(paquetesResp.getBody());
        boolean hasProp1Packages = paquetesResp.getBody().stream()
                .anyMatch(p -> createdPaqueteId != null && createdPaqueteId.equals(p.idPaquete()));
        assertFalse(hasProp1Packages, "Foreign property context must not see Property 1 packages under RLS");
    }
}
