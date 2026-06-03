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

    /**
     * 链路追踪请求头名称，网关、服务和日志 MDC 可使用该字段串联一次请求的全链路日志。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 当前线程的 traceId 存储，适用于 Servlet 同步请求链路，线程复用前必须调用 clear 清理。
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 写入当前线程的链路追踪号。
     *
     * @param traceId 链路追踪号
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 获取当前线程的链路追踪号。
     *
     * @return 链路追踪号，未设置时返回 null
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 清理当前线程链路追踪号，避免线程池复用时串号。
     */
    public static void clear() {
        TRACE_ID.remove();
    }
}
