package com.scott.payment.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.gateway.support.GatewayOpenApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayOpenApiExceptionHandlerTests
 * @date : 2026-10-26 00:00
 * @email : scott_x@163.com
 * @description : 验证 Gateway 对商户 OpenAPI 和付款人 Checkout API 的上游异常返回受众隔离的英文 F2000，并保留 HTTP 状态与 traceId。
 * @status : create
 */
class GatewayOpenApiExceptionHandlerTests {

    /** 未分类 OpenAPI 异常返回 HTTP 500 + F2000，响应体不包含内部地址或异常说明。 */
    @Test
    void shouldReturnControlledSystemErrorForOpenApiFailure() {
        GatewayOpenApiExceptionHandler handler = handler();
        MockServerWebExchange exchange = openApiExchange();

        handler.handle(exchange, new IllegalStateException("upstream 10.0.0.8 leaked secret-token")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("sdk-trace-123");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"F2000\",\"message\":\"Processing could not be completed. Check the original order or contact support.\",\"data\":null}")
                .doesNotContain("10.0.0.8", "secret-token");
    }

    /** Gateway 已知连接失败保持 503，响应体仍使用统一 F2000 而不是框架错误页。 */
    @Test
    void shouldPreserveKnownGatewayHttpStatus() {
        GatewayOpenApiExceptionHandler handler = handler();
        MockServerWebExchange connectionFailure = openApiExchange();
        handler.handle(connectionFailure, new ConnectException("connection refused 10.0.0.9")).block();
        assertThat(connectionFailure.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        MockServerWebExchange timeout = openApiExchange();
        handler.handle(timeout, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "upstream timeout")).block();
        assertThat(timeout.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    /** 付款人 Checkout API 的 Gateway 异常必须使用付款人英文文案，不能落入框架错误页或商户文案。 */
    @Test
    void shouldReturnPayerEnglishMessageForCheckoutApiFailure() {
        GatewayOpenApiExceptionHandler handler = handler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/checkout/api/v1/payment/submit")
                .header(TraceContext.TRACE_ID_HEADER, "checkout-trace-123")
                .build());

        handler.handle(exchange, new ConnectException("connection refused 10.0.0.9")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"F2000\",\"message\":\"Processing could not be completed. Check the original order or contact the merchant.\",\"data\":null}")
                .doesNotContain("10.0.0.9");
    }

    /** 管理系统等内部命名空间不允许被商户 OpenAPI 五位码污染。 */
    @Test
    void shouldPropagateFailureOutsideOpenApiNamespace() {
        GatewayOpenApiExceptionHandler handler = handler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/system/configs").build());
        IllegalStateException failure = new IllegalStateException("admin failure");

        assertThatThrownBy(() -> handler.handle(exchange, failure).block())
                .isSameAs(failure);
    }

    private static GatewayOpenApiExceptionHandler handler() {
        return new GatewayOpenApiExceptionHandler(
                new GatewayOpenApiErrorWriter(new ObjectMapper()));
    }

    private static MockServerWebExchange openApiExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/rest/payment/v1/payment")
                .header(TraceContext.TRACE_ID_HEADER, "sdk-trace-123")
                .header("X-Request-Id", "sdk-request-456")
                .build());
    }
}
