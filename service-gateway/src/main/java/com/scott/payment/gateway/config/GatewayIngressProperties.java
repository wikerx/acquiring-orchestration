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

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
