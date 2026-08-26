package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.repository.UnitRepository;
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
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private UnitService unitService;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void create_withSuperadmin_shouldSucceed() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        UnitRequestDTO request = new UnitRequestDTO();
        request.setIdentificador("101");
        request.setIdPropiedad(1L);

        when(unitRepository.create(any())).thenReturn(100L);

        Long id = unitService.create(request);
        assertEquals(100L, id);
    }

    @Test
    void create_withPropertyAdmin_shouldSucceedAndLockProperty() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(5L).build());

        UnitRequestDTO request = new UnitRequestDTO();
        request.setIdentificador("101");
        request.setIdPropiedad(99L); // Malicious attempt to create unit for another property

        when(unitRepository.create(any())).thenReturn(101L);

        Long id = unitService.create(request);
        
        assertEquals(101L, id);
        assertEquals(5L, request.getIdPropiedad()); // Anti-spoofing should reset to 5
    }

    @Test
    void create_withUnitAdmin_shouldBeRejected() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(4L).roleCode("ADMIN_UNIDAD").roleScope("UNIDAD")
                .organizationId(1L).propertyId(1L).unitId(1L).build());

        UnitRequestDTO request = new UnitRequestDTO();
        request.setIdentificador("102");

        assertThrows(AccessDeniedException.class, () -> unitService.create(request));
    }
    
    @Test
    void create_withResident_shouldBeRejected() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(5L).roleCode("RESIDENTE").roleScope("UNIDAD")
                .organizationId(1L).propertyId(1L).unitId(2L).build());

        UnitRequestDTO request = new UnitRequestDTO();
        request.setIdentificador("103");

        assertThrows(AccessDeniedException.class, () -> unitService.create(request));
    }

    @Test
    void update_propertyAdmin_locksProperty() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(5L).build());

        UnitRequestDTO request = new UnitRequestDTO();
        request.setIdentificador("Updated");
        request.setIdPropiedad(99L);

        unitService.update(1L, request);
        
        verify(unitRepository).update(eq(1L), any());
        assertEquals(5L, request.getIdPropiedad());
    }
}
