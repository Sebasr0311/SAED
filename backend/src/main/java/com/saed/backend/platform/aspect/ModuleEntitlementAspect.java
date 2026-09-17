package com.saed.backend.platform.aspect;

import com.saed.backend.platform.annotation.RequireModule;
import com.saed.backend.platform.service.ModuleEntitlementService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect that intercepts controller methods annotated with @RequireModule
 * or belonging to classes annotated with @RequireModule.
 * Enforces SaaS plan module entitlements before controller execution.
 */
@Aspect
@Component
@Order(250)
public class ModuleEntitlementAspect {

    private static final Logger log = LoggerFactory.getLogger(ModuleEntitlementAspect.class);

    private final ModuleEntitlementService moduleEntitlementService;

    public ModuleEntitlementAspect(ModuleEntitlementService moduleEntitlementService) {
        this.moduleEntitlementService = moduleEntitlementService;
    }

    @Before("@within(com.saed.backend.platform.annotation.RequireModule) || @annotation(com.saed.backend.platform.annotation.RequireModule)")
    public void enforceModuleEntitlement(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Method-level annotation has precedence
        RequireModule annotation = method.getAnnotation(RequireModule.class);

        // 2. Class-level annotation if not present on method
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequireModule.class);
        }

        if (annotation != null && annotation.value() != null && !annotation.value().isBlank()) {
            String moduleCode = annotation.value().trim();
            log.debug("[ModuleEntitlementAspect] Evaluando acceso a módulo '{}' para {}.{}",
                    moduleCode, joinPoint.getTarget().getClass().getSimpleName(), method.getName());
            moduleEntitlementService.checkModuleAccess(moduleCode);
        }
    }
}
