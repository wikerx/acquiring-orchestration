package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商户开户资料安全配置，当前仅承载相关人员证件号字段级加密密钥
 */
@Data
@ConfigurationProperties(prefix = "acquiring.merchant-profile")
public class MerchantProfileProperties {

    /**
     * 董事、UBO 等人员证件号的字段级加密主密钥；敏感、运行时必配，不允许写入日志。
     */
    private String encryptionSecret;
}
