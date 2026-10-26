package com.saed.backend.reservas;

import com.saed.backend.reservas.service.impl.SanctionsCommonAreaRestrictionChecker;
import com.saed.backend.sanciones.service.SancionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** F10-07 integration: the reservation restriction seam delegates to the sanctions module. */
class SanctionsRestrictionCheckerTest {

    @Test
    void delegatesToTheSanctionsModule() {
        SancionService sanciones = mock(SancionService.class);
        LocalDate day = LocalDate.of(2026, 10, 12);
        when(sanciones.hasActiveCommonAreaRestriction(701L, day)).thenReturn(true);
        SanctionsCommonAreaRestrictionChecker checker = new SanctionsCommonAreaRestrictionChecker(sanciones);
        assertTrue(checker.hasActiveCommonAreaRestriction(701L, day));
        assertFalse(checker.hasActiveCommonAreaRestriction(702L, day));
    }
}
