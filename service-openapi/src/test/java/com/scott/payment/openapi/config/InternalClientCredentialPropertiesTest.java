package com.scott.payment.openapi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 OpenAPI 的 Payment、Payout 调用边分别执行失败关闭的 HMAC 配置门禁。 */
class InternalClientCredentialPropertiesTest {

    @Test
    void paymentClientShouldValidateOnlyForRemoteMode() {
        PaymentClientProperties properties = new PaymentClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setRemoteEnabled(false);
        assertThatCode(properties::validate).doesNotThrowAnyException();

        properties.setRemoteEnabled(true);
        properties.setInternalSecret("unit-test-openapi-payment-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void payoutClientShouldRejectWeakSecretAndAcceptItsOwnEdgeCredential() {
        PayoutClientProperties properties = new PayoutClientProperties();
        properties.setInternalSecret("dev-internal-service-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-openapi-payout-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
