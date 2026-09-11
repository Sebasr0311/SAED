package com.saed.backend.authorization.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when an operational mutation (POST, PUT, PATCH, DELETE) is attempted
 * within the context of a property whose status is INACTIVA.
 */
public class InactivePropertyException extends AccessDeniedException {

    public InactivePropertyException(String message) {
        super(message);
    }
}
