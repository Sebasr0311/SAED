package com.saed.backend.authorization.exception;

import java.util.List;

/**
 * Thrown when a hard deletion is requested for a property that still owns financial,
 * legal or audit history. Extends {@link IllegalStateException} so the global handler
 * maps it to 409 CONFLICT. Deactivation (estado INACTIVA) is the supported path.
 */
public class PropertyHasHistoryException extends IllegalStateException {

    private final transient List<String> historyTables;

    public PropertyHasHistoryException(List<String> historyTables) {
        super("La propiedad no puede eliminarse definitivamente porque conserva historial financiero, "
                + "legal o de auditoría (" + String.join(", ", historyTables) + "). "
                + "Desactívela cambiando su estado a INACTIVA (PATCH /api/v1/properties/{id}/status).");
        this.historyTables = List.copyOf(historyTables);
    }

    public List<String> getHistoryTables() {
        return historyTables;
    }
}
