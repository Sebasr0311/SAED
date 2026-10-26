package com.saed.backend.person;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.controller.DashboardController;
import com.saed.backend.identity.controller.UsuarioController;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.repository.PersonaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class DiferenciacionPropietarioResidenteTest {

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private UsuarioController usuarioController;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private com.saed.backend.person.service.PersonaService personaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Test-owned unit and person: the demo unit 1 / person 4 are the seeded household of Apto 101 and must not be
    // re-assigned, deactivated or unlinked by this suite.
    private static final Long TEST_UNIT_ID = 9960L;
    private static final Long TEST_PERSONA_ID = 9970L;
    private static final String TEST_UNIT_IDENTIFIER = "T-9960";

    private void asSuperadmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
    }

    /** DEC-02: SUPERADMIN has no operational access; the suite acts as the demo property administrator (user 2). */
    private void asPropertyAdmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).organizationId(1L).propertyId(1L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", "n/a",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"))));
    }

    private void cleanFixture() {
        asSuperadmin();
        jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ?", TEST_UNIT_ID);
        jdbcTemplate.update("DELETE FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ?", TEST_UNIT_ID);
        jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_PERSONA IN (SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO LIKE 'BATCH_%')");
        jdbcTemplate.update("DELETE FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA IN (SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO LIKE 'BATCH_%')");
    }

    @BeforeEach
    public void setup() {
        asSuperadmin();
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
        Integer unit = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?", Integer.class, TEST_UNIT_ID);
        if (unit == null || unit == 0) {
            jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (?, 1, 1, ?, 'ACTIVA')",
                    TEST_UNIT_ID, TEST_UNIT_IDENTIFIER);
        }
        Integer persona = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = ?", Integer.class, TEST_PERSONA_ID);
        if (persona == null || persona == 0) {
            jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO, ID_ORGANIZACION, ID_PROPIEDAD) "
                    + "VALUES (?, 1, '1000009970', 'NATURAL', 'Diferenciacion', 'Test', 'dif9970@saed.com', 'ACTIVO', 1, 1)", TEST_PERSONA_ID);
        }
        // The application start-up clears the tenant columns of persons without a household link, so they are set on every run.
        jdbcTemplate.update("UPDATE PERSONAS SET ESTADO = 'ACTIVO', ID_ORGANIZACION = 1, ID_PROPIEDAD = 1 WHERE ID_PERSONA = ?", TEST_PERSONA_ID);
        cleanFixture();
        asPropertyAdmin();
    }

    @AfterEach
    public void tearDown() {
        try {
            cleanFixture();
            jdbcTemplate.update("UPDATE PERSONAS SET ESTADO = 'ACTIVO' WHERE ID_PERSONA = ?", TEST_PERSONA_ID);
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        SaedContextHolder.clearContext();
    }

    @Test
    public void testAsignarPropietarioNoResidente_InsertsPropietarioAndDeactivatesResidente() {
        Map<String, Object> payload = Map.of(
                "idApartamento", TEST_UNIT_ID,
                "tipoRelacion", "PROPIETARIO_NO_RESIDENTE"
        );

        ResponseEntity<Void> response = dashboardController.asignarApartamento(TEST_PERSONA_ID, payload);
        assertEquals(200, response.getStatusCode().value());

        // Debe existir en PROPIETARIOS_UNIDAD con estado ACTIVO
        Integer propActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertNotNull(propActivo);
        assertTrue(propActivo > 0, "El propietario no residente debe estar activo en PROPIETARIOS_UNIDAD");

        // NO debe existir en RESIDENTES_UNIDAD con estado ACTIVO
        Integer resActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertEquals(0, resActivo != null ? resActivo : 0, "El propietario no residente NO debe figurar activo en RESIDENTES_UNIDAD");
    }

    @Test
    public void testAsignarPropietarioResidente_ActiveInBothTables() {
        Map<String, Object> payload = Map.of(
                "idApartamento", TEST_UNIT_ID,
                "tipoRelacion", "PROPIETARIO_RESIDENTE"
        );

        ResponseEntity<Void> response = dashboardController.asignarApartamento(TEST_PERSONA_ID, payload);
        assertEquals(200, response.getStatusCode().value());

        Integer propActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertTrue(propActivo != null && propActivo > 0);

        Integer resActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'PROPIETARIO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertTrue(resActivo != null && resActivo > 0);
    }

    @Test
    public void testAsignarArrendatario_ActiveOnlyInResidentesUnidad() {
        Map<String, Object> payload = Map.of(
                "idApartamento", TEST_UNIT_ID,
                "tipoRelacion", "ARRENDATARIO",
                "sinContrato", true // a tenant needs an active contract or an explicit no-contract confirmation
        );

        ResponseEntity<Void> response = dashboardController.asignarApartamento(TEST_PERSONA_ID, payload);
        assertEquals(200, response.getStatusCode().value());

        Integer resActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertTrue(resActivo != null && resActivo > 0);

        Integer propActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_UNIT_ID, TEST_PERSONA_ID
        );
        assertEquals(0, propActivo != null ? propActivo : 0);
    }

    @Test
    public void testPersonaDTO_TipoRelacionConstructorCompatibility() {
        PersonaDTO dto = new PersonaDTO(
                1L, 1L, "12345", "NATURAL", "Carlos", "A", "Gomez", "R",
                "carlos@saed.com", "3001234567", "ACTIVO", 10L, "101", "PROPIETARIO_NO_RESIDENTE"
        );
        assertEquals("PROPIETARIO_NO_RESIDENTE", dto.tipoRelacion());
        assertEquals("101", dto.numeroApartamento());
    }

    @Test
    public void testDesvinculacionProtegida_SoftDeletePreservaHistorico() {
        // Requisitos #14 y #15: No destrucción física, soft-delete y preservación de auditoría
        // F05-01: unlinking is scoped to the caller's property; the identity is deactivated only when no active
        // link remains (this fixture has links only in that property).
        dashboardController.asignarApartamento(TEST_PERSONA_ID, Map.of("idApartamento", TEST_UNIT_ID, "tipoRelacion", "ARRENDATARIO", "sinContrato", true));
        Long propId = jdbcTemplate.queryForObject(
                "SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = ?", Long.class, TEST_UNIT_ID);
        Long orgId = jdbcTemplate.queryForObject(
                "SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = ?", Long.class, propId);
        personaRepository.unlinkFromScope(TEST_PERSONA_ID, propId, orgId);

        String estadoPersona = jdbcTemplate.queryForObject(
                "SELECT ESTADO FROM PERSONAS WHERE ID_PERSONA = ?",
                String.class,
                TEST_PERSONA_ID
        );
        assertEquals("INACTIVO", estadoPersona, "El habitante debe pasar a estado INACTIVO");

        Integer resActivos = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class,
                TEST_PERSONA_ID
        );
        assertEquals(0, resActivos != null ? resActivos : 0, "No deben quedar registros activos en RESIDENTES_UNIDAD");
    }

    @Test
    public void testImportarBatch_CreacionYAsignacion() {
        // Requisito #16: Carga masiva por lotes (Batch Ingestion)
        String docTest = "BATCH_" + System.currentTimeMillis();
        com.saed.backend.person.dto.PersonaBatchItemDTO item = new com.saed.backend.person.dto.PersonaBatchItemDTO(
                "CC",
                docTest,
                "Maria Camila",
                "Restrepo Ochoa",
                "maria.batch@saed.com",
                "3119876543",
                TEST_UNIT_IDENTIFIER,
                "PROPIETARIO_RESIDENTE"
        );

        com.saed.backend.person.dto.PersonaBatchResultDTO resultado = personaService.importarBatch(java.util.List.of(item));
        assertNotNull(resultado);
        assertEquals(1, resultado.total());
        assertEquals(1, resultado.procesados());
        assertEquals(0, resultado.fallidos());

        // Verificar que la persona se haya creado y asignado
        java.util.Optional<PersonaDTO> creada = personaRepository.findByNumeroDocumento(docTest);
        assertTrue(creada.isPresent(), "La persona debe haber sido persistida");
        assertEquals("PROPIETARIO_RESIDENTE", creada.get().tipoRelacion());
    }
}
