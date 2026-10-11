package com.saed.backend.pqrs;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.exception.PqrsInvalidTransitionException;
import com.saed.backend.pqrs.repository.TicketRepository;
import com.saed.backend.pqrs.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F9-01 / F09-05: resident replies never move a ticket; only property admins transition, valid transitions only. */
class TicketServiceF9Test {

    private static final long PROPERTY = 3L;
    private static final long RESIDENT_USER = 7L;
    private static final long RESIDENT_PERSON = 70L;
    private static final long ADMIN_USER = 9L;

    private final TicketRepository repository = mock(TicketRepository.class);
    private final TicketServiceImpl service = new TicketServiceImpl(repository, mock(EmailService.class), mock(JdbcTemplate.class));

    @AfterEach
    void clearContext() {
        SaedContextHolder.clearContext();
    }

    private void asResident() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(RESIDENT_USER).propertyId(PROPERTY).unitId(31L).roleCode("RESIDENTE").build());
        when(repository.getIdPersonaFromUsuario(RESIDENT_USER)).thenReturn(RESIDENT_PERSON);
    }

    private void asAdmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(ADMIN_USER).propertyId(PROPERTY).roleCode("ADMIN_PROPIEDAD").build());
    }

    private TicketResponseDTO ticket(String estado, long radicaPersona) {
        TicketResponseDTO t = new TicketResponseDTO();
        t.setIdTicket(11L);
        t.setIdPersonaRadica(radicaPersona);
        t.setEstado(estado);
        t.setFechaRadicacion(ZonedDateTime.now());
        when(repository.findById(11L, PROPERTY)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void residentCannotChangeStateWhenReplying() {
        asResident();
        ticket("EN_GESTION", RESIDENT_PERSON);

        assertThrows(AccessDeniedException.class, () -> service.responderTicket(11L, "Mi respuesta", "CERRADO"));
        verify(repository, never()).updateEstado(anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(repository, never()).insertTrazabilidad(anyLong(), anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    void residentReplyIsRecordedWithoutChangingTheState() {
        asResident();
        ticket("EN_GESTION", RESIDENT_PERSON);

        service.responderTicket(11L, "Sigue fallando la luz", null);

        verify(repository, never()).updateEstado(anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(repository).insertTrazabilidad(eq(11L), eq(RESIDENT_USER), eq("RESPUESTA_RESIDENTE"),
                eq("EN_GESTION"), eq("EN_GESTION"), eq("Sigue fallando la luz"), isNull());
    }

    @Test
    void residentCannotUseTheAdministrativeOperations() {
        asResident();
        ticket("RADICADO", RESIDENT_PERSON);

        assertThrows(AccessDeniedException.class, () -> service.updateTicketStatus(11L, "CERRADO", null));
        assertThrows(AccessDeniedException.class, () -> service.asignarTicket(11L, 5L, null));
        assertThrows(AccessDeniedException.class, () -> service.actualizarPrioridad(11L, "EMERGENCIA"));
        verify(repository, never()).asignarResponsable(anyLong(), anyLong(), anyLong());
        verify(repository, never()).actualizarPrioridad(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void residentCannotReadAnotherResidentsTicketAndGetsNotFound() {
        asResident();
        ticket("RADICADO", 999L);

        assertThrows(NoSuchElementException.class, () -> service.getTicketById(11L));
        assertThrows(NoSuchElementException.class, () -> service.responderTicket(11L, "Hola hola", null));
    }

    @Test
    void unknownTicketIsNotFound() {
        asAdmin();
        when(repository.findById(404L, PROPERTY)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getTicketById(404L));
    }

    @Test
    void adminCannotMakeAnInvalidTransition() {
        asAdmin();
        ticket("CERRADO", RESIDENT_PERSON);

        assertThrows(PqrsInvalidTransitionException.class, () -> service.updateTicketStatus(11L, "EN_GESTION", "x"));
        verify(repository, never()).updateEstado(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void adminTransitionPersistsWithCompareAndSet() {
        asAdmin();
        ticket("EN_GESTION", RESIDENT_PERSON);
        when(repository.updateEstado(11L, PROPERTY, "EN_GESTION", "RESUELTO", "Listo")).thenReturn(1);

        service.updateTicketStatus(11L, "RESUELTO", "Listo");

        verify(repository).insertTrazabilidad(11L, ADMIN_USER, "CAMBIO_ESTADO", "EN_GESTION", "RESUELTO", "Listo", null);
    }

    @Test
    void adminReplyThatLosesTheRaceFails() {
        asAdmin();
        ticket("EN_GESTION", RESIDENT_PERSON);
        when(repository.updateEstado(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThrows(PqrsInvalidTransitionException.class, () -> service.responderTicket(11L, "Resuelto", "RESUELTO"));
        verify(repository, never()).insertTrazabilidad(anyLong(), anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    void adminCannotAssignAResponsibleOutsideTheProperty() {
        asAdmin();
        ticket("RADICADO", RESIDENT_PERSON);
        when(repository.esResponsableDePropiedad(55L, PROPERTY)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.asignarTicket(11L, 55L, null));
        verify(repository, never()).asignarResponsable(anyLong(), anyLong(), anyLong());
    }

    @Test
    void adminAssignsAResponsibleOfTheProperty() {
        asAdmin();
        ticket("RADICADO", RESIDENT_PERSON);
        when(repository.esResponsableDePropiedad(56L, PROPERTY)).thenReturn(true);
        when(repository.asignarResponsable(11L, PROPERTY, 56L)).thenReturn(1);

        service.asignarTicket(11L, 56L, null);

        verify(repository).asignarResponsable(11L, PROPERTY, 56L);
    }

    @Test
    void superAdminHasNoPqrsAccess() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).propertyId(PROPERTY).roleCode("SUPERADMIN").build());
        ticket("RADICADO", RESIDENT_PERSON);

        assertThrows(AccessDeniedException.class, () -> service.updateTicketStatus(11L, "EN_GESTION", null));
        assertThrows(AccessDeniedException.class, () -> service.getAllTickets());
    }

    @Test
    void residentTicketUsesTheSessionUnitNotTheClientUnit() {
        asResident();
        TicketRequestDTO req = request("MEDIA");
        req.setIdUnidad(888L);
        when(repository.create(any(), anyLong(), any(), anyLong(), anyString(), any(), any())).thenReturn(100L);

        service.createTicket(req);

        verify(repository).create(any(), eq(PROPERTY), eq(31L), eq(RESIDENT_USER), anyString(), any(), any());
    }

    @Test
    void defaultSlaHoursFollowDec05WhenThePropertyHasNoConfiguration() {
        asAdmin();
        assertEquals(Duration.ofHours(1), slaFor("CRITICA", "EMERGENCIA"));
        assertEquals(Duration.ofHours(4), slaFor("ALTA", "ALTA"));
        assertEquals(Duration.ofHours(24), slaFor("MEDIA", "MEDIA"));
        assertEquals(Duration.ofHours(72), slaFor("BAJA", "BAJA"));
    }

    private Duration slaFor(String requested, String stored) {
        when(repository.getSlaHoras(PROPERTY, stored)).thenReturn(Optional.empty());
        when(repository.create(any(), anyLong(), any(), anyLong(), anyString(), any(), any())).thenReturn(100L);
        ArgumentCaptor<TicketRequestDTO> reqCaptor = ArgumentCaptor.forClass(TicketRequestDTO.class);
        ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> due = ArgumentCaptor.forClass(ZonedDateTime.class);

        service.createTicket(request(requested));

        verify(repository, org.mockito.Mockito.atLeastOnce())
                .create(reqCaptor.capture(), anyLong(), any(), anyLong(), anyString(), start.capture(), due.capture());
        assertEquals(stored, reqCaptor.getValue().getPrioridad());
        return Duration.between(start.getValue(), due.getValue());
    }

    private static TicketRequestDTO request(String prioridad) {
        TicketRequestDTO req = new TicketRequestDTO();
        req.setAsunto("Fuga de agua");
        req.setDescripcion("Hay una fuga de agua en el sótano");
        req.setTipo("RECLAMO");
        req.setCategoria("MANTENIMIENTO");
        req.setPrioridad(prioridad);
        return req;
    }
}
