package com.saed.backend.person.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaBatchItemDTO;
import com.saed.backend.person.dto.PersonaBatchResultDTO;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-01 hardening: PERSONAS has no row-level policy, so linking, importing and editing a person must verify that
 * the identity belongs to the caller's organization. Foreign identities are indistinguishable from missing ones.
 */
class PersonaTenantBoundaryTest {

    private static final Long ORG = 1L;
    private static final Long PROP = 10L;
    private static final Long UNIT = 100L;
    private static final Long FOREIGN = 777L;
    private static final Long OWN = 500L;

    private final UnitInhabitantRepository inhabitants = mock(UnitInhabitantRepository.class);
    private final UnitInhabitantServiceImpl inhabitantService =
            new UnitInhabitantServiceImpl(inhabitants, mock(ConvivienteQuotaService.class));
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final PersonaServiceImpl personaService = new PersonaServiceImpl(personas);

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private void admin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD").organizationId(ORG).propertyId(PROP).build());
        when(inhabitants.findUnitScope(UNIT)).thenReturn(Optional.of(new UnitInhabitantRepository.UnitScope(PROP, ORG)));
    }

    private void resident() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE").roleScope("UNIDAD")
                .organizationId(ORG).propertyId(PROP).unitId(UNIT).build());
    }

    // ---- UnitInhabitantServiceImpl ----

    @Test
    void adminCannotLinkAPersonOfAnotherOrganizationAsResident() {
        admin();
        when(inhabitants.personBelongsToOrganization(FOREIGN, ORG)).thenReturn(false);

        assertThrows(NoSuchElementException.class,
                () -> inhabitantService.addResident(UNIT, new UnitResidentRequestDTO(FOREIGN, "FAMILIAR")));
        verify(inhabitants, never()).insertResident(anyLong(), any());
    }

    @Test
    void adminCannotLinkAPersonOfAnotherOrganizationAsOwner() {
        admin();
        when(inhabitants.personBelongsToOrganization(FOREIGN, ORG)).thenReturn(false);

        assertThrows(NoSuchElementException.class,
                () -> inhabitantService.addOwner(UNIT, new UnitOwnerRequestDTO(FOREIGN, null, null)));
        verify(inhabitants, never()).insertOwner(anyLong(), any());
    }

    @Test
    void residentCannotLinkAForeignPersonAsHouseholdMember() {
        resident();
        when(inhabitants.personBelongsToOrganization(FOREIGN, ORG)).thenReturn(false);

        assertThrows(NoSuchElementException.class,
                () -> inhabitantService.addResident(UNIT, new UnitResidentRequestDTO(FOREIGN, "CONVIVIENTE")));
        verify(inhabitants, never()).insertResident(anyLong(), any());
    }

    @Test
    void aPersonOfTheCallersOrganizationCanBeLinked() {
        admin();
        when(inhabitants.personBelongsToOrganization(OWN, ORG)).thenReturn(true);
        when(inhabitants.insertResident(UNIT, new UnitResidentRequestDTO(OWN, "FAMILIAR"))).thenReturn(9L);

        assertEquals(9L, inhabitantService.addResident(UNIT, new UnitResidentRequestDTO(OWN, "FAMILIAR")));
    }

    // ---- PersonaServiceImpl ----

    private void propertyAdmin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD").organizationId(ORG).propertyId(PROP).build());
    }

    private static PersonaDTO persona(Long id, String doc) {
        return new PersonaDTO(id, 1L, doc, "NATURAL", "Ana", null, "Paz", null, "a@b.co", "300", "ACTIVO",
                UNIT, "101", "RESIDENTE");
    }

    @Test
    void importNeverAssignsAForeignIdentityToAUnit() {
        propertyAdmin();
        when(personas.findTipoDocumentoIdByCodigo(anyString())).thenReturn(Optional.of(1L));
        when(personas.findUnidadIdByNumero(PROP, "101")).thenReturn(Optional.of(UNIT));
        when(personas.findByNumeroDocumento("222")).thenReturn(Optional.of(persona(FOREIGN, "222")));
        when(personas.findOrganizationId(FOREIGN)).thenReturn(Optional.of(2L));

        PersonaBatchResultDTO result = personaService.importarBatch(List.of(
                new PersonaBatchItemDTO("CC", "222", "Ana", "Paz", "x@y.co", "1", "101", "FAMILIAR")));

        verify(personas, never()).asignarUnidad(anyLong(), anyLong(), any());
        verify(personas, never()).update(anyLong(), any());
        assertEquals(0, result.procesados());
        assertEquals(1, result.fallidos());
    }

    @Test
    void aLegacyIdentityWithoutOrganizationIsEditableOnlyWhenVisibleInScope() {
        propertyAdmin();
        when(personas.findPersonaIdByUserId(3L)).thenReturn(Optional.of(900L));
        when(personas.findById(FOREIGN)).thenReturn(Optional.of(persona(FOREIGN, "222")));
        when(personas.findOrganizationId(FOREIGN)).thenReturn(Optional.empty());
        // visible to findById (the callers current scope) is not enough for the second lookup used by the policy:
        when(personas.findById(FOREIGN)).thenReturn(Optional.of(persona(FOREIGN, "222")), Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> personaService.updatePersona(FOREIGN, new PersonaRequestDTO(1L, "222", "NATURAL", "Ana", null, "Paz", null, "a@b.co", "300")));
        verify(personas, never()).update(anyLong(), any());
    }

    @Test
    void aLegacyIdentityVisibleInScopeRemainsEditable() {
        propertyAdmin();
        when(personas.findPersonaIdByUserId(3L)).thenReturn(Optional.of(900L));
        when(personas.findById(OWN)).thenReturn(Optional.of(persona(OWN, "111")));
        when(personas.findOrganizationId(OWN)).thenReturn(Optional.empty());

        personaService.updatePersona(OWN, new PersonaRequestDTO(1L, "111", "NATURAL", "Ana", null, "Paz", null, "a@b.co", "300"));
        verify(personas).update(anyLong(), any());
    }
}
