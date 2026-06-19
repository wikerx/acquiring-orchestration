package com.scott.payment.job.support;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdSupport
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心 TraceId 线程上下文支持类
 * @status : create
 */

public final class TraceIdSupport {

    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdSupport() {
    }

    /**
     * 生成新的 traceId。
     *
     * @return traceId
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 绑定 traceId 到当前线程。
     *
     * @param traceId traceId
     */
    public static void bindTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清理当前线程 traceId。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
