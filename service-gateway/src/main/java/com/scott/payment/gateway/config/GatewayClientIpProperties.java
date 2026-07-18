package com.scott.payment.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayClientIpProperties
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 网关客户端 IP 透传配置，位于 service-gateway 配置层，用于控制是否信任上游代理写入的转发头。
 * @status : create
 */
@ConfigurationProperties(prefix = "acquiring.gateway.client-ip")
public class GatewayClientIpProperties {

    /**
     * 是否信任 X-Forwarded-For。默认 false，避免公网请求伪造客户端 IP；部署在可信负载均衡之后时可显式开启。
     */
    private boolean trustForwardedHeader = false;

    /**
     * 获取是否信任转发头。
     *
     * @return true 表示信任 X-Forwarded-For
     */
    public boolean isTrustForwardedHeader() {
        return trustForwardedHeader;
    }

    /**
     * 设置是否信任转发头。
     *
     * @param trustForwardedHeader true 表示信任 X-Forwarded-For
     */
    public void setTrustForwardedHeader(boolean trustForwardedHeader) {
        this.trustForwardedHeader = trustForwardedHeader;
    }
}
