package com.saed.backend.person;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.dto.ResidentRegistrationRequest;
import com.saed.backend.person.dto.ResidentRegistrationResult;
import com.saed.backend.person.dto.TutorRequestDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.AccountProvisioner;
import com.saed.backend.person.service.DependentService;
import com.saed.backend.person.service.PersonaService;
import com.saed.backend.person.service.ResidentRegistrationService;
import com.saed.backend.person.service.UnitInhabitantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F05-01: a resident is registered completely or not at all. Every step reuses the owning service; any failure
 * propagates so the surrounding transaction rolls back; scope comes from the server context. No database.
 */
class ResidentRegistrationServiceTest {

    private static final Long ORG = 1L;
    private static final Long PROP = 10L;
    private static final Long UNIT = 100L;

    private final PersonaService personas = mock(PersonaService.class);
    private final UnitInhabitantService inhabitants = mock(UnitInhabitantService.class);
    private final UnitInhabitantRepository inhabitantRepository = mock(UnitInhabitantRepository.class);
    private final DependentService dependents = mock(DependentService.class);
    private final FinanzasService finanzas = mock(FinanzasService.class);
    private final AccountProvisioner accounts = mock(AccountProvisioner.class);
    private final ResidentRegistrationService service = new ResidentRegistrationService(
            personas, inhabitants, inhabitantRepository, dependents, finanzas, accounts);

    @BeforeEach
    void setUp() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(ORG).propertyId(PROP).build());
        when(inhabitantRepository.findUnitScope(UNIT)).thenReturn(Optional.of(new UnitInhabitantRepository.UnitScope(PROP, ORG)));
        when(personas.createPersona(any())).thenReturn(500L, 501L);
        when(inhabitants.addResident(eq(UNIT), any())).thenReturn(900L);
        when(inhabitants.addOwner(eq(UNIT), any())).thenReturn(800L);
        when(finanzas.createContrato(any())).thenReturn(700L);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static PersonaRequestDTO person(String doc, String email) {
        return new PersonaRequestDTO(1L, doc, "NATURAL", "Ana", null, "Paz", null, email, "300");
    }

    private static ResidentRegistrationRequest request(String relation, ResidentRegistrationRequest.Guardian guardian,
                                                       ResidentRegistrationRequest.Account account,
                                                       ResidentRegistrationRequest.Contract contract) {
        return new ResidentRegistrationRequest(UNIT, relation, person("111", "ana@example.invalid"), guardian, account, contract);
    }

    @Test
    void personLinkGuardianAndAccountAreCreatedInOrder() {
        when(accounts.provision(any())).thenReturn(Map.of("idUsuario", 42L, "passwordGenerada", "tmp"));

        ResidentRegistrationResult result = service.register(request("FAMILIAR",
                new ResidentRegistrationRequest.Guardian(null, person("222", null), "MADRE"),
                new ResidentRegistrationRequest.Account("Ana.Paz", null, true), null));

        InOrder order = inOrder(personas, inhabitants, dependents, accounts);
        order.verify(personas, org.mockito.Mockito.times(2)).createPersona(any());
        order.verify(inhabitants).addResident(UNIT, new UnitResidentRequestDTO(500L, "FAMILIAR"));
        order.verify(dependents).createTutor(new TutorRequestDTO(500L, 501L, "MADRE", null, "ACTIVO"));
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        order.verify(accounts).provision(payload.capture());
        assertEquals("ana.paz", payload.getValue().get("username"));
        assertEquals("RESIDENTE", payload.getValue().get("rol"));
        assertEquals(500L, payload.getValue().get("idPersona"));
        assertEquals(UNIT, payload.getValue().get("idUnidad"));
        assertEquals(500L, result.personId());
        assertEquals(501L, result.guardianPersonId());
        assertEquals(42L, result.userId());
        assertEquals("tmp", result.generatedPassword());
        assertNull(result.ownerLinkId());
    }

    @Test
    void anOwnerWhoDoesNotLiveThereIsOnlyAnOwner() {
        ResidentRegistrationResult result = service.register(request("PROPIETARIO_NO_RESIDENTE", null, null, null));

        verify(inhabitants).addOwner(eq(UNIT), eq(new UnitOwnerRequestDTO(500L, new BigDecimal("100"), "S")));
        verify(inhabitants, never()).addResident(anyLong(), any());
        assertEquals(800L, result.ownerLinkId());
        assertNull(result.residentLinkId());
    }

    @Test
    void anOwnerWhoLivesThereIsOwnerAndResident() {
        service.register(request("PROPIETARIO_RESIDENTE", null, null, null));
        verify(inhabitants).addOwner(eq(UNIT), any());
        verify(inhabitants).addResident(UNIT, new UnitResidentRequestDTO(500L, "PROPIETARIO"));
    }

    @Test
    void aTenantGetsTheLeaseBeforeTheLink() {
        service.register(request("ARRENDATARIO", null, null,
                new ResidentRegistrationRequest.Contract(new BigDecimal("1000000"), null, null, null, 5L)));

        InOrder order = inOrder(finanzas, inhabitants);
        ArgumentCaptor<ContratoRequestDTO> contract = ArgumentCaptor.forClass(ContratoRequestDTO.class);
        order.verify(finanzas).createContrato(contract.capture());
        order.verify(inhabitants).addResident(UNIT, new UnitResidentRequestDTO(500L, "ARRENDATARIO"));
        assertEquals(500L, contract.getValue().idResidente());
        assertEquals(UNIT, contract.getValue().idApartamento());
        assertEquals(5L, contract.getValue().idPlantilla());
    }

    @Test
    void aTenantWithoutLeaseDataIsRejectedBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.register(request("ARRENDATARIO", null, null, null)));
        verifyNoInteractions(personas, inhabitants, dependents, finanzas, accounts);
    }

    @Test
    void anUnknownRelationIsRejectedBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.register(request("INVENTADO", null, null, null)));
        verifyNoInteractions(personas, inhabitants);
    }

    @Test
    void aFailureInALaterStepPropagatesAndTheRegistrationIsTransactional() throws Exception {
        when(accounts.provision(any())).thenThrow(new IllegalStateException("username taken"));

        assertThrows(IllegalStateException.class, () -> service.register(request("FAMILIAR", null,
                new ResidentRegistrationRequest.Account("ana", null, true), null)));

        Transactional tx = ResidentRegistrationService.class
                .getMethod("register", ResidentRegistrationRequest.class).getAnnotation(Transactional.class);
        assertTrue(tx != null && java.util.Arrays.asList(tx.rollbackFor()).contains(Exception.class));
    }

    @Test
    void aLinkRefusalStopsTheFlowBeforeTheGuardianAndTheAccount() {
        when(inhabitants.addResident(eq(UNIT), any())).thenThrow(new AccessDeniedException("cupo"));

        assertThrows(AccessDeniedException.class, () -> service.register(request("CONVIVIENTE",
                new ResidentRegistrationRequest.Guardian(null, person("222", null), "MADRE"),
                new ResidentRegistrationRequest.Account("ana", null, true), null)));

        verify(dependents, never()).createTutor(any());
        verify(accounts, never()).provision(any());
    }

    @Test
    void aUnitOutsideTheCallersPropertyIsNotFound() {
        when(inhabitantRepository.findUnitScope(UNIT)).thenReturn(Optional.of(new UnitInhabitantRepository.UnitScope(PROP + 1, ORG)));
        assertThrows(NoSuchElementException.class, () -> service.register(request("FAMILIAR", null, null, null)));
        verifyNoInteractions(personas, inhabitants);
    }

    @Test
    void anOrganizationAdministratorIsLimitedToTheirOrganization() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZACION").organizationId(ORG).build());
        when(inhabitantRepository.findUnitScope(UNIT)).thenReturn(Optional.of(new UnitInhabitantRepository.UnitScope(PROP, ORG + 1)));
        assertThrows(NoSuchElementException.class, () -> service.register(request("FAMILIAR", null, null, null)));
        verifyNoInteractions(personas);
    }

    @Test
    void residentsSuperAdminsAndAnonymousCallersCannotRegister() {
        for (String role : new String[]{"RESIDENTE", "SUPERADMIN", "PORTERO"}) {
            SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode(role).organizationId(ORG)
                    .propertyId(PROP).build());
            assertThrows(AccessDeniedException.class, () -> service.register(request("FAMILIAR", null, null, null)));
        }
        SaedContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.register(request("FAMILIAR", null, null, null)));
        verifyNoInteractions(personas, inhabitants);
    }

    @Test
    void aGuardianOfAnotherOrganizationIsNotFoundAndNothingIsWritten() {
        when(inhabitantRepository.personBelongsToOrganization(999L, ORG)).thenReturn(false);
        assertThrows(NoSuchElementException.class, () -> service.register(request("FAMILIAR",
                new ResidentRegistrationRequest.Guardian(999L, null, "PADRE"), null, null)));
        verifyNoInteractions(personas, inhabitants, dependents);
    }

    @Test
    void anExistingGuardianOfTheOrganizationIsLinkedWithoutCreatingAPerson() {
        when(inhabitantRepository.personBelongsToOrganization(321L, ORG)).thenReturn(true);
        service.register(request("FAMILIAR", new ResidentRegistrationRequest.Guardian(321L, null, "PADRE"), null, null));
        verify(personas, org.mockito.Mockito.times(1)).createPersona(any());
        verify(dependents).createTutor(new TutorRequestDTO(500L, 321L, "PADRE", null, "ACTIVO"));
    }

    @Test
    void anAccountNeedsTheEmailOfThePerson() {
        ResidentRegistrationRequest noEmail = new ResidentRegistrationRequest(UNIT, "FAMILIAR", person("111", null), null,
                new ResidentRegistrationRequest.Account("ana", null, true), null);
        assertThrows(IllegalArgumentException.class, () -> service.register(noEmail));
        verifyNoInteractions(personas);
    }
}
