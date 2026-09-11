package com.saed.backend.parqueaderos;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoMasivoRequestDTO;
import com.saed.backend.parqueaderos.repository.ParqueaderosRepository;
import com.saed.backend.parqueaderos.service.ParqueaderosService;
import com.saed.backend.parqueaderos.service.impl.ParqueaderosServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParqueaderosServiceTest {

    @Mock
    private ParqueaderosRepository parqueaderosRepository;

    private ParqueaderosService parqueaderosService;

    @BeforeEach
    void setUp() {
        parqueaderosService = new ParqueaderosServiceImpl(parqueaderosRepository);
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L)
                .organizationId(1L)
                .propertyId(5L)
                .roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD")
                .build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void testParqueaderoMasivoRequestDTODefaults() {
        ParqueaderoMasivoRequestDTO dto = new ParqueaderoMasivoRequestDTO(null, 10, 101, "PRIVADO", null);
        assertEquals("", dto.prefijo());
        assertEquals("DISPONIBLE", dto.estado());
        assertEquals(10, dto.cantidad());
        assertEquals(101, dto.numeroInicial());
        assertEquals("PRIVADO", dto.tipo());
    }

    @Test
    void testRegistrarParqueaderosMasivoDelegatesToRepositoryWithPropertyId() {
        ParqueaderoMasivoRequestDTO request = new ParqueaderoMasivoRequestDTO("P", 5, 1, "PRIVADO", "DISPONIBLE");

        List<ParqueaderoDTO> mockList = List.of(
                new ParqueaderoDTO(1L, 5L, "P-1", "PRIVADO", "DISPONIBLE"),
                new ParqueaderoDTO(2L, 5L, "P-2", "PRIVADO", "DISPONIBLE"),
                new ParqueaderoDTO(3L, 5L, "P-3", "PRIVADO", "DISPONIBLE"),
                new ParqueaderoDTO(4L, 5L, "P-4", "PRIVADO", "DISPONIBLE"),
                new ParqueaderoDTO(5L, 5L, "P-5", "PRIVADO", "DISPONIBLE")
        );

        when(parqueaderosRepository.registrarParqueaderosMasivo(eq(request), eq(5L))).thenReturn(mockList);

        List<ParqueaderoDTO> result = parqueaderosService.registrarParqueaderosMasivo(request);

        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals("P-1", result.get(0).numeroParqueadero());
        assertEquals("P-5", result.get(4).numeroParqueadero());
        verify(parqueaderosRepository).registrarParqueaderosMasivo(eq(request), eq(5L));
    }
}
