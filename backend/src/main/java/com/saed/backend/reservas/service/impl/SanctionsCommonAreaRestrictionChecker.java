package com.saed.backend.reservas.service.impl;

import com.saed.backend.reservas.service.CommonAreaRestrictionChecker;
import com.saed.backend.sanciones.service.SancionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** F10-07: reservations ask the sanctions module whether a unit is under a common-area suspension. */
@Component
public class SanctionsCommonAreaRestrictionChecker implements CommonAreaRestrictionChecker {

    private final SancionService sancionService;

    public SanctionsCommonAreaRestrictionChecker(SancionService sancionService) {
        this.sancionService = sancionService;
    }

    @Override
    public boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date) {
        return sancionService.hasActiveCommonAreaRestriction(unitId, date);
    }
}
