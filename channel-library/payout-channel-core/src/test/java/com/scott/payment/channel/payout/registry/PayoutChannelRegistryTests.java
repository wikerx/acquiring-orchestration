package com.scott.payment.channel.payout.registry;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelRegistryTests
 * @date : 2026-08-12 00:00
 * @description : 代付 Provider 注册器测试，覆盖规范化定位、重复编码和缺失实现保护。
 * @status : create
 */
class PayoutChannelRegistryTests {

    @Test
    void shouldRegisterProviderByNormalizedCode() {
        PayoutChannelRegistry registry = new PayoutChannelRegistry(
                Optional.of(List.of(new StubPayoutChannelClient("thunes", Set.of(PayoutChannelCapability.SUBMIT)))));

        assertThat(registry.getRequired(" THUNES ").channelCode()).isEqualTo("thunes");
        assertThat(registry.registeredClients()).containsOnlyKeys("THUNES");
    }

    @Test
    void shouldRejectDuplicateProviderCode() {
        assertThatThrownBy(() -> new PayoutChannelRegistry(Optional.of(List.of(
                new StubPayoutChannelClient("THUNES", Set.of()),
                new StubPayoutChannelClient("thunes", Set.of())))))
                .isInstanceOf(PayoutChannelException.class)
                .hasMessageContaining("代付渠道编码重复：THUNES");
    }

    @Test
    void shouldRejectMissingProvider() {
        PayoutChannelRegistry registry = new PayoutChannelRegistry(Optional.empty());

        assertThatThrownBy(() -> registry.getRequired("DLOCAL"))
                .isInstanceOf(PayoutChannelException.class)
                .hasMessageContaining("未找到代付渠道客户端：DLOCAL");
    }

    static final class StubPayoutChannelClient implements PayoutChannelClient {

        private final String channelCode;
        private final Set<PayoutChannelCapability> capabilities;

        StubPayoutChannelClient(String channelCode, Set<PayoutChannelCapability> capabilities) {
            this.channelCode = channelCode;
            this.capabilities = capabilities;
        }

        @Override
        public String channelCode() {
            return channelCode;
        }

        @Override
        public Set<PayoutChannelCapability> capabilities() {
            return capabilities;
        }
    }
}
