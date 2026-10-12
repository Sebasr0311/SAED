package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.AsambleaDTO;
import com.saed.backend.asambleas.dto.PoderRequestDTO;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proxy filing is limited to the unit's active owner (or an admin on the owner's behalf). */
class PoderRadicacionAuthorizationTest {

    private static final Long ASSEMBLY = 20L;
    private static final Long PROPERTY = 30L;
    private static final Long ORG = 40L;
    private static final Long UNIT = 5L;
    private static final Long OWNER = 100L;
    private static final Long OTHER_OWNER = 101L;
    private static final Long PROXY = 200L;
    private static final Long CALLER_USER = 60L;

    private final AsambleaRepository repository = mock(AsambleaRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AsambleaServiceImpl service = new AsambleaServiceImpl(repository, audit);

    @BeforeEach
    void setUp() {
        AsambleaDTO assembly = new AsambleaDTO();
        assembly.setIdAsamblea(ASSEMBLY);
        assembly.setIdPropiedad(PROPERTY);
        assembly.setIdOrganizacion(ORG);
        assembly.setEstado("EN_CURSO");
        when(repository.findById(ASSEMBLY)).thenReturn(Optional.of(assembly));
        when(repository.findPropiedadUnidad(UNIT)).thenReturn(PROPERTY);
        when(repository.existePoderUnidad(ASSEMBLY, UNIT)).thenReturn(false);
        when(repository.registrarPoder(anyLong(), any())).thenReturn(9L);
        when(repository.findPoderes(ASSEMBLY)).thenReturn(List.of());
        when(repository.esPropietarioLegalActivo(UNIT, OWNER)).thenReturn(true);
        when(repository.personaPerteneceAOrganizacion(PROXY, ORG)).thenReturn(true);
        when(repository.findPersonaUsuario(CALLER_USER)).thenReturn(OWNER);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private void caller(String role) {
        SaedContextHolder.setContext(SaedContext.builder().userId(CALLER_USER).organizationId(ORG).propertyId(PROPERTY)
                .roleCode(role).build());
    }

    private PoderRequestDTO request(Long unit, Long owner, Long proxy) {
        return PoderRequestDTO.builder().idUnidad(unit).idPersonaPropietario(owner).idPersonaApoderado(proxy).build();
    }

    @Test
    void anOwnerFilesTheirOwnProxy() {
        caller("PROPIETARIO");

        assertEquals(9L, service.radicarPoder(ASSEMBLY, request(UNIT, OWNER, PROXY)).getIdPoder());
    }

    @Test
    void aResidentCannotFileForAUnitTheyDoNotOwn() {
        caller("RESIDENTE");
        when(repository.findPersonaUsuario(CALLER_USER)).thenReturn(999L);

        assertThrows(AccessDeniedException.class, () -> service.radicarPoder(ASSEMBLY, request(UNIT, OWNER, PROXY)));
        verify(repository, never()).registrarPoder(anyLong(), any());
    }

    @Test
    void aResidentCannotNameAnotherOwnerAsGrantor() {
        caller("RESIDENTE");
        when(repository.esPropietarioLegalActivo(UNIT, OTHER_OWNER)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> service.radicarPoder(ASSEMBLY, request(UNIT, OTHER_OWNER, PROXY)));
        verify(repository, never()).registrarPoder(anyLong(), any());
    }

    @Test
    void aProxyHolderFromAnotherOrganizationIsRejected() {
        caller("PROPIETARIO");
        when(repository.personaPerteneceAOrganizacion(PROXY, ORG)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.radicarPoder(ASSEMBLY, request(UNIT, OWNER, PROXY)));
        verify(repository, never()).registrarPoder(anyLong(), any());
    }

    @Test
    void anAdminMustStillNameTheActiveOwnerOfTheUnit() {
        caller("ADMIN_PROPIEDAD");

        assertThrows(IllegalArgumentException.class, () -> service.radicarPoder(ASSEMBLY, request(UNIT, OTHER_OWNER, PROXY)));
        verify(repository, never()).registrarPoder(anyLong(), any());
    }

    @Test
    void anAdminMayFileOnBehalfOfTheOwner() {
        caller("ADMIN_PROPIEDAD");
        when(repository.findPersonaUsuario(CALLER_USER)).thenReturn(null);

        assertEquals(9L, service.radicarPoder(ASSEMBLY, request(UNIT, OWNER, PROXY)).getIdPoder());
    }
}
