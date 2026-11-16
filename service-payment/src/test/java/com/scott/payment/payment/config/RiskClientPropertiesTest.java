package com.scott.payment.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Payment 调用 Risk 时必须配置 service-payment 的独立强密钥。
 */
class RiskClientPropertiesTest {

    /**
     * 验证内部调用始终要求独立强密钥。
     */
    @Test
    void shouldAlwaysRequireStrongInternalCredential() {
        RiskClientProperties properties = new RiskClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    /**
     * 验证连接和响应等待时间不能超过内部同步调用的统一上限。
     */
    @Test
    void shouldRejectUnboundedOrOverSixtySecondTimeouts() {
        RiskClientProperties properties = new RiskClientProperties();
        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        properties.setReadTimeoutMillis(60_001);

        assertThatThrownBy(properties::validate).hasMessageContaining("60000ms");
    }

    /**
     * 验证默认容量足以覆盖本机二十线程并发，同时保持所有等待和生命周期有界。
     */
    @Test
    void shouldUseBoundedConnectionPoolDefaults() {
        RiskClientProperties properties = validProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.getMaxConnectionsTotal()).isEqualTo(64);
        assertThat(properties.getMaxConnectionsPerRoute()).isEqualTo(32);
        assertThat(properties.getConnectionRequestTimeoutMillis()).isEqualTo(3_000);
        assertThat(properties.getValidateAfterInactivityMillis()).isEqualTo(5_000);
        assertThat(properties.getConnectionTimeToLiveMillis()).isEqualTo(300_000);
        assertThat(properties.getIdleConnectionEvictMillis()).isEqualTo(30_000);
    }

    /**
     * 验证单路由容量不得超过连接池总容量。
     */
    @Test
    void shouldRejectPerRouteCapacityAboveTotalCapacity() {
        RiskClientProperties properties = validProperties();
        properties.setMaxConnectionsTotal(16);
        properties.setMaxConnectionsPerRoute(17);

        assertThatThrownBy(properties::validate)
                .hasMessageContaining("max connections per route");
    }

    /**
     * 验证连接获取等待不能配置为无界或超出内部调用预算。
     */
    @Test
    void shouldRejectInvalidConnectionRequestTimeout() {
        RiskClientProperties properties = validProperties();
        properties.setConnectionRequestTimeoutMillis(60_001);

        assertThatThrownBy(properties::validate)
                .hasMessageContaining("connection request timeout");
    }

    /**
     * 构造通过内部身份校验的客户端配置。
     *
     * @return 带独立测试密钥的配置
     */
    private RiskClientProperties validProperties() {
        RiskClientProperties properties = new RiskClientProperties();
        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        return properties;
    }
}
