package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayIngressRequestBodyFilterTests {

    @Test
    void shouldDigestAndReplayProtectedRequestBody() throws Exception {
        byte[] body = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        GatewayIngressAuthProperties properties = new GatewayIngressAuthProperties();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> downstreamBody = new AtomicReference<>();
        AtomicReference<Object> downstreamDigest = new AtomicReference<>();

        new GatewayIngressRequestBodyFilter(properties).doFilter(request, response, (received, ignored) -> {
            downstreamBody.set(received.getInputStream().readAllBytes());
            downstreamDigest.set(received.getAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE));
        });

        assertThat(downstreamBody.get()).containsExactly(body);
        assertThat(downstreamDigest.get()).isEqualTo(GatewayIngressSignature.payloadSha256(body));
    }

    @Test
    void shouldRejectOversizedProtectedRequestBody() throws Exception {
        GatewayIngressAuthProperties properties = new GatewayIngressAuthProperties();
        properties.setMaxRequestBodyBytes(8);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/channel/v1/callbacks/MPGS");
        request.setContent(new byte[9]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new GatewayIngressRequestBodyFilter(properties).doFilter(
                request, response, (received, ignored) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
    }
}
