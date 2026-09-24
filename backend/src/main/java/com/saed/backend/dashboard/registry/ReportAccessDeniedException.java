package com.saed.backend.dashboard.registry;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando una operación sobre reportes configurados o historial viola el aislamiento tenant.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ReportAccessDeniedException extends RuntimeException {

    public ReportAccessDeniedException(String message) {
        super(message);
    }
}
