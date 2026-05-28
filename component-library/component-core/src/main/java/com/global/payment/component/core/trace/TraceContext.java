package com.global.payment.component.core.trace;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}

