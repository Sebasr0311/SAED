package com.saed.backend.person.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

/** DEC-01: every person belongs to the organization of the server-side context. */
class PersonaOrganizationBindingTest {

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void organizationComesFromTheContext() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(10L).propertyId(100L).build());

        assertEquals(10L, PersonaOrganizationBinding.contextOrganization(jdbc));
        verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void organizationIsDerivedFromThePropertyWhenTheContextHasNone() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).propertyId(100L).build());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of(10L));

        assertEquals(10L, PersonaOrganizationBinding.contextOrganization(jdbc));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingContextOrUnresolvableOrganizationFailsClosed() {
        assertThrows(IllegalStateException.class, () -> PersonaOrganizationBinding.contextOrganization(jdbc));

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).build());
        assertThrows(IllegalStateException.class, () -> PersonaOrganizationBinding.contextOrganization(jdbc));

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).propertyId(100L).build());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> PersonaOrganizationBinding.contextOrganization(jdbc));
    }

    @Test
    void insertedPersonCarriesTheOrganization() {
        PersonaOrganizationBinding.insertNaturalPerson(jdbc, 10L, 1L, "CC123", "Ana", "Diaz", "a@example.invalid", "300");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture(), any(KeyHolder.class), eq(new String[] {"ID_PERSONA"}));
        assertTrue(sql.getValue().toUpperCase().contains("INSERT INTO PERSONAS"));
        assertTrue(sql.getValue().toUpperCase().contains("ID_ORGANIZACION"));
        assertEquals(10L, params.getValue().getValue("org"));
        assertEquals("CC123", params.getValue().getValue("doc"));
    }

    @Test
    void personWithoutOrganizationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PersonaOrganizationBinding.insertNaturalPerson(
                jdbc, null, 1L, "CC123", "Ana", "Diaz", null, null));
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class),
                any(String[].class));
    }
}
