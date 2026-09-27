package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileProperties
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户开户资料安全配置，当前仅承载相关人员证件号字段级加密密钥
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.merchant-profile")
public class MerchantProfileProperties {

    /** 董事、UBO 等人员证件号的字段级加密主密钥；敏感、运行时必配，不允许写入日志。 */
    private String encryptionSecret;
}
