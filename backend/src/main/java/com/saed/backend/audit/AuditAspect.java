package com.saed.backend.audit;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

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

        String ipOrigen = resolveClientIp();
        String userAgent = resolveUserAgent();
        String correlationId = CorrelationIdHolder.get();

        String action = auditable.action();
        String resource = auditable.resource();

        String payloadJson = null;
        if (auditable.includePayload() && joinPoint.getArgs() != null && joinPoint.getArgs().length > 0) {
            payloadJson = auditSanitizer.sanitizeToJson(joinPoint.getArgs());
        }

        Long affectedEntityId = extractEntityId(joinPoint.getArgs());

        try {
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
                    action, resource, affectedEntityId,
                    ipOrigen, userAgent,
                    payloadJson, stateNew
            );

            return result;
        } catch (Throwable ex) {
            String sanitizedError = "Error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
            auditService.recordFailure(
                    userId, orgId, propId,
                    action, resource, affectedEntityId,
                    ipOrigen, userAgent,
                    payloadJson, sanitizedError
            );
            throw ex;
        }
    }

    private Long extractEntityId(Object[] args) {
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

    private Long extractEntityIdFromResult(Object result) {
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

    private String resolveClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "127.0.0.1";
        }
        HttpServletRequest request = attributes.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "System/Internal";
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader("User-Agent");
    }
}
