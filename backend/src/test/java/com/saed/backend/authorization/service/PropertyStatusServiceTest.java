package com.saed.backend.authorization.service;

import com.saed.backend.authorization.exception.InactivePropertyException;
import com.saed.backend.authorization.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyStatusServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    private PropertyStatusService propertyStatusService;

    @BeforeEach
    void setUp() {
        propertyStatusService = new PropertyStatusService(propertyRepository);
    }

    @Test
    void isPropertyActive_nullPropertyId_returnsTrue() {
        assertTrue(propertyStatusService.isPropertyActive(null));
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void isPropertyActive_activeProperty_returnsTrue() {
        when(propertyRepository.getPropertyStatus(1L)).thenReturn(Optional.of("ACTIVA"));

        assertTrue(propertyStatusService.isPropertyActive(1L));
        verify(propertyRepository, times(1)).getPropertyStatus(1L);

        // Second call should hit the cache within TTL
        assertTrue(propertyStatusService.isPropertyActive(1L));
        verify(propertyRepository, times(1)).getPropertyStatus(1L);
    }

    @Test
    void isPropertyActive_inactiveProperty_returnsFalse() {
        when(propertyRepository.getPropertyStatus(2L)).thenReturn(Optional.of("INACTIVA"));

        assertFalse(propertyStatusService.isPropertyActive(2L));
        verify(propertyRepository, times(1)).getPropertyStatus(2L);
    }

    @Test
    void validatePropertyIsActive_throwsWhenInactive() {
        when(propertyRepository.getPropertyStatus(3L)).thenReturn(Optional.of("INACTIVA"));

        InactivePropertyException ex = assertThrows(
                InactivePropertyException.class,
                () -> propertyStatusService.validatePropertyIsActive(3L)
        );
        assertTrue(ex.getMessage().contains("inactiva"));
    }

    @Test
    void validatePropertyIsActive_doesNotThrowWhenActive() {
        when(propertyRepository.getPropertyStatus(4L)).thenReturn(Optional.of("ACTIVA"));

        assertDoesNotThrow(() -> propertyStatusService.validatePropertyIsActive(4L));
    }

    @Test
    void updateCache_overridesCachedValueImmediately() {
        when(propertyRepository.getPropertyStatus(5L)).thenReturn(Optional.of("ACTIVA"));

        assertTrue(propertyStatusService.isPropertyActive(5L));

        // Admin deactivates property
        propertyStatusService.updateCache(5L, "INACTIVA");

        assertFalse(propertyStatusService.isPropertyActive(5L));
        // Should not query repository again because cache was updated
        verify(propertyRepository, times(1)).getPropertyStatus(5L);
    }

    @Test
    void invalidateCache_forcesRequery() {
        when(propertyRepository.getPropertyStatus(6L)).thenReturn(Optional.of("ACTIVA"));

        assertTrue(propertyStatusService.isPropertyActive(6L));

        propertyStatusService.invalidateCache(6L);

        assertTrue(propertyStatusService.isPropertyActive(6L));
        verify(propertyRepository, times(2)).getPropertyStatus(6L);
    }
}
