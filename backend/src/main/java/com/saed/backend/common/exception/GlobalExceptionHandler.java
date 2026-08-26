package com.saed.backend.common.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

        // Generic fallback for DB
        System.err.println("DB Error: " + message);
        response.put("code", "DATABASE_ERROR");
        response.put("message", "Ha ocurrido un error en la capa de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(com.saed.backend.identity.exception.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(com.saed.backend.identity.exception.InvalidCredentialsException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "UNAUTHORIZED");
        response.put("message", ex.getMessage()); // Will output "Credenciales invalidas"
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
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
