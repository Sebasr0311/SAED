package com.saed.backend.mantenimiento;

import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.mantenimiento.plan.PlanFrecuencia;
import com.saed.backend.mantenimiento.plan.PlanMantenimientoDTO;
import com.saed.backend.mantenimiento.plan.PlanMantenimientoRepository;
import com.saed.backend.mantenimiento.plan.PlanMantenimientoService;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** F09-01: recurring maintenance plans generate exactly one scheduled order per period, tenant-scoped. */
class PlanMantenimientoF9Test {

    private static final long PROPERTY = 3L;
    private static final LocalDate START = LocalDate.of(2026, 1, 31);

    private final PlanMantenimientoRepository repository = mock(PlanMantenimientoRepository.class);
    private final ActivoRepository activos = mock(ActivoRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final PlanMantenimientoService service = new PlanMantenimientoService(repository, activos, proveedores,
            mock(PropertyRepository.class));

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void asAdmin(long property) {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(1L).propertyId(property)
                .roleCode("ADMIN_PROPIEDAD").build());
    }

    private static PlanMantenimientoDTO plan(Long id, String frecuencia, LocalDate inicio) {
        return new PlanMantenimientoDTO(id, PROPERTY, 7L, "Revision ascensor", "Revision mensual del ascensor",
                "PREVENTIVO", "MEDIA", frecuencia, inicio, new BigDecimal("150000"), null, "S");
    }

    @Test
    void latestOccurrenceFollowsTheFrequencyAndClampsMonthEnds() {
        assertEquals(Optional.empty(), PlanFrecuencia.MENSUAL.ultimaOcurrencia(START, START.minusDays(1)));
        assertEquals(Optional.of(START), PlanFrecuencia.MENSUAL.ultimaOcurrencia(START, START));
        assertEquals(Optional.of(LocalDate.of(2026, 2, 28)),
                PlanFrecuencia.MENSUAL.ultimaOcurrencia(START, LocalDate.of(2026, 3, 30)));
        assertEquals(Optional.of(LocalDate.of(2026, 3, 31)),
                PlanFrecuencia.MENSUAL.ultimaOcurrencia(START, LocalDate.of(2026, 3, 31)));
        assertEquals(Optional.of(LocalDate.of(2026, 7, 31)),
                PlanFrecuencia.SEMESTRAL.ultimaOcurrencia(START, LocalDate.of(2026, 12, 1)));
        assertEquals(Optional.of(START), PlanFrecuencia.ANUAL.ultimaOcurrencia(START, LocalDate.of(2027, 1, 30)));
    }

    @Test
    void generatesTheDueOrderOnce() {
        asAdmin(PROPERTY);
        when(repository.listarActivos(PROPERTY)).thenReturn(List.of(plan(1L, "MENSUAL", START)));
        when(repository.existeOrden(1L, "2026-03-31")).thenReturn(false);
        when(repository.crearOrden(any(), eq(LocalDate.of(2026, 3, 31)), eq("2026-03-31"))).thenReturn(500L);

        assertEquals(1, service.generarOrdenesProgramadas(PROPERTY, LocalDate.of(2026, 4, 10)));

        verify(repository).crearOrden(any(), eq(LocalDate.of(2026, 3, 31)), eq("2026-03-31"));
    }

    @Test
    void replayAfterRestartCreatesNothing() {
        asAdmin(PROPERTY);
        when(repository.listarActivos(PROPERTY)).thenReturn(List.of(plan(1L, "MENSUAL", START)));
        when(repository.existeOrden(1L, "2026-03-31")).thenReturn(true);

        assertEquals(0, service.generarOrdenesProgramadas(PROPERTY, LocalDate.of(2026, 4, 10)));

        verify(repository, never()).crearOrden(any(), any(), anyString());
    }

    @Test
    void concurrentInstanceLosingTheUniqueIndexRaceDoesNotDuplicateOrFail() {
        asAdmin(PROPERTY);
        when(repository.listarActivos(PROPERTY)).thenReturn(List.of(plan(1L, "MENSUAL", START), plan(2L, "ANUAL", START)));
        when(repository.crearOrden(any(), any(), anyString()))
                .thenThrow(new DuplicateKeyException("UQ_MANT_PLAN_PERIODO"))
                .thenReturn(501L);

        assertEquals(1, service.generarOrdenesProgramadas(PROPERTY, LocalDate.of(2026, 4, 10)));
        verify(repository, times(2)).crearOrden(any(), any(), anyString());
    }

    @Test
    void planNotYetStartedGeneratesNothing() {
        asAdmin(PROPERTY);
        when(repository.listarActivos(PROPERTY)).thenReturn(List.of(plan(1L, "MENSUAL", LocalDate.of(2026, 6, 1))));

        assertEquals(0, service.generarOrdenesProgramadas(PROPERTY, LocalDate.of(2026, 4, 10)));
        verify(repository, never()).crearOrden(any(), any(), anyString());
    }

    @Test
    void generationIsScopedToTheContextProperty() {
        asAdmin(PROPERTY);

        assertThrows(AccessDeniedException.class, () -> service.generarOrdenesProgramadas(4L, LocalDate.of(2026, 4, 10)));
        verifyNoInteractions(repository);
    }

    @Test
    void creatingAPlanValidatesAssetSupplierAndFrequency() {
        asAdmin(PROPERTY);
        when(activos.buscarPorId(7L, PROPERTY)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.crearPlan(plan(null, "MENSUAL", START)));

        when(activos.buscarPorId(7L, PROPERTY)).thenReturn(Optional.of(mock(ActivoDTO.class)));
        assertThrows(IllegalArgumentException.class, () -> service.crearPlan(plan(null, "QUINCENAL", START)));

        PlanMantenimientoDTO withSupplier = new PlanMantenimientoDTO(null, null, 7L, "Revision", "Revision del equipo",
                "PREVENTIVO", "MEDIA", "MENSUAL", START, BigDecimal.ZERO, 80L, null);
        when(proveedores.buscarPorId(80L, 1L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.crearPlan(withSupplier));
        verify(repository, never()).crear(any(), anyLong(), anyLong());

        when(proveedores.buscarPorId(80L, 1L)).thenReturn(Optional.of(mock(ProveedorDTO.class)));
        when(repository.crear(any(), eq(PROPERTY), eq(9L))).thenReturn(12L);
        assertEquals(12L, service.crearPlan(withSupplier));
    }

    @Test
    void superAdminCannotManagePlans() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).propertyId(PROPERTY).roleCode("SUPERADMIN").build());

        assertThrows(AccessDeniedException.class, () -> service.listarPlanes());
        assertThrows(AccessDeniedException.class, () -> service.generarOrdenesProgramadas(PROPERTY, LocalDate.now()));
        assertTrue(true);
    }
}
