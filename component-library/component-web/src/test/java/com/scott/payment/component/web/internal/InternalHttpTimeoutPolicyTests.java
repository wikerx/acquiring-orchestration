package com.scott.payment.component.web.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一内部 HTTP 超时边界测试。
 */
class InternalHttpTimeoutPolicyTests {

    @Test
    void shouldAcceptPositiveTimeoutsAtThePlatformLimit() {
        assertThatCode(() -> InternalHttpTimeoutPolicy.validate("test-client", 3_000, 60_000))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectZeroNegativeAndOverLimitTimeouts() {
        assertThatThrownBy(() -> InternalHttpTimeoutPolicy.validate("test-client", 0, 30_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> InternalHttpTimeoutPolicy.validate("test-client", 3_000, 60_001))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("60000ms");
    }
}
