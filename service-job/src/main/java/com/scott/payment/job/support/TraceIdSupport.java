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

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdSupport() {
    }

    /**
     * 生成新的 traceId。
     *
     * @return traceId
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 绑定 traceId 到当前线程。
     *
     * @param traceId traceId
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param traceId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void bindTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清理当前线程 traceId。
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
