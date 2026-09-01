package com.saed.backend.audit;

public interface AuditService {

    /**
     * Persists an audit record in an isolated transaction (REQUIRES_NEW) so that
     * audit records survive rollbacks of failed business transactions.
     */
    void recordEvent(AuditEntry entry);

    void recordSuccess(Long idUsuario, Long idOrganizacion, Long idPropiedad,
                       String accion, String entidad, Long idEntidadAfectada,
                       String ipOrigen, String userAgent,
                       String estadoAnterior, String estadoNuevo);

    void recordFailure(Long idUsuario, Long idOrganizacion, Long idPropiedad,
                       String accion, String entidad, Long idEntidadAfectada,
                       String ipOrigen, String userAgent,
                       String estadoAnterior, String errorReason);
}
