package com.scott.payment.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressProperties
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : service-gateway 收银台入口签名配置，共享密钥只允许通过环境变量或配置中心注入。
 * @status : create
 */
@ConfigurationProperties(prefix = "acquiring.gateway-ingress")
public class GatewayIngressProperties {

    /** 与两个收银台下游服务共享的环境专属 HMAC 密钥，不允许写入日志。 */
    private String secret;

    /** @return 外部注入的 HMAC 共享密钥 */
    public String getSecret() {
        return secret;
    }

    /** @param secret 外部注入的 HMAC 共享密钥 */
    public void setSecret(String secret) {
        this.secret = secret;
    }
}
