package com.scott.payment.merchant.config;

import org.junit.jupiter.api.Test;

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
        properties.setPassword("short");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 bytes");
    }
}
