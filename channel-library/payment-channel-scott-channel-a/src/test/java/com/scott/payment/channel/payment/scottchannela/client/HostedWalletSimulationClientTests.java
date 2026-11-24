package com.scott.payment.channel.payment.scottchannela.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostedWalletSimulationClientTests {

    private final HostedWalletSimulationClient client = new HostedWalletSimulationClient();

    @Test
    void acceptsAnyConfiguredMidOnlyForTheLocalScottChannelRoute() {
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090", "MID-200047"))
                .isTrue();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090/", "MID-200048"))
                .isTrue();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090", " "))
                .isFalse();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "https://channel.example", "MID-200047"))
                .isFalse();
        assertThat(client.supportsRoute("OTHER_CHANNEL", "http://127.0.0.1:58090", "MID-200047"))
                .isFalse();
    }
}
