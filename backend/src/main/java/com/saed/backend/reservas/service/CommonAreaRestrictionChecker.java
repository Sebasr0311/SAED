package com.saed.backend.reservas.service;

import java.time.LocalDate;

/**
 * F10-07 seam: tells whether a unit has an active sanction that restricts the use of common areas on a
 * date. The sanctions module (F10) is expected to provide {@code hasActiveCommonAreaRestriction}; until
 * then {@link com.saed.backend.reservas.service.impl.JdbcCommonAreaRestrictionChecker} reads SANCIONES
 * directly. Replace the bean to swap the implementation.
 */
public interface CommonAreaRestrictionChecker {

    boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date);
}
