package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Admin 调用 Payment、Job 时固定服务身份并拒绝缺失或弱 HMAC 密钥。 */
class InternalClientCredentialPropertiesTest {

    @Test
    void paymentClientShouldRequireFixedCallerAndStrongSecret() {
        PaymentInternalClientProperties properties = new PaymentInternalClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("short-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-admin-payment-secret-at-least-32-bytes");
        properties.setInternalCaller("service-job");
        assertThatThrownBy(properties::validate).hasMessageContaining("caller must be service-admin");

        properties.setInternalCaller("service-admin");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void jobClientShouldValidateOnlyWhenRemoteInvocationIsEnabled() {
        JobSchedulerClientProperties properties = new JobSchedulerClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setRemoteEnabled(false);
        assertThatCode(properties::validate).doesNotThrowAnyException();

        properties.setRemoteEnabled(true);
        properties.setInternalSecret("unit-test-admin-job-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
