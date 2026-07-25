package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentChannelRegistryTests {

    @Test
    void shouldRegisterChannelClientByUppercaseCode() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(new StubChannelClient("mpgs"))));

        assertThat(registry.getRequired("MPGS").channelCode()).isEqualTo("mpgs");
        assertThat(registry.registeredClients()).containsKey("MPGS");
    }

    @Test
    void shouldRejectDuplicatedChannelCode() {
        List<PaymentChannelClient> clients = List.of(new StubChannelClient("MPGS"), new StubChannelClient("mpgs"));

        assertThatThrownBy(() -> new PaymentChannelRegistry(Optional.of(clients)))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("渠道编码重复：MPGS");
    }

    @Test
    void shouldThrowUnsupportedOperationWhenCapabilityNotImplemented() {
        PaymentChannelClient client = new StubChannelClient("MPGS");

        assertThatThrownBy(() -> client.authorize(new ChannelAuthorizeRequest()))
                .isInstanceOf(ChannelUnsupportedOperationException.class)
                .hasMessageContaining("当前渠道[MPGS]不支持交易能力[AUTHORIZATION]");
    }

    @Test
    void defaultClientShouldSupportAnyPersistedQueryReference() {
        PaymentChannelClient client = new StubChannelClient("WPGXML");
        ChannelQueryRequest byRequestId = new ChannelQueryRequest();
        byRequestId.getExtension().put("requestId", "CR-LOCAL-001");
        ChannelQueryRequest byOrder = new ChannelQueryRequest();
        byOrder.setChannelOrderNo("ORDER-001");
        ChannelQueryRequest missing = new ChannelQueryRequest();

        assertThat(client.supportsQueryReference(byRequestId)).isTrue();
        assertThat(client.supportsQueryReference(byOrder)).isTrue();
        assertThat(client.supportsQueryReference(missing)).isFalse();
    }

    private static class StubChannelClient implements PaymentChannelClient {

        private final String channelCode;

        private StubChannelClient(String channelCode) {
            this.channelCode = channelCode;
        }

        @Override
        public String channelCode() {
            return channelCode;
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.QUERY);
        }
    }
}
