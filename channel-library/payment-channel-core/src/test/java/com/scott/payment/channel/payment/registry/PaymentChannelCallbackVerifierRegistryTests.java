package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackVerifierRegistryTests
 * @date : 2026-08-12 00:00
 * @description : 回调验签注册器测试，覆盖 Provider 优先、迁移回退和重复渠道编码保护。
 * @status : create
 */
class PaymentChannelCallbackVerifierRegistryTests {

    @Test
    void shouldPreferProviderVerifierAndFallbackForUnknownChannel() {
        AtomicBoolean providerCalled = new AtomicBoolean();
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        PaymentChannelCallbackVerifier provider = verifier(Set.of("MPGS"), providerCalled);
        PaymentChannelCallbackVerifier fallback = verifier(Set.of(), fallbackCalled);
        PaymentChannelCallbackVerifierRegistry registry = new PaymentChannelCallbackVerifierRegistry(
                List.of(fallback, provider));

        registry.verify(request("mpgs"));
        assertThat(providerCalled).isTrue();
        assertThat(fallbackCalled).isFalse();

        registry.verify(request("NEW_PROVIDER"));
        assertThat(fallbackCalled).isTrue();
    }

    @Test
    void shouldRejectDuplicateNormalizedChannelCode() {
        assertThatThrownBy(() -> new PaymentChannelCallbackVerifierRegistry(List.of(
                verifier(Set.of("mpgs"), new AtomicBoolean()),
                verifier(Set.of(" MPGS "), new AtomicBoolean()))))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("MPGS");
    }

    @Test
    void shouldRejectDuplicateFallbackVerifier() {
        assertThatThrownBy(() -> new PaymentChannelCallbackVerifierRegistry(List.of(
                verifier(Set.of(), new AtomicBoolean()),
                verifier(Set.of(), new AtomicBoolean()))))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("回退实现重复");
    }

    private PaymentChannelCallbackVerifier verifier(Set<String> codes, AtomicBoolean called) {
        return new PaymentChannelCallbackVerifier() {
            @Override
            public Set<String> channelCodes() {
                return codes;
            }

            @Override
            public void verify(ChannelCallbackVerificationRequest request) {
                called.set(true);
            }
        };
    }

    private ChannelCallbackVerificationRequest request(String channelCode) {
        return new ChannelCallbackVerificationRequest(channelCode, "POST", "/callback", Map.of(),
                "{}", "secret", Map.of(), 300_000, 1_800_000_000_000L);
    }
}
