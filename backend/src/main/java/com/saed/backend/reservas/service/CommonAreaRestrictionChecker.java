package com.saed.backend.reservas.service;

import java.time.LocalDate;

/**
 * F10-07 seam: whether a unit has an active sanction that restricts the use of common areas on a date.
 * Implemented by {@link com.saed.backend.reservas.service.impl.SanctionsCommonAreaRestrictionChecker},
 * which delegates to {@code SancionService.hasActiveCommonAreaRestriction}.
 */
public interface CommonAreaRestrictionChecker {

    boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date);
}
