package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentChannelCallbackProtocolRegistryTests {

    @Test
    void shouldResolveProtocolCaseInsensitivelyAndRejectUnknownChannel() {
        StubProtocol protocol = new StubProtocol(Set.of("MPGS"), ChannelCallbackAuthentication.REQUIRED);
        PaymentChannelCallbackProtocolRegistry registry =
                new PaymentChannelCallbackProtocolRegistry(List.of(protocol));

        assertThat(registry.getRequired("mpgs")).isSameAs(protocol);
        assertThatThrownBy(() -> registry.getRequired("UNKNOWN"))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void shouldRejectDuplicateProviderCode() {
        assertThatThrownBy(() -> new PaymentChannelCallbackProtocolRegistry(List.of(
                new StubProtocol(Set.of("MPGS"), ChannelCallbackAuthentication.REQUIRED),
                new StubProtocol(Set.of("mpgs"), ChannelCallbackAuthentication.REQUIRED))))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void shouldEnforceHttpContractBeforeProviderCodeRuns() {
        PaymentChannelCallbackProtocolRegistry registry =
                new PaymentChannelCallbackProtocolRegistry(List.of(
                        new StubProtocol(Set.of("MPGS"), ChannelCallbackAuthentication.REQUIRED)));

        assertThatThrownBy(() -> registry.validateAndGet(request("GET", "application/json", "{}")))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("HTTP");
        assertThatThrownBy(() -> registry.validateAndGet(request("POST", "text/plain", "{}")))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("Content-Type");
        assertThatThrownBy(() -> registry.validateAndGet(request("POST", "application/json", "12345")))
                .isInstanceOf(ChannelRequestException.class)
                .hasMessageContaining("超过");
    }

    @Test
    void shouldFailClosedForProtocolWithUnsupportedAuthentication() {
        PaymentChannelCallbackProtocolRegistry registry =
                new PaymentChannelCallbackProtocolRegistry(List.of(
                        new StubProtocol(Set.of("WPGXML"), ChannelCallbackAuthentication.UNSUPPORTED)));

        assertThatThrownBy(() -> registry.validateAndGet(new RawChannelCallbackRequest(
                "WPGXML", ChannelCallbackScene.STANDARD, "POST", "/callback", null,
                Map.of("Content-Type", List.of("application/json")), new byte[0], "127.0.0.1")))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED));
    }

    /** 无签名渠道在主动查询确认链路落地前必须失败关闭，不能仅凭回调正文推进交易。 */
    @Test
    void shouldFailClosedForUnsignedProtocolUntilActiveQueryIsImplemented() {
        PaymentChannelCallbackProtocolRegistry registry =
                new PaymentChannelCallbackProtocolRegistry(List.of(
                        new StubProtocol(Set.of("UNSIGNED"),
                                ChannelCallbackAuthentication.NONE_WITH_ACTIVE_QUERY)));

        assertThatThrownBy(() -> registry.validateAndGet(new RawChannelCallbackRequest(
                "UNSIGNED", ChannelCallbackScene.STANDARD, "POST", "/callback", null,
                Map.of("Content-Type", List.of("application/json")), new byte[0], "127.0.0.1")))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED))
                .hasMessageContaining("主动查询");
    }

    private RawChannelCallbackRequest request(String method, String contentType, String body) {
        return new RawChannelCallbackRequest(
                "MPGS", ChannelCallbackScene.STANDARD, method, "/callback", null,
                Map.of("Content-Type", List.of(contentType)), body.getBytes(StandardCharsets.UTF_8), "127.0.0.1");
    }

    private record StubProtocol(Set<String> channelCodes,
                                ChannelCallbackAuthentication authentication)
            implements PaymentChannelCallbackProtocol {

        @Override
        public ChannelCallbackProtocolDescriptor descriptor() {
            return new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"), Set.of("application/json"), Set.of(ChannelCallbackScene.STANDARD),
                    4, authentication);
        }

        @Override
        public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                                   ChannelCallbackVerificationContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                      VerifiedChannelEvent event) {
            return ChannelCallbackAcknowledgement.plainText("OK");
        }
    }
}
