package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.repository.AssignmentRepository;
import com.saed.backend.authorization.repository.RoleRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentManagementServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AssignmentManagementService service;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void create_superadmin_canAssignAnyScope() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        RoleDTO targetRole = new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION");
        targetRole.setIdRol(2L);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(targetRole));
        when(assignmentRepository.create(any(), eq(1L))).thenReturn(50L);

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(2L);
        request.setIdOrganizacion(1L);

        Long id = service.create(request);
        assertEquals(50L, id);
    }

    @Test
    void create_propertyScope_cannotAssignGlobalRole() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(1L).build());

        RoleDTO targetRole = new RoleDTO("SUPERADMIN", "GLOBAL");
        targetRole.setIdRol(1L);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(targetRole));

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(1L);

        assertThrows(AccessDeniedException.class, () -> service.create(request));
    }

    @Test
    void create_residente_cannotCreateAssignments() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("RESIDENTE").roleScope("UNIDAD")
                .organizationId(1L).propertyId(1L).unitId(1L).build());

        RoleDTO targetRole = new RoleDTO("RESIDENTE", "UNIDAD");
        targetRole.setIdRol(5L);
        when(roleRepository.findById(5L)).thenReturn(Optional.of(targetRole));

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(5L);

        assertThrows(AccessDeniedException.class, () -> service.create(request));
    }

    @Test
    void create_orgScope_missingOrgId_shouldFail() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        RoleDTO targetRole = new RoleDTO("ADMIN_ORGANIZACION", "ORGANIZACION");
        targetRole.setIdRol(2L);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(targetRole));

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(2L);
        // No organization ID provided

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void create_unitScope_missingIds_shouldFail() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        RoleDTO targetRole = new RoleDTO("RESIDENTE", "UNIDAD");
        targetRole.setIdRol(5L);
        when(roleRepository.findById(5L)).thenReturn(Optional.of(targetRole));

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(5L);
        request.setIdOrganizacion(1L);
        // Missing property and unit

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void create_orgScope_antiSpoofing_locksOrganization() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(5L).build());

        RoleDTO targetRole = new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD");
        targetRole.setIdRol(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(targetRole));
        when(assignmentRepository.create(any(), eq(2L))).thenReturn(99L);

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(3L);
        request.setIdOrganizacion(999L); // attacker tries spoofing
        request.setIdPropiedad(1L);

        service.create(request);

        // Anti-spoofing should have overwritten org to 5
        assertEquals(5L, request.getIdOrganizacion());
    }

    @Test
    void create_roleNotFound_shouldFail() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        AssignmentRequestDTO request = new AssignmentRequestDTO();
        request.setIdUsuario(10L);
        request.setIdRol(999L);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }
}
