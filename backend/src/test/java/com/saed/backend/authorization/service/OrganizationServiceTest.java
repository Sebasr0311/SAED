package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import com.saed.backend.authorization.repository.OrganizationRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void create_withSuperadmin_shouldSucceed() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Test Org");
        request.setIdentificacionFiscal("900123456");
        request.setEmailContacto("test@test.com");

        when(organizationRepository.create(any(), eq(1L))).thenReturn(100L);

        Long id = organizationService.create(request);
        assertEquals(100L, id);
    }

    @Test
    void create_withPropertyScope_shouldBeRejected() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(1L).build());

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Hacked Org");
        request.setIdentificacionFiscal("900999999");
        request.setEmailContacto("hacked@test.com");

        assertThrows(AccessDeniedException.class, () -> organizationService.create(request));
    }

    @Test
    void update_orgScope_canUpdateOwnOrg() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(5L).build());

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Updated Name");
        request.setIdentificacionFiscal("900123456");
        request.setEmailContacto("test@test.com");

        organizationService.update(5L, request);
        verify(organizationRepository).update(eq(5L), any());
    }

    @Test
    void update_orgScope_cannotUpdateOtherOrg() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(5L).build());

        OrganizationRequestDTO request = new OrganizationRequestDTO();
        request.setNombre("Hacked");
        request.setIdentificacionFiscal("900000000");
        request.setEmailContacto("x@x.com");

        assertThrows(AccessDeniedException.class, () -> organizationService.update(99L, request));
    }

    @Test
    void updateStatus_nonGlobal_shouldBeRejected() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(1L).build());

        assertThrows(AccessDeniedException.class, () -> organizationService.updateStatus(1L, "INACTIVA"));
    }
}
