package com.scott.payment.openapi.config;

import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 OpenAPI 商户限流配置在应用启动阶段拒绝非法资源边界。 */
class OpenApiRateLimitPropertiesTests {

    @Test
    void shouldAcceptPositiveCompleteConfiguration() {
        assertThatCode(validProperties()::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidRequestWindowCapacityOrPolicy() {
        OpenApiRateLimitProperties properties = validProperties();
        properties.setMaxRequests(0);
        assertInvalid(properties);

        properties = validProperties();
        properties.setWindow(Duration.ofSeconds(-1));
        assertInvalid(properties);

        properties = validProperties();
        properties.setLocalMaxIdentities(0);
        assertInvalid(properties);

        properties = validProperties();
        properties.setFailurePolicy(null);
        assertInvalid(properties);
    }

    private OpenApiRateLimitProperties validProperties() {
        OpenApiRateLimitProperties properties = new OpenApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(200);
        properties.setWindow(Duration.ofMinutes(1));
        properties.setLocalMaxIdentities(10_000);
        properties.setFailurePolicy(RateLimitFailurePolicy.LOCAL_FALLBACK);
        return properties;
    }

    private void assertInvalid(OpenApiRateLimitProperties properties) {
        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }
}
