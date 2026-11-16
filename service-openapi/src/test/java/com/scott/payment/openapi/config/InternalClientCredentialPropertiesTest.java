package com.scott.payment.openapi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 OpenAPI 的 Payment、Payout 调用边分别执行失败关闭的 HMAC 配置门禁。
 */
class InternalClientCredentialPropertiesTest {

    @Test
    void paymentClientShouldAlwaysRequireItsOwnEdgeCredential() {
        PaymentClientProperties properties = new PaymentClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

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

    @Test
    void clientsShouldRejectZeroAndOverSixtySecondTimeouts() {
        PaymentClientProperties payment = new PaymentClientProperties();
        payment.setInternalSecret("unit-test-openapi-payment-secret-at-least-32-bytes");
        payment.setConnectTimeoutMillis(0);
        assertThatThrownBy(payment::validate).hasMessageContaining("positive");

        PayoutClientProperties payout = new PayoutClientProperties();
        payout.setInternalSecret("unit-test-openapi-payout-secret-at-least-32-bytes");
        payout.setReadTimeoutMillis(60_001);
        assertThatThrownBy(payout::validate).hasMessageContaining("60000ms");
    }

    @Test
    void paymentClientShouldRejectInvalidConnectionPoolBoundaries() {
        PaymentClientProperties properties = validPaymentProperties();
        properties.setMaxConnectionsTotal(0);
        assertThatThrownBy(properties::validate).hasMessageContaining("max connections total");

        properties = validPaymentProperties();
        properties.setMaxConnectionsPerRoute(properties.getMaxConnectionsTotal() + 1);
        assertThatThrownBy(properties::validate).hasMessageContaining("max connections per route");

        properties = validPaymentProperties();
        properties.setConnectionRequestTimeoutMillis(0);
        assertThatThrownBy(properties::validate).hasMessageContaining("connection request timeout");
    }

    @Test
    void paymentClientDefaultsShouldBoundDownstreamConcurrency() {
        PaymentClientProperties properties = validPaymentProperties();

        assertThat(properties.getMaxConnectionsTotal()).isEqualTo(32);
        assertThat(properties.getMaxConnectionsPerRoute()).isEqualTo(16);
        assertThat(properties.getConnectionRequestTimeoutMillis()).isEqualTo(10_000);
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    private PaymentClientProperties validPaymentProperties() {
        PaymentClientProperties properties = new PaymentClientProperties();
        properties.setInternalSecret("unit-test-openapi-payment-secret-at-least-32-bytes");
        return properties;
    }
}
