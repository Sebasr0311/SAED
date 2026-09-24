package com.saed.backend.pqrs.exception;

public class PqrsInvalidStateException extends RuntimeException {
    public PqrsInvalidStateException(String message) {
        super(message);
    }
}
