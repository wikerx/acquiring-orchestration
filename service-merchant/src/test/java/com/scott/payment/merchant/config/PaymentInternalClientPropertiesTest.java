package com.scott.payment.merchant.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证商户后台只以 service-merchant 身份签发交易写命令。 */
class PaymentInternalClientPropertiesTest {

    @Test
    void shouldRequireMerchantCallerAndStrongSecret() {
        PaymentInternalClientProperties properties = new PaymentInternalClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("short-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-merchant-payment-secret-at-least-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
