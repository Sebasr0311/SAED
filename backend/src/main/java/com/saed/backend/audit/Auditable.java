package com.saed.backend.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare that a service or domain mutation method must be intercepted and audited
 * in the immutable AUDITORIA_LOG table.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Action performed (e.g., 'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'APPROVE', 'REJECT', 'EXECUTE').
     */
    String action();

    /**
     * Target resource or entity affected (e.g., 'USUARIO', 'PROPIEDAD', 'UNIDAD', 'PAGO', 'SANCION').
     */
    String resource();

    /**
     * Category of the audit event.
     */
    AuditCategory category() default AuditCategory.OPERATIONAL;

    /**
     * Severity of the event.
     */
    AuditSeverity severity() default AuditSeverity.INFO;

    /**
     * Whether to capture and sanitize the request arguments / payload.
     */
    boolean includePayload() default true;
}
