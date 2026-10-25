package com.scott.payment.openapi.security;

import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantIpWhitelistAccessServiceTests {

    private static final String MERCHANT_ID = "200045";

    @Test
    void shouldRejectWhenMerchantHasNoEffectiveWhitelist() {
        MerchantOpenApiAccessPolicy policy = new MerchantOpenApiAccessPolicy();
        MockHttpServletRequest request = requestFrom("203.0.113.10");

        assertDenied(service(policy), request, MerchantIpWhitelistDenyReason.WHITELIST_EMPTY,
                "Forbidden:merchant ip not allowed, requestIp: 203.0.113.10");
    }

    @Test
    void shouldIgnoreSpoofedGatewayClientIpWithoutAuthenticatedGateway() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP, "203.0.113.10");

        assertThat(service.checkAccess(MERCHANT_ID, request)).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldUseGatewayClientIpAfterGatewayAuthentication() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("203.0.113.10"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP, "203.0.113.10");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(service.checkAccess(MERCHANT_ID, request)).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldNotFallbackToGatewayNodeIpWhenTrustedHeaderIsMissing() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertDenied(service, request, MerchantIpWhitelistDenyReason.CLIENT_IP_MISSING,
                "Forbidden:merchant ip not allowed, requestIp: unknown");
    }

    @Test
    void shouldRequireExactWhitelistMatch() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("203.0.113.100"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        assertThatThrownBy(() -> service.checkAccess(MERCHANT_ID, request))
                .isInstanceOf(MerchantIpWhitelistAccessDeniedException.class)
                .hasMessage("Forbidden:merchant ip not allowed, requestIp: 203.0.113.10")
                .extracting(exception -> ((MerchantIpWhitelistAccessDeniedException) exception).getReason())
                .isEqualTo(MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED);
    }

    @Test
    void shouldRejectInvalidClientIpWithInternalReasonOnly() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("203.0.113.10"));
        MockHttpServletRequest request = requestFrom("invalid-ip");

        assertDenied(service, request, MerchantIpWhitelistDenyReason.CLIENT_IP_INVALID,
                "Forbidden:merchant ip not allowed, requestIp: unknown");
    }

    @Test
    void shouldNormalizeIpv6BeforeExactMatch() {
        MerchantIpWhitelistAccessService service = service(enabledPolicy("2001:db8:0:0:0:0:0:1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:0db8::1");

        assertThat(service.checkAccess(MERCHANT_ID, request)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    private MerchantIpWhitelistAccessService service(MerchantOpenApiAccessPolicy policy) {
        MerchantOpenApiAccessPolicyCacheService cacheService = mock(MerchantOpenApiAccessPolicyCacheService.class);
        when(cacheService.findPolicy(MERCHANT_ID)).thenReturn(policy);
        return new MerchantIpWhitelistAccessService(cacheService);
    }

    private MerchantOpenApiAccessPolicy enabledPolicy(String... allowedIps) {
        MerchantOpenApiAccessPolicy policy = new MerchantOpenApiAccessPolicy();
        policy.setAllowedIps(new LinkedHashSet<>(Set.of(allowedIps)));
        return policy;
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private void assertDenied(MerchantIpWhitelistAccessService service,
                              MockHttpServletRequest request,
                              MerchantIpWhitelistDenyReason reason,
                              String expectedMessage) {
        assertThatThrownBy(() -> service.checkAccess(MERCHANT_ID, request))
                .isInstanceOf(MerchantIpWhitelistAccessDeniedException.class)
                .hasMessage(expectedMessage)
                .extracting(exception -> ((MerchantIpWhitelistAccessDeniedException) exception).getReason())
                .isEqualTo(reason);
    }
}
