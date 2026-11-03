package com.scott.payment.merchant.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地开发默认登录凭据配置门禁测试。
 */
class MerchantDefaultLoginCredentialPropertiesTest {

    /**
     * 验证默认关闭时无需保存任何固定密码。
     */
    @Test
    void shouldAllowDisabledConfigurationWithoutPassword() {
        MerchantDefaultLoginCredentialProperties properties = new MerchantDefaultLoginCredentialProperties();

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
    }

    /**
     * 验证显式启用时必须提供足够长度的进程外密码。
     */
    @Test
    void shouldRejectEnabledConfigurationWithoutStrongPassword() {
        MerchantDefaultLoginCredentialProperties properties = new MerchantDefaultLoginCredentialProperties();
        properties.setEnabled(true);
        properties.setMerchantId("200045");
        properties.setPassword("short");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 bytes");
    }

    /**
     * 验证显式启用时必须指定唯一的本地种子商户号。
     */
    @Test
    void shouldRejectEnabledConfigurationWithoutMerchantId() {
        MerchantDefaultLoginCredentialProperties properties = new MerchantDefaultLoginCredentialProperties();
        properties.setEnabled(true);
        properties.setPassword("MerchantLocal@123456");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("merchant-id must be configured");
    }

    /**
     * 验证开发凭据可绑定到明确的种子商户号，避免多商户数据下随机选中账号。
     */
    @Test
    void shouldRetainConfiguredSeedMerchantId() {
        MerchantDefaultLoginCredentialProperties properties = new MerchantDefaultLoginCredentialProperties();
        properties.setMerchantId("200045");

        assertThat(properties.getMerchantId()).isEqualTo("200045");
    }
}
