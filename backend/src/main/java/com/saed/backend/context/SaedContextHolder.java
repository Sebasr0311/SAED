package com.saed.backend.context;

public class SaedContextHolder {
    private static final ThreadLocal<SaedContext> contextHolder = new ThreadLocal<>();

    public static void setContext(SaedContext context) {
        contextHolder.set(context);
    }

    public static SaedContext getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}
