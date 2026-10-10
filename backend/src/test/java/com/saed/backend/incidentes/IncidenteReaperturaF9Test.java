package com.saed.backend.incidentes;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.controller.IncidenteController;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.dto.IncidenteEstadoRequestDTO;
import com.saed.backend.incidentes.dto.IncidenteInvolucradoDTO;
import com.saed.backend.incidentes.exception.IncidenteInvalidTransitionException;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import com.saed.backend.incidentes.service.impl.IncidenteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** F9-03 / F09-04: supported, audited incident reopen; foreign/missing ids are 404; residents get a reduced view. */
class IncidenteReaperturaF9Test {

    private static final long PROPERTY = 2L;

    private final IncidenteRepository repository = mock(IncidenteRepository.class);
    private final IncidenteServiceImpl service = new IncidenteServiceImpl(repository, mock(NamedParameterJdbcTemplate.class));

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void as(String role, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(15L).propertyId(PROPERTY).unitId(unit)
                .roleCode(role).build());
    }

    private IncidenteDTO incident(String estado, Long unit) {
        IncidenteDTO dto = new IncidenteDTO();
        dto.setIdIncidente(40L);
        dto.setIdUnidad(unit);
        dto.setEstado(estado);
        dto.setHallazgosInvestigacion("Camara 3 muestra al vecino del 502");
        dto.setSancionSugerida("Multa");
        dto.setNumeroDenunciaPolicia("N-123");
        when(repository.findById(40L, PROPERTY)).thenReturn(Optional.of(dto));
        return dto;
    }

    @Test
    void controllerExposesAnAdminOnlyReopenRoute() throws Exception {
        Method reabrir = Arrays.stream(IncidenteController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("reabrirIncidente")).findFirst().orElseThrow();
        assertTrue(Arrays.asList(reabrir.getAnnotation(PostMapping.class).value()).contains("/{id}/reabrir"));
        assertEquals("hasAuthority('SCOPE_ADMIN_PROPIEDAD')", reabrir.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void adminReopensAClosedIncidentAndTheReasonIsPersisted() {
        as("ADMIN_PROPIEDAD", null);
        incident("CERRADO", 31L);
        when(repository.reabrir(40L, PROPERTY, 15L, "Nueva evidencia del vecino")).thenReturn(1);

        service.reabrirIncidente(40L, "  Nueva evidencia del vecino ");

        verify(repository).reabrir(40L, PROPERTY, 15L, "Nueva evidencia del vecino");
    }

    @Test
    void reopenRequiresAMeaningfulReason() {
        as("ADMIN_PROPIEDAD", null);
        incident("CERRADO", 31L);

        assertThrows(IllegalArgumentException.class, () -> service.reabrirIncidente(40L, null));
        assertThrows(IllegalArgumentException.class, () -> service.reabrirIncidente(40L, "   "));
        assertThrows(IllegalArgumentException.class, () -> service.reabrirIncidente(40L, "corto"));
        verify(repository, never()).reabrir(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void onlyClosedIncidentsCanBeReopened() {
        as("ADMIN_PROPIEDAD", null);
        incident("EN_INVESTIGACION", 31L);

        assertThrows(IncidenteInvalidTransitionException.class,
                () -> service.reabrirIncidente(40L, "Nueva evidencia del vecino"));
        verify(repository, never()).reabrir(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void concurrentReopenIsAConflict() {
        as("ADMIN_PROPIEDAD", null);
        incident("CERRADO", 31L);
        when(repository.reabrir(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reabrirIncidente(40L, "Nueva evidencia del vecino"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void genericStatusChangeCannotBypassTheReopenReason() {
        as("ADMIN_PROPIEDAD", null);
        incident("CERRADO", 31L);
        IncidenteEstadoRequestDTO dto = new IncidenteEstadoRequestDTO();
        dto.setEstado("EN_INVESTIGACION");

        assertThrows(IncidenteInvalidTransitionException.class, () -> service.cambiarEstado(40L, dto));
        verify(repository, never()).iniciarInvestigacion(anyLong(), anyLong(), anyLong());
    }

    @Test
    void nonAdminsCannotReopen() {
        for (String role : List.of("PORTERO", "RESIDENTE", "SUPERADMIN")) {
            as(role, 31L);
            incident("CERRADO", 31L);
            assertThrows(AccessDeniedException.class, () -> service.reabrirIncidente(40L, "Nueva evidencia del vecino"));
        }
        verify(repository, never()).reabrir(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void missingOrForeignIncidentIsNotFound() {
        as("ADMIN_PROPIEDAD", null);
        when(repository.findById(41L, PROPERTY)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.reabrirIncidente(41L, "Nueva evidencia del vecino"));

        as("RESIDENTE", 99L);
        incident("REPORTADO", 31L);
        assertThrows(NoSuchElementException.class, () -> service.getIncidenteById(40L));
    }

    @Test
    void superAdminCannotReadIncidents() {
        as("SUPERADMIN", null);
        incident("REPORTADO", 31L);
        assertThrows(AccessDeniedException.class, () -> service.getIncidenteById(40L));
        assertThrows(AccessDeniedException.class, () -> service.getAllIncidentes());
    }

    @Test
    void residentSeesAReducedViewOfTheirOwnUnitIncident() {
        as("RESIDENTE", 31L);
        incident("EN_INVESTIGACION", 31L);
        when(repository.findInvolucradosByIncidente(40L)).thenReturn(List.of(new IncidenteInvolucradoDTO()));

        IncidenteDTO view = service.getIncidenteById(40L);

        assertNull(view.getHallazgosInvestigacion());
        assertNull(view.getSancionSugerida());
        assertNull(view.getNumeroDenunciaPolicia());
        assertTrue(view.getInvolucrados() == null || view.getInvolucrados().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getInvolucrados(40L));
    }

    @Test
    void failingInvolvedPartyAbortsTheReportInsteadOfBeingSwallowed() {
        as("ADMIN_PROPIEDAD", null);
        IncidenteDTO req = new IncidenteDTO();
        req.setTitulo("Rotura de vidrio");
        req.setTipoIncidente("DANO_BIEN_COMUN");
        req.setDescripcionHechos("Se rompio el vidrio del lobby");
        IncidenteInvolucradoDTO bad = new IncidenteInvolucradoDTO();
        bad.setRolEnIncidente("NO_EXISTE");
        req.setInvolucrados(List.of(bad));
        when(repository.createIncidente(any(), anyLong(), anyLong())).thenReturn(40L);
        incident("REPORTADO", null);

        assertThrows(IllegalArgumentException.class, () -> service.reportarIncidente(req));
    }
}
