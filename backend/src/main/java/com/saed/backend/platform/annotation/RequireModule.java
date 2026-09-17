package com.saed.backend.platform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce module entitlements on Controller classes or handler methods.
 * If applied at the class level, all handler methods require the specified module.
 * If applied at the method level, it overrides or specifies the required module for that method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireModule {
    /**
     * Unique canonical code of the module (e.g., "ASAMBLEAS", "OBRAS", "POLIZAS", "RESERVAS").
     */
    String value();
}
