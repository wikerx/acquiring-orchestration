package com.scott.payment.data.config.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Data 调用 Merchant 导出来源的内部客户端配置门禁测试。
 *
 * <p>确保调用方身份、独立密钥、服务地址和超时在发起请求前已满足约束。</p>
 */
class DataMerchantClientPropertiesTest {

    /**
     * 缺少密钥、使用弱密钥或冒用其他服务身份时必须失败关闭。
     */
    @Test
    void shouldRequireDataCallerAndStrongSecret() {
        DataMerchantClientProperties properties = new DataMerchantClientProperties();
        assertThatThrownBy(properties::validate).hasMessageContaining("secret is required");

        properties.setInternalSecret("short-secret");
        assertThatThrownBy(properties::validate).hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-data-merchant-secret-at-least-32-bytes");
        properties.setInternalCaller("service-admin");
        assertThatThrownBy(properties::validate).hasMessageContaining("caller must be service-data");

        properties.setInternalCaller("service-data");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    /**
     * 非法地址和越界超时不得进入 Merchant 来源客户端。
     */
    @Test
    void shouldRejectInvalidEndpointAndTimeout() {
        DataMerchantClientProperties properties = validProperties();
        properties.setBaseUrl("service-merchant");
        assertThatThrownBy(properties::validate).hasMessageContaining("absolute HTTP URL");

        properties.setBaseUrl("http://service-merchant");
        properties.setReadTimeoutMillis(60_001);
        assertThatThrownBy(properties::validate).hasMessageContaining("must not exceed");
    }

    /**
     * 构造满足身份和密钥要求的 Data 到 Merchant 客户端配置。
     */
    private DataMerchantClientProperties validProperties() {
        DataMerchantClientProperties properties = new DataMerchantClientProperties();
        properties.setInternalSecret("unit-test-data-merchant-secret-at-least-32-bytes");
        return properties;
    }
}
