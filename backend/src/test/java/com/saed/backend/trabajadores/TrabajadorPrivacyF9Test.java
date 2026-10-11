package com.saed.backend.trabajadores;

import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.exception.TrabajadorNoEncontradoException;
import com.saed.backend.trabajadores.repository.ObraTrabajadorRepository;
import com.saed.backend.trabajadores.repository.TrabajadorRepository;
import com.saed.backend.trabajadores.service.impl.TrabajadorServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F9-02 / F09-03: residents see minimal worker data, only for their own property; no SUPERADMIN path. */
class TrabajadorPrivacyF9Test {

    private static final long PROPERTY = 4L;

    private final TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
    private final ObraTrabajadorRepository obraTrabajadores = mock(ObraTrabajadorRepository.class);
    private final ObraRepository obras = mock(ObraRepository.class);
    private final TrabajadorServiceImpl service = new TrabajadorServiceImpl(trabajadores, obraTrabajadores,
            mock(ProveedorRepository.class), mock(PersonaRepository.class), obras, mock(PropertyRepository.class));

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static TrabajadorDTO worker() {
        return new TrabajadorDTO(5L, 50L, 6L, "Obras SAS", "CC", "1020304050", "Ana", "Maria", "Ruiz", "Paz",
                "Ana Ruiz", "ana@example.com", "3001234567", "Electricista", "Sura",
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6), LocalDate.now().plusMonths(2),
                null, "ACTIVO", true);
    }

    private static void as(String role, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(8L).organizationId(2L).propertyId(PROPERTY)
                .unitId(unit).roleCode(role).build());
    }

    private static void assertMinimal(TrabajadorDTO t) {
        assertEquals("Ana Ruiz", t.nombreCompleto());
        assertEquals("Obras SAS", t.razonSocialProveedor());
        assertEquals("Electricista", t.oficioEspecialidad());
        assertTrue(t.arlVigente());
        assertNull(t.numeroDocumento());
        assertNull(t.tipoDocumento());
        assertNull(t.email());
        assertNull(t.telefono());
        assertNull(t.idPersona());
        assertNull(t.arlAseguradora());
        assertNull(t.arlFechaAfiliacion());
        assertNull(t.arlFechaVencimiento());
        assertNull(t.certAlturasVencimiento());
    }

    @Test
    void residentListsOnlyMinimalWorkersOfTheirProperty() {
        as("RESIDENTE", 31L);
        when(trabajadores.listarParaPropiedad(PROPERTY, null)).thenReturn(List.of(worker()));

        List<TrabajadorDTO> result = service.listarTrabajadores(null, null, null);

        assertEquals(1, result.size());
        assertMinimal(result.get(0));
        verify(trabajadores, never()).listar(any(), any(), any(), any());
    }

    @Test
    void ownerGetsAMinimalWorkerOfTheirPropertyAndNotFoundOtherwise() {
        as("PROPIETARIO", 31L);
        when(trabajadores.buscarPorIdEnPropiedad(5L, PROPERTY)).thenReturn(Optional.of(worker()));
        when(trabajadores.buscarPorIdEnPropiedad(6L, PROPERTY)).thenReturn(Optional.empty());

        assertMinimal(service.obtenerTrabajador(5L));
        assertThrows(TrabajadorNoEncontradoException.class, () -> service.obtenerTrabajador(6L));
        verify(trabajadores, never()).buscarPorId(anyLong(), any());
    }

    @Test
    void residentSeesMinimalWorkersAssignedToTheirOwnWork() {
        as("RESIDENTE", 31L);
        ObraDTO obra = new ObraDTO();
        obra.setIdObra(70L);
        obra.setIdUnidad(31L);
        when(obras.findByIdDirecto(70L)).thenReturn(Optional.of(obra));
        when(obraTrabajadores.listarPorObra(70L)).thenReturn(List.of(
                new ObraTrabajadorDTO(1L, 70L, 5L, "S", null, null, 9L, worker())));

        List<ObraTrabajadorDTO> result = service.listarTrabajadoresObra(70L);

        assertMinimal(result.get(0).trabajador());
    }

    @Test
    void adminStillSeesTheFullRecord() {
        as("ADMIN_PROPIEDAD", null);
        when(trabajadores.listar(2L, null, null, null)).thenReturn(List.of(worker()));

        assertEquals("1020304050", service.listarTrabajadores(null, null, null).get(0).numeroDocumento());
    }

    @Test
    void superAdminHasNoWorkerAccess() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build());

        assertThrows(AccessDeniedException.class, () -> service.listarTrabajadores(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.autorizarTrabajadorObra(70L, 5L));
    }
}
