package com.saed.backend.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation that requires an active and valid membership
 * is attempted for an organization that has no active, valid, or unexpired membership.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InactiveMembershipException extends AccessDeniedException {

    public InactiveMembershipException(String message) {
        super(message);
    }
}
