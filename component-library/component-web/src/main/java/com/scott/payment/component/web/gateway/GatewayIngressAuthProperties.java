package com.scott.payment.component.web.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressAuthProperties
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : Servlet 下游的 Gateway 入口验签配置，按受保护路径启用并从外部注入共享密钥。
 * @status : create
 */
@Component
@ConfigurationProperties(prefix = "acquiring.gateway-ingress")
public class GatewayIngressAuthProperties {

    private String secret;
    private long allowedClockSkewMillis = 60_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAllowedClockSkewMillis() {
        return allowedClockSkewMillis;
    }

    public void setAllowedClockSkewMillis(long allowedClockSkewMillis) {
        this.allowedClockSkewMillis = allowedClockSkewMillis;
    }

}
