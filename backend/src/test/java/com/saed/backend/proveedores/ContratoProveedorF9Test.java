package com.saed.backend.proveedores;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoProveedorCreateDTO;
import com.saed.backend.proveedores.contratos.AvisoRenovacionRepository;
import com.saed.backend.proveedores.contratos.AvisoRenovacionService;
import com.saed.backend.proveedores.contratos.ContratoPorVencerDTO;
import com.saed.backend.proveedores.contratos.ContratoProveedorValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** F09-02: supplier contract validation and idempotent, tenant-scoped renewal notices. */
class ContratoProveedorF9Test {

    private static final long PROPERTY = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    private final AvisoRenovacionRepository avisos = mock(AvisoRenovacionRepository.class);
    private final AvisoRenovacionService service = new AvisoRenovacionService(avisos);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void asAdmin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).propertyId(PROPERTY).roleCode("ADMIN_PROPIEDAD").build());
    }

    private static ContratoProveedorCreateDTO contract(LocalDate start, LocalDate end) {
        return new ContratoProveedorCreateDTO(5L, "CT-01", "Aseo zonas comunes", new BigDecimal("1000000"),
                "MENSUAL", start, end, 30);
    }

    private static ContratoPorVencerDTO due(long id, LocalDate end) {
        return new ContratoPorVencerDTO(id, PROPERTY, "CT-" + id, "Aseo SAS", end, 30);
    }

    @Test
    void contractDatesAreRequiredAndOrdered() {
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(contract(null, TODAY)));
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(contract(TODAY, null)));
        assertThrows(IllegalArgumentException.class,
                () -> ContratoProveedorValidator.validar(contract(TODAY, TODAY.minusDays(1))));
        assertDoesNotThrow(() -> ContratoProveedorValidator.validar(contract(TODAY, TODAY)));
    }

    @Test
    void contractRequiresSupplierNumberObjectAndNonNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(
                new ContratoProveedorCreateDTO(null, "CT", "Aseo", BigDecimal.ONE, null, TODAY, TODAY, 30)));
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(
                new ContratoProveedorCreateDTO(5L, " ", "Aseo", BigDecimal.ONE, null, TODAY, TODAY, 30)));
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(
                new ContratoProveedorCreateDTO(5L, "CT", null, BigDecimal.ONE, null, TODAY, TODAY, 30)));
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(
                new ContratoProveedorCreateDTO(5L, "CT", "Aseo", new BigDecimal("-1"), null, TODAY, TODAY, 30)));
        assertThrows(IllegalArgumentException.class, () -> ContratoProveedorValidator.validar(
                new ContratoProveedorCreateDTO(5L, "CT", "Aseo", BigDecimal.ONE, null, TODAY, TODAY, 400)));
    }

    @Test
    void noticeWindowStartsDiasAlertaBeforeTheEnd() {
        LocalDate end = TODAY.plusDays(30);
        assertTrue(AvisoRenovacionService.enVentanaDeAviso(end, 30, TODAY));
        assertFalse(AvisoRenovacionService.enVentanaDeAviso(end, 29, TODAY));
        assertTrue(AvisoRenovacionService.enVentanaDeAviso(TODAY, 30, TODAY));
        assertFalse(AvisoRenovacionService.enVentanaDeAviso(TODAY.minusDays(1), 30, TODAY));
    }

    @Test
    void dueContractGetsOneNoticeAndOneAlert() {
        asAdmin();
        when(avisos.contratosPorVencer(PROPERTY, TODAY)).thenReturn(List.of(due(1L, TODAY.plusDays(10))));
        when(avisos.existeAviso(1L, TODAY.plusDays(10).toString())).thenReturn(false);

        assertEquals(1, service.generarAvisos(PROPERTY, TODAY));

        verify(avisos).registrarAviso(any(), eq(TODAY.plusDays(10).toString()));
        verify(avisos).crearAlertaAdmin(eq(PROPERTY), anyString());
    }

    @Test
    void replayDoesNotRepeatTheNotice() {
        asAdmin();
        when(avisos.contratosPorVencer(PROPERTY, TODAY)).thenReturn(List.of(due(1L, TODAY.plusDays(10))));
        when(avisos.existeAviso(1L, TODAY.plusDays(10).toString())).thenReturn(true);

        assertEquals(0, service.generarAvisos(PROPERTY, TODAY));
        verify(avisos, never()).registrarAviso(any(), anyString());
        verify(avisos, never()).crearAlertaAdmin(anyLong(), anyString());
    }

    @Test
    void concurrentInstanceThatLosesTheRaceCreatesNoAlert() {
        asAdmin();
        when(avisos.contratosPorVencer(PROPERTY, TODAY)).thenReturn(List.of(due(1L, TODAY.plusDays(10))));
        when(avisos.registrarAviso(any(), anyString())).thenThrow(new DuplicateKeyException("UQ_AVISO_RENOV_PERIODO"));

        assertEquals(0, service.generarAvisos(PROPERTY, TODAY));
        verify(avisos, never()).crearAlertaAdmin(anyLong(), anyString());
    }

    @Test
    void noticesAreScopedToTheContextPropertyAndAdmins() {
        asAdmin();
        assertThrows(AccessDeniedException.class, () -> service.generarAvisos(4L, TODAY));

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).propertyId(PROPERTY).roleCode("SUPERADMIN").build());
        assertThrows(AccessDeniedException.class, () -> service.generarAvisos(PROPERTY, TODAY));
        verifyNoInteractions(avisos);
    }
}
