package com.scott.payment.job.support;

import com.scott.payment.component.core.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceContextTaskDecoratorTest
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : TraceContextTaskDecorator 自动化测试，验证任务线程执行时恢复 traceId 并在执行结束后还原原线程上下文。
 * @status : create
 */
class TraceContextTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void shouldPropagateTraceIdAndRestorePreviousThreadContext() {
        TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();
        TraceContext.setTraceId("submit-trace-001");
        Runnable decorated = decorator.decorate(() -> {
            assertThat(TraceContext.getTraceId()).isEqualTo("submit-trace-001");
            assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("submit-trace-001");
        });

        TraceContext.setTraceId("worker-old-trace");
        decorated.run();

        assertThat(TraceContext.getTraceId()).isEqualTo("worker-old-trace");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("worker-old-trace");
    }

    @Test
    void shouldClearWorkerContextWhenSubmitterHasNoTraceId() {
        TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();
        Runnable decorated = decorator.decorate(() -> {
            assertThat(TraceContext.getTraceId()).isNull();
            assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isNull();
        });

        TraceContext.setTraceId("worker-old-trace");
        decorated.run();

        assertThat(TraceContext.getTraceId()).isEqualTo("worker-old-trace");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("worker-old-trace");
    }

    @Test
    void shouldClearTraceInsideTaskAfterExceptionAndRestoreOuterContext() {
        TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();
        TraceContext.setTraceId("submit-trace-002");
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            observedTraceId.set(TraceContext.getTraceId());
            throw new IllegalStateException("boom");
        });

        TraceContext.setTraceId("worker-old-trace");
        try {
            decorated.run();
        } catch (IllegalStateException ignored) {
            // 测试异常场景下 finally 是否仍然恢复外层上下文。
        }

        assertThat(observedTraceId.get()).isEqualTo("submit-trace-002");
        assertThat(TraceContext.getTraceId()).isEqualTo("worker-old-trace");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo("worker-old-trace");
    }
}
