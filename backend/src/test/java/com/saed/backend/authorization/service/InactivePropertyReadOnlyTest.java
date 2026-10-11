package com.saed.backend.authorization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.exception.InactivePropertyException;
import com.saed.backend.authorization.repository.AssignmentRepository;
import com.saed.backend.authorization.repository.BlockRepository;
import com.saed.backend.authorization.repository.PropertyConfigRepository;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.authorization.repository.RoleRepository;
import com.saed.backend.authorization.repository.UnitRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import com.saed.backend.security.filter.InactivePropertyFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F04-03: an inactive property is read-only. Authorized historical reads work; every direct (property-scoped
 * caller, request filter) or indirect (organization-scoped caller naming the property, service layer) operational
 * mutation is denied, and the status lookup fails closed.
 */
class InactivePropertyReadOnlyTest {

    private static final Long PROP = 10L;
    private static final Long ORG = 1L;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static PropertyStatusService statusWith(PropertyRepository repo) {
        return new PropertyStatusService(repo);
    }

    private static PropertyStatusService inactiveStatus() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.getPropertyStatus(PROP)).thenReturn(Optional.of("INACTIVA"));
        return statusWith(repo);
    }

    private static void orgAdmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").organizationId(ORG).build());
    }

    // ---- status lookup fails closed (F4-H08) ----

    @Test
    void statusLookupFailureIsTreatedAsNotActive() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.getPropertyStatus(PROP)).thenThrow(new DataAccessResourceFailureException("down"));
        assertFalse(statusWith(repo).isPropertyActive(PROP));
    }

    @Test
    void unknownPropertyIsTreatedAsNotActive() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.getPropertyStatus(PROP)).thenReturn(Optional.empty());
        assertFalse(statusWith(repo).isPropertyActive(PROP));
    }

    @Test
    void suspendedPropertyIsAlsoReadOnly() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.getPropertyStatus(PROP)).thenReturn(Optional.of("SUSPENDIDA"));
        assertFalse(statusWith(repo).isPropertyActive(PROP));
    }

    // ---- request filter: direct mutations by property-scoped callers ----

    @ParameterizedTest
    @CsvSource({
            "ADMIN_PROPIEDAD, PROPIEDAD, POST, /api/v1/units",
            "ADMIN_PROPIEDAD, PROPIEDAD, PUT, /api/v1/units/5",
            "ADMIN_PROPIEDAD, PROPIEDAD, POST, /api/v1/properties/10/blocks",
            "ADMIN_PROPIEDAD, PROPIEDAD, PUT, /api/v1/properties/10/config/MAX",
            "ADMIN_PROPIEDAD, PROPIEDAD, PUT, /api/v1/properties/10",
            "ADMIN_PROPIEDAD, PROPIEDAD, POST, /api/v1/units/5/residents",
            "ADMIN_PROPIEDAD, PROPIEDAD, POST, /api/v1/contratos",
            "ADMIN_PROPIEDAD, PROPIEDAD, PATCH, /api/v1/assignments/3/status",
            "ADMIN_PROPIEDAD, PROPIEDAD, POST, /api/v1/usuarios",
            "RESIDENTE, UNIDAD, PUT, /api/v1/personas/4",
            "RESIDENTE_CONVIVENCIA, UNIDAD, POST, /api/v1/reservas",
            "PORTERO, PROPIEDAD, POST, /api/v1/paquetes",
            "ADMIN_PROPIEDAD, PROPIEDAD, DELETE, /api/v1/units/5/residents/7"
    })
    void filterDeniesMutationsOfPropertyScopedCallers(String role, String scope, String method, String uri)
            throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode(role).roleScope(scope).organizationId(ORG).propertyId(PROP).build());
        InactivePropertyFilter filter = new InactivePropertyFilter(inactiveStatus(), new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(method, uri), response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PROPERTY_INACTIVE"));
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN_PROPIEDAD, PROPIEDAD, /api/v1/units",
            "ADMIN_PROPIEDAD, PROPIEDAD, /api/v1/cuotas",
            "RESIDENTE, UNIDAD, /api/v1/paz-y-salvos",
            "PORTERO, PROPIEDAD, /api/v1/paquetes"
    })
    void filterKeepsAuthorizedHistoricalReads(String role, String scope, String uri) throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode(role).roleScope(scope).organizationId(ORG).propertyId(PROP).build());
        InactivePropertyFilter filter = new InactivePropertyFilter(inactiveStatus(), new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ---- service layer: indirect mutations by organization-scoped callers (no property in context) ----

    @Test
    void propertyUpdateOnInactivePropertyIsDenied() {
        orgAdmin();
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.getPropertyStatus(PROP)).thenReturn(Optional.of("INACTIVA"));
        PropertyService svc = new PropertyService(repo, statusWith(repo), mock(PlanLimitService.class));

        assertThrows(InactivePropertyException.class, () -> svc.update(PROP, new PropertyRequestDTO()));
        verify(repo, never()).update(anyLong(), any());
    }

    @Test
    void reactivationThroughStatusEndpointRemainsPossible() {
        orgAdmin();
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.findById(PROP)).thenReturn(Optional.empty());
        PropertyService svc = new PropertyService(repo, statusWith(repo), mock(PlanLimitService.class));

        svc.updateStatus(PROP, "ACTIVA");
        verify(repo).updateStatus(PROP, "ACTIVA");
    }

    @Test
    void unitCreateAndUpdateOnInactivePropertyAreDenied() {
        orgAdmin();
        UnitRepository units = mock(UnitRepository.class);
        PlanLimitService plan = mock(PlanLimitService.class);
        when(plan.getOrganizationIdForProperty(PROP)).thenReturn(ORG);
        UnitService svc = new UnitService(units, plan, null, inactiveStatus());

        UnitRequestDTO req = new UnitRequestDTO();
        req.setIdPropiedad(PROP);
        req.setIdentificador("101");
        assertThrows(InactivePropertyException.class, () -> svc.create(req));

        UnitDTO existing = new UnitDTO();
        existing.setId(5L);
        existing.setIdPropiedad(PROP);
        when(units.findById(5L)).thenReturn(Optional.of(existing));
        assertThrows(InactivePropertyException.class, () -> svc.update(5L, req));
        verify(units, never()).create(any());
        verify(units, never()).update(any(), any());
    }

    @Test
    void blockMutationsOnInactivePropertyAreDenied() {
        orgAdmin();
        BlockRepository blocks = mock(BlockRepository.class);
        PlanLimitService plan = mock(PlanLimitService.class);
        when(plan.getOrganizationIdForProperty(PROP)).thenReturn(ORG);
        BlockServiceImpl svc = new BlockServiceImpl(blocks, plan, inactiveStatus());
        BlockRequestDTO req = new BlockRequestDTO();

        assertThrows(InactivePropertyException.class, () -> svc.create(PROP, req));
        assertThrows(InactivePropertyException.class, () -> svc.update(PROP, 1L, req));
        assertThrows(InactivePropertyException.class, () -> svc.updateStatus(PROP, 1L, "INACTIVO"));
        assertThrows(InactivePropertyException.class, () -> svc.delete(PROP, 1L));
    }

    @Test
    void propertyConfigWritesOnInactivePropertyAreDeniedButReadsWork() {
        orgAdmin();
        PropertyConfigRepository repo = mock(PropertyConfigRepository.class);
        PlanLimitService plan = mock(PlanLimitService.class);
        when(plan.getOrganizationIdForProperty(PROP)).thenReturn(ORG);
        PropertyConfigServiceImpl svc = new PropertyConfigServiceImpl(repo, plan, inactiveStatus());

        assertThrows(InactivePropertyException.class, () -> svc.saveOrUpdate(PROP, "K", "V", null));
        assertThrows(InactivePropertyException.class, () -> svc.saveBatch(PROP, Map.of("K", "V")));
        svc.findAll(PROP);
    }

    @Test
    void newAssignmentIntoInactivePropertyIsDenied() {
        orgAdmin();
        RoleRepository roles = mock(RoleRepository.class);
        RoleDTO role = new RoleDTO();
        role.setCodigo("ADMIN_PROPIEDAD");
        role.setAlcance("PROPIEDAD");
        when(roles.findById(2L)).thenReturn(Optional.of(role));
        PropertyRepository props = mock(PropertyRepository.class);
        PropertyDTO prop = new PropertyDTO();
        prop.setIdOrganizacion(ORG);
        when(props.findById(PROP)).thenReturn(Optional.of(prop));
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentManagementService svc = new AssignmentManagementService(assignments, roles, props,
                mock(PlanLimitService.class), null, inactiveStatus());

        AssignmentRequestDTO req = new AssignmentRequestDTO();
        req.setIdRol(2L);
        req.setIdUsuario(8L);
        req.setIdPropiedad(PROP);
        assertThrows(InactivePropertyException.class, () -> svc.create(req));
        verify(assignments, never()).create(any(), any());
    }
}
