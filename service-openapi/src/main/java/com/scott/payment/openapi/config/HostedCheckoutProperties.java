package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hosted Checkout 开放接口配置。
 */
@Data
@ConfigurationProperties(prefix = "openapi.hosted-checkout")
public class HostedCheckoutProperties {

    /**
     * 与 service-payment 保持一致的 token pepper，用于把浏览器 opaqueToken 转换为 tokenHash。
     */
    private String tokenPepper = "dev-hosted-checkout-token-pepper-change-me";

    /**
     * token hash 算法标识，当前实现固定为 HMAC-SHA256。
     */
    private String tokenHashAlg = "HMAC_SHA256";

    /**
     * 商户未传 expireMinutes 时的默认有效分钟数。
     */
    private int defaultExpireMinutes = 30;

    /**
     * 收银台会话最长有效分钟数。
     */
    private int maxExpireMinutes = 1440;

    /**
     * 默认最大支付尝试次数。
     */
    private int defaultMaxAttemptCount = 3;
}
