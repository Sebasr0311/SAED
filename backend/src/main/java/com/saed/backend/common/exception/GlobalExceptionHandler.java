package com.saed.backend.common.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseException(DataAccessException ex) {
        String message = ex.getMessage();
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        // Catch ORA-28115 RLS Violation
        if (message != null && message.contains("ORA-28115")) {
            ex.printStackTrace();
            response.put("code", "ACCESS_DENIED_RLS");
            response.put("message", "Operación bloqueada por la política de seguridad RLS. Acceso al tenant denegado.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        // Custom PKG_SAED_SESSION application errors (-20083, -20084, -20099)
        if (message != null && message.contains("ORA-2008")) {
            response.put("code", "CONTEXT_SPOOFING_DETECTED");
            response.put("message", "Intento de establecer un contexto de seguridad no autorizado.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        if (message != null && message.contains("ORA-20099")) {
            response.put("code", "AUDIT_IMMUTABILITY_VIOLATION");
            response.put("message", "No está permitido alterar o eliminar registros de auditoría.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && (message.contains("ORA-00001") || message.contains("UIX_ASIGNACION_UNICA"))) {
            response.put("code", "CONFLICT");
            response.put("message", "El registro ya existe o la asignación está duplicada.");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        // Generic fallback for DB
        System.err.println("DB Error: " + message);
        response.put("code", "DATABASE_ERROR");
        response.put("message", "Ha ocurrido un error en la capa de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "FORBIDDEN");
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "BAD_REQUEST");
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(java.util.NoSuchElementException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "NOT_FOUND");
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.saed.backend.identity.exception.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(com.saed.backend.identity.exception.InvalidCredentialsException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "UNAUTHORIZED");
        response.put("message", ex.getMessage()); // Will output "Credenciales invalidas"
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }
    
    
    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionException(CannotCreateTransactionException ex) {
        String message = ex.getMessage();
        if (ex.getCause() != null) {
            message += " " + ex.getCause().getMessage();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        if (message != null && message.contains("ORA-2008")) {
            response.put("code", "CONTEXT_SPOOFING_DETECTED");
            response.put("message", "Intento de establecer un contexto de seguridad no autorizado.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        response.put("code", "INTERNAL_SERVER_ERROR");
        response.put("message", "Error al iniciar transaccin en la base de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "INTERNAL_SERVER_ERROR");
        response.put("message", "Error interno del servidor");
        ex.printStackTrace();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
