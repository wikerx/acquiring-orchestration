package com.scott.payment.channel.payment.scottchannela.adapter;

import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.scottchannela.client.PaymentApiClient;
import com.scott.payment.channel.payment.scottchannela.config.ChannelProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScottChannelA SPI 能力声明回归测试。
 **/
class ScottChannelAPaymentAdapterTests {

    @Test
    void shouldDeclarePublishedCardLifecycleCapabilities() {
        ChannelProperties properties = new ChannelProperties();
        ScottChannelAPaymentAdapter client = new ScottChannelAPaymentAdapter(
                new PaymentApiClient(properties));

        assertThat(client.capabilities()).containsExactlyInAnyOrder(
                ChannelCapability.PAYMENT,
                ChannelCapability.AUTHORIZATION,
                ChannelCapability.PRE_AUTHORIZATION,
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                ChannelCapability.CAPTURE,
                ChannelCapability.PRE_AUTH_COMPLETION,
                ChannelCapability.REFUND,
                ChannelCapability.VOID,
                ChannelCapability.QUERY);
        assertThat(client.capabilities())
                .doesNotContain(ChannelCapability.REVERSAL, ChannelCapability.THREE_DS_AUTHENTICATION);
    }
}
