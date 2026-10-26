package com.saed.backend.parqueaderos;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.repository.ParqueaderosRepository;
import com.saed.backend.parqueaderos.service.impl.ParqueaderosServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F07-03: assigning a spot is a conditional claim, so two concurrent assignments cannot both occupy it. */
class ParkingAssignmentConcurrencyTest {

    private ParqueaderosRepository repo;
    private ParqueaderosServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(ParqueaderosRepository.class);
        service = new ParqueaderosServiceImpl(repo);
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L).propertyId(70L)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        when(repo.getParqueaderoById(15L)).thenReturn(Optional.of(new ParqueaderoDTO(15L, 70L, "P-15", "PRIVADO", "DISPONIBLE")));
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static AsignacionParqueaderoRequestDTO request(long spot) {
        return new AsignacionParqueaderoRequestDTO(spot, 701L, null, "PROPIETARIO", java.math.BigDecimal.ZERO);
    }

    @Test
    void spotClaimedConcurrentlyIsAConflict() {
        when(repo.reservarParqueadero(15L, 70L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.crearAsignacion(request(15L)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repo, never()).crearAsignacion(any());
    }

    @Test
    void assignmentIsCreatedOnlyAfterTheConditionalClaim() {
        when(repo.reservarParqueadero(15L, 70L)).thenReturn(true);
        service.crearAsignacion(request(15L));
        verify(repo).crearAsignacion(any());
        verify(repo, never()).actualizarEstadoParqueadero(anyLong(), anyString());
    }

    @Test
    void spotOfAnotherPropertyIsDenied() {
        when(repo.getParqueaderoById(16L)).thenReturn(Optional.of(new ParqueaderoDTO(16L, 80L, "P-16", "PRIVADO", "DISPONIBLE")));
        assertThrows(AccessDeniedException.class, () -> service.crearAsignacion(request(16L)));
        verify(repo, never()).reservarParqueadero(anyLong(), anyLong());
    }
}
