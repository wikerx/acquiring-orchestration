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
    private int defaultExpireMinutes = 1440;

    /**
     * 收银台会话最长有效分钟数。
     */
    private int maxExpireMinutes = 1440;

    /**
     * 默认最大支付尝试次数。
     */
    private int defaultMaxAttemptCount = 3;

    /** UAT/生产必须通过配置中心覆盖的 callback/redirect URL 加密密钥。 */
    private String sensitiveFieldEncryptionKey = "dev-hosted-checkout-field-key-change-me";

    /** callback/redirect URL 密钥版本，必须与 service-payment 同步轮换。 */
    private String sensitiveFieldKeyVersion = "dev-v1";
}
