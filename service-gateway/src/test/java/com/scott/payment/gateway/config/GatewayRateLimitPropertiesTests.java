package com.scott.payment.gateway.config;

import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Gateway 限流配置在应用启动阶段拒绝非法资源边界。 */
class GatewayRateLimitPropertiesTests {

    @Test
    void shouldAcceptPositiveCompleteConfiguration() {
        GatewayRateLimitProperties properties = validProperties();

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidRequestWindowCapacityOrPolicy() {
        GatewayRateLimitProperties properties = validProperties();
        properties.setMaxRequests(0);
        assertInvalid(properties);

        properties = validProperties();
        properties.setWindow(Duration.ZERO);
        assertInvalid(properties);

        properties = validProperties();
        properties.setLocalMaxIdentities(0);
        assertInvalid(properties);

        properties = validProperties();
        properties.setFailurePolicy(null);
        assertInvalid(properties);
    }

    private GatewayRateLimitProperties validProperties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(200);
        properties.setWindow(Duration.ofMinutes(1));
        properties.setLocalMaxIdentities(10_000);
        properties.setFailurePolicy(RateLimitFailurePolicy.LOCAL_FALLBACK);
        return properties;
    }

    private void assertInvalid(GatewayRateLimitProperties properties) {
        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }
}
