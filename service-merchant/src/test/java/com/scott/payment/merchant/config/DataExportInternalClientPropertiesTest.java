package com.scott.payment.merchant.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Merchant 调用 Data 异步导出服务的内部客户端配置门禁测试。
 */
class DataExportInternalClientPropertiesTest {

    /**
     * 缺少密钥、使用弱密钥或冒用其他服务身份时必须失败关闭。
     */
    @Test
    void shouldRequireMerchantCallerAndStrongSecret() {
        DataExportInternalClientProperties properties = new DataExportInternalClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("short-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-merchant-data-secret-at-least-32-bytes");
        properties.setInternalCaller("service-admin");
        assertThatThrownBy(properties::validate).hasMessageContaining("caller must be service-merchant");

        properties.setInternalCaller("service-merchant");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    /**
     * 非法地址和越界超时不得进入 Data 任务客户端。
     */
    @Test
    void shouldRejectInvalidEndpointAndTimeout() {
        DataExportInternalClientProperties properties = validProperties();
        properties.setBaseUrl("service-data");
        assertThatThrownBy(properties::validate).hasMessageContaining("absolute HTTP URL");

        properties.setBaseUrl("http://service-data");
        properties.setConnectTimeoutMillis(0);
        assertThatThrownBy(properties::validate).hasMessageContaining("must be positive");
    }

    /**
     * 构造满足身份和密钥要求的 Merchant 到 Data 客户端配置。
     */
    private DataExportInternalClientProperties validProperties() {
        DataExportInternalClientProperties properties = new DataExportInternalClientProperties();
        properties.setInternalSecret("unit-test-merchant-data-secret-at-least-32-bytes");
        return properties;
    }
}
