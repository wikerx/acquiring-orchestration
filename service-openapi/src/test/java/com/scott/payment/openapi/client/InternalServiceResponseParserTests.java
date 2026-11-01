package com.scott.payment.openapi.client;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证内部服务响应解析不会丢失请求发送边界和写操作结果确定性。
 */
class InternalServiceResponseParserTests {

    /**
     * 写操作收到损坏响应时必须标记请求已发送且结果不确定。
     */
    @Test
    void shouldMarkMalformedWriteResponseAsUncertain() {
        assertThatThrownBy(() -> InternalServiceResponseParser.parse(
                "not-json",
                new TypeReference<CommonResult<Object>>() {
                },
                "service-payment response is invalid",
                true))
                .isInstanceOfSatisfying(OpenApiContextualException.class, exception -> {
                    assertThat(exception.getErrorContext().phase()).isEqualTo(OpenApiResultPhase.PLATFORM);
                    assertThat(exception.getErrorContext().origin()).isEqualTo(OpenApiResultOrigin.PLATFORM_SYSTEM);
                    assertThat(exception.getErrorContext().requestSent()).isTrue();
                    assertThat(exception.getErrorContext().outcomeUncertain()).isTrue();
                });
    }

    /**
     * 只读调用收到空响应时保留请求已发送事实，但不标记业务写入结果不确定。
     */
    @Test
    void shouldKeepMalformedReadResponseOutcomeCertain() {
        assertThatThrownBy(() -> InternalServiceResponseParser.parse(
                " ",
                new TypeReference<CommonResult<Object>>() {
                },
                "service-payment query response is invalid",
                false))
                .isInstanceOfSatisfying(OpenApiContextualException.class, exception -> {
                    assertThat(exception.getErrorContext().requestSent()).isTrue();
                    assertThat(exception.getErrorContext().outcomeUncertain()).isFalse();
                });
    }

    /**
     * 合法统一响应应保持业务码和 data 原样返回。
     */
    @Test
    void shouldParseValidInternalResponse() {
        CommonResult<String> result = InternalServiceResponseParser.parse(
                "{\"code\":\"A0000\",\"message\":\"Success\",\"data\":\"accepted\"}",
                new TypeReference<CommonResult<String>>() {
                },
                "service-payment response is invalid",
                true);

        assertThat(result.getCode()).isEqualTo("A0000");
        assertThat(result.getData()).isEqualTo("accepted");
    }
}
