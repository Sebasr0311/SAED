package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F5-H03 / F05-02: RESIDENTE_CONVIVENCIA must not read finances. Contracts and paz y salvo certificates are closed
 * to the role; the only remaining financial signal is the boolean arrears flag the reservation/works pages need,
 * without amounts or itemized reasons.
 */
class ConvivenciaFinanceExposureTest {

    private static final String CONVIVENCIA = "SCOPE_RESIDENTE_CONVIVENCIA";

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static String classRule(Class<?> type) {
        return type.getAnnotation(PreAuthorize.class).value();
    }

    private static String methodRule(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                PreAuthorize p = m.getAnnotation(PreAuthorize.class);
                return p != null ? p.value() : classRule(type);
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void contractsAreClosedToConvivencia() {
        assertFalse(classRule(ContratosController.class).contains(CONVIVENCIA));
        for (String m : List.of("getContratos", "getContratoPorId", "descargarPdf")) {
            assertFalse(methodRule(ContratosController.class, m).contains(CONVIVENCIA), m);
        }
    }

    @Test
    void pazYSalvoCertificatesAndUnitStatementsAreClosedToConvivencia() {
        assertFalse(classRule(PazYSalvoController.class).contains(CONVIVENCIA));
        for (String m : List.of("listar", "estadoFinanciero", "detalle", "descargar", "descargarPdfAlias")) {
            assertFalse(methodRule(PazYSalvoController.class, m).contains(CONVIVENCIA), m);
        }
        assertTrue(methodRule(PazYSalvoController.class, "miEstadoFinanciero").contains(CONVIVENCIA));
    }

    private static PazYSalvoEstadoFinancieroDTO debt() {
        return new PazYSalvoEstadoFinancieroDTO(100L, "101", new BigDecimal("250000"), new BigDecimal("50000"),
                new BigDecimal("300000"), false, List.of("Cartera vencida por $250.000"));
    }

    @Test
    void convivenciaGetsOnlyTheArrearsFlag() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE_CONVIVENCIA")
                .roleScope("UNIDAD").organizationId(1L).propertyId(10L).unitId(100L).build());
        PazYSalvoService service = mock(PazYSalvoService.class);
        when(service.verificarEstadoFinanciero(100L)).thenReturn(debt());

        ResponseEntity<ApiResponse<PazYSalvoEstadoFinancieroDTO>> r = new PazYSalvoController(service).miEstadoFinanciero();
        PazYSalvoEstadoFinancieroDTO dto = r.getBody().getData();

        assertTrue(dto.enMora());
        assertNull(dto.saldoCartera());
        assertNull(dto.saldoMultasNoFacturadas());
        assertNull(dto.saldoTotalExigible());
        assertFalse(String.valueOf(dto.motivosBloqueo()).contains("$"));
    }

    @Test
    void titularStillGetsTheFullStatement() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("RESIDENTE")
                .roleScope("UNIDAD").organizationId(1L).propertyId(10L).unitId(100L).build());
        PazYSalvoService service = mock(PazYSalvoService.class);
        when(service.verificarEstadoFinanciero(100L)).thenReturn(debt());

        PazYSalvoEstadoFinancieroDTO dto = new PazYSalvoController(service).miEstadoFinanciero().getBody().getData();
        assertEquals(new BigDecimal("300000"), dto.saldoTotalExigible());
    }
}
