package com.saed.backend.person.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaBatchItemDTO;
import com.saed.backend.person.dto.PersonaBatchResultDTO;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.person.repository.impl.PersonaRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F5-H05 / DEC-01: PERSONAS has no row-level policy, so the application must scope every read and write.
 * Residents edit only themselves (titulars also their non-titular household); convivencia only themselves;
 * administrators only persons of their organization within their scope. Document identity is immutable for
 * non-administrators. Absent context never broadens scope.
 */
class PersonaScopeAuthorizationTest {

    private static final Long ORG = 1L;
    private static final Long PROP = 10L;
    private static final Long UNIT = 100L;
    private static final Long SELF = 500L;
    private static final Long OTHER = 501L;

    private final PersonaRepository repo = mock(PersonaRepository.class);
    private final PersonaServiceImpl service = new PersonaServiceImpl(repo);

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void unitCaller(String role) {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode(role).roleScope("UNIDAD")
                .organizationId(ORG).propertyId(PROP).unitId(UNIT).build());
    }

    private static void propertyAdmin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD").organizationId(ORG).propertyId(PROP).build());
    }

    private static PersonaDTO persona(Long id, String doc) {
        return new PersonaDTO(id, 1L, doc, "NATURAL", "Ana", null, "Paz", null, "a@b.co", "300", "ACTIVO",
                UNIT, "101", "RESIDENTE");
    }

    private static PersonaRequestDTO request(String doc) {
        return new PersonaRequestDTO(1L, doc, "NATURAL", "Ana", null, "Paz", null, "a@b.co", "300");
    }

    // ---- update ----

    @Test
    void residentCanEditThemselvesButNotTheirDocument() {
        unitCaller("RESIDENTE");
        when(repo.findPersonaIdByUserId(7L)).thenReturn(Optional.of(SELF));
        when(repo.findById(SELF)).thenReturn(Optional.of(persona(SELF, "111")));

        service.updatePersona(SELF, request("111"));
        verify(repo).update(eq(SELF), any());

        assertThrows(IllegalArgumentException.class, () -> service.updatePersona(SELF, request("999")));
    }

    @Test
    void convivienteCannotEditAnotherPersonOfTheUnit() {
        unitCaller("RESIDENTE_CONVIVENCIA");
        when(repo.findPersonaIdByUserId(7L)).thenReturn(Optional.of(SELF));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findActiveResidentType(OTHER, UNIT)).thenReturn(Optional.of("CONVIVIENTE"));

        assertThrows(NoSuchElementException.class, () -> service.updatePersona(OTHER, request("222")));
        verify(repo, never()).update(anyLong(), any());
    }

    @Test
    void titularCanEditNonTitularHouseholdMember() {
        unitCaller("RESIDENTE");
        when(repo.findPersonaIdByUserId(7L)).thenReturn(Optional.of(SELF));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findActiveResidentType(OTHER, UNIT)).thenReturn(Optional.of("FAMILIAR"));
        when(repo.findOrganizationId(OTHER)).thenReturn(Optional.of(ORG));

        service.updatePersona(OTHER, request("222"));
        verify(repo).update(eq(OTHER), any());
    }

    @Test
    void titularCannotEditAnotherTitularOrAnUnrelatedPerson() {
        unitCaller("RESIDENTE");
        when(repo.findPersonaIdByUserId(7L)).thenReturn(Optional.of(SELF));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findActiveResidentType(OTHER, UNIT)).thenReturn(Optional.of("PROPIETARIO"));
        assertThrows(NoSuchElementException.class, () -> service.updatePersona(OTHER, request("222")));

        when(repo.findActiveResidentType(OTHER, UNIT)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updatePersona(OTHER, request("222")));
        verify(repo, never()).update(anyLong(), any());
    }

    @Test
    void adminCannotEditPersonOfAnotherOrganizationEvenIfVisible() {
        propertyAdmin();
        when(repo.findPersonaIdByUserId(3L)).thenReturn(Optional.of(900L));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findOrganizationId(OTHER)).thenReturn(Optional.of(2L));

        assertThrows(AccessDeniedException.class, () -> service.updatePersona(OTHER, request("222")));
        verify(repo, never()).update(anyLong(), any());
    }

    @Test
    void adminCanEditPersonOfTheirOrganization() {
        propertyAdmin();
        when(repo.findPersonaIdByUserId(3L)).thenReturn(Optional.of(900L));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findOrganizationId(OTHER)).thenReturn(Optional.of(ORG));

        service.updatePersona(OTHER, request("223"));
        verify(repo).update(eq(OTHER), any());
    }

    @Test
    void personOutsideScopeIsNotFoundOnUpdate() {
        propertyAdmin();
        when(repo.findById(OTHER)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updatePersona(OTHER, request("222")));
    }

    @Test
    void superadminAndMissingContextCannotEditPersons() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        assertThrows(AccessDeniedException.class, () -> service.updatePersona(OTHER, request("222")));
        SaedContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.updatePersona(OTHER, request("222")));
        verify(repo, never()).update(anyLong(), any());
    }

    // ---- detail ----

    @Test
    void unitCallerReadsOnlySelfOrPersonsOfTheirUnit() {
        unitCaller("RESIDENTE_CONVIVENCIA");
        when(repo.findPersonaIdByUserId(7L)).thenReturn(Optional.of(SELF));
        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.isLinkedToUnit(OTHER, UNIT)).thenReturn(false);
        assertThrows(NoSuchElementException.class, () -> service.getPersonaById(OTHER));

        when(repo.isLinkedToUnit(OTHER, UNIT)).thenReturn(true);
        assertEquals(OTHER, service.getPersonaById(OTHER).id());
    }

    // ---- delete / unlink ----

    @Test
    void deleteUnlinksOnlyWithinCallerScopeAndRequiresVisibility() {
        propertyAdmin();
        when(repo.findById(OTHER)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.deletePersona(OTHER));
        verify(repo, never()).unlinkFromScope(anyLong(), any(), any());

        when(repo.findById(OTHER)).thenReturn(Optional.of(persona(OTHER, "222")));
        service.deletePersona(OTHER);
        verify(repo).unlinkFromScope(OTHER, PROP, ORG);
    }

    // ---- import ----

    @Test
    void importDoesNotOverwritePersonOfAnotherOrganization() {
        propertyAdmin();
        when(repo.findTipoDocumentoIdByCodigo(anyString())).thenReturn(Optional.of(1L));
        when(repo.findByNumeroDocumento("222")).thenReturn(Optional.of(persona(OTHER, "222")));
        when(repo.findOrganizationId(OTHER)).thenReturn(Optional.of(2L));

        PersonaBatchResultDTO result = service.importarBatch(List.of(
                new PersonaBatchItemDTO("CC", "222", "Ana", "Paz", "x@y.co", "1", null, null)));

        verify(repo, never()).update(anyLong(), any());
        assertFalse(result.errores().isEmpty());
    }

    @Test
    void importNeverReturnsRawDatabaseMessages() {
        propertyAdmin();
        when(repo.findTipoDocumentoIdByCodigo(anyString())).thenReturn(Optional.of(1L));
        when(repo.findByNumeroDocumento("333")).thenReturn(Optional.empty());
        when(repo.insert(any())).thenThrow(new DataIntegrityViolationException(
                "ORA-00001: unique constraint (SAED.UQ_PERSONAS_EMAIL) violated"));

        PersonaBatchResultDTO result = service.importarBatch(List.of(
                new PersonaBatchItemDTO("CC", "333", "Ana", "Paz", "x@y.co", "1", null, null)));

        assertEquals(1, result.fallidos());
        assertFalse(result.errores().get(0).detalle().contains("ORA-"));
    }

    // ---- repository: absent or global context never lists every person ----

    @Test
    @SuppressWarnings("unchecked")
    void repositoryListWithoutTenantContextReturnsNothing() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PersonaRepositoryImpl impl = new PersonaRepositoryImpl(jdbc);

        assertTrue(impl.findAll(10, 0).isEmpty());
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertTrue(impl.findAll(10, 0).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryDetailWithoutTenantContextIsRestrictedToSelf() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        new PersonaRepositoryImpl(jdbc).findById(OTHER);
        assertTrue(sql.getValue().contains("usr_self.ID_USUARIO = :currentUserId"), sql.getValue());
    }
}
