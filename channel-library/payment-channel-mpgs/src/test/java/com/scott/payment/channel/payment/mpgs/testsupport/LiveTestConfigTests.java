package com.scott.payment.channel.payment.mpgs.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MPGS live 测试配置边界测试。
 */
class LiveTestConfigTests {

    @Test
    void shouldRejectNonOfficialCardBeforeCreatingChannelRequest() {
        assertThatThrownBy(() -> LiveTestConfig.requireOfficialCard("5555555555554444"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MPGS live tests only accept cards published in the official test-card catalog");
    }
}
