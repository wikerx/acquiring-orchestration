package com.scott.payment.component.web.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceRequestBodyFilterTest
 * @date : 2026-08-20 23:58
 * @email : scott_x@163.com
 * @description : 验证内部请求体过滤器按原始字节计算摘要、向下游完整回放正文并限制内存占用
 * @status : create
 */
class InternalServiceRequestBodyFilterTest {

    /** 验证摘要和下游读取到的正文均来自完全相同的原始字节。 */
    @Test
    void shouldDigestAndReplayExactRequestBody() throws Exception {
        byte[] body = "{\"merchantNo\":\"200046\",\"amount\":\"100.00\"}"
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payment/create");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> downstreamBody = new AtomicReference<>();
        AtomicReference<Object> downstreamDigest = new AtomicReference<>();
        FilterChain chain = (downstreamRequest, downstreamResponse) -> {
            downstreamBody.set(downstreamRequest.getInputStream().readAllBytes());
            downstreamDigest.set(downstreamRequest.getAttribute(
                    InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE));
        };

        new InternalServiceRequestBodyFilter().doFilter(request, response, chain);

        assertThat(downstreamBody.get()).containsExactly(body);
        assertThat(downstreamDigest.get()).isEqualTo(InternalServiceSignature.payloadSha256(body));
    }

    /** 验证非内部接口不预读正文。 */
    @Test
    void shouldNotFilterExternalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rest/payment/v1/create");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> downstreamRequest = new AtomicReference<>();
        FilterChain chain = (receivedRequest, receivedResponse) -> downstreamRequest.set(receivedRequest);

        new InternalServiceRequestBodyFilter().doFilter(request, response, chain);

        assertThat(downstreamRequest.get()).isSameAs(request);
        assertThat(request.getAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE)).isNull();
    }

    /** 验证超出一 MiB 的内部正文会在进入 MVC 前被拒绝。 */
    @Test
    void shouldRejectOversizedInternalRequestBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payment/create");
        request.setContent(new byte[InternalServiceRequestBodyFilter.MAX_INTERNAL_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (receivedRequest, receivedResponse) -> invoked.set(true);

        new InternalServiceRequestBodyFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
    }
}
