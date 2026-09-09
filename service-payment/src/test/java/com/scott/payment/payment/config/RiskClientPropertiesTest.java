package com.scott.payment.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Payment 调用 Risk 时仅在远程模式接受 service-payment 的独立强密钥。 */
class RiskClientPropertiesTest {

    @Test
    void shouldFailClosedInRemoteModeAndAllowExplicitLocalMode() {
        RiskClientProperties properties = new RiskClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setRemoteEnabled(false);
        assertThatCode(properties::validate).doesNotThrowAnyException();

        properties.setRemoteEnabled(true);
        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
