package com.saed.backend.porteria.intercom;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.repository.PorteriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F07-02: intercom requests are persisted; the gate reads the residents' real decision. */
class IntercomServiceTest {

    private static final long PROP = 70L;
    private static final long UNIT = 701L;
    private static final long VISIT = 5000L;
    private static final String TOKEN = "tok";

    private IntercomRepository repo;
    private PorteriaRepository porteria;
    private IntercomService service;

    @BeforeEach
    void setUp() {
        repo = mock(IntercomRepository.class);
        porteria = mock(PorteriaRepository.class);
        service = new IntercomService(repo, porteria);
        when(porteria.getQrAccesoByToken(TOKEN)).thenReturn(Optional.of(new QrAccesoDTO(77L, VISIT, TOKEN,
            ZonedDateTime.now(), ZonedDateTime.now().plusHours(1), 1, 0, null, null, null, "ACTIVO", 40L)));
        when(porteria.getVisitaById(VISIT)).thenReturn(Optional.of(new VisitaDTO(VISIT, UNIT, 900L, "CODIGO_QR",
            "m", 40L, ZonedDateTime.now(), "PROGRAMADA", ZonedDateTime.now())));
        when(porteria.findVisitaPropertyId(VISIT)).thenReturn(Optional.of(PROP));
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void portero(long prop) {
        SaedContextHolder.setContext(SaedContext.builder().userId(30L).organizationId(1L).propertyId(prop)
            .roleCode("PORTERO").roleScope("PROPIEDAD").build());
    }

    private static void resident(long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(1L).propertyId(PROP)
            .unitId(unit).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    private static IntercomRequest req(String estado) {
        return new IntercomRequest(9L, PROP, UNIT, VISIT, "Ana", estado, ZonedDateTime.now(),
            ZonedDateTime.now().plusMinutes(5), null, null);
    }

    @Test
    void gateRequestIsPersistedAsPendingAndNeverReportedAsApproved() {
        portero(PROP);
        when(repo.findOpenByVisita(VISIT)).thenReturn(Optional.empty());
        when(repo.create(eq(PROP), eq(UNIT), eq(VISIT), eq(30L), any(), anyInt())).thenReturn(9L);
        when(repo.findById(9L)).thenReturn(Optional.of(req("PENDIENTE")));

        Map<String, Object> res = service.requestFromQr(TOKEN);

        assertEquals(9L, res.get("idSolicitud"));
        assertEquals("PENDIENTE", res.get("estado"));
        verify(repo).create(eq(PROP), eq(UNIT), eq(VISIT), eq(30L), any(), anyInt());
    }

    @Test
    void repeatedGateRequestReusesTheOpenRequest() {
        portero(PROP);
        when(repo.findOpenByVisita(VISIT)).thenReturn(Optional.of(req("PENDIENTE")));

        Map<String, Object> res = service.requestFromQr(TOKEN);

        assertEquals(9L, res.get("idSolicitud"));
        verify(repo, never()).create(anyLong(), anyLong(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void gateOfAnotherPropertyCannotRequest() {
        portero(80L);
        assertThrows(NoSuchElementException.class, () -> service.requestFromQr(TOKEN));
        verify(repo, never()).create(anyLong(), anyLong(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void requestWithoutContextIsDenied() {
        assertThrows(AccessDeniedException.class, () -> service.requestFromQr(TOKEN));
    }

    @Test
    void residentListsOnlyOwnUnitPendingRequests() {
        resident(UNIT);
        when(repo.findPendingByUnits(List.of(UNIT))).thenReturn(List.of(req("PENDIENTE")));

        List<Map<String, Object>> list = service.pendingForCaller();

        assertEquals(1, list.size());
        assertEquals(9L, list.get(0).get("idMensaje"));
    }

    @Test
    void residentApprovesOwnUnitRequest() {
        resident(UNIT);
        when(repo.findById(9L)).thenReturn(Optional.of(req("PENDIENTE")));
        when(repo.decide(9L, "APROBADA", 40L)).thenReturn(1);

        service.decide(9L, true);

        verify(repo).decide(9L, "APROBADA", 40L);
    }

    @Test
    void residentOfAnotherUnitCannotDecide() {
        resident(999L);
        when(repo.findById(9L)).thenReturn(Optional.of(req("PENDIENTE")));
        assertThrows(NoSuchElementException.class, () -> service.decide(9L, true));
        verify(repo, never()).decide(anyLong(), anyString(), anyLong());
    }

    @Test
    void secondDecisionOrExpiredRequestIsAConflict() {
        resident(UNIT);
        when(repo.findById(9L)).thenReturn(Optional.of(req("PENDIENTE")));
        when(repo.decide(9L, "RECHAZADA", 40L)).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.decide(9L, false));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repo).expireIfDue(9L);
    }

    @Test
    void gateSeesThePersistedDecision() {
        portero(PROP);
        when(repo.findLatestByVisita(VISIT)).thenReturn(Optional.of(req("RECHAZADA")));

        Map<String, Object> res = service.resultForVisit(VISIT);

        assertEquals("RECHAZADA", res.get("estado"));
        assertEquals(0, res.get("confirmado"));
    }

    @Test
    void gateSeesNoDecisionWhileStillPending() {
        portero(PROP);
        when(repo.findLatestByVisita(VISIT)).thenReturn(Optional.of(req("PENDIENTE")));

        Map<String, Object> res = service.resultForVisit(VISIT);

        assertEquals("PENDIENTE", res.get("estado"));
        assertNull(res.get("confirmado"));
    }

    @Test
    void gateCancelsAPendingRequestOnce() {
        portero(PROP);
        when(repo.findById(9L)).thenReturn(Optional.of(req("PENDIENTE")));
        when(repo.cancel(9L)).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> service.cancel(9L));
    }

    @Test
    void superadminCannotReadIntercomRequests() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(PROP)
            .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.resultForVisit(VISIT));
    }
}
