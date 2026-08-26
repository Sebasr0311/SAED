package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void create_withSuperadmin_shouldSucceed() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        PropertyRequestDTO request = new PropertyRequestDTO();
        request.setNombre("Test Property");
        request.setIdOrganizacion(1L);

        when(propertyRepository.create(any())).thenReturn(100L);

        Long id = propertyService.create(request);
        assertEquals(100L, id);
    }

    @Test
    void create_withOrgAdmin_shouldSucceedAndLockOrg() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(5L).build());

        PropertyRequestDTO request = new PropertyRequestDTO();
        request.setNombre("Test Property");
        request.setIdOrganizacion(99L); // Malicious attempt to create property for another org

        when(propertyRepository.create(any())).thenReturn(101L);

        Long id = propertyService.create(request);
        
        assertEquals(101L, id);
        assertEquals(5L, request.getIdOrganizacion()); // Anti-spoofing should reset to 5
    }

    @Test
    void create_withPropertyAdmin_shouldBeRejected() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(1L).build());

        PropertyRequestDTO request = new PropertyRequestDTO();
        request.setNombre("Test");

        assertThrows(AccessDeniedException.class, () -> propertyService.create(request));
    }

    @Test
    void update_orgAdmin_locksOrg() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(5L).build());

        PropertyRequestDTO request = new PropertyRequestDTO();
        request.setNombre("Updated");
        request.setIdOrganizacion(99L);

        propertyService.update(1L, request);
        
        verify(propertyRepository).update(eq(1L), any());
        assertEquals(5L, request.getIdOrganizacion());
    }
}
