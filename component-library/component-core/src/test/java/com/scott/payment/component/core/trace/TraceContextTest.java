package com.scott.payment.component.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceContextTest
 * @date : 未确认
 * @email : scott_x@163.com
 * @description : Trace Context Test 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() {
        String traceId = TraceContext.getOrCreateTraceId();

        assertThat(traceId).hasSize(32);
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(TraceContext.getTraceId()).isEqualTo(traceId);
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isEqualTo(traceId);
    }

    @Test
    void shouldResolveValidExternalTraceId() {
        String traceId = TraceContext.resolveOrCreate(" trace-ABC_123 ");

        assertThat(traceId).isEqualTo("trace-ABC_123");
    }

    @Test
    void shouldReplaceInvalidExternalTraceId() {
        String traceId = TraceContext.resolveOrCreate("bad trace with space");

        assertThat(traceId).hasSize(32);
        assertThat(traceId).matches("[0-9a-f]{32}");
    }

    @Test
    void shouldClearTraceIdFromThreadAndMdc() {
        TraceContext.setTraceId("trace-to-clear");

        TraceContext.clear();

        assertThat(TraceContext.getTraceId()).isNull();
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID_KEY)).isNull();
    }
}
