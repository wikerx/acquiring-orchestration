package com.scott.payment.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Payment 调用 Risk 时必须配置 service-payment 的独立强密钥。 */
class RiskClientPropertiesTest {

    @Test
    void shouldAlwaysRequireStrongInternalCredential() {
        RiskClientProperties properties = new RiskClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
