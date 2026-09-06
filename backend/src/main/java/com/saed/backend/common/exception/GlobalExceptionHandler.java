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
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;

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
            SaedContext ctx = SaedContextHolder.getContext();
            Long usr = (ctx != null) ? ctx.getUserId() : null;
            Long org = (ctx != null) ? ctx.getOrganizationId() : null;
            Long prop = (ctx != null) ? ctx.getPropertyId() : null;

            jdbcTemplate.update(
                "CALL SP_REGISTRAR_AUDITORIA(" +
                "p_id_usuario => :usr, p_id_organizacion => :org, p_id_propiedad => :prop, " +
                "p_accion => 'ACCESO_DENEGADO', p_entidad => :ent, " +
                "p_resultado => 'FALLIDO', p_estado_nuevo => :motivo)",
                new MapSqlParameterSource()
                    .addValue("usr", usr)
                    .addValue("org", org)
                    .addValue("prop", prop)
                    .addValue("ent", entidad)
                    .addValue("motivo", "{\"motivo\":\"" + motivo.replace("\"", "'") + "\"}")
            );
        } catch (Exception ignored) {
            // La auditoria nunca debe impedir responder el error
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
            response.put("message", "Operaci\u00f3n rechazada por contexto de seguridad inv\u00e1lido.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && message.contains("ORA-20099")) {
            response.put("code", "AUDIT_IMMUTABILITY_VIOLATION");
            response.put("message", "No est\u00e1 permitido alterar o eliminar registros de auditor\u00eda.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && (message.contains("ORA-00001") || message.contains("UIX_ASIGNACION_UNICA"))) {
            log.warn("Conflicto de base de datos detectado: {}", message);
            response.put("code", "CONFLICT");
            response.put("message", "El registro ya existe o la asignación está duplicada.");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        // ORA-01839 / ORA-01843 / ORA-01847 Date format or invalid date value
        if (message != null && (message.contains("ORA-01839") || message.contains("ORA-01843") || message.contains("ORA-01847") || message.contains("ORA-01861"))) {
            log.warn("Error de fecha detectado en base de datos: {}", message);
            response.put("code", "INVALID_DATE_FORMAT");
            response.put("message", "Formato o valor de fecha no válido en la solicitud.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-01722 Invalid number
        if (message != null && message.contains("ORA-01722")) {
            log.warn("Error de conversión numérica detectado en base de datos: {}", message);
            response.put("code", "INVALID_NUMBER");
            response.put("message", "Se envió un valor no numérico en un campo que requiere número.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-12899 Value too large for column
        if (message != null && message.contains("ORA-12899")) {
            log.warn("Desborde de longitud de columna detectado: {}", message);
            response.put("code", "FIELD_TOO_LONG");
            response.put("message", "Uno de los textos enviados supera la longitud máxima permitida.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-02290 Check constraint violated (ej: montos positivos)
        if (message != null && message.contains("ORA-02290")) {
            log.warn("Violación de restricción de validación detectada: {}", message);
            response.put("code", "CONSTRAINT_VIOLATION");
            response.put("message", "Los valores enviados violan una restricción de validación.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // Generic fallback for DB
        log.error("DB Error: " + message, ex);
        response.put("code", "DATABASE_ERROR");
        response.put("message", "Ha ocurrido un error en la capa de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "TYPE_MISMATCH");
        String param = ex.getName();
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "número";
        response.put("message", "El parámetro '" + param + "' debe ser de tipo " + expectedType + ".");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "MALFORMED_JSON");
        response.put("message", "El cuerpo de la petición contiene formato JSON inválido o tipos de datos incompatibles.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ClassCastException.class)
    public ResponseEntity<Map<String, Object>> handleClassCastException(ClassCastException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "INVALID_DATA_TYPE");
        response.put("message", "Uno o más campos contienen un tipo de dato incompatible con el esperado.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        response.put("success", false);
        response.put("code", "VALIDATION_FAILED");
        response.put("message", "Error de validaci\u00f3n en los campos enviados");
        response.put("errors", errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimitExceeded(PlanLimitExceededException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "PLAN_LIMIT_EXCEEDED");
        response.put("limitType", ex.getLimitType());
        response.put("currentCount", ex.getCurrentCount());
        response.put("maxLimit", ex.getMaxLimit());
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
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
            response.put("message", "Operaci\u00f3n rechazada por contexto de seguridad inv\u00e1lido.");
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