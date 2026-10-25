package com.scott.payment.data.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证商户回调 HTTP 客户端遵守平台统一超时上限。 */
class DataMerchantNotificationConfigTests {

    @Test
    void shouldAcceptDefaultTimeoutBudget() {
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();

        assertThat(properties.getConnectTimeoutMillis()).isEqualTo(3_000);
        assertThat(properties.getReadTimeoutMillis()).isEqualTo(10_000);
        assertThatCode(() -> new DataMerchantNotificationConfig().validateTimeouts(properties))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectConnectionOrReadTimeoutAbovePlatformLimit() {
        DataMerchantNotificationProperties connectTooLong = new DataMerchantNotificationProperties();
        connectTooLong.setConnectTimeoutMillis(60_001);
        DataMerchantNotificationProperties readTooLong = new DataMerchantNotificationProperties();
        readTooLong.setReadTimeoutMillis(60_001);

        assertThatThrownBy(() -> new DataMerchantNotificationConfig().validateTimeouts(connectTooLong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("60000ms");
        assertThatThrownBy(() -> new DataMerchantNotificationConfig().validateTimeouts(readTooLong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("60000ms");
    }
}
