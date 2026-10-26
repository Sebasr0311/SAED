package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaResidenteDTO;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F10-17: residents get a minimized policy view without money fields; the write operations are transactional.
 */
class PolizaSeguroResidentViewTest {

    private static final Long PROPERTY_ID = 30L;

    private final PolizaSeguroRepository repository = mock(PolizaSeguroRepository.class);
    private final PolizaSeguroService service = new PolizaSeguroService(repository);

    @BeforeEach
    void setUp() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(2L).propertyId(PROPERTY_ID)
                .roleCode("RESIDENTE").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void residentsSeeOnlyTheMinimizedViewOfCurrentPolicies() {
        PolizaSeguroDTO current = policy(1L, "POL-1", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(11));
        PolizaSeguroDTO expired = policy(2L, "POL-2", LocalDate.now().minusYears(2), LocalDate.now().minusYears(1));
        when(repository.findAllByPropiedad(PROPERTY_ID)).thenReturn(List.of(current, expired));

        List<PolizaResidenteDTO> result = service.getPolizasVigentesResidentes();

        assertEquals(1, result.size());
        PolizaResidenteDTO view = result.get(0);
        assertEquals("Insurer", view.getCompaniaAseguradora());
        assertEquals("POL-1", view.getNumeroPoliza());
        assertEquals("TODO_RIESGO_AREAS_COMUNES", view.getRamoCobertura());
        assertNotNull(view.getFechaInicio());
        assertNotNull(view.getFechaFin());
        assertEquals("VIGENTE", view.getEstado());
    }

    @Test
    void theResidentViewCarriesNoMoneyOrContactFields() {
        List<String> fields = Arrays.stream(PolizaResidenteDTO.class.getDeclaredFields()).map(f -> f.getName()).toList();

        for (String forbidden : List.of("valorAsegurado", "valorPrimaAnual", "deducible", "nombreCorredorAgente",
                "telefonoContactoAgente", "documentoCaratulaUrl", "idDocumento", "idPropiedad")) {
            assertFalse(fields.contains(forbidden), "resident view must not expose " + forbidden);
        }
    }

    @Test
    void writeOperationsAreTransactional() throws Exception {
        for (String name : List.of("createPoliza", "updatePoliza", "deletePoliza", "delete")) {
            Method method = Arrays.stream(PolizaSeguroService.class.getMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            assertTrue(method.isAnnotationPresent(Transactional.class), name + " must be @Transactional");
        }
    }

    private PolizaSeguroDTO policy(Long id, String number, LocalDate start, LocalDate end) {
        PolizaSeguroDTO dto = new PolizaSeguroDTO();
        dto.setIdPoliza(id);
        dto.setCompaniaAseguradora("Insurer");
        dto.setNumeroPoliza(number);
        dto.setRamoCobertura("TODO_RIESGO_AREAS_COMUNES");
        dto.setValorAsegurado(new BigDecimal("1000000"));
        dto.setValorPrimaAnual(new BigDecimal("1000"));
        dto.setDeducible("10%");
        dto.setFechaInicio(start);
        dto.setFechaFin(end);
        return dto;
    }
}
