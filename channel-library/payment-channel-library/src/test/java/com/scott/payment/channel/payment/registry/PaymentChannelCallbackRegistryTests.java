package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.exception.ChannelException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentChannelCallbackRegistryTests {

    @Test
    void shouldRegisterCallbackHandlerByUppercaseCode() {
        PaymentChannelCallbackRegistry registry = new PaymentChannelCallbackRegistry(Optional.of(List.of(new StubHandler("mpgs"))));

        assertThat(registry.getRequired("MPGS").channelCode()).isEqualTo("mpgs");
        assertThat(registry.registeredHandlers()).containsKey("MPGS");
    }

    @Test
    void shouldRejectDuplicatedCallbackCode() {
        List<PaymentChannelCallbackHandler> handlers = List.of(new StubHandler("MPGS"), new StubHandler("mpgs"));

        assertThatThrownBy(() -> new PaymentChannelCallbackRegistry(Optional.of(handlers)))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("渠道回调编码重复：MPGS");
    }

    private static class StubHandler implements PaymentChannelCallbackHandler {

        private final String channelCode;

        private StubHandler(String channelCode) {
            this.channelCode = channelCode;
        }

        @Override
        public String channelCode() {
            return channelCode;
        }

        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            return new ChannelCallbackResult();
        }
    }
}
