package com.scott.payment.component.core.trace;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceContext
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 链路追踪上下文工具
 * @status : create
 */
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

