package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyConfigDTO;
import com.saed.backend.authorization.repository.PropertyConfigRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyConfigServiceTest {

    @Mock
    private PropertyConfigRepository repository;

    @Mock
    private PlanLimitService planLimitService;

    private PropertyConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PropertyConfigServiceImpl(repository, planLimitService);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void findByKey_whenNotPersisted_shouldReturnSystemDefault() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        when(repository.findByPropertyIdAndKey(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD"))
                .thenReturn(Optional.empty());

        Optional<PropertyConfigDTO> result = service.findByKey(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD");
        assertTrue(result.isPresent());
        assertEquals("4", result.get().getValor());
    }

    @Test
    void findByKey_whenPersisted_shouldReturnPersistedValue() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        PropertyConfigDTO dto = new PropertyConfigDTO();
        dto.setIdPropiedad(10L);
        dto.setClave("LIMITE_CONVIVIENTES_POR_UNIDAD");
        dto.setValor("6");

        when(repository.findByPropertyIdAndKey(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD"))
                .thenReturn(Optional.of(dto));

        Optional<PropertyConfigDTO> result = service.findByKey(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD");
        assertTrue(result.isPresent());
        assertEquals("6", result.get().getValor());
    }

    @Test
    void getIntValue_shouldParseCorrectly() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        PropertyConfigDTO dto = new PropertyConfigDTO();
        dto.setIdPropiedad(10L);
        dto.setClave("TOLERANCIA_MORA_DIAS");
        dto.setValor("15");

        when(repository.findByPropertyIdAndKey(10L, "TOLERANCIA_MORA_DIAS"))
                .thenReturn(Optional.of(dto));

        int val = service.getIntValue(10L, "TOLERANCIA_MORA_DIAS", 30);
        assertEquals(15, val);
    }

    @Test
    void saveOrUpdate_withInvalidNegativeLimit_shouldThrowIllegalArgumentException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        assertThrows(IllegalArgumentException.class, () ->
                service.saveOrUpdate(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD", "-1", null));
        verify(repository, never()).saveOrUpdate(any(), any(), any(), any());
    }

    @Test
    void saveOrUpdate_withValidValue_shouldPersist() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        service.saveOrUpdate(10L, "LIMITE_CONVIVIENTES_POR_UNIDAD", "5", "Nuevo límite");
        verify(repository).saveOrUpdate(eq(10L), eq("LIMITE_CONVIVIENTES_POR_UNIDAD"), eq("5"), eq("Nuevo límite"));
    }

    @Test
    void crossPropertyAccess_shouldThrowAccessDenied() {
        // Admin for property 10 attempts to access config for property 20
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .propertyId(10L).build());

        assertThrows(AccessDeniedException.class, () ->
                service.findAll(20L));
    }
}
