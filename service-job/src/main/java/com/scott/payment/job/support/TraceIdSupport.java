package com.scott.payment.job.support;

import com.scott.payment.component.core.trace.TraceContext;

/**
 * 调度中心 TraceId 线程上下文支持类
 */
public final class TraceIdSupport {

    private TraceIdSupport() {
    }

    /**
     * 生成新的 traceId。
     *
     * @return traceId
     */
    public static String newTraceId() {
        return TraceContext.newTraceId();
    }

    /**
     * 绑定 traceId 到当前线程。
     *
     * @param traceId traceId
     */
    public static void bindTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            TraceContext.setTraceId(traceId);
        }
    }

    /**
     * 清理当前线程 traceId。
     */
    public static void clear() {
        TraceContext.clear();
    }
}
