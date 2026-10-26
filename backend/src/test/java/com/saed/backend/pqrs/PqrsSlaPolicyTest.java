package com.saed.backend.pqrs;

import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.service.PqrsSlaPolicy;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F9-04 / F09-05 / DEC-05: default SLA hours and the server-side at-risk/overdue clock. */
class PqrsSlaPolicyTest {

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneId.of("America/Bogota"));
    private static final ZonedDateTime DUE = START.plusHours(4);

    @Test
    void defaultHoursFollowDec05() {
        assertEquals(1, PqrsSlaPolicy.defaultHours("CRITICA"));
        assertEquals(1, PqrsSlaPolicy.defaultHours("EMERGENCIA"));
        assertEquals(4, PqrsSlaPolicy.defaultHours("ALTA"));
        assertEquals(24, PqrsSlaPolicy.defaultHours("MEDIA"));
        assertEquals(72, PqrsSlaPolicy.defaultHours("BAJA"));
    }

    @Test
    void criticaIsStoredAsTheEmergencyPriority() {
        assertEquals("EMERGENCIA", PqrsSlaPolicy.normalizePriority("critica"));
        assertEquals("ALTA", PqrsSlaPolicy.normalizePriority(" alta "));
    }

    @Test
    void openTicketBelowSeventyFivePercentIsOnTime() {
        PqrsSlaPolicy.SlaStatus s = PqrsSlaPolicy.evaluate(START, DUE, "EN_GESTION", null, START.plusMinutes(179));
        assertEquals(PqrsSlaPolicy.ON_TIME, s.estado());
        assertFalse(s.vencido());
    }

    @Test
    void openTicketAtSeventyFivePercentIsAtRisk() {
        PqrsSlaPolicy.SlaStatus s = PqrsSlaPolicy.evaluate(START, DUE, "RADICADO", null, START.plusHours(3));
        assertEquals(PqrsSlaPolicy.AT_RISK, s.estado());
        assertEquals(75, s.porcentajeConsumido());
        assertFalse(s.vencido());
    }

    @Test
    void openTicketExactlyAtDueTimeIsStillAtRisk() {
        PqrsSlaPolicy.SlaStatus s = PqrsSlaPolicy.evaluate(START, DUE, "RADICADO", null, DUE);
        assertEquals(PqrsSlaPolicy.AT_RISK, s.estado());
        assertFalse(s.vencido());
    }

    @Test
    void openTicketPastDueTimeIsOverdue() {
        PqrsSlaPolicy.SlaStatus s = PqrsSlaPolicy.evaluate(START, DUE, "ASIGNADO", null, DUE.plusMinutes(1));
        assertEquals(PqrsSlaPolicy.OVERDUE, s.estado());
        assertTrue(s.vencido());
    }

    @Test
    void closedTicketIsMeasuredAtItsClosingTime() {
        assertEquals(PqrsSlaPolicy.MET,
                PqrsSlaPolicy.evaluate(START, DUE, "CERRADO", DUE.minusMinutes(5), DUE.plusDays(3)).estado());
        PqrsSlaPolicy.SlaStatus late = PqrsSlaPolicy.evaluate(START, DUE, "CERRADO", DUE.plusHours(1), DUE.plusDays(3));
        assertEquals(PqrsSlaPolicy.BREACHED, late.estado());
        assertFalse(late.vencido(), "a closed ticket is no longer pending, so it is not flagged as overdue");
    }

    @Test
    void ticketDtoExposesTheServerSideSlaStatus() {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setEstado("EN_GESTION");
        dto.setFechaRadicacion(START);
        dto.setFechaLimiteSla(DUE);
        dto.aplicarSla(START.plusHours(5));
        assertEquals(PqrsSlaPolicy.OVERDUE, dto.getSlaEstado());
        assertEquals(125, dto.getSlaPorcentajeConsumido());
        assertTrue(dto.getVencido());
    }
}
