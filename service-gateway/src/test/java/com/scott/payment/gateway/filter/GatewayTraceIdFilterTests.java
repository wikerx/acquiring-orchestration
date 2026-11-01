package com.scott.payment.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.trace.TraceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Gateway trace 透传及安全日志字段，不允许异常原文和堆栈污染对外链路日志。
 */
class GatewayTraceIdFilterTests {

    /**
     * 异常链路必须保留 trace、路径、路由摘要和类型，同时过滤可能包含内网地址或密钥的 message。
     */
    @Test
    void shouldLogOnlyApprovedGatewayFailureFields() {
        GatewayTraceIdFilter filter = new GatewayTraceIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/rest/payment/v1/payment")
                .header(TraceContext.TRACE_ID_HEADER, "sdk-trace-123")
                .header("X-Request-Id", "sdk-request-456")
                .build());
        AtomicReference<String> forwardedTraceId = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("upstream 10.0.0.8 secret-token");

        List<String> messages = captureLogs(() -> assertThatThrownBy(() -> filter.filter(exchange, current -> {
            forwardedTraceId.set(current.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
            return Mono.error(failure);
        }).block()).isSameAs(failure));

        assertThat(forwardedTraceId).hasValue("sdk-trace-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("sdk-trace-123");
        assertThat(messages).anyMatch(message -> message.contains("event: GATEWAY_REQUEST_START")
                && message.contains("traceId: sdk-trace-123")
                && message.contains("requestId: sdk-request-456"));
        assertThat(messages).anyMatch(message -> message.contains("event: GATEWAY_ROUTE_COMPLETE")
                && message.contains("routeSuccess: false")
                && message.contains("errorType: IllegalStateException"));
        assertThat(messages).anyMatch(message -> message.contains("event: GATEWAY_REQUEST_ERROR")
                && message.contains("traceId: sdk-trace-123")
                && message.contains("requestId: sdk-request-456")
                && message.contains("path: /api/rest/payment/v1/payment")
                && message.contains("routeId: unknown")
                && message.contains("targetService: unknown")
                && message.contains("errorType: IllegalStateException"));
        assertThat(messages).noneMatch(message -> message.contains("10.0.0.8")
                || message.contains("secret-token")
                || message.contains("upstream"));
    }

    /**
     * 外部请求号不符合单行短标识约束时只能记录 unknown，不能原样进入 Gateway 日志。
     */
    @Test
    void shouldRejectUntrustedRequestIdFromGatewayLog() {
        GatewayTraceIdFilter filter = new GatewayTraceIdFilter();
        String oversizedRequestId = "x".repeat(80);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/rest/payment/v1/payment")
                .header(TraceContext.TRACE_ID_HEADER, "sdk-trace-123")
                .header("X-Request-Id", oversizedRequestId)
                .build());

        List<String> messages = captureLogs(() -> filter.filter(exchange, ignored -> Mono.empty()).block());

        assertThat(messages).anyMatch(message -> message.contains("event: GATEWAY_REQUEST_END")
                && message.contains("requestId: unknown"));
        assertThat(messages).noneMatch(message -> message.contains(oversizedRequestId));
    }

    private static List<String> captureLogs(Runnable invocation) {
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayTraceIdFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            invocation.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
            TraceContext.clear();
        }
    }
}
