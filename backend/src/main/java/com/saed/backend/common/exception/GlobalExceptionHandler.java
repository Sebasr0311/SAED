package com.saed.backend.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — manejo central de errores + auditoria de seguridad.
 *
 * Registra en AUDITORIA_LOG (via SP_REGISTRAR_AUDITORIA, AUTONOMOUS) los
 * intentos de acceso denegado: violaciones RLS (ORA-28115, IDOR entre
 * tenants), AccessDeniedException y contexto spoofing (ORA-2008x). Esto es
 * lo que un auditor de seguridad real busca: no solo que se bloquea, sino
 * que quede evidencia de QUIEN intento acceder a QUE.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GlobalExceptionHandler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Registra un intento denegado en AUDITORIA_LOG (best-effort, nunca rompe la respuesta). */
    private void registrarAccesoDenegado(String motivo, String entidad) {
        try {
            jdbcTemplate.update(
                "CALL SP_REGISTRAR_AUDITORIA(" +
                "p_id_usuario => :usr, p_id_organizacion => :org, p_id_propiedad => :prop, " +
                "p_accion => 'ACCESO_DENEGADO', p_entidad => :ent, " +
                "p_resultado => 'FALLIDO', p_estado_nuevo => :motivo)",
                new MapSqlParameterSource()
                    .addValue("usr", ctxNumber("ID_USUARIO"))
                    .addValue("org", ctxNumber("ID_ORGANIZACION"))
                    .addValue("prop", ctxNumber("ID_PROPIEDAD"))
                    .addValue("ent", entidad)
                    .addValue("motivo", "{\"motivo\":\"" + motivo.replace("\"", "'") + "\"}")
            );
        } catch (Exception ignored) {
            // La auditoria nunca debe impedir responder el error
        }
    }

    /** Lee un atributo de SAED_CTX desde la BD (la sesion ya lo tiene seteado). */
    private Long ctxNumber(String attr) {
        try {
            String v = jdbcTemplate.queryForObject(
                "SELECT SYS_CONTEXT('SAED_CTX', :attr) FROM DUAL",
                new MapSqlParameterSource("attr", attr),
                String.class
            );
            return v != null && !v.isBlank() && !"null".equals(v) ? Long.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseException(DataAccessException ex) {
        String message = ex.getMessage();
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        // ORA-28115 RLS Violation (IDOR entre tenants)
        if (message != null && message.contains("ORA-28115")) {
            registrarAccesoDenegado("Violacion RLS: intento de acceso a recurso de otro tenant", "RLS");
            response.put("code", "ACCESS_DENIED_RLS");
            response.put("message", "Operaci\u00f3n bloqueada por la pol\u00edtica de seguridad RLS. Acceso al tenant denegado.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // Custom PKG_SAED_SESSION application errors (-20083, -20084, -20099)
        if (message != null && message.contains("ORA-2008")) {
            registrarAccesoDenegado("Contexto spoofing detectado (PKG_SAED_SESSION)", "SEGURIDAD");
            response.put("code", "CONTEXT_SPOOFING_DETECTED");
            response.put("message", "Error de seguridad Oracle: " + message);
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && message.contains("ORA-20099")) {
            response.put("code", "AUDIT_IMMUTABILITY_VIOLATION");
            response.put("message", "No est\u00e1 permitido alterar o eliminar registros de auditor\u00eda.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && (message.contains("ORA-00001") || message.contains("UIX_ASIGNACION_UNICA"))) {
            ex.printStackTrace();
            response.put("code", "CONFLICT");
            response.put("message", "El registro ya existe o la asignaci\u00f3n est\u00e1 duplicada.");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        // Generic fallback for DB
        log.error("DB Error: {}", message);
        response.put("code", "DATABASE_ERROR");
        response.put("message", "Ha ocurrido un error en la capa de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        registrarAccesoDenegado("AccessDeniedException: " + ex.getMessage(), "AUTORIZACION");
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
        response.put("message", ex.getMessage());
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
            registrarAccesoDenegado("Contexto spoofing en transaccion", "SEGURIDAD");
            response.put("code", "CONTEXT_SPOOFING_DETECTED");
            response.put("message", "Error de seguridad Oracle: " + message);
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        response.put("code", "INTERNAL_SERVER_ERROR");
        response.put("message", "Error al iniciar transacci\u00f3n en la base de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "INTERNAL_SERVER_ERROR");
        response.put("message", "Error interno del servidor");
        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}