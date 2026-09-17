package com.saed.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an organization attempts to upload a file that would exceed
 * its contracted cumulative storage quota according to its active membership plan.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class StorageQuotaExceededException extends RuntimeException {

    private final long limitBytes;
    private final long usedBytes;
    private final long requestedBytes;
    private final long availableBytes;

    public StorageQuotaExceededException(long limitBytes, long usedBytes, long requestedBytes) {
        super(String.format("Cuota global de almacenamiento excedida para la organización. Límite: %d bytes, Usado: %d bytes, Solicitado: %d bytes, Disponible: %d bytes.",
                limitBytes, usedBytes, requestedBytes, Math.max(0, limitBytes - usedBytes)));
        this.limitBytes = limitBytes;
        this.usedBytes = usedBytes;
        this.requestedBytes = requestedBytes;
        this.availableBytes = Math.max(0, limitBytes - usedBytes);
    }

    public StorageQuotaExceededException(String message, long limitBytes, long usedBytes, long requestedBytes) {
        super(message);
        this.limitBytes = limitBytes;
        this.usedBytes = usedBytes;
        this.requestedBytes = requestedBytes;
        this.availableBytes = Math.max(0, limitBytes - usedBytes);
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getRequestedBytes() {
        return requestedBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }
}
