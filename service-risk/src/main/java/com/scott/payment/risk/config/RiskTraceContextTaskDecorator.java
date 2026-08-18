package com.scott.payment.risk.config;

import com.scott.payment.component.core.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskTraceContextTaskDecorator
 * @date : 2026-08-05 00:00
 * @email : scott_x@163.com
 * @description : 风控只读线程池链路上下文装饰器，传播并在任务结束后恢复 MDC 与 TraceContext
 * @status : create
 */
final class RiskTraceContextTaskDecorator implements TaskDecorator {

    /**
     * 捕获提交线程上下文并在任务线程中临时恢复，结束后还原任务线程原上下文。
     *
     * @param runnable 原始只读风控任务
     * @return 带链路传播和清理边界的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        String capturedTraceId = resolveTraceId(capturedMdc);
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            String previousTraceId = resolveTraceId(previousMdc);
            try {
                restore(capturedMdc, capturedTraceId);
                runnable.run();
            } finally {
                TraceContext.clear();
                restore(previousMdc, previousTraceId);
            }
        };
    }

    /**
     * 恢复一份 MDC 和 TraceContext 快照。
     *
     * @param mdcContext MDC 键值快照
     * @param traceId 链路追踪号
     */
    private void restore(Map<String, String> mdcContext, String traceId) {
        TraceContext.clear();
        if (mdcContext == null || mdcContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdcContext);
        }
        if (traceId != null && !traceId.isBlank()) {
            TraceContext.setTraceId(traceId);
        }
    }

    /**
     * 优先从 TraceContext 读取链路号，缺失时回退到 MDC 快照。
     *
     * @param mdcContext MDC 键值快照
     * @return 当前可传播的链路号
     */
    private String resolveTraceId(Map<String, String> mdcContext) {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return mdcContext == null ? null : mdcContext.get(TraceContext.MDC_TRACE_ID_KEY);
    }
}
