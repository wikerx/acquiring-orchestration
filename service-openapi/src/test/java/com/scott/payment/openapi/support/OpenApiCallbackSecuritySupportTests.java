package com.scott.payment.openapi.support;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackProtocolRegistry;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenApiCallbackSecuritySupportTests {

    private static final String CHANNEL_CODE = "TEST_PROVIDER";
    private static final byte[] RAW_BODY = "{\"status\":\"SUCCESS\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldPassExactBodyAndAllHeaderValuesToProviderProtocol() {
        CapturingProtocol protocol = new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false);
        OpenApiCallbackSecuritySupport support = support(properties(), protocol, (channel, hint) -> "secret");
        MockHttpServletRequest request = request();
        request.addHeader("X-Provider-Value", "first");
        request.addHeader("X-Provider-Value", "second");

        OpenApiCallbackSecuritySupport.CallbackSecurityResult result = support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request, RAW_BODY);

        assertThat(protocol.request.get().body()).containsExactly(RAW_BODY);
        assertThat(protocol.request.get().headerValues("X-Provider-Value"))
                .containsExactly("first", "second");
        assertThat(result.event().callbackEventId()).isEqualTo("event-001");
        assertThat(result.signatureValid()).isTrue();
    }

    @Test
    void shouldFailClosedForUnknownProvider() {
        OpenApiCallbackSecuritySupport support = support(
                properties(), new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false),
                (channel, hint) -> "secret");

        assertThatThrownBy(() -> support.verifyChannelCallback(
                "UNKNOWN", ChannelCallbackScene.STANDARD, request(), RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("protocol is not configured");
    }

    @Test
    void shouldNotFallBackToEnvironmentSecretForMidScopedCallback() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelSecrets().put(CHANNEL_CODE, "environment-wide-secret");
        CapturingProtocol protocol = new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, true);
        OpenApiCallbackSecuritySupport support = support(properties, protocol, (channel, hint) -> null);

        assertThatThrownBy(() -> support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request(), RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SECRET_MISSING");
        assertThat(protocol.context.get().defaultSecret()).isNull();
    }

    @Test
    void shouldMapMidSecretLookupFailureToInternalServerError() {
        CapturingProtocol protocol = new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, true);
        OpenApiCallbackSecuritySupport support = support(
                properties(), protocol, (channel, hint) -> {
                    throw new IllegalStateException("database unavailable");
                });

        assertThatThrownBy(() -> support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request(), RAW_BODY))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("F500"));
    }

    @Test
    void shouldRejectProtocolWhoseAuthenticationIsNotReady() {
        CapturingProtocol protocol = new CapturingProtocol(ChannelCallbackAuthentication.UNSUPPORTED, false);
        OpenApiCallbackSecuritySupport support = support(properties(), protocol, (channel, hint) -> "secret");

        assertThatThrownBy(() -> support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request(), RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ALGORITHM_UNSUPPORTED");
    }

    @Test
    void shouldUseOnlyTrustedGatewayClientIpForWhitelist() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelAllowedIps().put(CHANNEL_CODE, List.of("192.0.2.10"));
        OpenApiCallbackSecuritySupport support = support(
                properties, new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false),
                (channel, hint) -> "secret");
        MockHttpServletRequest request = request();
        request.addHeader("X-Gateway-Client-Ip", "192.0.2.10");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request, RAW_BODY).ipAllowed()).isTrue();
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeadersWithoutAuthenticatedGateway() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelAllowedIps().put(CHANNEL_CODE, List.of("192.0.2.10"));
        OpenApiCallbackSecuritySupport support = support(
                properties, new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false),
                (channel, hint) -> "secret");
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.10");
        request.setRemoteAddr("198.51.100.20");

        assertThatThrownBy(() -> support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request, RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("source ip is not allowed");
    }

    @Test
    void shouldIgnoreGatewayClientIpHeaderAuthenticatedOnlyByLegacySignature() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelAllowedIps().put(CHANNEL_CODE, List.of("198.51.100.20"));
        OpenApiCallbackSecuritySupport support = support(
                properties, new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false),
                (channel, hint) -> "secret");
        MockHttpServletRequest request = request();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("X-Gateway-Client-Ip", "192.0.2.10");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request, RAW_BODY).ipAllowed()).isTrue();
        assertThat(support.resolveClientIp(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void shouldFailWhenWhitelistIsRequiredButMissing() {
        OpenApiCallbackProperties properties = properties();
        properties.setChannelIpWhitelistRequired(true);
        OpenApiCallbackSecuritySupport support = support(
                properties, new CapturingProtocol(ChannelCallbackAuthentication.REQUIRED, false),
                (channel, hint) -> "secret");

        assertThatThrownBy(() -> support.verifyChannelCallback(
                CHANNEL_CODE, ChannelCallbackScene.STANDARD, request(), RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("whitelist is required");
    }

    private OpenApiCallbackSecuritySupport support(OpenApiCallbackProperties properties,
                                                   PaymentChannelCallbackProtocol protocol,
                                                   ChannelCallbackSecretResolver resolver) {
        return new OpenApiCallbackSecuritySupport(
                properties,
                mock(SecurityInterceptEventRecorder.class),
                new PaymentChannelCallbackProtocolRegistry(List.of(protocol)),
                resolver);
    }

    private OpenApiCallbackProperties properties() {
        OpenApiCallbackProperties properties = new OpenApiCallbackProperties();
        properties.getChannelSecrets().put(CHANNEL_CODE, "secret");
        return properties;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/channel/v1/callbacks/" + CHANNEL_CODE);
        request.setContentType("application/json");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static final class CapturingProtocol implements PaymentChannelCallbackProtocol {

        private final ChannelCallbackAuthentication authentication;
        private final boolean midScoped;
        private final AtomicReference<RawChannelCallbackRequest> request = new AtomicReference<>();
        private final AtomicReference<ChannelCallbackVerificationContext> context = new AtomicReference<>();

        private CapturingProtocol(ChannelCallbackAuthentication authentication, boolean midScoped) {
            this.authentication = authentication;
            this.midScoped = midScoped;
        }

        @Override
        public Set<String> channelCodes() {
            return Set.of(CHANNEL_CODE);
        }

        @Override
        public ChannelCallbackProtocolDescriptor descriptor() {
            return new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"), Set.of("application/json"), Set.of(ChannelCallbackScene.STANDARD),
                    1024, authentication);
        }

        @Override
        public String credentialHint(RawChannelCallbackRequest request) {
            return midScoped ? "MID-001" : null;
        }

        @Override
        public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                                   ChannelCallbackVerificationContext context) {
            this.request.set(request);
            this.context.set(context);
            if (midScoped && (context.defaultSecret() == null || context.defaultSecret().isBlank())) {
                throw new ChannelCallbackVerificationException(
                        ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                        "provider callback secret is missing");
            }
            return new VerifiedChannelEvent(
                    CHANNEL_CODE, credentialHint(request), "event-001",
                    ChannelCallbackKind.FINANCIAL_TRANSACTION,
                    "order-001", "transaction-001", "SUCCESS", "SUCCESS",
                    null, "USD", "00", "Approved", Map.of());
        }

        @Override
        public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                      VerifiedChannelEvent event) {
            return ChannelCallbackAcknowledgement.platformSuccess("accepted");
        }
    }
}
