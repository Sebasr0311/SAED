package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Policies outside the caller's property, or already gone, are reported as not found (404) for
 * read, update and delete (audit F10-17).
 */
class PolizaSeguroNotFoundTest {

    private static final Long PROPERTY_ID = 30L;

    private final PolizaSeguroRepository repository = mock(PolizaSeguroRepository.class);
    private final PolizaSeguroService service = new PolizaSeguroService(repository);

    @BeforeEach
    void setUp() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(2L).propertyId(PROPERTY_ID)
                .roleCode("ADMIN_PROPIEDAD").build());
        when(repository.findByIdAndPropiedad(any(), any())).thenReturn(Optional.empty());
        when(repository.update(any())).thenReturn(0);
        when(repository.delete(any(), any())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void readingAMissingPolicyIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> service.getPolizaById(9L));
    }

    @Test
    void updatingAMissingPolicyIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> service.updatePoliza(9L, validPolicy()));
    }

    @Test
    void deletingAMissingPolicyIsNotFound() {
        assertThrows(NoSuchElementException.class, () -> service.deletePoliza(9L));
    }

    private PolizaSeguroDTO validPolicy() {
        PolizaSeguroDTO dto = new PolizaSeguroDTO();
        dto.setCompaniaAseguradora("Insurer");
        dto.setNumeroPoliza("POL-9");
        dto.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        dto.setValorAsegurado(new BigDecimal("1000000"));
        dto.setValorPrimaAnual(new BigDecimal("1000"));
        dto.setFechaInicio(LocalDate.now().minusMonths(1));
        dto.setFechaFin(LocalDate.now().plusMonths(11));
        return dto;
    }
}
