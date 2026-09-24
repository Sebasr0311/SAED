package com.saed.backend.common.exception;

import com.saed.backend.audit.CorrelationIdHolder;
import com.saed.backend.common.dto.FieldErrorDetail;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.*;

/**
 * GlobalExceptionHandler — Punto canónico central para errores REST, seguridad y auditoría en SAED 2.0.
 *
 * Satisface de forma estricta el API Response & Error Contract v1:
 * - Determinismo: cada excepción produce status HTTP, functional code y estructura uniforme.
 * - Seguridad: nunca expone SQL, stack traces, ORA errors, nombres de tablas ni credenciales al cliente.
 * - Trazabilidad: propaga traceId (desde CorrelationIdHolder o UUID) y timestamp ISO-8601 UTC en cada payload.
 * - Frontend-friendly: fieldErrors estructurados para formularios y compatibilidad total con err.code y err.errors.
 * - Auditoría activa: registra en AUDITORIA_LOG (vía SP_REGISTRAR_AUDITORIA) intentos denegados y violaciones de seguridad.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GlobalExceptionHandler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Helper canónico para instanciar la estructura transversal de error v1.
     */
    private Map<String, Object> createErrorResponse(HttpStatus status, String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", status.value());
        response.put("code", code);
        response.put("message", message);

        String traceId = CorrelationIdHolder.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.put("traceId", traceId);
        response.put("timestamp", Instant.now().toString());
        return response;
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

    // =========================================================================
    // 1. CAPA DE PERSISTENCIA Y ORACLE DATABASE
    // =========================================================================

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseException(DataAccessException ex) {
        String message = ex.getMessage();

        // ORA-28115 RLS Violation (IDOR entre tenants)
        if (message != null && message.contains("ORA-28115")) {
            registrarAccesoDenegado("Violacion RLS: intento de acceso a recurso de otro tenant", "RLS");
            Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED_RLS",
                    "Operación bloqueada por la política de seguridad RLS. Acceso al tenant denegado.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // ORA-28112 RLS Policy Function Failure Diagnostic
        if (message != null && message.contains("ORA-28112")) {
            log.error("Fallo de función de política RLS detectado: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
                    "Ha ocurrido un error en la capa de datos: " + message);
            try {
                var errors = jdbcTemplate.getJdbcOperations().queryForList(
                    "SELECT NAME, TYPE, LINE, POSITION, TEXT FROM USER_ERRORS WHERE NAME IN ('PKG_SAED_SECURITY_RLS', 'PKG_SAED_SESSION')"
                );
                response.put("plsqlErrors", errors);
                var objects = jdbcTemplate.getJdbcOperations().queryForList(
                    "SELECT OBJECT_NAME, OBJECT_TYPE, STATUS FROM USER_OBJECTS WHERE OBJECT_NAME IN ('PKG_SAED_SECURITY_RLS', 'PKG_SAED_SESSION')"
                );
                response.put("objectStatus", objects);
                try {
                    jdbcTemplate.getJdbcOperations().execute("ALTER PACKAGE PKG_SAED_SECURITY_RLS COMPILE BODY");
                    response.put("recompileRls", "SUCCESS");
                } catch (Exception exComp) {
                    response.put("recompileRlsError", exComp.getMessage());
                }
            } catch (Exception exDiag) {
                response.put("diagError", exDiag.getMessage());
            }
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Custom PKG_SAED_SESSION application errors (-20083, -20084, -20099)
        if (message != null && message.contains("ORA-2008")) {
            registrarAccesoDenegado("Contexto spoofing detectado (PKG_SAED_SESSION)", "SEGURIDAD");
            Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "CONTEXT_SPOOFING_DETECTED",
                    "Operación rechazada por contexto de seguridad inválido.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && message.contains("ORA-20099")) {
            Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "AUDIT_IMMUTABILITY_VIOLATION",
                    "No está permitido alterar o eliminar registros de auditoría.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        if (message != null && message.contains("ORA-20060")) {
            Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "REPORT_HISTORY_IMMUTABILITY_VIOLATION",
                    "HISTORIAL_REPORTES es inmutable. No se permite modificar ni eliminar registros de auditoría.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // ORA-00001 Conflicto de unicidad
        if (message != null && (message.contains("ORA-00001") || message.contains("UIX_ASIGNACION_UNICA"))) {
            log.warn("Conflicto de base de datos detectado: {}", message);
            String code = "CONFLICT";
            String conflictMsg = "El registro ya existe o la asignación está duplicada.";

            if (message.contains("UQ_PERSONAS_DOCUMENTO")) {
                conflictMsg = "El número de documento ya se encuentra registrado en el sistema.";
            } else if (message.contains("UQ_ORGANIZACIONES_NIT")) {
                conflictMsg = "El NIT o identificación fiscal ya se encuentra registrado para otra organización.";
            } else if (message.contains("UQ_ORGANIZACIONES_EMAIL")) {
                conflictMsg = "El correo electrónico de contacto ya se encuentra registrado para otra organización.";
            } else if (message.contains("UQ_USUARIOS_EMAIL")) {
                conflictMsg = "El correo electrónico ya se encuentra registrado para otro usuario.";
            } else if (message.contains("UQ_USUARIOS_NOMBRE") || message.contains("UQ_USR_USERNAME")) {
                conflictMsg = "El nombre de usuario ya se encuentra en uso.";
            } else if (message.contains("UQ_USUARIOS_PERSONA") || message.contains("UQ_USR_RESIDENTE")) {
                conflictMsg = "La persona ya cuenta con una cuenta de usuario vinculada en el sistema.";
            } else if (message.contains("UIX_ASIGNACION_UNICA") || message.contains("UQ_ASIGNACIONES_ROLES")) {
                conflictMsg = "La asignación de rol ya existe para este usuario en la organización o propiedad.";
            } else if (message.contains("UQ_VEHICULOS_PLACA") || message.contains("UQ_VEH_PLACA_VISITA")) {
                conflictMsg = "La placa del vehículo ya se encuentra registrada.";
            } else if (message.contains("UQ_UNIDADES_NUMERO") || message.contains("UQ_APT_NUMERO")) {
                conflictMsg = "El número o nomenclatura de la unidad ya existe en esta propiedad.";
            } else if (message.contains("UQ_CUOTA_UNICA") || message.contains("UQ_CUOTA_PERIODO")) {
                conflictMsg = "Ya existe una cuota generada para esta unidad y periodo.";
            } else if (message.contains("UQ_QR_CODIGO") || message.contains("UQ_QR_VISITA")) {
                conflictMsg = "El código QR ya se encuentra asignado.";
            } else if (message.contains("UIX_ZONA_NOMBRE")) {
                code = "ZONA_NOMBRE_DUPLICADO";
                conflictMsg = "Ya existe una zona común con ese nombre en esta propiedad.";
            } else if (message.contains("UQ_PROVEEDOR_NIT")) {
                code = "PROVEEDOR_NIT_DUPLICADO";
                conflictMsg = "Ya existe un proveedor registrado con ese NIT o documento en esta organización.";
            } else if (message.contains("UIX_ACTIVO_CODIGO") || message.contains("UQ_ACTIVO_CODIGO_PROP")) {
                code = "CODIGO_ACTIVO_DUPLICADO";
                conflictMsg = "Ya existe un activo registrado con ese código en esta copropiedad.";
            }

            Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, code, conflictMsg);
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        // ORA-02292 Foreign key / Integrity constraint violated (Child record found)
        if (message != null && message.contains("ORA-02292")) {
            log.warn("Violación de clave foránea / registro dependiente detectado: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "FOREIGN_KEY_VIOLATION",
                    "No se puede eliminar o modificar el recurso porque tiene registros dependientes asociados.");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        // ORA-02291 Parent key not found
        if (message != null && message.contains("ORA-02291")) {
            log.warn("Violación de clave foránea (padre inexistente): {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "FOREIGN_KEY_NOT_FOUND",
                    "El registro hace referencia a una entidad inexistente.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-01400 Cannot insert NULL into mandatory column
        if (message != null && message.contains("ORA-01400")) {
            log.warn("Intento de insertar valor nulo en columna requerida: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "NULL_VALUE_NOT_ALLOWED",
                    "Un campo obligatorio no fue proporcionado en la solicitud.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-01839 / ORA-01843 / ORA-01847 / ORA-01861 Date format or invalid date value
        if (message != null && (message.contains("ORA-01839") || message.contains("ORA-01843") || message.contains("ORA-01847") || message.contains("ORA-01861"))) {
            log.warn("Error de fecha detectado en base de datos: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT",
                    "Formato o valor de fecha no válido en la solicitud.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-01722 Invalid number
        if (message != null && message.contains("ORA-01722")) {
            log.warn("Error de conversión numérica detectado en base de datos: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_NUMBER",
                    "Se envió un valor no numérico en un campo que requiere número.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-12899 Value too large for column
        if (message != null && message.contains("ORA-12899")) {
            log.warn("Desborde de longitud de columna detectado: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "FIELD_TOO_LONG",
                    "Uno de los textos enviados supera la longitud máxima permitida.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // ORA-02290 Check constraint violated
        if (message != null && message.contains("ORA-02290")) {
            log.warn("Violación de restricción de validación detectada: {}", message);
            Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION",
                    "Los valores enviados violan una restricción de validación.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // Generic fallback for DB
        String specificError = (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        log.error("DB Error: " + message + " | Cause: " + specificError, ex);
        Map<String, Object> response = createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
                "Ha ocurrido un error en la capa de datos: " + specificError);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionException(CannotCreateTransactionException ex) {
        String message = ex.getMessage();
        if (ex.getCause() != null) {
            message += " " + ex.getCause().getMessage();
        }

        if (message != null && message.contains("ORA-2008")) {
            registrarAccesoDenegado("Contexto spoofing en transaccion", "SEGURIDAD");
            Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "CONTEXT_SPOOFING_DETECTED",
                    "Operación rechazada por contexto de seguridad inválido.");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        log.error("Error al iniciar transacción en la base de datos: {}", message, ex);
        Map<String, Object> response = createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Error al iniciar transacción en la base de datos.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =========================================================================
    // 2. VALIDACIÓN DE ENTRADA Y PARSING HTTP
    // =========================================================================

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Error de validación en los campos enviados");
        Map<String, String> errors = new LinkedHashMap<>();
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
            fieldErrors.add(new FieldErrorDetail(
                error.getField(),
                error.getCode() != null ? error.getCode() : "INVALID_VALUE",
                error.getDefaultMessage()
            ));
        });

        response.put("errors", errors);
        response.put("fieldErrors", fieldErrors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Error de validación en los parámetros de la solicitud");
        Map<String, String> errors = new LinkedHashMap<>();
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();

        ex.getConstraintViolations().forEach(violation -> {
            String property = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "param";
            errors.put(property, violation.getMessage());
            fieldErrors.add(new FieldErrorDetail(
                property,
                violation.getConstraintDescriptor() != null && violation.getConstraintDescriptor().getAnnotation() != null
                    ? violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                    : "INVALID_VALUE",
                violation.getMessage()
            ));
        });

        response.put("errors", errors);
        response.put("fieldErrors", fieldErrors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(jakarta.validation.ValidationException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String param = ex.getName();
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "número";
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "El parámetro '" + param + "' debe ser de tipo " + expectedType + ".");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "El cuerpo de la petición contiene formato JSON inválido o tipos de datos incompatibles.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ClassCastException.class)
    public ResponseEntity<Map<String, Object>> handleClassCastException(ClassCastException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_DATA_TYPE",
                "Uno o más campos contienen un tipo de dato incompatible con el esperado.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // 3. SEGURIDAD, AUTENTICACIÓN, AUTORIZACIÓN Y MULTI-TENANT
    // =========================================================================

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Credenciales inválidas o token de acceso no proporcionado.");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(com.saed.backend.identity.exception.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(com.saed.backend.identity.exception.InvalidCredentialsException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        registrarAccesoDenegado("AccessDeniedException: " + ex.getMessage(), "AUTORIZACION");
        Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(com.saed.backend.authorization.exception.InactivePropertyException.class)
    public ResponseEntity<Map<String, Object>> handleInactiveProperty(com.saed.backend.authorization.exception.InactivePropertyException ex) {
        registrarAccesoDenegado("Operación rechazada por copropiedad inactiva: " + ex.getMessage(), "PROPIEDAD_INACTIVA");
        Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "PROPERTY_INACTIVE", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(com.saed.backend.platform.exception.InactiveMembershipException.class)
    public ResponseEntity<Map<String, Object>> handleInactiveMembership(com.saed.backend.platform.exception.InactiveMembershipException ex) {
        registrarAccesoDenegado("Operación rechazada por membresía inactiva o vencida: " + ex.getMessage(), "MEMBRESIA_INACTIVA");
        Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "MEMBERSHIP_INACTIVE", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(com.saed.backend.platform.exception.ModuleNotEntitledException.class)
    public ResponseEntity<Map<String, Object>> handleModuleNotEntitled(com.saed.backend.platform.exception.ModuleNotEntitledException ex) {
        registrarAccesoDenegado("Módulo no contratado o inhabilitado: " + ex.getModuleCode(), "ENTITLEMENT");
        Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "MODULE_NOT_ENTITLED", ex.getMessage());
        response.put("moduleCode", ex.getModuleCode());
        response.put("errorType", "PLAN_MODULE_RESTRICTION");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("Violación de seguridad / bloqueo rate-limit detectado: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "SECURITY_BLOCKED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    // =========================================================================
    // 4. LÍMITES DE PLAN Y CUOTAS DE PLATAFORMA
    // =========================================================================

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimitExceeded(PlanLimitExceededException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "PLAN_LIMIT_EXCEEDED", ex.getMessage());
        response.put("limitType", ex.getLimitType());
        response.put("currentCount", ex.getCurrentCount());
        response.put("maxLimit", ex.getMaxLimit());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.person.exception.ConvivienteLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleConvivienteLimitExceeded(com.saed.backend.person.exception.ConvivienteLimitExceededException ex) {
        log.warn("Límite de convivientes superado para unidad {}: límite={}, actuales={}", ex.getUnitId(), ex.getLimit(), ex.getCurrentCount());
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "CONVIVIENTE_LIMIT_EXCEEDED", ex.getMessage());
        response.put("unitId", ex.getUnitId());
        response.put("limit", ex.getLimit());
        response.put("currentCount", ex.getCurrentCount());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleStorageQuotaExceeded(StorageQuotaExceededException ex) {
        log.warn("Cuota de almacenamiento excedida: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "STORAGE_QUOTA_EXCEEDED", ex.getMessage());
        response.put("limitBytes", ex.getLimitBytes());
        response.put("usedBytes", ex.getUsedBytes());
        response.put("requestedBytes", ex.getRequestedBytes());
        response.put("availableBytes", ex.getAvailableBytes());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // =========================================================================
    // 5. REGLAS DE NEGOCIO (422 UNPROCESSABLE ENTITY)
    // =========================================================================

    @ExceptionHandler(com.saed.backend.dashboard.export.ExportVolumeLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleExportVolumeLimitExceeded(com.saed.backend.dashboard.export.ExportVolumeLimitExceededException ex) {
        log.warn("Límite de volumen de exportación excedido: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "EXCEEDS_EXPORT_LIMIT", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.UnidadEnMoraException.class)
    public ResponseEntity<Map<String, Object>> handleUnidadEnMoraException(com.saed.backend.reservas.exception.UnidadEnMoraException ex) {
        log.warn("Rechazo de operación por mora financiera: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "UNIDAD_EN_MORA",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.TrabajadorInactivoException.class)
    public ResponseEntity<Map<String, Object>> handleTrabajadorInactivo(com.saed.backend.trabajadores.exception.TrabajadorInactivoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "TRABAJADOR_INACTIVO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.ArlVencidaException.class)
    public ResponseEntity<Map<String, Object>> handleArlVencida(com.saed.backend.trabajadores.exception.ArlVencidaException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "ARL_VENCIDA", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.TrabajadorSinArlException.class)
    public ResponseEntity<Map<String, Object>> handleTrabajadorSinArl(com.saed.backend.trabajadores.exception.TrabajadorSinArlException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "TRABAJADOR_SIN_ARL", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.ProveedorInactivoException.class)
    public ResponseEntity<Map<String, Object>> handleProveedorInactivo(com.saed.backend.trabajadores.exception.ProveedorInactivoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "PROVEEDOR_INACTIVO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.TrabajadorNoAutorizadoException.class)
    public ResponseEntity<Map<String, Object>> handleTrabajadorNoAutorizado(com.saed.backend.trabajadores.exception.TrabajadorNoAutorizadoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "TRABAJADOR_NO_AUTORIZADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(com.saed.backend.sanciones.exception.ConceptoMultaNoConfiguradoException.class)
    public ResponseEntity<Map<String, Object>> handleConceptoMultaNoConfigurado(com.saed.backend.sanciones.exception.ConceptoMultaNoConfiguradoException ex) {
        log.warn("Concepto de multa no configurado: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "CONCEPTO_MULTA_NO_CONFIGURADO", ex.getMessage());
        response.put("codigo", "CONCEPTO_MULTA_NO_CONFIGURADO");
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // =========================================================================
    // 6. FINITE STATE MACHINES (FSM) Y TRANSICIONES DE ESTADO (400 BAD REQUEST)
    // =========================================================================

    @ExceptionHandler(com.saed.backend.pqrs.exception.PqrsInvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handlePqrsInvalidState(com.saed.backend.pqrs.exception.PqrsInvalidStateException ex) {
        log.warn("Estado de ticket PQRS no reconocido: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ESTADO_PQRS_INVALIDO", ex.getMessage());
        response.put("codigo", "ESTADO_PQRS_INVALIDO");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.pqrs.exception.PqrsInvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handlePqrsInvalidTransition(com.saed.backend.pqrs.exception.PqrsInvalidTransitionException ex) {
        log.warn("Transición inválida de estado en ticket PQRS: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TRANSICION_ESTADO_INVALIDA", ex.getMessage());
        response.put("codigo", "TRANSICION_ESTADO_INVALIDA");
        response.put("estadoOrigen", ex.getEstadoOrigen());
        response.put("estadoDestino", ex.getEstadoDestino());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.incidentes.exception.IncidenteInvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleIncidenteInvalidState(com.saed.backend.incidentes.exception.IncidenteInvalidStateException ex) {
        log.warn("Estado de incidente no válido: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ESTADO_INCIDENTE_INVALIDO", ex.getMessage());
        response.put("codigo", "ESTADO_INCIDENTE_INVALIDO");
        response.put("estado", ex.getEstado());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.incidentes.exception.IncidenteInvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIncidenteInvalidTransition(com.saed.backend.incidentes.exception.IncidenteInvalidTransitionException ex) {
        log.warn("Transición inválida de estado en incidente: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TRANSICION_ESTADO_INVALIDA", ex.getMessage());
        response.put("codigo", "TRANSICION_ESTADO_INVALIDA");
        response.put("estadoOrigen", ex.getEstadoOrigen());
        response.put("estadoDestino", ex.getEstadoDestino());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.ReservaEstadoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleReservaEstadoInvalido(com.saed.backend.reservas.exception.ReservaEstadoInvalidoException ex) {
        log.warn("Estado de reserva inválido: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ESTADO_RESERVA_INVALIDO",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        response.put("codigo", "ESTADO_RESERVA_INVALIDO");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.ReservaTransicionInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleReservaTransicionInvalida(com.saed.backend.reservas.exception.ReservaTransicionInvalidaException ex) {
        log.warn("Transición inválida de estado en reserva: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TRANSICION_ESTADO_INVALIDA",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        response.put("codigo", "TRANSICION_ESTADO_INVALIDA");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.activos.exception.TransicionEstadoActivoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleTransicionEstadoActivoInvalida(com.saed.backend.activos.exception.TransicionEstadoActivoInvalidaException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TRANSICION_ESTADO_INVALIDA", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.activos.exception.ActivoEstadoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleActivoEstadoInvalido(com.saed.backend.activos.exception.ActivoEstadoInvalidoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ESTADO_ACTIVO_INVALIDO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.TransicionMantenimientoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleTransicionMantenimientoInvalida(com.saed.backend.mantenimiento.exception.TransicionMantenimientoInvalidaException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "TRANSICION_MANTENIMIENTO_INVALIDA", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.MantenimientoEstadoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleMantenimientoEstadoInvalido(com.saed.backend.mantenimiento.exception.MantenimientoEstadoInvalidoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ESTADO_MANTENIMIENTO_INVALIDO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // 7. CONFLICTOS DE RECURSOS (409 CONFLICT)
    // =========================================================================

    @ExceptionHandler(com.saed.backend.reservas.exception.ReservaSolapadaException.class)
    public ResponseEntity<Map<String, Object>> handleReservaSolapada(com.saed.backend.reservas.exception.ReservaSolapadaException ex) {
        log.warn("Conflicto de reserva por solapamiento de horario: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "RESERVA_SOLAPADA",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.ZonaNombreDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleZonaNombreDuplicado(com.saed.backend.reservas.exception.ZonaNombreDuplicadoException ex) {
        log.warn("Conflicto de nombre de zona duplicado: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "ZONA_NOMBRE_DUPLICADO",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.proveedores.exception.ProveedorNITDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleProveedorNITDuplicado(com.saed.backend.proveedores.exception.ProveedorNITDuplicadoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "PROVEEDOR_NIT_DUPLICADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.activos.exception.CodigoActivoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleCodigoActivoDuplicado(com.saed.backend.activos.exception.CodigoActivoDuplicadoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "CODIGO_ACTIVO_DUPLICADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.MantenimientoConflictoActivoException.class)
    public ResponseEntity<Map<String, Object>> handleMantenimientoConflictoActivo(com.saed.backend.mantenimiento.exception.MantenimientoConflictoActivoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "CONFLICTO_ACTIVO_MANTENIMIENTO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.MantenimientoConflictoBloqueoException.class)
    public ResponseEntity<Map<String, Object>> handleMantenimientoConflictoBloqueo(com.saed.backend.mantenimiento.exception.MantenimientoConflictoBloqueoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "CONFLICTO_BLOQUEO_ZONA", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.TrabajadorObraDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleTrabajadorObraDuplicado(com.saed.backend.trabajadores.exception.TrabajadorObraDuplicadoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "TRABAJADOR_OBRA_DUPLICADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.DocumentoTrabajadorDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentoTrabajadorDuplicado(com.saed.backend.trabajadores.exception.DocumentoTrabajadorDuplicadoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "DOCUMENTO_DUPLICADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Estado inválido en operación: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.saed.backend.porteria.exception.QrAccessException.class)
    public ResponseEntity<Map<String, Object>> handleQrAccessException(com.saed.backend.porteria.exception.QrAccessException ex) {
        log.warn("Rechazo de validación QR: {}", ex.getMessage());
        HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.CONFLICT;
        String code = ex.getCode() != null ? ex.getCode() : "CONFLICT";
        Map<String, Object> response = createErrorResponse(status, code, ex.getMessage());
        return new ResponseEntity<>(response, status);
    }

    // =========================================================================
    // 8. ERRORES DE RECURSO NO ENCONTRADO (404 NOT FOUND)
    // =========================================================================

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(java.util.NoSuchElementException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no fue encontrado.");
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(org.springframework.web.servlet.NoHandlerFoundException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no fue encontrado.");
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.saed.backend.proveedores.exception.ProveedorNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleProveedorNoEncontrado(com.saed.backend.proveedores.exception.ProveedorNoEncontradoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "PROVEEDOR_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.saed.backend.activos.exception.ActivoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleActivoNoEncontrado(com.saed.backend.activos.exception.ActivoNoEncontradoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "ACTIVO_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.MantenimientoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleMantenimientoNoEncontrado(com.saed.backend.mantenimiento.exception.MantenimientoNoEncontradoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "MANTENIMIENTO_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.saed.backend.trabajadores.exception.TrabajadorNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleTrabajadorNoEncontrado(com.saed.backend.trabajadores.exception.TrabajadorNoEncontradoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.NOT_FOUND, "TRABAJADOR_NO_ENCONTRADO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // 9. OTROS ERRORES DE CLIENTE (400 BAD REQUEST / 405 METHOD NOT ALLOWED)
    // =========================================================================

    @ExceptionHandler(com.saed.backend.reservas.exception.ZonaNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleZonaNoDisponible(com.saed.backend.reservas.exception.ZonaNoDisponibleException ex) {
        log.warn("Zona no disponible para reserva: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ZONA_NO_DISPONIBLE",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.AforoExcedidoException.class)
    public ResponseEntity<Map<String, Object>> handleAforoExcedido(com.saed.backend.reservas.exception.AforoExcedidoException ex) {
        log.warn("Aforo máximo de zona común excedido: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "AFORO_EXCEDIDO",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        response.put("codigo", "AFORO_EXCEDIDO");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.reservas.exception.AsistentesInvalidosException.class)
    public ResponseEntity<Map<String, Object>> handleAsistentesInvalidos(com.saed.backend.reservas.exception.AsistentesInvalidosException ex) {
        log.warn("Cantidad de asistentes de reserva inválida: {}", ex.getReason());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "ASISTENTES_INVALIDOS",
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        response.put("codigo", "ASISTENTES_INVALIDOS");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.mantenimiento.exception.ProveedorNoValidoException.class)
    public ResponseEntity<Map<String, Object>> handleProveedorNoValido(com.saed.backend.mantenimiento.exception.ProveedorNoValidoException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "PROVEEDOR_NO_VALIDO", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedOperation(UnsupportedOperationException ex) {
        log.warn("Operación no permitida o deshabilitada: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "OPERATION_NOT_ALLOWED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        Map<String, Object> response = createErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status.name();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        Map<String, Object> response = createErrorResponse(status, code, message);
        return new ResponseEntity<>(response, ex.getStatusCode());
    }

    @ExceptionHandler(com.saed.backend.dashboard.registry.InvalidReportKeyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidReportKeyException(com.saed.backend.dashboard.registry.InvalidReportKeyException ex) {
        log.warn("Intento de ejecución de reporte con clave no permitida: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REPORT_KEY", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.saed.backend.dashboard.registry.ReportAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleReportAccessDeniedException(com.saed.backend.dashboard.registry.ReportAccessDeniedException ex) {
        log.warn("Acceso denegado a configuración o historial de reportes: {}", ex.getMessage());
        Map<String, Object> response = createErrorResponse(HttpStatus.FORBIDDEN, "REPORT_ACCESS_DENIED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 10. ERROR INTERNO / FALLBACK GENERAL (500 INTERNAL SERVER ERROR)
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled unexpected exception: {}", ex.getMessage(), ex);
        Map<String, Object> response = createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Error interno del servidor");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}