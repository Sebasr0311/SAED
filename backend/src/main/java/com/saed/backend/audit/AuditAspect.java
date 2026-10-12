package com.saed.backend.audit;

import com.saed.backend.common.util.ClientRequestInfo;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Records successful {@link Auditable} operations. It is the innermost advice, inside the transaction advisor (see
 * {@link com.saed.backend.config.TransactionOrderConfig}), so the success record joins the audited transaction and
 * commits atomically with it. Failures are recorded by {@link AuditFailureAspect} once the transaction has ended.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private final AuditService auditService;
    private final AuditSanitizer auditSanitizer;

    public AuditAspect(AuditService auditService, AuditSanitizer auditSanitizer) {
        this.auditService = auditService;
        this.auditSanitizer = auditSanitizer;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        SaedContext context = SaedContextHolder.getContext();
        Long userId = (context != null && context.getUserId() != null) ? context.getUserId() : null;
        Long orgId = (context != null && context.getOrganizationId() != null) ? context.getOrganizationId() : null;
        Long propId = (context != null && context.getPropertyId() != null) ? context.getPropertyId() : null;

        String payloadJson = null;
        if (auditable.includePayload() && joinPoint.getArgs() != null && joinPoint.getArgs().length > 0) {
            payloadJson = auditSanitizer.sanitizeToJson(joinPoint.getArgs());
        }

        Long affectedEntityId = extractEntityId(joinPoint.getArgs());

        Object result = joinPoint.proceed();

        // Try to extract entity ID from result if not already found in args
        if (affectedEntityId == null) {
            affectedEntityId = extractEntityIdFromResult(result);
        }

        String stateNew = null;
        if (result != null) {
            stateNew = auditSanitizer.sanitizeToJson(result);
        }

        auditService.recordSuccess(
                userId, orgId, propId,
                auditable.action(), auditable.resource(), affectedEntityId,
                ClientRequestInfo.ip(), ClientRequestInfo.userAgent(),
                payloadJson, stateNew
        );

        return result;
    }

    static Long extractEntityId(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long l) {
                return l;
            }
            if (arg instanceof Integer i) {
                return i.longValue();
            }
            if (arg instanceof Map<?, ?> map) {
                Object idVal = map.get("id");
                if (idVal == null) idVal = map.get("id_usuario");
                if (idVal == null) idVal = map.get("id_propiedad");
                if (idVal == null) idVal = map.get("id_organizacion");
                if (idVal == null) idVal = map.get("id_unidad");
                if (idVal instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return null;
    }

    private static Long extractEntityIdFromResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Number num) {
            return num.longValue();
        }
        if (result instanceof Map<?, ?> map) {
            Object idVal = map.get("id");
            if (idVal == null) idVal = map.get("id_usuario");
            if (idVal == null) idVal = map.get("id_propiedad");
            if (idVal == null) idVal = map.get("id_organizacion");
            if (idVal == null) idVal = map.get("id_unidad");
            if (idVal instanceof Number n) {
                return n.longValue();
            }
        }
        try {
            Method getIdMethod = result.getClass().getMethod("getId");
            Object idVal = getIdMethod.invoke(result);
            if (idVal instanceof Number n) {
                return n.longValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
