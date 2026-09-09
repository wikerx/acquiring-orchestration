package com.scott.payment.clearing.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPropertiesTest
 * @date : 2026-08-26 08:10
 * @email : scott_x@163.com
 * @description : 验证自动清分只接受正式28表拓扑，并拒绝非法超时、重试和消费线程参数。
 * @status : create
 */
class ClearingPropertiesTest {

    @Test
    void shouldAllowAutomaticRuntimeWithTwentyEightTableTopology() {
        ClearingProperties properties = new ClearingProperties();

        assertThatCode(() -> properties.validateRuntime(28)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRuntimeBeforeTwentyEightTableTopology() {
        ClearingProperties properties = new ClearingProperties();

        assertThatThrownBy(() -> properties.validateRuntime(25))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("28-table");
    }

    @Test
    void shouldRejectInvalidProcessingTimeout() {
        ClearingProperties properties = new ClearingProperties();
        properties.setProcessingTimeoutSeconds(0);

        assertThatThrownBy(() -> properties.validateRuntime(28))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void shouldRejectInvalidConsumerThreadBounds() {
        ClearingProperties properties = new ClearingProperties();
        properties.setConsumerMinThreads(32);
        properties.setConsumerMaxThreads(16);

        assertThatThrownBy(() -> properties.validateRuntime(28))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thread bounds");
    }
}
