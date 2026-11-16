package com.scott.payment.data.config.export;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 导出任务线程上下文装饰器，传播链路信息并显式绑定主库数据源。
 */
final class DataExportTaskContextDecorator implements TaskDecorator {

    /**
     * 捕获提交线程的日志上下文，并在专用线程中恢复主库和链路边界。
     *
     * @param runnable 原始导出任务
     * @return 带上下文恢复和清理逻辑的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        String capturedTraceId = TraceContext.getTraceId();
        return () -> {
            try {
                restoreTraceContext(capturedMdc, capturedTraceId);
                DynamicDataSourceContextHolder.push(DataSourceName.MASTER);
                runnable.run();
            } finally {
                DynamicDataSourceContextHolder.poll();
                TraceContext.clear();
                MDC.clear();
            }
        };
    }

    /**
     * 恢复任务创建线程的 MDC 和追踪号，缺失时保持干净上下文。
     *
     * @param capturedMdc MDC 键值快照
     * @param capturedTraceId 链路追踪号
     */
    private void restoreTraceContext(Map<String, String> capturedMdc, String capturedTraceId) {
        if (capturedMdc == null || capturedMdc.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(capturedMdc);
        }
        if (capturedTraceId != null && !capturedTraceId.isBlank()) {
            TraceContext.setTraceId(capturedTraceId);
        }
    }
}
