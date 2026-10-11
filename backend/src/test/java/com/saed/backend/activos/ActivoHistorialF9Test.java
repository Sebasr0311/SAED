package com.saed.backend.activos;

import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoHistorialDTO;
import com.saed.backend.activos.exception.ActivoNoEncontradoException;
import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.activos.service.impl.ActivoServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.repository.MantenimientoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F09-01: the asset record shows its real maintenance history (orders, execution, cost, report). */
class ActivoHistorialF9Test {

    private static final long PROPERTY = 3L;

    private final ActivoRepository activos = mock(ActivoRepository.class);
    private final MantenimientoRepository mantenimientos = mock(MantenimientoRepository.class);
    private final ActivoServiceImpl service = new ActivoServiceImpl(activos, mantenimientos);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    @Test
    void historyListsTheAssetMaintenanceOrders() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).propertyId(PROPERTY).roleCode("ADMIN_PROPIEDAD").build());
        when(activos.buscarPorId(7L, PROPERTY)).thenReturn(Optional.of(mock(ActivoDTO.class)));
        MantenimientoDTO m = new MantenimientoDTO();
        m.setIdMantenimiento(50L);
        m.setTipoMantenimiento("PREVENTIVO");
        m.setTitulo("Cambio de guayas");
        m.setEstado("COMPLETADO");
        m.setFechaProgramada(LocalDate.of(2026, 3, 1));
        m.setCostoReal(new BigDecimal("320000"));
        m.setInformeTecnicoUrl("https://files/informe.pdf");
        m.setRazonSocialProveedor("Ascensores SAS");
        when(mantenimientos.findAllByPropiedad(PROPERTY, null, null, null, 7L, null)).thenReturn(List.of(m));

        List<ActivoHistorialDTO> history = service.historial(7L);

        assertEquals(1, history.size());
        assertEquals(50L, history.get(0).idMantenimiento());
        assertEquals("COMPLETADO", history.get(0).estado());
        assertEquals(new BigDecimal("320000"), history.get(0).costoReal());
        assertEquals("https://files/informe.pdf", history.get(0).informeTecnicoUrl());
    }

    @Test
    void historyOfAnUnknownOrForeignAssetIsNotFound() {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).propertyId(PROPERTY).roleCode("ADMIN_PROPIEDAD").build());
        when(activos.buscarPorId(8L, PROPERTY)).thenReturn(Optional.empty());
        when(activos.buscarPorIdDirecto(8L)).thenReturn(Optional.of(mock(ActivoDTO.class)));

        assertThrows(ActivoNoEncontradoException.class, () -> service.historial(8L));
        verify(mantenimientos, never()).findAllByPropiedad(any(), any(), any(), any(), any(), any());
    }
}
