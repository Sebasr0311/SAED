package com.saed.backend.person;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.TutorDTO;
import com.saed.backend.person.dto.TutorRequestDTO;
import com.saed.backend.person.repository.DependentRepository;
import com.saed.backend.person.service.impl.DependentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F05-02 / F5-H08: guardian (tutor) records are scoped to the dependent. A resident manages guardians only of a
 * dependent living in their unit; staff only of dependents living in their property.
 */
class GuardianScopeTest {

    private static final Long UNIT = 100L;
    private static final Long OTHER_UNIT = 200L;
    private static final Long MINOR = 50L;
    private static final Long FOREIGN_MINOR = 60L;

    private DependentRepository repo;
    private DependentServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(DependentRepository.class);
        service = new DependentServiceImpl(repo);
        when(repo.findActiveUnitIdsOfPersona(MINOR)).thenReturn(List.of(UNIT));
        when(repo.findActiveUnitIdsOfPersona(FOREIGN_MINOR)).thenReturn(List.of(OTHER_UNIT));
        when(repo.isUnitInProperty(UNIT, 10L)).thenReturn(true);
        when(repo.isUnitInProperty(OTHER_UNIT, 10L)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void resident() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE").roleScope("UNIDAD")
                .organizationId(1L).propertyId(10L).unitId(UNIT).build());
    }

    private static TutorRequestDTO req(Long minor) {
        return new TutorRequestDTO(minor, 900L, "MADRE", null, "ACTIVO");
    }

    private static TutorDTO tutor(Long minor) {
        return new TutorDTO(5L, minor, 900L, "MADRE", null, "ACTIVO", null, null, null, null, null);
    }

    @Test
    void residentCreatesGuardianOnlyForDependentOfTheirUnit() {
        resident();
        when(repo.createTutor(any())).thenReturn(tutor(MINOR));
        assertEquals(MINOR, service.createTutor(req(MINOR)).personaMenorId());
        assertThrows(AccessDeniedException.class, () -> service.createTutor(req(FOREIGN_MINOR)));
    }

    @Test
    void residentCannotReadOrChangeGuardiansOfAnotherUnit() {
        resident();
        when(repo.getTutorById(6L)).thenReturn(Optional.of(tutor(FOREIGN_MINOR)));
        assertThrows(NoSuchElementException.class, () -> service.getTutorById(6L));
        assertThrows(NoSuchElementException.class, () -> service.getTutoresByMenor(FOREIGN_MINOR));
        assertThrows(NoSuchElementException.class, () -> service.updateTutor(6L, req(FOREIGN_MINOR)));
        assertThrows(NoSuchElementException.class, () -> service.deleteTutor(6L));
        verify(repo, never()).deleteTutor(anyLong());
        verify(repo, never()).updateTutor(anyLong(), any());
    }

    @Test
    void guardianCannotBeMovedToADependentOutOfScope() {
        resident();
        when(repo.getTutorById(5L)).thenReturn(Optional.of(tutor(MINOR)));
        assertThrows(AccessDeniedException.class, () -> service.updateTutor(5L, req(FOREIGN_MINOR)));
    }

    @Test
    void staffIsLimitedToDependentsOfTheirPropertyAndSuperadminIsDenied() {
        SaedContextHolder.setContext(SaedContext.builder().userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(10L).build());
        when(repo.createTutor(any())).thenReturn(tutor(MINOR));
        service.createTutor(req(MINOR));
        assertThrows(AccessDeniedException.class, () -> service.createTutor(req(FOREIGN_MINOR)));

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.createTutor(req(MINOR)));
    }
}
