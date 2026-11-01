package com.scott.payment.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商户资料安全配置，统一承载证件号和变更快照字段级加密密钥
 */
@Data
@ConfigurationProperties(prefix = "acquiring.merchant-profile")
public class MerchantProfileProperties {

    /**
     * Base64 编码的 AES-256 主密钥，敏感且禁止写入日志。
     */
    private String encryptionSecret;
}
