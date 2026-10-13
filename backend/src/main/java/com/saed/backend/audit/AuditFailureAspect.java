package com.saed.backend.audit;

import com.saed.backend.common.util.ClientRequestInfo;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Records failed {@link Auditable} operations. It is the outermost advice, so the failure audit (its own
 * REQUIRES_NEW transaction) runs after the business transaction has rolled back and returned its connection: a
 * request never holds one pooled connection while waiting for a second one. An audit that cannot be written is
 * logged and never replaces the business exception.
 */
@Aspect
@Component
@Order(AuditFailureAspect.ORDER)
public class AuditFailureAspect {

    /**
     * Outside the transaction advisor and every other application aspect, but after Spring's
     * ExposeInvocationInterceptor (HIGHEST_PRECEDENCE + 1), which annotation binding requires.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final Logger log = LoggerFactory.getLogger(AuditFailureAspect.class);

    private final AuditService auditService;
    private final AuditSanitizer auditSanitizer;

    public AuditFailureAspect(AuditService auditService, AuditSanitizer auditSanitizer) {
        this.auditService = auditService;
        this.auditSanitizer = auditSanitizer;
    }

    @Around("@annotation(auditable)")
    public Object auditFailure(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            record(joinPoint, auditable, ex);
            throw ex;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Auditable auditable, Throwable ex) {
        try {
            SaedContext context = SaedContextHolder.getContext();
            String payloadJson = auditable.includePayload() && joinPoint.getArgs() != null && joinPoint.getArgs().length > 0
                    ? auditSanitizer.sanitizeToJson(joinPoint.getArgs())
                    : null;
            auditService.recordFailure(
                    context != null ? context.getUserId() : null,
                    context != null ? context.getOrganizationId() : null,
                    context != null ? context.getPropertyId() : null,
                    auditable.action(), auditable.resource(), AuditAspect.extractEntityId(joinPoint.getArgs()),
                    ClientRequestInfo.ip(), ClientRequestInfo.userAgent(),
                    payloadJson, "Error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()
            );
        } catch (RuntimeException auditError) {
            log.error("Failure audit not persisted: action={}, resource={}, error={}",
                    auditable.action(), auditable.resource(), auditError.getClass().getSimpleName());
        }
    }
}
