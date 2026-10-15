package com.scott.payment.gateway.filter;

import com.scott.payment.gateway.config.GatewayClientIpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Gateway 只接受可信直接上游提供的转发链，并从右向左剥离可信代理。 */
class GatewayClientIpHeaderFilterTests {

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromUntrustedPeer() {
        GatewayClientIpProperties properties = properties(true, List.of("10.0.0.0/8"));
        ServerWebExchange forwarded = apply(properties, request("203.0.113.9", "198.51.100.7"));

        assertThat(clientIp(forwarded)).isEqualTo("203.0.113.9");
    }

    @Test
    void shouldResolveFirstUntrustedAddressFromRightSideOfTrustedChain() {
        GatewayClientIpProperties properties = properties(true, List.of("10.0.0.0/8"));
        ServerWebExchange forwarded = apply(properties,
                request("10.0.0.2", "192.0.2.66, 198.51.100.7, 10.0.0.3"));

        assertThat(clientIp(forwarded)).isEqualTo("198.51.100.7");
    }

    @Test
    void shouldFallBackToTcpPeerWhenForwardedChainIsMalformed() {
        GatewayClientIpProperties properties = properties(true, List.of("10.0.0.0/8"));
        ServerWebExchange forwarded = apply(properties,
                request("10.0.0.2", "198.51.100.7, not-an-ip"));

        assertThat(clientIp(forwarded)).isEqualTo("10.0.0.2");
    }

    @Test
    void shouldNormalizeIpv6TrustedProxyAndClient() {
        GatewayClientIpProperties properties = properties(true, List.of("2001:db8:1::/48"));
        ServerWebExchange forwarded = apply(properties,
                request("2001:db8:1::2", "2001:db8:2::7, 2001:db8:1::3"));

        assertThat(clientIp(forwarded)).isEqualTo("2001:db8:2:0:0:0:0:7");
    }

    private GatewayClientIpProperties properties(boolean trustForwarded, List<String> cidrs) {
        GatewayClientIpProperties properties = new GatewayClientIpProperties();
        properties.setTrustForwardedHeader(trustForwarded);
        properties.setTrustedProxyCidrs(cidrs);
        return properties;
    }

    private MockServerWebExchange request(String remoteIp, String forwardedFor) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/rest/iso/v1/countries/query")
                .remoteAddress(new InetSocketAddress(remoteIp, 443))
                .header("X-Forwarded-For", forwardedFor)
                .header(GatewayClientIpHeaderFilter.HEADER_GATEWAY_CLIENT_IP, "forged")
                .build());
    }

    private ServerWebExchange apply(GatewayClientIpProperties properties, MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new GatewayClientIpHeaderFilter(properties).filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();
        return forwarded.get();
    }

    private String clientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders()
                .getFirst(GatewayClientIpHeaderFilter.HEADER_GATEWAY_CLIENT_IP);
    }
}
