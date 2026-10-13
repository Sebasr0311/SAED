package com.saed.backend.finanzas;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoProveedorCreateDTO;
import com.saed.backend.finanzas.repository.ContratoProveedorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F09 integration (finance side): supplier contracts validate their request, answer 404 (not a 403 that
 * names the other organization) for foreign records, always filter by the session property and validate
 * the target state. Mocked JDBC; no database.
 */
class ContratoProveedorRepositoryTest {
    private static final long PROPERTY = 100L;

    private NamedParameterJdbcTemplate jdbc;
    private ContratoProveedorRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new ContratoProveedorRepository(jdbc);
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(10L).propertyId(PROPERTY)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static ContratoProveedorCreateDTO request() {
        return new ContratoProveedorCreateDTO(7L, "C-001", "Mantenimiento ascensores", new BigDecimal("1000000"),
                "MENSUAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 30);
    }

    @Test
    void creationRunsTheBusinessValidatorBeforeAnyDatabaseWork() {
        ContratoProveedorCreateDTO invalid = new ContratoProveedorCreateDTO(7L, "C-001", "Objeto", new BigDecimal("10"),
                "MENSUAL", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), 30);

        assertThrows(IllegalArgumentException.class, () -> repo.crear(invalid));
        assertThrows(IllegalArgumentException.class, () -> repo.crear(new ContratoProveedorCreateDTO(7L, null, "Objeto",
                new BigDecimal("10"), "MENSUAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 30)));

        verifyNoInteractions(jdbc);
    }

    @Test
    void aSupplierOfAnotherOrganizationIsNotFoundWithoutNamingIt() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map.of(
                "ID_PROVEEDOR", 7L, "PROV_ORG_ID", 99L, "PROV_ESTADO", "ACTIVO", "PROP_ORG_ID", 10L)));

        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> repo.crear(request()));

        assertFalse(ex.getMessage().contains("99"), ex.getMessage());
        assertFalse(ex.getMessage().toLowerCase().contains("organizaci"), ex.getMessage());
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class),
                any(org.springframework.jdbc.support.KeyHolder.class), any(String[].class));
    }

    @Test
    void anUnknownSupplierIsAlsoNotFound() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        assertThrows(NoSuchElementException.class, () -> repo.crear(request()));
    }

    @Test
    void listingRequiresAPropertyInTheSession() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(10L)
                .roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        assertThrows(AccessDeniedException.class, () -> repo.listar());
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingAlwaysFiltersByTheSessionProperty() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repo.listar();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("cp.ID_PROPIEDAD = :propId"), sql.getValue());
        assertEquals(PROPERTY, params.getValue().getValue("propId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BORRADOR", "CANCELADO", "", "vigente ; DROP"})
    void anInvalidTargetStateIsRefused(String state) {
        assertThrows(IllegalArgumentException.class, () -> repo.actualizarEstado(5L, state));

        verify(jdbc, never()).update(anyString(), anyMap());
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void aValidStateIsNormalizedAndApplied() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        repo.actualizarEstado(5L, "suspendido");

        ArgumentCaptor<Map<String, ?>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).update(contains("UPDATE CONTRATOS_PROVEEDOR"), params.capture());
        assertEquals("SUSPENDIDO", params.getValue().get("estado"));
    }

    @Test
    void aForeignContractIsNotFoundWhenChangingItsStateOrDeletingIt() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        assertThrows(NoSuchElementException.class, () -> repo.actualizarEstado(5L, "VIGENTE"));
        assertThrows(NoSuchElementException.class, () -> repo.eliminar(5L));
    }
}
