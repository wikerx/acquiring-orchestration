package com.scott.payment.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminInternalClientProperties
 * @date : 2026-08-06 00:00
 * @description : service-merchant 调用 service-admin 内部访问配置接口的 HMAC 签名配置。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "merchant.admin-client")
public class AdminInternalClientProperties {

    /** 内部调用方标识。 */
    private String internalCaller = "service-merchant";

    /** HMAC 共享密钥，预发与生产必须由配置中心注入。 */
    private String internalSecret = "dev-internal-service-secret";
}
