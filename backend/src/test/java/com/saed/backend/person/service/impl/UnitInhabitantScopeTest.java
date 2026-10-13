package com.saed.backend.person.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.repository.impl.UnitInhabitantRepositoryImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F05-01 / F05-02 / F5-H07: unit inhabitant management is scoped and fails closed; unlinking a person from one
 * unit revokes only that unit's resident access (never the person's other assignments or accounts) and never
 * elevates the database context.
 */
class UnitInhabitantScopeTest {

    private static final Long ORG = 1L;
    private static final Long PROP = 10L;
    private static final Long UNIT = 100L;
    private static final Long PERSON = 500L;

    private final UnitInhabitantRepository repo = mock(UnitInhabitantRepository.class);
    private final ConvivienteQuotaService quota = mock(ConvivienteQuotaService.class);
    private final UnitInhabitantServiceImpl service = new UnitInhabitantServiceImpl(repo, quota);

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void ctx(String role, String scope, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode(role).roleScope(scope)
                .organizationId(ORG).propertyId(PROP).unitId(unit).build());
    }

    private static UnitResidentDTO resident(String type) {
        PersonaDTO p = new PersonaDTO(PERSON, 1L, "1", "NATURAL", "A", null, "B", null, null, null, "ACTIVO",
                UNIT, "101", type);
        return new UnitResidentDTO(9L, p, type, LocalDate.of(2026, 1, 1), null, "ACTIVO");
    }

    private void unitInProperty(Long prop, Long org) {
        when(repo.findUnitScope(UNIT)).thenReturn(Optional.of(new UnitInhabitantRepository.UnitScope(prop, org)));
    }

    // ---- fail closed ----

    @Test
    void residentWithoutResolvableUnitIsDenied() {
        ctx("RESIDENTE", "UNIDAD", null);
        assertThrows(AccessDeniedException.class, () -> service.getResidentsByUnitId(UNIT));
        assertThrows(AccessDeniedException.class,
                () -> service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "FAMILIAR")));
        assertThrows(AccessDeniedException.class, () -> service.unlinkResident(UNIT, 9L));
        verify(repo, never()).insertResident(anyLong(), any());
    }

    @Test
    void missingContextAndSuperadminAreDenied() {
        assertThrows(AccessDeniedException.class, () -> service.getResidentsByUnitId(UNIT));
        ctx("SUPERADMIN", "GLOBAL", null);
        assertThrows(AccessDeniedException.class, () -> service.getResidentsByUnitId(UNIT));
        assertThrows(AccessDeniedException.class, () -> service.addOwner(UNIT, new UnitOwnerRequestDTO(PERSON, null, null)));
    }

    @Test
    void convivienteCannotManageOthers() {
        ctx("RESIDENTE_CONVIVENCIA", "UNIDAD", UNIT);
        when(repo.findResidentByIdAndUnitId(UNIT, 9L)).thenReturn(Optional.of(resident("FAMILIAR")));
        assertThrows(AccessDeniedException.class,
                () -> service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "FAMILIAR")));
        assertThrows(AccessDeniedException.class, () -> service.unlinkResident(UNIT, 9L));
        assertThrows(AccessDeniedException.class, () -> service.updateResidentStatus(UNIT, 9L, "INACTIVO"));
        assertThrows(AccessDeniedException.class, () -> service.deleteResidentPermanently(UNIT, 9L));
    }

    @Test
    void titularCannotRegisterAnotherTitular() {
        ctx("RESIDENTE", "UNIDAD", UNIT);
        assertThrows(AccessDeniedException.class,
                () -> service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "PROPIETARIO")));
        assertThrows(AccessDeniedException.class,
                () -> service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "ARRENDATARIO")));
    }

    @Test
    void titularAddingConvivienteGoesThroughTheLockedQuota() {
        ctx("RESIDENTE", "UNIDAD", UNIT);
        when(repo.personBelongsToOrganization(PERSON, ORG)).thenReturn(true);
        when(repo.insertResident(UNIT, new UnitResidentRequestDTO(PERSON, "CONVIVIENTE"))).thenReturn(3L);
        service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "CONVIVIENTE"));
        verify(quota).validateAndLockQuota(UNIT);
    }

    @Test
    void propertyAdminCannotManageUnitOfAnotherProperty() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        unitInProperty(11L, ORG);
        assertThrows(AccessDeniedException.class,
                () -> service.addResident(UNIT, new UnitResidentRequestDTO(PERSON, "FAMILIAR")));
        assertThrows(AccessDeniedException.class,
                () -> service.addOwner(UNIT, new UnitOwnerRequestDTO(PERSON, null, null)));
        assertThrows(AccessDeniedException.class, () -> service.unlinkResident(UNIT, 9L));
        verify(repo, never()).insertOwner(anyLong(), any());
    }

    @Test
    void orgAdminCannotManageUnitOfAnotherOrganization() {
        SaedContextHolder.setContext(SaedContext.builder().userId(2L).roleCode("ADMIN_ORGANIZACION")
                .roleScope("ORGANIZACION").organizationId(ORG).build());
        unitInProperty(11L, 2L);
        assertThrows(AccessDeniedException.class, () -> service.getResidentsByUnitId(UNIT));
    }

    // ---- unlink preserves other assignments ----

    @Test
    void adminUnlinkRevokesOnlyThisUnitsAccessInCallerContext() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        unitInProperty(PROP, ORG);
        when(repo.findResidentByIdAndUnitId(UNIT, 9L)).thenReturn(Optional.of(resident("ARRENDATARIO")));
        when(repo.unlinkResident(UNIT, 9L)).thenReturn(1);

        service.unlinkResident(UNIT, 9L);

        verify(repo).revokeUnitResidentAccess(UNIT, PERSON);
        verify(repo, never()).revokeHouseholdAccessAsTitular(anyLong(), anyLong());
    }

    @Test
    void titularUnlinkUsesTheValidatedHouseholdRevocation() {
        ctx("RESIDENTE", "UNIDAD", UNIT);
        when(repo.findResidentByIdAndUnitId(UNIT, 9L)).thenReturn(Optional.of(resident("CONVIVIENTE")));
        when(repo.unlinkResident(UNIT, 9L)).thenReturn(1);

        service.unlinkResident(UNIT, 9L);

        verify(repo).revokeHouseholdAccessAsTitular(UNIT, PERSON);
        verify(repo, never()).revokeUnitResidentAccess(anyLong(), anyLong());
    }

    @Test
    void titularCannotUnlinkTheTitular() {
        ctx("RESIDENTE", "UNIDAD", UNIT);
        when(repo.findResidentByIdAndUnitId(UNIT, 9L)).thenReturn(Optional.of(resident("PROPIETARIO")));
        assertThrows(AccessDeniedException.class, () -> service.unlinkResident(UNIT, 9L));
        verify(repo, never()).unlinkResident(anyLong(), anyLong());
    }

    // ---- repository: no context elevation, no global account changes ----

    @Test
    @SuppressWarnings("unchecked")
    void repositoryUnlinkNeverElevatesContextNorTouchesAccounts() {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate plain = mock(JdbcTemplate.class);
        when(named.getJdbcOperations()).thenReturn(plain);
        when(named.getJdbcTemplate()).thenReturn(plain);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(named.update(sql.capture(), any(SqlParameterSource.class))).thenReturn(1);
        when(named.update(sql.capture(), any(Map.class))).thenReturn(1);
        when(named.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(PERSON));

        UnitInhabitantRepositoryImpl impl = new UnitInhabitantRepositoryImpl(named);
        impl.unlinkResident(UNIT, 9L);
        impl.deleteResidentPermanently(UNIT, 9L);
        impl.revokeUnitResidentAccess(UNIT, PERSON);

        verifyNoInteractions(plain);
        for (String s : sql.getAllValues()) {
            String u = s.toUpperCase();
            assertFalse(u.contains("PKG_SAED_SESSION"), s);
            assertFalse(u.startsWith("UPDATE USUARIOS"), s);
            if (u.contains("USUARIO_ASIGNACIONES")) {
                assertTrue(u.contains("ID_UNIDAD = :UNITID"), s);
            }
        }
    }
}
