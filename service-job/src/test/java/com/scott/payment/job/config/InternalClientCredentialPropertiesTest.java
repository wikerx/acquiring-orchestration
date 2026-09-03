package com.scott.payment.job.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Job 调用 Data、Payment 时使用固定 service-job 身份和独立强密钥。 */
class InternalClientCredentialPropertiesTest {

    @Test
    void dataClientShouldRejectMissingWeakAndUnexpectedCredentials() {
        DataInternalClientProperties properties = new DataInternalClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("dev-internal-service-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-job-data-secret-at-least-32-bytes");
        properties.setInternalCaller("service-admin");
        assertThatThrownBy(properties::validate).hasMessageContaining("caller must be service-job");
    }

    @Test
    void paymentClientShouldAcceptOnlyTheJobPaymentEdgeCredential() {
        PaymentInternalClientProperties properties = new PaymentInternalClientProperties();
        properties.setInternalSecret("unit-test-job-payment-secret-at-least-32-bytes");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
