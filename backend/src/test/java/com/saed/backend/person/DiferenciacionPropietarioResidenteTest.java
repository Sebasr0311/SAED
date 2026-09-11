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
    private JdbcTemplate jdbcTemplate;

    private static final Long TEST_UNIT_ID = 1L;
    private static final Long TEST_PERSONA_ID = 4L;

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin_global",
                        "n/a",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_SUPERADMIN"))
                )
        );

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @AfterEach
    public void tearDown() {
        try {
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
                "tipoRelacion", "ARRENDATARIO"
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
}
