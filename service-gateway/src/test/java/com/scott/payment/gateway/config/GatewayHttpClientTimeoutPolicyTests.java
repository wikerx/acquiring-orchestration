package com.scott.payment.gateway.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Gateway 下游 HTTP 超时缺失、非法或超过平台上限时失败关闭。 */
class GatewayHttpClientTimeoutPolicyTests {

    @Test
    void shouldAcceptPositiveTimeoutsAtThePlatformLimit() {
        assertThatCode(() -> GatewayHttpClientTimeoutPolicy.validate(3_000, Duration.ofSeconds(60)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingNonPositiveAndOverLimitTimeouts() {
        assertThatThrownBy(() -> GatewayHttpClientTimeoutPolicy.validate(null, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect timeout");
        assertThatThrownBy(() -> GatewayHttpClientTimeoutPolicy.validate(3_000, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response timeout");
        assertThatThrownBy(() -> GatewayHttpClientTimeoutPolicy.validate(0, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> GatewayHttpClientTimeoutPolicy.validate(3_000, Duration.ofSeconds(61)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("60000ms");
    }
}
