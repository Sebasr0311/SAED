package com.saed.backend.audit;

/**
 * ThreadLocal holder for the Correlation ID associated with the current HTTP request or worker execution.
 * Guarantees context isolation and safe cleanup across pooled threads (HikariCP / Tomcat workers).
 */
public final class CorrelationIdHolder {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String correlationId) {
        CURRENT_CORRELATION_ID.set(correlationId);
    }

    public static String get() {
        return CURRENT_CORRELATION_ID.get();
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
    }
}
