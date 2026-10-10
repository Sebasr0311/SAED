package com.saed.backend.porteria.intercom;

import java.util.List;
import java.util.Optional;

/** Persistence of intercom requests (table INTERCOM_SOLICITUDES, V5.60). */
public interface IntercomRepository {

    long create(Long propId, Long unitId, Long visitaId, Long solicitadoPor, String nombreVisitante, int ttlMinutes);

    Optional<IntercomRequest> findById(Long id);

    /** The latest request of the visit that is still PENDIENTE and not expired. */
    Optional<IntercomRequest> findOpenByVisita(Long visitaId);

    Optional<IntercomRequest> findLatestByVisita(Long visitaId);

    List<IntercomRequest> findPendingByUnits(List<Long> unitIds);

    /** PENDIENTE and not expired to {@code estado}; returns rows updated (0 on replay or expiry). */
    int decide(Long id, String estado, Long respondidoPor);

    /** PENDIENTE to CANCELADA; returns rows updated. */
    int cancel(Long id);

    /** PENDIENTE past its expiry to EXPIRADA. */
    int expireIfDue(Long id);
}
