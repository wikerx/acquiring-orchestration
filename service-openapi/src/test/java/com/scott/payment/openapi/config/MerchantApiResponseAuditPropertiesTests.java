package com.scott.payment.openapi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商户响应审计投递模式配置测试。
 *
 * <p>验证缺少 Nacos 配置时保留 HTTP 兼容路径，显式空配置则在启动阶段失败关闭。</p>
 */
class MerchantApiResponseAuditPropertiesTests {

    /**
     * 未配置投递模式时应默认使用历史 HTTP 回写路径，避免未完成 MQ 部署的环境启动后丢失审计。
     */
    @Test
    void shouldDefaultToHttpCompatibilityMode() {
        MerchantApiResponseAuditProperties properties = new MerchantApiResponseAuditProperties();

        assertThat(properties.getMode()).isEqualTo(MerchantApiResponseAuditProperties.Mode.HTTP);
    }

    /**
     * 显式绑定为空模式时应拒绝启动，避免静默选择错误交付语义。
     */
    @Test
    void shouldRejectNullDeliveryMode() {
        MerchantApiResponseAuditProperties properties = new MerchantApiResponseAuditProperties();
        properties.setMode(null);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mode is required");
    }
}
