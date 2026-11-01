package com.scott.payment.openapi.client;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证平台内部 HTTP 异常不会丢失稳定业务码或伪装成渠道结果。
 */
class InternalServiceCallExceptionMapperTests {

    /**
     * 下游非 2xx 响应携带统一业务码时必须保留该码，供 OpenAPI 映射器准确投影。
     */
    @Test
    void shouldPreserveBusinessCodeFromInternalServiceResponse() {
        HttpClientErrorException responseException = HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                "Conflict",
                HttpHeaders.EMPTY,
                "{\"code\":\"F510001\",\"message\":\"idempotency conflict\",\"data\":null}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ApiException mapped = InternalServiceCallExceptionMapper.map(
                responseException, "service-payment call failed", true);

        assertThat(mapped).isNotInstanceOf(OpenApiContextualException.class);
        assertThat(mapped.getCode()).isEqualTo("F510001");
    }

    /**
     * 写请求未取得 HTTP 响应时保留结果不确定事实，但来源仍是平台内部调用。
     */
    @Test
    void shouldMarkTransportFailureAsUncertainForWriteOperation() {
        ApiException mapped = InternalServiceCallExceptionMapper.map(
                new ResourceAccessException("simulated timeout"),
                "service-payment call failed",
                true);

        assertThat(mapped).isInstanceOfSatisfying(OpenApiContextualException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("F2000");
            assertThat(exception.getErrorContext().phase()).isEqualTo(OpenApiResultPhase.PLATFORM);
            assertThat(exception.getErrorContext().origin()).isEqualTo(OpenApiResultOrigin.PLATFORM_SYSTEM);
            assertThat(exception.getErrorContext().requestSent()).isNull();
            assertThat(exception.getErrorContext().outcomeUncertain()).isTrue();
        });
    }

    /**
     * 只读查询超时不得被标记为写入结果不确定。
     */
    @Test
    void shouldKeepReadOperationOutcomeCertain() {
        ApiException mapped = InternalServiceCallExceptionMapper.map(
                new ResourceAccessException("simulated timeout"),
                "service-payment query call failed",
                false);

        assertThat(mapped).isInstanceOfSatisfying(OpenApiContextualException.class,
                exception -> assertThat(exception.getErrorContext().outcomeUncertain()).isFalse());
    }

    /**
     * 无法解析的非 2xx 写响应证明请求已送达，但不能据此推断渠道结果。
     */
    @Test
    void shouldRetainRequestSentEvidenceForMalformedErrorResponse() {
        HttpClientErrorException responseException = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "not-json".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ApiException mapped = InternalServiceCallExceptionMapper.map(
                responseException, "service-payment call failed", true);

        assertThat(mapped).isInstanceOfSatisfying(OpenApiContextualException.class, exception -> {
            assertThat(exception.getErrorContext().requestSent()).isTrue();
            assertThat(exception.getErrorContext().outcomeUncertain()).isTrue();
            assertThat(exception.getErrorContext().origin()).isEqualTo(OpenApiResultOrigin.PLATFORM_SYSTEM);
        });
    }
}
