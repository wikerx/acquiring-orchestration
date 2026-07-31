package com.scott.payment.job.support;

import com.scott.payment.component.core.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceContextTaskDecorator
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : 调度中心线程池 traceId 传播装饰器，在异步执行前恢复提交线程的 MDC 和 TraceContext，执行完成后清理线程上下文。
 * @status : create
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    /**
     * 捕获任务提交线程的 MDC/traceId，并在执行线程中临时恢复。
     * <p>
     * 任务结束后先清理本次上下文，再恢复执行线程原有上下文，避免线程池复用造成跨任务串号。
     * </p>
     *
     * @param runnable 原始异步任务
     * @return 带上下文传播和清理逻辑的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        String capturedTraceId = traceId(capturedMdc);
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            String previousTraceId = traceId(previousMdc);
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
     * 恢复线程执行上下文。
     *
     * @param mdcContext MDC 键值快照
     * @param traceId    线程级链路号
     */
    private void restore(Map<String, String> mdcContext, String traceId) {
        TraceContext.clear();
        if (mdcContext == null || mdcContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdcContext);
        }
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        TraceContext.setTraceId(traceId);
    }

    /**
     * 从 TraceContext 或 MDC 快照中提取 traceId。
     *
     * @param mdcContext MDC 键值快照
     * @return traceId
     */
    private String traceId(Map<String, String> mdcContext) {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return mdcContext == null ? null : mdcContext.get(TraceContext.MDC_TRACE_ID_KEY);
    }
}
