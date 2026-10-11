package com.saed.backend.obras;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.obras.dto.IngresoObraDTO;
import com.saed.backend.obras.dto.InspeccionObraDTO;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.obras.service.impl.ObraServiceImpl;
import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.repository.ObraTrabajadorRepository;
import com.saed.backend.trabajadores.service.TrabajadorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F09-03: only approved, eligible workers enter within the authorized window; completion requires inspection. */
class ObraIngresoInspeccionF9Test {

    private static final long PROPERTY = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    private final ObraRepository obras = mock(ObraRepository.class);
    private final ObraTrabajadorRepository asignaciones = mock(ObraTrabajadorRepository.class);
    private final PazYSalvoService pazYSalvo = mock(PazYSalvoService.class);
    private final ObraServiceImpl service = new ObraServiceImpl(obras, pazYSalvo, mock(TrabajadorService.class), asignaciones);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void as(String role, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).propertyId(PROPERTY).unitId(unit).roleCode(role).build());
    }

    private ObraDTO obra(String estado) {
        ObraDTO o = new ObraDTO();
        o.setIdObra(70L);
        o.setIdUnidad(31L);
        o.setEstado(estado);
        o.setFechaInicio(TODAY.minusDays(5));
        o.setFechaFinEstimada(TODAY.plusDays(5));
        when(obras.findById(70L, PROPERTY)).thenReturn(Optional.of(o));
        return o;
    }

    private static TrabajadorDTO worker(LocalDate arlVence, String estado) {
        return new TrabajadorDTO(5L, 50L, 6L, "Obras SAS", "CC", "1020", "Ana", null, "Ruiz", null, "Ana Ruiz",
                "ana@example.com", "300", "Electricista", "Sura", TODAY.minusMonths(2), arlVence, null, null, estado,
                true);
    }

    private void assignment(String autorizado, TrabajadorDTO worker) {
        when(asignaciones.buscarPorObraYTrabajador(70L, 5L)).thenReturn(Optional.of(
                new ObraTrabajadorDTO(1L, 70L, 5L, autorizado, null, null, 9L, worker)));
    }

    @Test
    void approvedEligibleWorkerEntersWithinTheWindow() {
        as("PORTERO", null);
        obra("EN_EJECUCION");
        assignment("S", worker(TODAY.plusMonths(3), "ACTIVO"));

        IngresoObraDTO result = service.verificarIngresoTrabajador(70L, 5L, TODAY);

        assertTrue(result.permitido());
        assertEquals("Ana Ruiz", result.nombreTrabajador());
        assertEquals("Obras SAS", result.empresa());
    }

    @Test
    void unauthorizedRevokedOrUnassignedWorkersAreDenied() {
        as("PORTERO", null);
        obra("EN_EJECUCION");
        assignment("N", worker(TODAY.plusMonths(3), "ACTIVO"));
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY).permitido());

        when(asignaciones.buscarPorObraYTrabajador(70L, 5L)).thenReturn(Optional.empty());
        IngresoObraDTO unassigned = service.verificarIngresoTrabajador(70L, 5L, TODAY);
        assertFalse(unassigned.permitido());
        assertNull(unassigned.nombreTrabajador());
    }

    @Test
    void expiredArlOrInactiveWorkerIsDenied() {
        as("ADMIN_PROPIEDAD", null);
        obra("EN_EJECUCION");
        assignment("S", worker(TODAY.minusDays(1), "ACTIVO"));
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY).permitido());

        assignment("S", worker(TODAY.plusMonths(3), "INACTIVO"));
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY).permitido());
    }

    @Test
    void entryOutsideTheAuthorizedWindowOrStateIsDenied() {
        as("PORTERO", null);
        obra("EN_EJECUCION");
        assignment("S", worker(TODAY.plusYears(1), "ACTIVO"));
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY.plusDays(6)).permitido());
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY.minusDays(6)).permitido());

        obra("SOLICITADA");
        assertFalse(service.verificarIngresoTrabajador(70L, 5L, TODAY).permitido());
    }

    @Test
    void entryCheckIsStaffOnlyAndForeignWorksAreNotFound() {
        as("RESIDENTE", 31L);
        obra("EN_EJECUCION");
        assertThrows(AccessDeniedException.class, () -> service.verificarIngresoTrabajador(70L, 5L, TODAY));

        as("PORTERO", null);
        when(obras.findById(71L, PROPERTY)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.verificarIngresoTrabajador(71L, 5L, TODAY));
    }

    @Test
    void completionRequiresAnAcceptingInspection() {
        as("ADMIN_PROPIEDAD", null);
        obra("EN_EJECUCION");

        assertThrows(IllegalArgumentException.class, () -> service.finalizarObra(70L, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizarObra(70L, new InspeccionObraDTO("RECHAZADA", "Escombros en el pasillo")));
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizarObra(70L, new InspeccionObraDTO("APROBADA", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.cambiarEstado(70L, "FINALIZADA"));
        verify(obras, never()).finalizarConInspeccion(anyLong(), anyLong(), anyString(), anyString(), any());

        when(obras.finalizarConInspeccion(70L, PROPERTY, "APROBADA", "Zona limpia y sin daños", 9L)).thenReturn(1);
        service.finalizarObra(70L, new InspeccionObraDTO("aprobada", "Zona limpia y sin daños"));
        verify(obras).finalizarConInspeccion(70L, PROPERTY, "APROBADA", "Zona limpia y sin daños", 9L);
    }

    @Test
    void residentCannotSeeAnotherUnitsWork() {
        as("RESIDENTE", 99L);
        obra("APROBADA");
        assertThrows(NoSuchElementException.class, () -> service.getObraById(70L));

        when(obras.findById(72L, PROPERTY)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getObraById(72L));
    }

    @Test
    void approvalStillRequiresPazYSalvo() {
        as("ADMIN_PROPIEDAD", null);
        obra("SOLICITADA");
        when(pazYSalvo.verificarEstadoFinanciero(31L)).thenReturn(mock(PazYSalvoEstadoFinancieroDTO.class));
        assertThrows(RuntimeException.class, () -> service.aprobarObra(70L));
    }
}
